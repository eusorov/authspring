# API verify-email (GET signed link) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add **`GET /api/email/verify/{id}/{hash}`** (Laravel-inspired shapes; path segment differs from Laravel’s `/verify-email/`): validate **signed URL** + **SHA-256 email hash** (hex), set `email_verified_at` when needed, then **HTTP 302** redirect to the SPA with `email_verified`, JWT `api_token`, `auto_login`, `user_id`, `user_name` (Sanctum replaced by existing **`JwtService`**).

**Architecture:** A small **`LaravelSignedUrlValidator`** ports `Illuminate\Routing\UrlGenerator::hasCorrectSignature` + `signatureHasNotExpired` (HMAC-SHA256 over `request URL without query` + `?` + query string **excluding** `signature`). **`EmailVerificationService`** loads `User` by id, checks path `hash` equals **`EmailVerificationHashes.sha256Hex(user.getEmail())`** (lowercase hex of SHA-256 over UTF-8 email bytes). **`VerifyEmailController`** returns `ResponseEntity` with `Location` built from **`FrontendProperties.baseUrl`**. The HMAC **signing key** must be the same string **`LaravelSignedUrlSigner`** and **`LaravelSignedUrlValidator`** use (`app.verification.signing-key` / `VERIFICATION_SIGNING_KEY`); **`app.verification.public-base-url`** must match the host/path the client requests so the signed string matches.

**Tech stack:** Spring Boot 4, Spring MVC (`API-Version: 1`), `javax.crypto.Mac` (HMAC-SHA256), `MessageDigest` (SHA-256 for path `hash`), existing JPA `User` / `UserRepository`, existing `JwtService`, MockMvc + Testcontainers ITs.

**Reference (Laravel):**

- Signed URL: `vendor/laravel/framework/src/Illuminate/Routing/UrlGenerator.php` — `hasCorrectSignature`, `signatureHasNotExpired` (lines ~400–454).
- **Hash segment:** Laravel’s `EmailVerificationRequest` uses **`sha1($email)`** in the path; **this project uses SHA-256(hex)** instead — verification links from Laravel will **not** match unless you add a compatibility layer. Same for links generated here: they are **not** Laravel-identical.
- controller: `app/Http/Controllers/AuthRest/VerifyEmailController.php` — redirect with `email_verified=1&api_token=...&auto_login=1&user_id=...&user_name=...`.

---

