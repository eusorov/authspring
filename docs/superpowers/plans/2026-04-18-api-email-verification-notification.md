# API email verification notification (POST) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add **`POST /api/email/verification-notification`** matching `routes/auth.php` (`verification.send.api`) and `EmailVerificationNotificationController::store`: **JWT-authenticated** (Laravel `auth:sanctum` equivalent), **409** when the user is already verified, **200** `{"status":"verification-link-sent"}` after sending the verification email, plus **in-process rate limiting** aligned with Laravel `throttle:6,1` (six attempts per rolling minute per authenticated user).

**Architecture:** A **`LaravelSignedUrlSigner`** builds the same **absolute** signed URL string that **`LaravelSignedUrlValidator`** validates on **`GET /api/email/verify/{id}/{hash}`** (HMAC-SHA256 over `scheme + host + path + ?expires=...` without `signature`, then append `&signature=`). Path `{hash}` is **`EmailVerificationHashes.sha256Hex(email)`** (SHA-256, lowercase hex), not Laravel’s `sha1($email)`. Mail content follows **`VerifyEmailApi`** / `config/mail_templates.php` **`verification`** block, mirrored as **`VerificationMailProperties`** + **`EmailVerificationMailSender`** (plain text body with action URL, same UTF-8 pattern as **`PasswordResetEmailSender`**). **`EmailVerificationNotificationService`** loads the current **`User`**, branches on **`emailVerifiedAt`**, applies **`VerificationNotificationRateLimiter`**, and delegates send.

**Tech stack:** Spring Boot 4, Spring Security JWT (`UserPrincipal`), `JavaMailSender`, existing `VerificationProperties` (extended), `UserRepository`, MockMvc + Testcontainers ITs.

**Reference (Laravel):**

- Route: `routes/auth.php` — `POST /email/verification-notification`, middleware `auth:sanctum`, `throttle:6,1`.
- Controller: `/app/Http/Controllers/AuthRest/EmailVerificationNotificationController.php` — 409 JSON `email-already-verified`, 200 `verification-link-sent`.
- Notification: `/app/Notifications/VerifyEmailApi.php` — `URL::temporarySignedRoute(..., false)` + optional `app.url` prefix; mail from `mail_templates.verification`.

---

## File structure

| File | Responsibility |
|------|------------------|
| `app/src/main/java/com/authspring/api/config/VerificationProperties.java` | Extend with **`publicBaseUrl`** (absolute API base for links, e.g. `https://api.example.com`) and **`expireMinutes`** (signed link TTL; Laravel `auth.verification.expire` default 60). |
| `app/src/main/resources/application.yml` | `app.verification.public-base-url`, `app.verification.expire-minutes`; `app.mail.verification.*` (subject, greeting, lines, action label, footer lines, salutation, from). |
| `app/src/test/resources/application-test.yml` | Fixed `public-base-url` (e.g. `https://example.com`) for signer/validator tests. |
| `app/src/main/java/com/authspring/AuthspringApplication.java` | Register **`VerificationMailProperties`** in `@EnableConfigurationProperties`. |
| `app/src/main/java/com/authspring/api/security/EmailVerificationHashes.java` | Shared **`sha256Hex(email)`** for path `hash` (SHA-256 digest, lowercase hex). |
| `app/src/main/java/com/authspring/api/security/LaravelSignedUrlSigner.java` | Build signed **`GET /api/email/verify/{id}/{hash}?expires=...&signature=...`** using the same HMAC rules as **`LaravelSignedUrlValidator`**. |
| `app/src/main/java/com/authspring/api/service/EmailVerificationService.java` | Use **`EmailVerificationHashes.sha256Hex`** for expected path `hash`. |
| `app/src/main/java/com/authspring/api/config/VerificationMailProperties.java` | `app.mail.verification` — mirrors  `mail_templates.verification`. |
| `app/src/main/java/com/authspring/api/service/EmailVerificationMailSender.java` | Send verification email via **`MimeMessageHelper`**, UTF-8, `createMimeMessage()`. |
| `app/src/main/java/com/authspring/api/service/VerificationNotificationRateLimiter.java` | Max **6** requests per **60s** rolling window per **user id** (in-memory). |
| `app/src/main/java/com/authspring/api/service/EmailVerificationNotificationService.java` | Orchestrate: already verified → 409; rate limited → 429; else send mail → 200. |
| `app/src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java` | `POST /api/email/verification-notification`, JSON responses. |
| `app/src/main/java/com/authspring/api/security/SecurityConfig.java` | **`authenticated()`** for `POST /api/email/verification-notification`. |
| `app/src/test/java/com/authspring/api/security/LaravelSignedUrlSignerTest.java` | Built URL validates with **`LaravelSignedUrlValidator`** on **`MockHttpServletRequest`**. |
| `app/src/test/java/com/authspring/api/AuthEmailVerificationNotificationIT.java` | Login → POST → 200 + `send` called; verified user → 409; no token → 401; 7th POST within window → 429. |

