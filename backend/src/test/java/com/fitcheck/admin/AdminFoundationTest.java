package com.fitcheck.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.TestAccounts;
import com.fitcheck.ai.AiUsage;
import com.fitcheck.ai.AiUsageRepository;
import com.fitcheck.ai.AiUsageService;
import com.fitcheck.auth.JwtService;
import com.fitcheck.common.exception.TooManyRequestsException;
import com.fitcheck.style.StyleDescriber;
import com.fitcheck.style.UserStyle;
import com.fitcheck.style.UserStyleRepository;
import com.fitcheck.stylist.FakeStylistClient;
import com.fitcheck.stylist.StylistClient;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 8.1: who is admin (the ADMIN_EMAILS setting + a confirmed email), the admin area being
 * invisible to everyone else, frozen accounts being cut off at once, last login/activity, and
 * admins having no AI limits. The admin setting here is test-only.
 */
@SpringBootTest(properties = "fitcheck.admin.emails= boss@example.com , Second.Admin@Example.com")
@AutoConfigureMockMvc
@Transactional
class AdminFoundationTest {

    @TestConfiguration
    static class FakeClaude {
        @Bean
        @Primary
        StylistClient fakeStylistClient() {
            return new FakeStylistClient();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwt;

    @Autowired
    private AdminAccess adminAccess;

    @Autowired
    private FrozenAccounts frozenAccounts;

    @Autowired
    private AdminAudit audit;

    @Autowired
    private AdminAuditRepository auditEntries;

    @Autowired
    private AiUsageService aiUsage;

    @Autowired
    private AiUsageRepository aiUsageRows;

    @Autowired
    private UserStyleRepository styles;

    @Autowired
    private StyleDescriber describer;

    private User account(String email, boolean confirmed) {
        User user = new User(email, passwordEncoder.encode("long-enough-1"));
        if (confirmed) {
            user.markEmailVerified(Instant.now());
        }
        return users.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
    }

    // ---- who is admin ----

    @Test
    void onlyAConfirmedListedAccountIsAdminInAnyLetterCase() {
        assertThat(adminAccess.isAdmin(account("boss@example.com", true))).isTrue();
        assertThat(adminAccess.isAdmin(account("second.admin@example.com", true))).isTrue();
        assertThat(adminAccess.isAdmin(account("someone@example.com", true))).isFalse();
    }

    @Test
    void signingUpWithTheAdminsEmailDoesNotMakeYouAdminUntilItIsConfirmed() {
        assertThat(adminAccess.isAdmin(account("boss@example.com", false))).isFalse();
    }

    @Test
    void theAdminAreaIsOpenToTheAdminAndShownInTheirProfile() throws Exception {
        User boss = account("boss@example.com", true);

        mvc.perform(get("/api/admin/me").header("Authorization", bearer(boss)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
        mvc.perform(get("/api/users/me").header("Authorization", bearer(boss)))
                .andExpect(jsonPath("$.admin").value(true));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"boss@example.com\",\"password\":\"long-enough-1\"}"))
                .andExpect(jsonPath("$.user.admin").value(true));
    }

    @Test
    void forEveryoneElseTheAdminAreaLooksExactlyLikeAPageThatDoesNotExist() throws Exception {
        User regular = account("regular@example.com", true);
        User unconfirmedBoss = account("boss@example.com", false);

        String unknown = mvc.perform(get("/api/admin/me-not-here-either").header("Authorization", bearer(regular)))
                .andReturn().getResponse().getContentAsString();
        for (User user : new User[] {regular, unconfirmedBoss}) {
            mvc.perform(get("/api/admin/me").header("Authorization", bearer(user)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No endpoint api/admin/me"));
        }
        // Same shape as a truly unknown endpoint (only the path differs).
        assertThat(JsonPath.<String>read(unknown, "$.message")).isEqualTo("No endpoint api/admin/me-not-here-either");
        mvc.perform(get("/api/users/me").header("Authorization", bearer(regular)))
                .andExpect(jsonPath("$.admin").value(false));
        // Not logged in: 401, like the rest of the API.
        mvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
    }

    // ---- frozen accounts ----

    private void freeze(User user) {
        user.freeze("Testing", Instant.now());
        users.saveAndFlush(user);
        frozenAccounts.markFrozen(user.getId()); // undone automatically when the test rolls back
    }

    @Test
    void aFrozenAccountIsCutOffOnTheVeryNextRequest() throws Exception {
        User user = account("frozen@example.com", true);
        String token = bearer(user);
        mvc.perform(get("/api/clothing").header("Authorization", token)).andExpect(status().isOk());

        freeze(user);

        // The same, still unexpired token no longer works.
        mvc.perform(get("/api/clothing").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void aFrozenAccountCannotLogInButAPasswordGuesserLearnsNothing() throws Exception {
        User user = account("frozen2@example.com", true);
        freeze(user);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"frozen2@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email, username or password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"frozen2@example.com\",\"password\":\"long-enough-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("This account has been suspended")));
    }

    @Test
    void aFrozenAccountsSessionCannotBeRenewed() throws Exception {
        MockHttpServletResponse registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestAccounts.registerJson("frozen3@example.com", "long-enough-1")))
                .andReturn().getResponse();
        Cookie refresh = registered.getCookie("fitcheck_refresh");
        freeze(users.findByEmailIgnoreCase("frozen3@example.com").orElseThrow());

        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void aFrozenAdminIsNoLongerAdmin() throws Exception {
        User boss = account("boss@example.com", true);
        freeze(boss);
        assertThat(adminAccess.isAdmin(boss.getId())).isFalse();
    }

    // ---- last login / activity ----

    @Test
    void loginsAndSessionRefreshesAreRecorded() throws Exception {
        MockHttpServletResponse registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestAccounts.registerJson("seen@example.com", "long-enough-1")))
                .andReturn().getResponse();
        User fresh = users.findByEmailIgnoreCase("seen@example.com").orElseThrow();
        assertThat(fresh.getLastLoginAt()).isNull(); // signing up isn't a login

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"seen@example.com\",\"password\":\"long-enough-1\"}")).andExpect(status().isOk());
        assertThat(users.findByEmailIgnoreCase("seen@example.com").orElseThrow().getLastLoginAt()).isNotNull();

        mvc.perform(post("/api/auth/refresh").cookie(registered.getCookie("fitcheck_refresh")))
                .andExpect(status().isOk());
        User after = users.findByEmailIgnoreCase("seen@example.com").orElseThrow();
        assertThat(after.getLastActiveAt()).isAfterOrEqualTo(after.getLastLoginAt());
    }

