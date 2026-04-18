# API forgot-password (send reset link) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/forgot-password` that mirrors Laravel `PasswordResetLinkController::store` and  downstream mail (`MailResetPasswordNotification`): validate `email`, persist a bcrypt-hashed token in `password_reset_tokens`, send email with a shaped frontend URL (plain reset token + short-lived JWT as `api_token`).

**Architecture:** New **`PasswordResetLinkService`** (send link) stays separate from **`PasswordResetService`** (consume token on `POST /api/reset-password`). Reuse **`UserRepository`**, **`PasswordResetTokenRepository`**, **`PasswordEncoder`**. Add **`spring-boot-starter-mail`**, **`JavaMailSender`**, and small **`@ConfigurationProperties`** records for JWT TTL extension, frontend base URL, and mail copy. **`JwtService`** gains **`createPasswordResetFlowToken(User)`** using **`jwt.password-reset-expiration-ms`**.

**Tech stack:** Spring Boot 4, Spring MVC (`API-Version: 1`), JPA, BCrypt, jjwt, Jakarta Mail, JUnit 5 + Mockito, Spring Boot Test + Testcontainers PostgreSQL + MockMvc.

---

## File structure

| File | Responsibility |
|------|----------------|
| `app/build.gradle` | Add `spring-boot-starter-mail`. |
| `app/src/main/resources/application.yml` | `jwt.password-reset-expiration-ms`, `spring.mail.*`, `app.frontend.base-url`, `app.mail.password-reset.*`. |
| `app/src/test/resources/application-test.yml` | Add `jwt.password-reset-expiration-ms` so `JwtProperties` binds under `@ActiveProfiles("test")`. |
| `app/src/main/java/com/authspring/api/config/JwtProperties.java` | Add `long passwordResetExpirationMs` to the record; bind `jwt.password-reset-expiration-ms`. |
| `app/src/main/java/com/authspring/api/config/FrontendProperties.java` | `app.frontend.base-url` → `baseUrl`. |
| `app/src/main/java/com/authspring/api/config/PasswordResetMailProperties.java` | Subject, greeting template, lines, footers, from, expiry minutes. |
| `app/src/main/java/com/authspring/AuthspringApplication.java` | Register new `@ConfigurationProperties` types. |
| `app/src/main/java/com/authspring/api/security/JwtService.java` | `createPasswordResetFlowToken(User)`. |
| `app/src/main/java/com/authspring/api/security/SecurityConfig.java` | `permitAll` `POST /api/forgot-password`. |
| `app/src/main/java/com/authspring/api/web/dto/ForgotPasswordRequest.java` | `email` validation. |
| `app/src/main/java/com/authspring/api/service/SendPasswordResetLinkOutcome.java` | `Sent`, `UserNotFound`. |
| `app/src/main/java/com/authspring/api/service/PasswordResetLinkService.java` | Token generation, persist row, call mail. |
| `app/src/main/java/com/authspring/api/service/PasswordResetEmailSender.java` | Build URL + plain-text email. |
| `app/src/main/java/com/authspring/api/web/ForgotPasswordController.java` | `POST /api/forgot-password`. |
| `app/src/test/java/com/authspring/api/service/PasswordResetLinkServiceTest.java` | Unit tests (Mockito). |
| `app/src/test/java/com/authspring/api/AuthForgotPasswordIT.java` | MockMvc + `@MockBean JavaMailSender`. |

---

### Task 1: Dependencies, JWT record, YAML, configuration properties registration

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/resources/application.yml`
- Modify: `app/src/main/java/com/authspring/api/config/JwtProperties.java`
- Create: `app/src/main/java/com/authspring/api/config/FrontendProperties.java`
- Create: `app/src/main/java/com/authspring/api/config/PasswordResetMailProperties.java`
- Modify: `app/src/main/java/com/authspring/AuthspringApplication.java`
- Modify: `app/src/test/resources/application-test.yml` (JWT third field for test profile)

- [ ] **Step 1: Add mail starter**

In `app/build.gradle`, inside `dependencies {`:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

- [ ] **Step 2: Extend `JwtProperties`**

Replace the record with:

```java
package com.authspring.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expirationMs, long passwordResetExpirationMs) {}
```

- [ ] **Step 3: Add `FrontendProperties`**

Create `app/src/main/java/com/authspring/api/config/FrontendProperties.java`:

```java
package com.authspring.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {}
```

- [ ] **Step 4: Add `PasswordResetMailProperties`**

Create `app/src/main/java/com/authspring/api/config/PasswordResetMailProperties.java`:

```java
package com.authspring.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.password-reset")
public record PasswordResetMailProperties(
        String subject,
        String greetingTemplate,
        List<String> lines,
        String actionLabel,
        List<String> footerLines,
        String salutation,
        int expiryMinutes,
        String fromAddress,
        String fromName) {}
