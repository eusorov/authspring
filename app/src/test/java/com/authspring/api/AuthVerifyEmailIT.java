package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
class AuthVerifyEmailIT {

    private static final String API_VERSION = "API-Version";
    private static final String SIGNING_KEY = "test-verification-signing-key-32chars!!";

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
        userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user"));
    }

    @Test
    void verifyEmailRedirectsWithJwtInLocation() throws Exception {
        User user = userRepository.findByEmail("ada@example.com").orElseThrow();
        Long id = user.getId();
        String email = user.getEmail();
        String hash = sha1Hex(email);
        long expires = Instant.now().getEpochSecond() + 3600;

        String fullUrl = "http://localhost/api/email/verify/" + id + "/" + hash;
        String original = fullUrl + "?expires=" + expires;
        String signature = hmacSha256Hex(original, SIGNING_KEY);

        mockMvc.perform(get("/api/email/verify/{id}/{hash}", id, hash)
                        .header(API_VERSION, "1")
                        .queryParam("expires", Long.toString(expires))
                        .queryParam("signature", signature))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("email_verified=1")))
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("api_token=")));

        User reloaded = userRepository.findById(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(reloaded.getEmailVerifiedAt());
    }

    @Test
    void verifyEmailInvalidSignatureReturns403() throws Exception {
        User user = userRepository.findByEmail("ada@example.com").orElseThrow();
        Long id = user.getId();
        String hash = sha1Hex(user.getEmail());

        mockMvc.perform(get("/api/email/verify/{id}/{hash}", id, hash)
                        .header(API_VERSION, "1")
                        .queryParam("expires", Long.toString(Instant.now().getEpochSecond() + 3600))
                        .queryParam("signature", "invalid"))
                .andExpect(status().isForbidden());
    }

    private static String sha1Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(d);
    }

    private static String hmacSha256Hex(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(out);
    }
}
