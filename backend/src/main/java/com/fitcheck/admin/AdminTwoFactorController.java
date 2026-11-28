package com.fitcheck.admin;

import com.fitcheck.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2-step login for admin, under /api/admin/2fa (admins only; AdminGuard lets these through
 * without the "code entered" pass, since they're how you get it).
 */
@RestController
@RequestMapping("/api/admin/2fa")
public class AdminTwoFactorController {

    public record CodeRequest(@NotBlank(message = "Enter the code") String code) {
    }

    public record DisableRequest(@NotBlank(message = "Password is required") String password,
            @NotBlank(message = "Enter the code") String code) {
    }

    /** @param unlocked whether this browser entered a code recently (admin pages are open) */
    public record StatusResponse(boolean enabled, boolean unlocked, long recoveryCodesLeft) {
    }

    public record RecoveryCodesResponse(List<String> recoveryCodes) {
    }

    private final AdminTwoFactorService twoFactor;
    private final AdminPassCookie pass;

    public AdminTwoFactorController(AdminTwoFactorService twoFactor, AdminPassCookie pass) {
        this.twoFactor = twoFactor;
        this.pass = pass;
    }

    /** GET /status: is 2-step on, and is this browser unlocked right now? */
    @GetMapping("/status")
    public StatusResponse status(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest request) {
        AdminTwoFactorService.Status s = twoFactor.status(me.id());
        return new StatusResponse(s.enabled(), s.enabled() && pass.isValidFor(request, me.id()), s.recoveryCodesLeft());
    }

    /** POST /setup: {secret, otpauthUri} to show as a QR code. 409 if already on. */
    @PostMapping("/setup")
    public AdminTwoFactorService.Setup setup(@AuthenticationPrincipal AuthenticatedUser me) {
        return twoFactor.startSetup(me.id());
    }

    /** POST /enable {code}: backup codes (shown once), and this browser is unlocked. */
    @PostMapping("/enable")
    public ResponseEntity<RecoveryCodesResponse> enable(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody CodeRequest request) {
        List<String> codes = twoFactor.enable(me.id(), request.code());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, pass.issue(me.id()).toString())
                .body(new RecoveryCodesResponse(codes));
    }

    /** POST /verify {code}: 204, this browser is unlocked for 8 hours. A backup code works too. */
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody CodeRequest request) {
        twoFactor.verify(me.id(), request.code());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, pass.issue(me.id()).toString()).build();
    }

    /** POST /disable {password, code}: 204, 2-step off and this browser locked again. */
    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody DisableRequest request) {
        twoFactor.disable(me.id(), request.password(), request.code());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, pass.clear().toString()).build();
    }

    /** POST /recovery-codes {code}: new backup codes (the old ones stop working). */
    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse renewRecoveryCodes(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody CodeRequest request) {
        return new RecoveryCodesResponse(twoFactor.regenerateRecoveryCodes(me.id(), request.code()));
    }
}