```

- [ ] **Step 5: Register properties beans**

Update `app/src/main/java/com/authspring/AuthspringApplication.java`:

```java
package com.authspring;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.config.JwtProperties;
import com.authspring.api.config.PasswordResetMailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.authspring.api")
@EnableConfigurationProperties({JwtProperties.class, FrontendProperties.class, PasswordResetMailProperties.class})
public class AuthspringApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthspringApplication.class, args);
    }
}
```

- [ ] **Step 6: Append to `application.yml`**

After the existing `jwt:` block, ensure it contains three keys (add `password-reset-expiration-ms`). Example full `jwt` section:

```yaml
jwt:
  secret: ${JWT_SECRET:change-me-use-at-least-32-bytes-for-hs256-dev-only!!}
  expiration-ms: 86400000
  password-reset-expiration-ms: ${JWT_PASSWORD_RESET_EXPIRATION_MS:3600000}
```

Append `spring.mail`, `app.frontend`, and `app.mail`:

```yaml
spring:
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: false
          starttls:
            enable: false

app:
  frontend:
    base-url: ${FRONTEND_CORS:http://localhost:3000}
  mail:
    password-reset:
      subject: ${PASSWORD_RESET_EMAIL_SUBJECT:Reset Your Password}
      greeting-template: "Hello {name}!"
      lines:
        - ${PASSWORD_RESET_EMAIL_LINE_1:You are receiving this email because we received a password reset request for your account.}
        - ${PASSWORD_RESET_EMAIL_LINE_2:Click the link below to reset your password. You will be redirected to our secure reset page.}
      action-label: ${PASSWORD_RESET_EMAIL_ACTION:Reset Password}
      footer-lines:
        - ${PASSWORD_RESET_EMAIL_FOOTER_1:This password reset link will expire in {minutes} minutes.}
        - ${PASSWORD_RESET_EMAIL_FOOTER_2:If you did not request a password reset, no further action is required.}
      salutation: ${PASSWORD_RESET_EMAIL_SALUTATION:Best regards, Team}
      expiry-minutes: ${PASSWORD_RESET_EMAIL_EXPIRY:60}
      from-address: ${MAIL_FROM_ADDRESS:noreply@example.com}
      from-name: ${MAIL_FROM_NAME:Team}
```

- [ ] **Step 7: Extend test profile JWT block**

In `app/src/test/resources/application-test.yml`, append `password-reset-expiration-ms` next to existing `jwt` keys:

```yaml
jwt:
  secret: test-jwt-secret-must-be-at-least-32-characters
  expiration-ms: 3600000
  password-reset-expiration-ms: 3600000
```

- [ ] **Step 8: Compile**

Run: `./gradlew :app:compileJava`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle app/src/main/resources/application.yml app/src/test/resources/application-test.yml \
  app/src/main/java/com/authspring/api/config/JwtProperties.java \
  app/src/main/java/com/authspring/api/config/FrontendProperties.java \
  app/src/main/java/com/authspring/api/config/PasswordResetMailProperties.java \
  app/src/main/java/com/authspring/AuthspringApplication.java
git commit -m "chore: mail starter and password-reset mail/jwt config"
```

---

### Task 2: JWT — short-lived token for email `api_token`

**Files:**
- Modify: `app/src/main/java/com/authspring/api/security/JwtService.java`

- [ ] **Step 1: Add method**

Add imports if missing: `java.util.Date`, `com.authspring.api.domain.User`.

Add method:

```java
public String createPasswordResetFlowToken(User user) {
    Instant now = Instant.now();
    Instant exp = now.plusMillis(properties.passwordResetExpirationMs());
    return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("purpose", "password_reset")
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(signingKey)
            .compact();
}
```

- [ ] **Step 2: Compile and test**

Run: `./gradlew :app:test`  
Expected: BUILD SUCCESSFUL (existing tests still pass)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/security/JwtService.java
git commit -m "feat(jwt): short-lived token for password-reset email URL"
```

---

### Task 3: DTO + outcomes

**Files:**
- Create: `app/src/main/java/com/authspring/api/web/dto/ForgotPasswordRequest.java`
- Create: `app/src/main/java/com/authspring/api/service/SendPasswordResetLinkOutcome.java`

- [ ] **Step 1: Create `ForgotPasswordRequest`**

```java
package com.authspring.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank @Email String email) {}
```

- [ ] **Step 2: Create `SendPasswordResetLinkOutcome`**

```java
package com.authspring.api.service;

