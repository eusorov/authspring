package com.authspring.api.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reset-password body: {@code token}, {@code email}, {@code password}, {@code confirmed} (password confirmation).
 */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "The password must be at least 8 characters.") String password,
        @NotBlank String confirmed) {

    @AssertTrue(message = "The password field confirmation does not match.")
    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmed);
    }
}