    // ---- no AI limits for the admin ----

    @Test
    void theAdminHasNoDailyStylistLimitButOthersDo() {
        User boss = account("boss@example.com", true);
        User regular = account("regular2@example.com", true);
        for (int i = 0; i < 20; i++) {
            aiUsageRows.save(new AiUsage(boss, Instant.now()));
            aiUsageRows.save(new AiUsage(regular, Instant.now()));
        }

        aiUsage.checkAndRecord(boss.getId()); // the 21st: fine
        assertThatThrownBy(() -> aiUsage.checkAndRecord(regular.getId())).isInstanceOf(TooManyRequestsException.class);

        AiUsageService.UsageSummary summary = aiUsage.summary(boss.getId());
        assertThat(summary.unlimited()).isTrue();
        assertThat(summary.used()).isEqualTo(21); // still counted, for the cost overview
        assertThat(aiUsage.summary(regular.getId()).unlimited()).isFalse();
    }

    @Test
    void theAdminHasNoDailyCapOnStyleNotes() {
        User boss = account("boss@example.com", true);
        UserStyle style = new UserStyle(boss.getId());
        style.changeNote("Minimal");
        style.markChanged();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < 10; i++) {
            style.countDescription(today); // today's 10 already used
        }
        styles.saveAndFlush(style);

        assertThat(describer.describeIfStale(boss.getId())).isEqualTo(StyleDescriber.Outcome.WRITTEN);
    }

    // ---- audit log ----

    @Test
    void auditEntriesKeepWhoDidWhatToWhom() {
        User boss = account("boss@example.com", true);
        User target = account("target@example.com", true);

        audit.record(boss, "TEST_ACTION", target, "because");

        AdminAuditEntry entry = auditEntries.findAllByTargetUserIdOrderByCreatedAtDesc(target.getId(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().getFirst();
        assertThat(entry.getAdminEmail()).isEqualTo("boss@example.com");
        assertThat(entry.getTargetEmail()).isEqualTo("target@example.com");
        assertThat(entry.getAction()).isEqualTo("TEST_ACTION");
        assertThat(entry.getDetails()).isEqualTo("because");
    }

    /**
     * Runs WITHOUT the test's transaction, like an admin action someone forgot to make
     * transactional: the audit log refuses, so an action can never happen without its log line
     * being part of the same all-or-nothing change.
     */
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void anAuditEntryCanOnlyBeWrittenAsPartOfTheActionItself() {
        User boss = new User("boss@example.com", "hash"); // not saved: nothing to clean up
        User target = new User("target@example.com", "hash");

        assertThatThrownBy(() -> audit.record(boss, "TEST_ACTION", target, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
