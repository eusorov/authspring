package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.service.PersonalAccessTokenService;
import com.jayway.jsonpath.JsonPath;
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
class SecureRouteIT {

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

    @Autowired
    private PersonalAccessTokenService personalAccessTokenService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user");
        userRepository.save(user);
    }

    @Test
    void securedTestEndpointWithValidJwtReturnsOk() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/login")
                        .header(API_VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/secured/test")
                        .header(API_VERSION, "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("secured-ok"))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void securedTestEndpointWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/secured/test").header(API_VERSION, "1")).andExpect(status().isUnauthorized());
    }

    /**
     * JWT alone is not enough: the matching {@code personal_access_tokens} row must exist. Logout removes
     * that row, so the same syntactically valid JWT must not authenticate afterward.
     */
    @Test
    void securedTestEndpointAfterPatRevokedReturns401EvenWithValidJwt() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/login")
                        .header(API_VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/logout")
                        .header(API_VERSION, "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/secured/test")
                        .header(API_VERSION, "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securedTestEndpointAfterPatDeletedByServiceReturns401EvenWithValidJwt() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/login")
                        .header(API_VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        personalAccessTokenService.revokeByJwtCompact(token);

        mockMvc.perform(get("/api/secured/test")
                        .header(API_VERSION, "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
