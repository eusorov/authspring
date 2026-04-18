package com.authspring.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_resets")
public class PasswordReset {

    @EmbeddedId
    private PasswordResetId id;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PasswordReset() {}

    public PasswordReset(PasswordResetId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public PasswordResetId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