---

### Task 1: Extend `VerificationProperties` and YAML

**Files:**

- Modify: `app/src/main/java/com/authspring/api/config/VerificationProperties.java`
- Modify: `app/src/main/resources/application.yml`
- Modify: `app/src/test/resources/application-test.yml`

- [ ] **Step 1: Replace `VerificationProperties` with three fields**

```java
package com.authspring.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(String signingKey, String publicBaseUrl, int expireMinutes) {}
```

- [ ] **Step 2: `application.yml` — append under existing `app.verification`**

Keep `signing-key` as today; add:

```yaml
    public-base-url: ${APP_URL:http://localhost:8080}
    expire-minutes: ${VERIFICATION_EXPIRE_MINUTES:60}
```

- [ ] **Step 3: `application-test.yml` — extend `app.verification`**

```yaml
    public-base-url: https://example.com
    expire-minutes: 60
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/authspring/api/config/VerificationProperties.java \
  app/src/main/resources/application.yml app/src/test/resources/application-test.yml
git commit -m "config: verification public base URL and link expiry for signed emails"
```

---

### Task 2: `EmailVerificationHashes` + update `EmailVerificationService`

**Files:**

- Create: `app/src/main/java/com/authspring/api/security/EmailVerificationHashes.java`
- Modify: `app/src/main/java/com/authspring/api/service/EmailVerificationService.java`

- [ ] **Step 1: Add `EmailVerificationHashes`**

```java
package com.authspring.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class EmailVerificationHashes {

    private static final HexFormat HEX = HexFormat.of();

    private EmailVerificationHashes() {}

    /** {@code SHA-256(email)} as lowercase hex, for {@code GET /api/email/verify/{id}/{hash}}. */
    public static String sha256Hex(String email) {
        Objects.requireNonNull(email, "email");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(email.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
```

- [ ] **Step 2: In `EmailVerificationService`, remove private digest helper and use `EmailVerificationHashes.sha256Hex(user.getEmail())`**

Replace the `expectedHash` line to:

```java
        String expectedHash = EmailVerificationHashes.sha256Hex(user.getEmail());
```

Delete any duplicate digest helper and now-unused imports.

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:test --tests com.authspring.api.AuthVerifyEmailIT --tests com.authspring.api.security.LaravelSignedUrlValidatorTest`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/authspring/api/security/EmailVerificationHashes.java \
  app/src/main/java/com/authspring/api/service/EmailVerificationService.java
git commit -m "refactor: shared SHA-256 hex for email verification path hash"
```

---

### Task 3: `LaravelSignedUrlSigner` + unit test + fix `LaravelSignedUrlValidatorTest`

**Files:**

- Create: `app/src/main/java/com/authspring/api/security/LaravelSignedUrlSigner.java`
- Create: `app/src/test/java/com/authspring/api/security/LaravelSignedUrlSignerTest.java`
- Modify: `app/src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java`

