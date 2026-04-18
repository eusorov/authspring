package com.authspring.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PasswordResetId implements Serializable {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "token", nullable = false, length = 255)
    private String token;

    protected PasswordResetId() {}

    public PasswordResetId(String email, String token) {
        this.email = email;
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PasswordResetId that = (PasswordResetId) o;
        return Objects.equals(email, that.email) && Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, token);
    }
}