public sealed interface SendPasswordResetLinkOutcome
        permits SendPasswordResetLinkOutcome.Sent, SendPasswordResetLinkOutcome.UserNotFound {

    record Sent() implements SendPasswordResetLinkOutcome {}

    record UserNotFound() implements SendPasswordResetLinkOutcome {}
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/web/dto/ForgotPasswordRequest.java \
  app/src/main/java/com/authspring/api/service/SendPasswordResetLinkOutcome.java
git commit -m "feat: forgot-password DTO and outcomes"
```

---

### Task 4: `PasswordResetEmailSender`

**Files:**
- Create: `app/src/main/java/com/authspring/api/service/PasswordResetEmailSender.java`

- [ ] **Step 1: Implement (URL + plain text)**

Create `app/src/main/java/com/authspring/api/service/PasswordResetEmailSender.java`:

```java
package com.authspring.api.service;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.config.PasswordResetMailProperties;
import com.authspring.api.domain.User;
import com.authspring.api.security.JwtService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailSender {

    private final JavaMailSender mailSender;
    private final JwtService jwtService;
    private final FrontendProperties frontend;
    private final PasswordResetMailProperties mail;

    public PasswordResetEmailSender(
            JavaMailSender mailSender,
            JwtService jwtService,
            FrontendProperties frontend,
            PasswordResetMailProperties mail) {
        this.mailSender = mailSender;
        this.jwtService = jwtService;
        this.frontend = frontend;
        this.mail = mail;
    }

    public void send(User user, String plainToken) throws MessagingException {
        String apiToken = jwtService.createPasswordResetFlowToken(user);
        String url = buildResetUrl(user, plainToken, apiToken);
        String body = buildBody(user, url);

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
        helper.setFrom(mail.fromAddress(), mail.fromName());
        helper.setTo(user.getEmail());
        helper.setSubject(mail.subject());
        helper.setText(body, false);
        mailSender.send(mime);
    }

    private String buildResetUrl(User user, String plainToken, String apiToken) {
        String base = frontend.baseUrl().replaceAll("/$", "");
        return base
                + "/?new_password=1&password_reset_token="
                + urlEncode(plainToken)
                + "&email="
                + urlEncode(user.getEmail())
                + "&api_token="
                + urlEncode(apiToken)
                + "&user_id="
                + user.getId()
                + "&user_name="
                + urlEncode(user.getName());
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String buildBody(User user, String url) {
        String greeting = mail.greetingTemplate().replace("{name}", user.getName());
        StringBuilder sb = new StringBuilder();
        sb.append(greeting).append("\n\n");
        for (String line : mail.lines()) {
            sb.append(line).append("\n\n");
        }
        sb.append(mail.actionLabel()).append(":\n").append(url).append("\n\n");
        for (String footer : mail.footerLines()) {
            String line = footer.replace("{minutes}", String.valueOf(mail.expiryMinutes()));
            sb.append(line).append("\n\n");
        }
        sb.append(mail.salutation());
        return sb.toString();
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileJava`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/service/PasswordResetEmailSender.java
git commit -m "feat: password reset email with style link"
```

---

### Task 5: `PasswordResetLinkService` + unit tests (TDD)

**Files:**
- Create: `app/src/main/java/com/authspring/api/service/PasswordResetLinkService.java`
- Create: `app/src/test/java/com/authspring/api/service/PasswordResetLinkServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/authspring/api/service/PasswordResetLinkServiceTest.java`:

```java
package com.authspring.api.service;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.domain.PasswordResetToken;
import com.authspring.api.domain.User;
import com.authspring.api.repo.PasswordResetTokenRepository;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.web.dto.ForgotPasswordRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetLinkServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetEmailSender passwordResetEmailSender;

    @InjectMocks
    private PasswordResetLinkService passwordResetLinkService;

    @Test
    void unknownEmail_returnsUserNotFound() throws Exception {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());

        var outcome = passwordResetLinkService.send(new ForgotPasswordRequest("A@B.COM"));

        assertInstanceOf(SendPasswordResetLinkOutcome.UserNotFound.class, outcome);
        verify(passwordResetTokenRepository, never()).save(any());
        verify(passwordResetEmailSender, never()).send(any(), any());
    }

    @Test
    void knownUser_savesHashedTokenAndSendsEmail() throws Exception {
        User user = new User("Ada", "ada@example.com", "hash", "user");
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("hashed-token");

        var outcome = passwordResetLinkService.send(new ForgotPasswordRequest("ada@example.com"));

        assertInstanceOf(SendPasswordResetLinkOutcome.Sent.class, outcome);
        ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetEmailSender).send(eq(user), plainCaptor.capture());
        String plain = plainCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(64, plain.length());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ada@example.com", tokenCaptor.getValue().getEmail());
        org.junit.jupiter.api.Assertions.assertEquals("hashed-token", tokenCaptor.getValue().getToken());
    }
}
```

- [ ] **Step 2: Run tests (expect failure until implementation)**

Run: `./gradlew :app:test --tests 'com.authspring.api.service.PasswordResetLinkServiceTest'`  
Expected: compile error or test failure until Step 3

- [ ] **Step 3: Implement `PasswordResetLinkService`**

Create `app/src/main/java/com/authspring/api/service/PasswordResetLinkService.java`:

```java
package com.authspring.api.service;

import com.authspring.api.domain.PasswordResetToken;
import com.authspring.api.domain.User;
import com.authspring.api.repo.PasswordResetTokenRepository;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.web.dto.ForgotPasswordRequest;
import jakarta.mail.MessagingException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUM =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender passwordResetEmailSender;

    public PasswordResetLinkService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetEmailSender passwordResetEmailSender) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetEmailSender = passwordResetEmailSender;
    }

    @Transactional
    public SendPasswordResetLinkOutcome send(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return new SendPasswordResetLinkOutcome.UserNotFound();
        }
        String plain = randomToken(64);
        String hash = passwordEncoder.encode(plain);
        passwordResetTokenRepository.save(new PasswordResetToken(email, hash, Instant.now()));
        try {
            passwordResetEmailSender.send(user, plain);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send password reset email", e);
        }
        return new SendPasswordResetLinkOutcome.Sent();
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:test --tests 'com.authspring.api.service.PasswordResetLinkServiceTest'`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/authspring/api/service/PasswordResetLinkService.java \
  app/src/test/java/com/authspring/api/service/PasswordResetLinkServiceTest.java
git commit -m "feat: password reset link service"
```

---

### Task 6: Controller + security

**Files:**
- Create: `app/src/main/java/com/authspring/api/web/ForgotPasswordController.java`
- Modify: `app/src/main/java/com/authspring/api/security/SecurityConfig.java`

- [ ] **Step 1: Create `ForgotPasswordController`**

Create `app/src/main/java/com/authspring/api/web/ForgotPasswordController.java`:

```java
package com.authspring.api.web;

import com.authspring.api.service.PasswordResetLinkService;
import com.authspring.api.service.SendPasswordResetLinkOutcome;
import com.authspring.api.web.dto.ForgotPasswordRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class ForgotPasswordController {

    private final PasswordResetLinkService passwordResetLinkService;

    public ForgotPasswordController(PasswordResetLinkService passwordResetLinkService) {
        this.passwordResetLinkService = passwordResetLinkService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> store(@Valid @RequestBody ForgotPasswordRequest request) {
        return switch (passwordResetLinkService.send(request)) {
            case SendPasswordResetLinkOutcome.Sent() -> ResponseEntity.ok(
                    Map.of("status", "We have e-mailed your password reset link!"));
            case SendPasswordResetLinkOutcome.UserNotFound() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
                    .body(userNotFoundProblem());
        };
    }

    private static ProblemDetail userNotFoundProblem() {
        String msg = "We can't find a user with that e-mail address.";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), msg);
        pd.setTitle("Password reset failed");
        pd.setProperty("message", "The given data was invalid.");
        pd.setProperty("errors", Map.of("email", List.of(msg)));
        return pd;
    }
}
```

- [ ] **Step 2: Permit endpoint**

In `app/src/main/java/com/authspring/api/security/SecurityConfig.java`, add alongside other `permitAll` lines:

```java
.requestMatchers(HttpMethod.POST, "/api/forgot-password").permitAll()
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:test`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/authspring/api/web/ForgotPasswordController.java \
  app/src/main/java/com/authspring/api/security/SecurityConfig.java
git commit -m "feat: POST /api/forgot-password"
```

---

### Task 7: Integration test

**Files:**
- Create: `app/src/test/java/com/authspring/api/AuthForgotPasswordIT.java`

- [ ] **Step 1: Add IT**

Create `app/src/test/java/com/authspring/api/AuthForgotPasswordIT.java`:

```java
package com.authspring.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.domain.User;
import com.authspring.api.repo.PasswordResetTokenRepository;
import com.authspring.api.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
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
class AuthForgotPasswordIT {

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

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user"));
    }

    @Test
    void forgotPasswordSendsMailAndPersistsToken() throws Exception {
        mockMvc.perform(post("/api/forgot-password")
                        .header(API_VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ada@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("We have e-mailed your password reset link!"));

        verify(javaMailSender).send(any(jakarta.mail.internet.MimeMessage.class));
        org.junit.jupiter.api.Assertions.assertTrue(passwordResetTokenRepository.findById("ada@example.com").isPresent());
    }

    @Test
    void forgotPasswordUnknownUserReturns422() throws Exception {
        mockMvc.perform(post("/api/forgot-password")
                        .header(API_VERSION, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.email[0]")
                        .value("We can't find a user with that e-mail address."));
    }
}
```

- [ ] **Step 2: Run IT**

Run: `./gradlew :app:test --tests 'com.authspring.api.AuthForgotPasswordIT'`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/authspring/api/AuthForgotPasswordIT.java
git commit -m "test: forgot-password integration tests"
```

---

### Task 8: Full verification

- [ ] **Step 1: Run full suite**

Run: `./gradlew :app:test`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit** (only if fixes were needed)

---

## Optional follow-ups

1. **Validation status code:** `GlobalExceptionHandler` maps `MethodArgumentNotValidException` to **400**; Laravel uses **422** for validation. If full parity is required, adjust the handler or document the difference.
2. **Rate limiting:** Add throttling for `/api/forgot-password` if product requires it.
3. **Async mail:** Replace synchronous `JavaMailSender.send` with `@Async` + failure handling if needed.

---

## Self-review

| Requirement | Task |
|-------------|------|
| `POST /api/forgot-password`, body `email`, success `status` string | Task 6 |
| Unknown user → 422 + `errors.email` (Laravel `passwords.user` copy) | Task 6 |
| Persist token in `password_reset_tokens` (bcrypt) | Task 5 |
| Email with style URL (`MailResetPasswordNotification`) | Tasks 1, 2, 4 |
| Security `permitAll` | Task 6 |
| Tests | Tasks 5, 7, 8 |

**Placeholder scan:** None.

**Type consistency:** `JwtProperties` includes `passwordResetExpirationMs`; YAML uses `password-reset-expiration-ms`. `ForgotPasswordRequest` used in service and controller.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-api-forgot-password-endpoint.md`.**

**Two execution options:**

1. **Subagent-driven (recommended)** — Dispatch a fresh subagent per task; review between tasks. **Required sub-skill:** `superpowers:subagent-driven-development`.

2. **Inline execution** — Run tasks in this session with `superpowers:executing-plans` and checkpoints.

**Which approach do you want?**
