package com.authspring.api.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration body: {@code name}, {@code email}, {@code password}, {@code password_confirmation}.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, message = "The password must be at least 8 characters.") String password,
        @NotBlank String password_confirmation) {

    @AssertTrue(message = "The password field confirmation does not match.")
    public boolean isPasswordMatching() {
        return password != null && password.equals(password_confirmation);
    }
}
