package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.domain.PasswordResetToken;
import com.authspring.api.domain.User;
import com.authspring.api.repo.PasswordResetTokenRepository;
import com.authspring.api.repo.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthResetPasswordIT {

    private static final String API_VERSION = "API-Version";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user");
        userRepository.save(user);
    }

    @Test
    void resetPasswordSuccessAndLoginWithNewPassword() throws Exception {
        String plainToken = "plain-reset-token";
        passwordResetTokenRepository.save(new PasswordResetToken(
                "ada@example.com", passwordEncoder.encode(plainToken), Instant.now()));

        mockMvc.perform(multipart("/api/reset-password")
                        .param("token", plainToken)
                        .param("email", "ada@example.com")
                        .param("password", "newsecret12")
                        .param("confirmed", "newsecret12")
                        .header(API_VERSION, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Your password has been reset."));

        mockMvc.perform(post("/api/login")
                        .header(API_VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"password\":\"newsecret12\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordUnknownUserReturns422() throws Exception {
        mockMvc.perform(multipart("/api/reset-password")
                        .param("token", "t")
                        .param("email", "nobody@example.com")
                        .param("password", "newsecret12")
                        .param("confirmed", "newsecret12")
                        .header(API_VERSION, "1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.email[0]")
                        .value("We can't find a user with that email address."));
    }

    @Test
    void resetPasswordInvalidTokenReturns422() throws Exception {
        passwordResetTokenRepository.save(new PasswordResetToken(
                "ada@example.com", passwordEncoder.encode("expected-token"), Instant.now()));

        mockMvc.perform(multipart("/api/reset-password")
                        .param("token", "wrong-token")
                        .param("email", "ada@example.com")
                        .param("password", "newsecret12")
                        .param("confirmed", "newsecret12")
                        .header(API_VERSION, "1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.email[0]").value("This password reset token is invalid."));
    }
}
