package com.authspring.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String token;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PasswordResetToken() {}

    public PasswordResetToken(String email, String token, Instant createdAt) {
        this.email = email;
        this.token = token;
        this.createdAt = createdAt;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
