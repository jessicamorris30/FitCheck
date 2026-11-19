package com.fitcheck.auth;

import com.fitcheck.auth.dto.RegisterRequest;
import com.fitcheck.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints under /api/auth. These are public (see SecurityConfig).
 *
 * <p>Step 3 has register only. Login, refresh and logout come in steps 4 and 5, and register
 * will then also log the new user in (return tokens) instead of just returning the user.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register
     * 201 Created + the new user, 400 if the body is invalid, 409 if the email is taken.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request));
    }
}
