package com.authspring.api.web.dto;

import com.authspring.api.domain.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        Long id,
        String name,
        String email,
        @JsonProperty("email_verified_at") Instant emailVerifiedAt,
        @JsonProperty("date_closed") LocalDate dateClosed,
        String role,
        @JsonProperty("remember_token") String rememberToken,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getEmailVerifiedAt(),
                user.getDateClosed(),
                user.getRole(),
                user.getRememberToken(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
