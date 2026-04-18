package com.authspring.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthSchemaPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private EntityManager entityManager;

    @Test
    void user_roundTrip() {
        User user = new User("Ada", "ada@example.com", "hash", "admin");
        user.setEmailVerifiedAt(Instant.parse("2025-01-01T00:00:00Z"));
        user.setDateClosed(LocalDate.of(2026, 1, 1));
        user.setRememberToken("remember");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User loaded = entityManager.find(User.class, user.getId());
        assertThat(loaded.getEmail()).isEqualTo("ada@example.com");
        assertThat(loaded.getRole()).isEqualTo("admin");
        assertThat(loaded.getRememberToken()).isEqualTo("remember");
    }

    @Test
    void passwordReset_roundTrip() {
        PasswordResetId id = new PasswordResetId("u@example.com", "tok");
        PasswordReset row = new PasswordReset(id, Instant.parse("2025-06-01T12:00:00Z"));
        entityManager.persist(row);
        entityManager.flush();
        entityManager.clear();

        PasswordReset loaded = entityManager.find(PasswordReset.class, id);
        assertThat(loaded.getCreatedAt()).isEqualTo(Instant.parse("2025-06-01T12:00:00Z"));
    }

    @Test
    void passwordResetToken_roundTrip() {
        PasswordResetToken prt = new PasswordResetToken(
                "u@example.com", "secret", Instant.parse("2025-06-02T08:00:00Z"));
        entityManager.persist(prt);
        entityManager.flush();
        entityManager.clear();

        PasswordResetToken loaded = entityManager.find(PasswordResetToken.class, "u@example.com");
        assertThat(loaded.getToken()).isEqualTo("secret");
    }
}