## File structure

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/authspring/api/config/VerificationProperties.java` | `app.verification.signing-key` — same string **`LaravelSignedUrlSigner`** and **`LaravelSignedUrlValidator`** use for HMAC (often aligned with a Laravel `APP_KEY` if you interoperate). |
| `app/src/main/resources/application.yml` | `app.verification.signing-key` (or `${APP_KEY:}` with doc comment). |
| `app/src/test/resources/application-test.yml` | Fixed test signing key for golden-vector tests. |
| `app/src/main/java/com/authspring/api/security/LaravelSignedUrlValidator.java` | `boolean isValid(HttpServletRequest request)` implementing Laravel’s HMAC + expiry check. |
| `app/src/main/java/com/authspring/api/service/EmailVerificationService.java` | Load user, verify id/hash, mark verified, build redirect target URL (or return outcome enum). |
| `app/src/main/java/com/authspring/api/web/VerifyEmailController.java` | `GET /api/email/verify/{id}/{hash}` → redirect or 403. |
| `app/src/main/java/com/authspring/api/security/SecurityConfig.java` | `permitAll` for `/api/**` (or a dedicated matcher); verify endpoint is unauthenticated. |
| `app/src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java` | Unit tests: HMAC matches Laravel for a fixed URL + key. |
| `app/src/test/java/com/authspring/api/AuthVerifyEmailIT.java` | Integration test: full GET with query `expires` + `signature`. |

---

### Task 1: Configuration

**Files:**
- Create: `app/src/main/java/com/authspring/api/config/VerificationProperties.java`
- Modify: `app/src/main/resources/application.yml`
- Modify: `app/src/test/resources/application-test.yml`
- Modify: `app/src/main/java/com/authspring/AuthspringApplication.java`

- [ ] **Step 1: Add `VerificationProperties`**

Create `app/src/main/java/com/authspring/api/config/VerificationProperties.java`:

```java
package com.authspring.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(String signingKey) {}
```

Use **`signingKey`** as the **same string Laravel uses** for `hash_hmac` — in Laravel this is `Illuminate\Support\Facades\URL::getKeyResolver()` / `config('app.key')` after the `base64:` prefix is decoded to raw bytes in some versions; **in practice** Laravel passes the **string key** to `hash_hmac`. Mirror your deployment: if `APP_KEY` is `base64:XXXX`, decode to bytes and Base64-encode for config, **or** store the raw key string Laravel’s `keyResolver` returns. Easiest cross-team approach: add a dedicated env **`VERIFICATION_SIGNING_KEY`** copied from the **exact** key material Laravel uses for signing (verify with one golden URL from Laravel).

- [ ] **Step 2: Register bean**

Add `VerificationProperties.class` to `@EnableConfigurationProperties({ ... })` in `AuthspringApplication.java`.

- [ ] **Step 3: YAML**

Append to `application.yml`:

```yaml
app:
  verification:
    signing-key: ${VERIFICATION_SIGNING_KEY:}
```

(Empty default only for local dev if you set it in env; production must set it.)

- [ ] **Step 4: Test profile**

In `application-test.yml`, add a **non-empty** key for unit tests (32+ random chars or a fixed test vector):

```yaml
app:
  verification:
    signing-key: test-verification-signing-key-32chars!!
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/authspring/api/config/VerificationProperties.java \
  app/src/main/resources/application.yml app/src/test/resources/application-test.yml \
  app/src/main/java/com/authspring/AuthspringApplication.java
git commit -m "config: verification signing key for Laravel-compatible signed URLs"
```

---

### Task 2: `LaravelSignedUrlValidator`

**Files:**
- Create: `app/src/main/java/com/authspring/api/security/LaravelSignedUrlValidator.java`

- [ ] **Step 1: Implement validator**

Create `app/src/main/java/com/authspring/api/security/LaravelSignedUrlValidator.java` as a `@Component` injecting `VerificationProperties`.

**Algorithm** (match `UrlGenerator::hasCorrectSignature` + `signatureHasNotExpired`):

1. `url = request.getRequestURL().toString()` (scheme + host + port + path) — Laravel `Request::url()` has no query; align with your reverse proxy: use `Forwarded`/`X-Forwarded-*` if TLS terminates in front — document in AGENTS.md if needed).
2. Build `queryString` from `request.getQueryString()` by splitting on `&`, **drop** the parameter whose name is `signature`, rejoin with `&`. If no query remains, `original = url`; else `original = url + "?" + queryString` (Laravel: `rtrim($url.'?'.$queryString, '?')`).
3. `expected = HmacUtils.hmacSha256Hex(original, signingKeyBytes)` — use **`Mac.getInstance("HmacSHA256")`** and hex-encode like PHP `hash_hmac('sha256', ..., ..., false)` (hex lowercase; Laravel compares with `hash_equals` to query param — ensure case matches; Laravel uses lowercase hex).
4. Compare constant-time to `request.getParameter("signature")`.
5. Expiry: `expires` query param (Unix timestamp); valid if absent or `Instant.now().getEpochSecond() <= Long.parseLong(expires)`.

```java
package com.authspring.api.security;

import com.authspring.api.config.VerificationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LaravelSignedUrlValidator {

    private final VerificationProperties properties;

    public LaravelSignedUrlValidator(VerificationProperties properties) {
        this.properties = properties;
    }

    public boolean hasValidSignature(HttpServletRequest request) {
        if (!hasCorrectSignature(request)) {
            return false;
        }
        return signatureHasNotExpired(request);
    }

    public boolean hasCorrectSignature(HttpServletRequest request) {
        String key = properties.signingKey();
        if (key == null || key.isEmpty()) {
            return false;
        }
        String fullUrl = request.getRequestURL().toString();
        String qs = request.getQueryString();
        String withoutSig = stripSignatureParameter(qs);
        String original =
                withoutSig == null || withoutSig.isEmpty() ? fullUrl : fullUrl + "?" + withoutSig;
        String expected = hmacSha256Hex(original, key);
        String provided = request.getParameter("signature");
        return provided != null && constantTimeEquals(expected, provided);
    }

    private static String stripSignatureParameter(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String part : queryString.split("&")) {
            if (part.startsWith("signature=")) {
                continue;
            }
            parts.add(part);
        }
        return String.join("&", parts);
    }

    private static boolean signatureHasNotExpired(HttpServletRequest request) {
        String exp = request.getParameter("expires");
        if (exp == null || exp.isEmpty()) {
            return true;
        }
        try {
            long ts = Long.parseLong(exp);
            return Instant.now().getEpochSecond() <= ts;
        } catch (NumberFormatException e) {
            return false;
        }
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

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
```

**Imports:** add `java.util.HexFormat` (Java 17+).

**Important:** Laravel’s key may be **binary** from `APP_KEY`; if hex comparison fails in tests, compare with Laravel’s actual `hash_hmac` output for the same `original` string — adjust key encoding (UTF-8 bytes vs raw app key bytes) until one golden test passes.

- [ ] **Step 2: Unit test with golden vector**

Create `app/src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java`: build a **mock** `HttpServletRequest` (Mockito or Spring `MockHttpServletRequest`) with:

- `requestURL` = `https://example.com/api/email/verify/1/<64-char-sha256-hex>...`
- `queryString` = `expires=9999999999&signature=<expected>`

Precompute `signature` using PHP one-liner or Laravel tinker **or** assert Java `hmacSha256Hex` matches PHP for the same `original` string and key.

Run: `./gradlew :app:test --tests 'com.authspring.api.security.LaravelSignedUrlValidatorTest'`  
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/security/LaravelSignedUrlValidator.java \
  app/src/test/java/com/authspring/api/security/LaravelSignedUrlValidatorTest.java
git commit -m "feat: Laravel-compatible signed URL validation"
```

---

### Task 3: `EmailVerificationService` + domain updates

**Files:**
- Create: `app/src/main/java/com/authspring/api/service/EmailVerificationOutcome.java` (sealed) or use `Optional` + exceptions — prefer sealed outcomes for controller clarity.
- Create: `app/src/main/java/com/authspring/api/service/EmailVerificationService.java`
- Modify: `app/src/main/java/com/authspring/api/domain/User.java` — add `setEmailVerifiedAt` already exists; ensure `setUpdatedAt` when marking verified.

- [ ] **Step 1: Sealed outcomes**

Create `app/src/main/java/com/authspring/api/service/EmailVerificationOutcome.java`:

```java
package com.authspring.api.service;

import com.authspring.api.domain.User;

public sealed interface EmailVerificationOutcome
        permits EmailVerificationOutcome.RedirectToFrontend, EmailVerificationOutcome.InvalidOrExpiredLink {

    record RedirectToFrontend(String redirectUrl) implements EmailVerificationOutcome {}

    record InvalidOrExpiredLink() implements EmailVerificationOutcome {}
}
```

- [ ] **Step 2: Service**

Create `app/src/main/java/com/authspring/api/service/EmailVerificationService.java`:

- Inject `UserRepository`, `LaravelSignedUrlValidator`, `JwtService`, `FrontendProperties`.
- Method: `EmailVerificationOutcome verify(HttpServletRequest request, Long id, String hash)`:
  1. If `!laravelSignedUrlValidator.hasValidSignature(request)` → `InvalidOrExpiredLink()`.
  2. `User user = userRepository.findById(id).orElse(null)` → if null → `InvalidOrExpiredLink()` (or dedicated not found; same HTTP shape as invalid).
  3. If `!id.equals(user.getId())` — redundant if loaded by id; skip.
  4. Compute `EmailVerificationHashes.sha256Hex(user.getEmail())` — must match the `{hash}` path segment (SHA-256 of UTF-8 email, lowercase hex). Use the **exact** email string stored on the user.
  5. If `!constantTimeEquals(expectedHash, hash)` → `InvalidOrExpiredLink()`.
  6. If `user.getEmailVerifiedAt() == null`, set `emailVerifiedAt = Instant.now()`, `updatedAt = Instant.now()`, `save(user)`.
  7. `String token = jwtService.createToken(user);`
  8. Build redirect URL:

```text
{frontendBase}/?email_verified=1&api_token={urlEncode(token)}&auto_login=1&user_id={id}&user_name={urlEncode(name)}
```

  9. Return `new RedirectToFrontend(url)`.

**Shared helper:** use `EmailVerificationHashes.sha256Hex(email)` (see `EmailVerificationHashes.java`).

- [ ] **Step 3: Run compile**

Run: `./gradlew :app:compileJava`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/authspring/api/service/EmailVerificationService.java \
  app/src/main/java/com/authspring/api/service/EmailVerificationOutcome.java
git commit -m "feat: email verification service (signed URL + SHA-256 path hash)"
```

---

### Task 4: `VerifyEmailController` + security

**Files:**
- Create: `app/src/main/java/com/authspring/api/web/VerifyEmailController.java`
- Modify: `app/src/main/java/com/authspring/api/security/SecurityConfig.java`

- [ ] **Step 1: Controller**

Create `app/src/main/java/com/authspring/api/web/VerifyEmailController.java`:

```java
package com.authspring.api.web;

import com.authspring.api.service.EmailVerificationOutcome;
import com.authspring.api.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class VerifyEmailController {

    private final EmailVerificationService emailVerificationService;

    public VerifyEmailController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/email/verify/{id}/{hash}")
    public ResponseEntity<?> verify(
            HttpServletRequest request,
            @PathVariable Long id,
            @PathVariable String hash) {
        return switch (emailVerificationService.verify(request, id, hash)) {
            case EmailVerificationOutcome.RedirectToFrontend(var url) ->
                    ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
            case EmailVerificationOutcome.InvalidOrExpiredLink() ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        };
    }
}
```

Use **403** for invalid link (Laravel often use 403 on failed signed middleware). Alternatively return **ProblemDetail** JSON — pick one and document; **403 empty** matches minimal plan; optional extension: RFC 9457 body.

- [ ] **Step 2: Security**

In `SecurityConfig`, ensure the verify route is reachable without JWT (e.g. `permitAll` on `/api/**` as in the current app, or a dedicated `requestMatchers(HttpMethod.GET, "/api/email/verify/**").permitAll()`).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/web/VerifyEmailController.java \
  app/src/main/java/com/authspring/api/security/SecurityConfig.java
git commit -m "feat: GET /api/email/verify/{id}/{hash} with redirect"
```

---

### Task 5: Integration test

**Files:**
- Create: `app/src/test/java/com/authspring/api/AuthVerifyEmailIT.java`

- [ ] **Step 1: IT**

Use same pattern as `AuthForgotPasswordIT`: `@SpringBootTest`, `@AutoConfigureMockMvc`, Testcontainers Postgres, `@ActiveProfiles("test")`, `API-Version: 1` header.

1. Insert user with known email, `email_verified_at` null.
2. Compute `hash = EmailVerificationHashes.sha256Hex(email)` matching service.
3. Build `expires` far future; compute `signature` using **same** `LaravelSignedUrlValidator` logic (or precomputed constant from test helper) for the **exact** request URL MockMvc will use (`http://localhost` + path + query).
4. `mockMvc.perform(get("/api/email/verify/{id}/{hash}").queryParam("expires", ...).queryParam("signature", ...).header("API-Version", "1"))`  
   Expect `302` and `Location` containing `email_verified=1` and `api_token=`.

**Note:** MockMvc default host may be `localhost` with port 80 — `getRequestURL()` in filter must match what validator uses. Use **`standaloneSetup`** or set **request** with `ServletUriComponentsBuilder.fromCurrentRequest()` — if fragile, test **`LaravelSignedUrlValidator`** + **`EmailVerificationService`** in unit tests and a **narrow** IT that only checks happy path with signature generated inside the test by calling a **test-only** `SignTestRequest` helper that uses the same HMAC code.

- [ ] **Step 2: Run tests**

Run: `./gradlew :app:test`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/authspring/api/AuthVerifyEmailIT.java
git commit -m "test: verify-email integration"
```

---

### Task 6: Optional — rate limiting

Laravel: `throttle:6,1`. Optional: Spring `@RateLimiter` / Bucket4j — **out of scope** unless product requires; document as follow-up.

---

## Self-review

| Requirement | Task |
|-------------|------|
| Route `GET /api/email/verify/{id}/{hash}` | Task 4 |
| Signed URL (`signature`, `expires`) | Tasks 2–3 |
| SHA-256 `hash` vs email (`sha256Hex`) | Task 3 |
| Mark `email_verified_at` | Task 3 |
| Redirect with `email_verified`, `api_token`, `auto_login`, `user_id`, `user_name` | Task 3 |
| Already verified still redirects with token () | Task 3 |
| Guest access (no JWT) | Task 4 |

**Placeholder scan:** Signing key encoding may need one iteration — covered in Task 2 note.

**Type consistency:** `EmailVerificationOutcome` variants match controller `switch`.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-api-verify-email.md`.**

**Two execution options:**

**1. Subagent-Driven (recommended)** — Fresh subagent per task; use **superpowers:subagent-driven-development**.

**2. Inline Execution** — Run tasks in this session with **superpowers:executing-plans**.

**Which approach?**
