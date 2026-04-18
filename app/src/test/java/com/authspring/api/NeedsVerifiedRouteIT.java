package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;
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
class NeedsVerifiedRouteIT {

    private static final String API_VERSION = "API-Version";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void withoutBearerReturns401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/needsverified").header(API_VERSION, "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }

    @Test
    void withValidPatButUnverifiedEmailReturns403() throws Exception {
        MvcResult reg =
                mockMvc.perform(multipart("/api/register")
                                .param("name", "Ada")
                                .param("email", "Ada@Example.com")
                                .param("password", "newsecret12")
                                .param("password_confirmation", "newsecret12")
                                .header(API_VERSION, "1"))
                        .andExpect(status().isOk())
                        .andReturn();
        String token = JsonPath.read(reg.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/needsverified")
                        .header(API_VERSION, "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You must verify your email"));
    }

    @Test
    void withValidPatAndVerifiedEmailReturns200() throws Exception {
        User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user");
        user.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        MvcResult login =
                mockMvc.perform(post("/api/login")
                                .header(API_VERSION, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/needsverified")
                        .header(API_VERSION, "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("needsverified-ok"));
    }
}