- [ ] **Step 1: Implement `LaravelSignedUrlSigner`**

```java
package com.authspring.api.security;

import com.authspring.api.config.VerificationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LaravelSignedUrlSigner {

    private final VerificationProperties properties;

    public LaravelSignedUrlSigner(VerificationProperties properties) {
        this.properties = properties;
    }

    /**
     * Absolute URL for GET /api/email/verify/{id}/{hash}?expires=...&signature=...
     * that {@link LaravelSignedUrlValidator#hasValidSignature} accepts for the same host/path/query.
     */
    public String buildVerifyEmailUrl(long userId, String email) {
        String key = properties.signingKey();
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("app.verification.signing-key is not set");
        }
        String base = properties.publicBaseUrl().replaceAll("/$", "");
        String hash = EmailVerificationHashes.sha256Hex(email);
        long expires =
                Instant.now().getEpochSecond() + properties.expireMinutes() * 60L;
        String originalWithoutSig =
                base + "/api/email/verify/" + userId + "/" + hash + "?expires=" + expires;
        String sig = hmacSha256Hex(originalWithoutSig, key);
        return originalWithoutSig + "&signature=" + sig;
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 2: Update `LaravelSignedUrlValidatorTest` constructor**

Change:

```java
    private final LaravelSignedUrlValidator validator =
            new LaravelSignedUrlValidator(new VerificationProperties(KEY));
```

to:

```java
    private final LaravelSignedUrlValidator validator =
            new LaravelSignedUrlValidator(new VerificationProperties(KEY, "https://example.com", 60));
