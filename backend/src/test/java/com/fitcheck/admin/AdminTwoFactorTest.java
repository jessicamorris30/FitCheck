package com.fitcheck.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 8.2: 2-step login for admin. Codes are computed here exactly like an authenticator app
 * would, from the secret the setup hands out. Rate limits are ON in this class (to test the
 * "5 wrong codes" lock); the admin setting is test-only.
 */
@SpringBootTest(properties = {
        "fitcheck.admin.emails=boss@example.com,deputy@example.com",
        "fitcheck.rate-limit.enabled=true"})
@AutoConfigureMockMvc
@Transactional
class AdminTwoFactorTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwt;

    @Autowired
    private RecoveryCodeRepository recoveryCodes;

    @Autowired
    private AdminAuditRepository audit;

    private User boss;

    @BeforeEach
    void setUp() {
        boss = account("boss@example.com");
    }

    private User account(String email) {
        User user = new User(email, passwordEncoder.encode("long-enough-1"));
        user.markEmailVerified(Instant.now());
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
    }

    private static long now() {
        return Totp.stepAt(Instant.now().getEpochSecond());
    }

    private ResultActions post2fa(User user, String path, String json, Cookie... cookies) throws Exception {
        var request = post("/api/admin/2fa/" + path).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request);
    }

    /** Runs the whole setup like the admin would; returns {secret, admin pass cookie, backup codes}. */
    private Object[] setUpTwoStep(User user) throws Exception {
        String secret = JsonPath.read(post2fa(user, "setup", null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.secret");
        MockHttpServletResponse enabled = post2fa(user, "enable", "{\"code\":\"" + Totp.code(secret, now()) + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse();
        List<String> backup = JsonPath.read(enabled.getContentAsString(), "$.recoveryCodes");
        return new Object[] {secret, enabled.getCookie(AdminPassCookie.NAME), backup};
    }

    // ---- setup ----

    @Test
    void withoutTwoStepTheAdminPagesAskForItToBeSetUp() throws Exception {
        mvc.perform(get("/api/admin/me").header("Authorization", bearer(boss)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AdminGuard.NEEDS_SETUP));
        mvc.perform(get("/api/admin/2fa/status").header("Authorization", bearer(boss)))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.unlocked").value(false));
    }

    @Test
    void setupGivesASecretAndOnlyAWorkingCodeSwitchesItOn() throws Exception {
        String body = post2fa(boss, "setup", null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(body, "$.secret");
        assertThat(secret).matches("[A-Z2-7]{32}");
        assertThat(JsonPath.<String>read(body, "$.otpauthUri")).startsWith("otpauth://totp/FitCheck").contains(secret);

        post2fa(boss, "enable", "{\"code\":\"000000\"}".replace("000000", wrongCodeFor(secret)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").value(AdminTwoFactorService.WRONG_CODE));
        assertThat(users.findById(boss.getId()).orElseThrow().isTotpEnabled()).isFalse();

        MockHttpServletResponse enabled = post2fa(boss, "enable", "{\"code\":\"" + Totp.code(secret, now()) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes", hasSize(10)))
                .andExpect(jsonPath("$.recoveryCodes[0]").value(org.hamcrest.Matchers.matchesPattern("[A-Z2-9]{5}-[A-Z2-9]{5}")))
                .andReturn().getResponse();

        // Setting it up also unlocks this browser: a locked-down cookie for admin paths only.
        String setCookie = enabled.getHeader("Set-Cookie");
        assertThat(setCookie).contains(AdminPassCookie.NAME + "=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/admin").contains("Max-Age=28800");
        // Backup codes are stored only as hashes, and the secret is never shown again.
        assertThat(recoveryCodes.countByUser_IdAndUsedAtIsNull(boss.getId())).isEqualTo(10);
        String status = mvc.perform(get("/api/admin/2fa/status").header("Authorization", bearer(boss)))
                .andReturn().getResponse().getContentAsString();
        assertThat(status).doesNotContain(secret);
        post2fa(boss, "setup", null).andExpect(status().isConflict());
        assertThat(audit.findAllByTargetUserIdOrderByCreatedAtDesc(boss.getId(),
                org.springframework.data.domain.PageRequest.of(0, 5)).getContent())
                .anyMatch(e -> e.getAction().equals("2FA_ENABLED"));
    }

    // ---- unlocking ----

    @Test
    void theAdminPagesOpenOnlyWithThePass() throws Exception {
        Object[] setup = setUpTwoStep(boss);
        Cookie pass = (Cookie) setup[1];

        mvc.perform(get("/api/admin/me").header("Authorization", bearer(boss)).cookie(pass))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/me").header("Authorization", bearer(boss)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(AdminGuard.NEEDS_CODE));
        // The pass alone is useless: it needs the same admin's login too.
        mvc.perform(get("/api/admin/me").cookie(pass)).andExpect(status().isUnauthorized());
    }

    @Test
    void aCodeUnlocksButCannotBeUsedTwice() throws Exception {
        String secret = (String) setUpTwoStep(boss)[0];
        String next = Totp.code(secret, now() + 1); // the setup already used the current step

        MockHttpServletResponse verified = post2fa(boss, "verify", "{\"code\":\"" + next + "\"}")
                .andExpect(status().isNoContent()).andReturn().getResponse();
        mvc.perform(get("/api/admin/me").header("Authorization", bearer(boss))
                        .cookie(verified.getCookie(AdminPassCookie.NAME)))
                .andExpect(status().isOk());

        post2fa(boss, "verify", "{\"code\":\"" + next + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").value(org.hamcrest.Matchers.containsString("already used")));
    }

    @Test
    void aBackupCodeWorksOnceAndTypingIsForgiving() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> backup = (List<String>) setUpTwoStep(boss)[2];
        String code = backup.getFirst();

        post2fa(boss, "verify", "{\"code\":\" " + code.toLowerCase().replace("-", " ") + " \"}")
                .andExpect(status().isNoContent());
        post2fa(boss, "verify", "{\"code\":\"" + code + "\"}").andExpect(status().isBadRequest());
        assertThat(recoveryCodes.countByUser_IdAndUsedAtIsNull(boss.getId())).isEqualTo(9);
    }

    @Test
    void fiveWrongCodesLockCodeEntryEvenForTheRightCode() throws Exception {
        String secret = (String) setUpTwoStep(boss)[0];
        for (int i = 0; i < AdminTwoFactorService.MAX_WRONG_CODES; i++) {
            post2fa(boss, "verify", "{\"code\":\"" + wrongCodeFor(secret) + "\"}").andExpect(status().isBadRequest());
        }
        post2fa(boss, "verify", "{\"code\":\"" + Totp.code(secret, now() + 1) + "\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Too many wrong codes")));
    }

    @Test
    void onePassCannotBeUsedByAnotherAdmin() throws Exception {
        Cookie bossPass = (Cookie) setUpTwoStep(boss)[1];
        User deputy = account("deputy@example.com");
        setUpTwoStep(deputy);

        mvc.perform(get("/api/admin/me").header("Authorization", bearer(deputy)).cookie(bossPass))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPassAndALoginTokenCanNeverStandInForEachOther() {
        String pass = jwt.createAdmin2faPass(boss.getId(), Duration.ofHours(8));
        String access = jwt.createAccessToken(boss.getId(), boss.getEmail());

        assertThat(jwt.parseAccessToken(pass)).isEmpty();
        assertThat(jwt.parseAdmin2faPass(access)).isEmpty();
        assertThat(jwt.parseAdmin2faPass(pass)).contains(boss.getId());
        assertThat(jwt.parseAdmin2faPass(jwt.createAdmin2faPass(boss.getId(), Duration.ofSeconds(-5)))).isEmpty();
    }

    @Test
    void nonAdminsCannotEvenSeeTheTwoStepEndpoints() throws Exception {
        User regular = new User("regular@example.com", "hash");
        regular.markEmailVerified(Instant.now());
        regular = users.saveAndFlush(regular);

        post2fa(regular, "setup", null).andExpect(status().isNotFound());
    }

    // ---- turning it off, logging out ----

    @Test
    void turningItOffNeedsThePasswordAndACode() throws Exception {
        String secret = (String) setUpTwoStep(boss)[0];

        post2fa(boss, "disable", "{\"password\":\"wrong\",\"code\":\"" + Totp.code(secret, now() + 1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Incorrect password"));
        assertThat(users.findById(boss.getId()).orElseThrow().isTotpEnabled()).isTrue();

        MockHttpServletResponse off = post2fa(boss, "disable",
                        "{\"password\":\"long-enough-1\",\"code\":\"" + Totp.code(secret, now() + 1) + "\"}")
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(off.getHeader("Set-Cookie")).contains(AdminPassCookie.NAME + "=").contains("Max-Age=0");
        assertThat(users.findById(boss.getId()).orElseThrow().isTotpEnabled()).isFalse();
        assertThat(recoveryCodes.countByUser_IdAndUsedAtIsNull(boss.getId())).isZero();
    }

    @Test
    void loggingOutLocksTheAdminPagesAgain() throws Exception {
        MockHttpServletResponse loggedOut = mvc.perform(post("/api/auth/logout")).andReturn().getResponse();
        assertThat(loggedOut.getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith(AdminPassCookie.NAME + "=") && c.contains("Max-Age=0"));
    }

    @Test
    void newBackupCodesReplaceTheOldOnes() throws Exception {
        Object[] setup = setUpTwoStep(boss);
        @SuppressWarnings("unchecked")
        List<String> old = (List<String>) setup[2];

        post2fa(boss, "recovery-codes", "{\"code\":\"" + Totp.code((String) setup[0], now() + 1) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes", hasSize(10)));
        post2fa(boss, "verify", "{\"code\":\"" + old.get(3) + "\"}").andExpect(status().isBadRequest());
    }

    /** A 6-digit code that isn't valid for any step the server accepts right now. */
    private static String wrongCodeFor(String secret) {
        long n = now();
        for (int candidate = 0; ; candidate++) {
            String code = String.format("%06d", candidate);
            if (Totp.matchingStep(secret, code, n) < 0) {
                return code;
            }
        }
    }
}
