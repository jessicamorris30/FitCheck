package com.fitcheck.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * JSON body of {@code POST /api/auth/login}: {@code { "login": "<email or username>", "password" }}.
 *
 * <p>{@code "email"} is still accepted as the field name (@JsonAlias), so nothing that logs in
 * the old way breaks. Only "not blank" is checked here: format rules belong to registration; at
 * login we just compare against what's stored.
 *
 * @param login an email (anything containing '@') or a username
 */
public record LoginRequest(
        @JsonAlias("email") @NotBlank(message = "Email or username is required") String login,
        @NotBlank(message = "Password is required") String password) {

    public LoginRequest {
        if (login != null) {
            login = login.trim();
        }
    }
}