```

- [ ] **Step 3: Add `LaravelSignedUrlSignerTest`**

```java
package com.authspring.api.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.authspring.api.config.VerificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LaravelSignedUrlSignerTest {

    private static final String KEY = "test-verification-signing-key-32chars!!";

    private final LaravelSignedUrlSigner signer =
            new LaravelSignedUrlSigner(new VerificationProperties(KEY, "https://example.com", 60));
    private final LaravelSignedUrlValidator validator =
            new LaravelSignedUrlValidator(new VerificationProperties(KEY, "https://example.com", 60));

    @Test
    void builtUrlPassesValidator() {
        String url = signer.buildVerifyEmailUrl(1L, "ada@example.com");
        int q = url.indexOf('?');
        String path = url.substring("https://example.com".length(), q);
        String qs = url.substring(q + 1);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        request.setRequestURI(path);
        request.setQueryString(qs);
        for (String part : qs.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                request.addParameter(part.substring(0, eq), part.substring(eq + 1));
            }
        }

        assertTrue(validator.hasValidSignature(request));
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:test --tests com.authspring.api.security.LaravelSignedUrlSignerTest --tests com.authspring.api.security.LaravelSignedUrlValidatorTest`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/authspring/api/security/LaravelSignedUrlSigner.java \
  app/src/test/java/com/authspring/api/security/LaravelSignedUrlSignerTest.java \
  app/src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java
git commit -m "feat: Laravel-compatible signed verification URL builder"
```

---

### Task 4: `VerificationMailProperties`, YAML, `EmailVerificationMailSender`

**Files:**

- Create: `app/src/main/java/com/authspring/api/config/VerificationMailProperties.java`
- Modify: `app/src/main/java/com/authspring/AuthspringApplication.java`
- Modify: `app/src/main/resources/application.yml`

- [ ] **Step 1: Add `VerificationMailProperties`**

```java
package com.authspring.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.verification")
public record VerificationMailProperties(
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

- [ ] **Step 2: Register in `AuthspringApplication`**

Add `VerificationMailProperties.class` to `@EnableConfigurationProperties({ ... })`.

- [ ] **Step 3: Append to `application.yml` under `app.mail`**

```yaml
    verification:
      subject: ${VERIFICATION_EMAIL_SUBJECT:Verify Your Email Address}
      greeting-template: ${VERIFICATION_EMAIL_GREETING:Hello Dear {name}!}
      lines:
        - ${VERIFICATION_EMAIL_LINE_1:Welcome! Thank you for registering with us.}
        - ${VERIFICATION_EMAIL_LINE_2:Please click the link below to verify your email address and activate your account.}
      action-label: ${VERIFICATION_EMAIL_ACTION:Verify Email Address}
      footer-lines:
        - ${VERIFICATION_EMAIL_FOOTER_1:This verification link will expire in {minutes} minutes.}
        - ${VERIFICATION_EMAIL_FOOTER_2:If you did not create an account, no further action is required.}
      salutation: ${VERIFICATION_EMAIL_SALUTATION:Best regards, Team}
      expiry-minutes: ${VERIFICATION_EMAIL_EXPIRY:60}
      from-address: ${MAIL_FROM_ADDRESS:noreply@example.com}
      from-name: ${MAIL_FROM_NAME:Team}
```

- [ ] **Step 4: Create `EmailVerificationMailSender`**

```java
package com.authspring.api.service;

import com.authspring.api.config.VerificationMailProperties;
import com.authspring.api.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationMailSender {

    private static final Charset MAIL_CHARSET = StandardCharsets.UTF_8;

    private final JavaMailSender mailSender;
    private final VerificationMailProperties mail;

    public EmailVerificationMailSender(JavaMailSender mailSender, VerificationMailProperties mail) {
        this.mailSender = mailSender;
        this.mail = mail;
    }

    public void send(User user, String verificationUrl) throws MessagingException, UnsupportedEncodingException {
        String body = buildBody(user, verificationUrl);

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, false, MAIL_CHARSET.name());
        helper.setFrom(mail.fromAddress(), mail.fromName());
        helper.setTo(user.getEmail());
        helper.setSubject(mail.subject());
        helper.setText(body, false);
        mailSender.send(mime);
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

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/authspring/api/config/VerificationMailProperties.java \
  app/src/main/java/com/authspring/api/service/EmailVerificationMailSender.java \
  app/src/main/java/com/authspring/AuthspringApplication.java \
  app/src/main/resources/application.yml
git commit -m "feat: verification email sender and mail template properties"
```

---

### Task 5: `VerificationNotificationRateLimiter`

**Files:**

- Create: `app/src/main/java/com/authspring/api/service/VerificationNotificationRateLimiter.java`

- [ ] **Step 1: Implement limiter (6 per 60s per user id)**

```java
package com.authspring.api.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class VerificationNotificationRateLimiter {

    private static final int MAX_REQUESTS = 6;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<Long, Deque<Long>> windowByUser = new ConcurrentHashMap<>();

    /** @return true if allowed, false if rate limited */
    public boolean tryAcquire(long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> dq = windowByUser.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW_MS) {
                dq.pollFirst();
            }
            if (dq.size() >= MAX_REQUESTS) {
                return false;
            }
            dq.addLast(now);
            return true;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/authspring/api/service/VerificationNotificationRateLimiter.java
git commit -m "feat: in-memory rate limiter for verification notification (6/min per user)"
```

---

### Task 6: `EmailVerificationNotificationService`

**Files:**

- Create: `app/src/main/java/com/authspring/api/service/EmailVerificationNotificationService.java`

- [ ] **Step 1: Implement service**

```java
package com.authspring.api.service;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.security.LaravelSignedUrlSigner;
import com.authspring.api.security.UserPrincipal;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationNotificationService {

    private final UserRepository userRepository;
    private final LaravelSignedUrlSigner signedUrlSigner;
    private final EmailVerificationMailSender mailSender;
    private final VerificationNotificationRateLimiter rateLimiter;

    public EmailVerificationNotificationService(
            UserRepository userRepository,
            LaravelSignedUrlSigner signedUrlSigner,
            EmailVerificationMailSender mailSender,
            VerificationNotificationRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.signedUrlSigner = signedUrlSigner;
        this.mailSender = mailSender;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public EmailVerificationNotificationOutcome send(UserPrincipal principal) {
        if (!rateLimiter.tryAcquire(principal.getId())) {
            return new EmailVerificationNotificationOutcome.RateLimited();
        }
        User user =
                userRepository
                        .findById(principal.getId())
                        .orElseThrow(() -> new IllegalStateException("User not found: " + principal.getId()));
        if (user.getEmailVerifiedAt() != null) {
            return new EmailVerificationNotificationOutcome.AlreadyVerified();
        }
        String url = signedUrlSigner.buildVerifyEmailUrl(user.getId(), user.getEmail());
        try {
            mailSender.send(user, url);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return new EmailVerificationNotificationOutcome.Sent();
    }
}
```

- [ ] **Step 2: Add outcome sealed hierarchy**

Create `app/src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java`:

```java
package com.authspring.api.service;

public sealed interface EmailVerificationNotificationOutcome {
    record AlreadyVerified() implements EmailVerificationNotificationOutcome {}

    record Sent() implements EmailVerificationNotificationOutcome {}

    record RateLimited() implements EmailVerificationNotificationOutcome {}
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/service/EmailVerificationNotificationService.java \
  app/src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java
git commit -m "feat: email verification notification orchestration"
```

---

### Task 7: `EmailVerificationNotificationController`

**Files:**

- Create: `app/src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java`

- [ ] **Step 1: Implement controller**

```java
package com.authspring.api.web;

import com.authspring.api.service.EmailVerificationNotificationOutcome;
import com.authspring.api.service.EmailVerificationNotificationService;
import com.authspring.api.security.UserPrincipal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class EmailVerificationNotificationController {

    private final EmailVerificationNotificationService notificationService;

    public EmailVerificationNotificationController(EmailVerificationNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Laravel: {@code EmailVerificationNotificationController::store}, route {@code verification.send.api}.
     */
    @PostMapping("/email/verification-notification")
    public ResponseEntity<?> store(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return switch (notificationService.send(principal)) {
            case EmailVerificationNotificationOutcome.AlreadyVerified() ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(
                                    Map.of(
                                            "status",
                                            "email-already-verified",
                                            "message",
                                            "Email address is already verified"));
            case EmailVerificationNotificationOutcome.RateLimited() ->
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(Map.of("message", "Too Many Attempts."));
            case EmailVerificationNotificationOutcome.Sent() ->
                    ResponseEntity.ok(Map.of("status", "verification-link-sent"));
        };
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java
git commit -m "feat: POST /api/email/verification-notification"
```

---

### Task 8: `SecurityConfig`

**Files:**

- Modify: `app/src/main/java/com/authspring/api/security/SecurityConfig.java`

- [ ] **Step 1: Require authentication for POST `/api/email/verification-notification`**

Inside `authorizeHttpRequests`, after the `forgot-password` line, add:

```java
                .requestMatchers(HttpMethod.POST, "/api/email/verification-notification").authenticated()
```

Place it before `.requestMatchers(HttpMethod.GET, "/api/test/secured")` or immediately after `forgot-password` for clarity.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/authspring/api/security/SecurityConfig.java
git commit -m "security: require JWT for verification notification endpoint"
```

---

### Task 9: `AuthEmailVerificationNotificationIT`

**Files:**

- Create: `app/src/test/java/com/authspring/api/AuthEmailVerificationNotificationIT.java`

- [ ] **Step 1: Integration test (Mock mail + full flow)**

```java
package com.authspring.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuthEmailVerificationNotificationIT.MockMailSenderConfig.class)
@Transactional
class AuthEmailVerificationNotificationIT {

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
    private JavaMailSender javaMailSender;

    @TestConfiguration
    static class MockMailSenderConfig {

        @Bean
        @Primary
        JavaMailSender javaMailSender() {
            JavaMailSender mock = Mockito.mock(JavaMailSender.class);
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            Mockito.when(mock.createMimeMessage()).thenReturn(mimeMessage);
            return mock;
        }
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new User("Ada", "ada@example.com", passwordEncoder.encode("secret"), "user"));
    }

    @Test
    void sendsVerificationWhenUnverified() throws Exception {
        String token = loginToken();

        mockMvc.perform(
                        post("/api/email/verification-notification")
                                .header(API_VERSION, "1")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verification-link-sent"));

        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    void alreadyVerifiedReturns409() throws Exception {
        User u = userRepository.findByEmail("ada@example.com").orElseThrow();
        u.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
        userRepository.save(u);

        String token = loginToken();

        mockMvc.perform(
                        post("/api/email/verification-notification")
                                .header(API_VERSION, "1")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("email-already-verified"));
    }

    @Test
    void withoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/email/verification-notification").header(API_VERSION, "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seventhRequestInOneMinuteReturns429() throws Exception {
        String token = loginToken();

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(
                            post("/api/email/verification-notification")
                                    .header(API_VERSION, "1")
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(
                        post("/api/email/verification-notification")
                                .header(API_VERSION, "1")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too Many Attempts."));

        verify(javaMailSender, times(6)).send(any(MimeMessage.class));
    }

    private String loginToken() throws Exception {
        MvcResult login =
                mockMvc.perform(
                                post("/api/login")
                                        .header(API_VERSION, "1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.token");
    }
}
```

- [ ] **Step 2: Confirm `UserRepository.findByEmail`**

`app/src/main/java/com/authspring/api/repo/UserRepository.java` already declares `Optional<User> findByEmail(String email)` — no change required.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/authspring/api/AuthEmailVerificationNotificationIT.java
git commit -m "test: integration tests for verification notification endpoint"
```

---

## Self-review

**1. Spec coverage**

| Requirement | Task |
|-------------|------|
| `POST /api/email/verification-notification` | Task 7 |
| JWT auth (Sanctum parity) | Task 8 + existing JWT filter |
| 409 + body `email-already-verified` / message | Task 6–7 |
| 200 `verification-link-sent` | Task 6–7 |
| Send mail with signed verify URL | Task 3–4, 6 |
| `throttle:6,1` | Task 5–7 (429 + message) |
| `API-Version: 1` | Controller `@RequestMapping(..., version = "1")` — clients send header like other endpoints |

**2. Placeholder scan:** No TBD/TODO/skip steps; each task includes concrete code and commands.

**3. Type consistency:** `VerificationProperties` is `(String, String, int)` everywhere; outcomes are `AlreadyVerified`, `Sent`, `RateLimited`; `UserPrincipal.getId()` is `Long` — rate limiter uses `long userId` consistently.

**Gaps / notes**

- **Ops:** Set **`APP_URL`** (or `app.verification.public-base-url`) to the **public API origin** users click from email (must match TLS host and path the browser sends to **`LaravelSignedUrlValidator`**).
- **Horizontal scale:** In-memory rate limiting is **per instance**; Redis or API gateway throttling is a later improvement.
- **Laravel `VerifyEmailApi`** uses `temporarySignedRoute(..., false)` then prefixes `app.url`; Authspring signs the **full** URL via `publicBaseUrl` to match **`LaravelSignedUrlValidator`**. The **`{hash}`** segment is **SHA-256(hex)** in Authspring (`EmailVerificationHashes.sha256Hex`), not Laravel’s **`sha1($email)`** — links are generated end-to-end by this API (or any client that mirrors the same path and hash rules).

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-api-email-verification-notification.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration. **REQUIRED SUB-SKILL:** superpowers:subagent-driven-development.

**2. Inline Execution** — Execute tasks in this session using superpowers:executing-plans, batch execution with checkpoints.

Which approach?
