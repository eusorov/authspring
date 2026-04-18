# `GET /api/needsverified` (auth + verified email) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Version control:** Do **not** create git commits while executing this plan (no per-task commits). Stage or commit only if the project owner explicitly asks.

**Goal:** Expose `GET /api/needsverified` (API-Version `1`) that returns success only when the caller presents a valid Bearer JWT with a persisted personal access token **and** the user’s `email_verified_at` is non-null. Callers without a valid session or with unverified email must not see a successful response. **Do not** add `/api/needsverified` rules to `authorizeHttpRequests` — authentication and verification are declared only via **`@RequiresAuth`** and **`@EmailVerifiedGuard`** (`@PreAuthorize`).

**Architecture:** Reuse the existing JWT + PAT pipeline (`JwtAuthenticationFilter`). Extend `UserPrincipal` to carry `email_verified_at` from the loaded `User` entity. Enable **method security** with `@EnableMethodSecurity`. Introduce **`@RequiresAuth`**: meta-annotation **`@PreAuthorize("isAuthenticated()")`** for any controller that should require a logged-in principal. Introduce **`@EmailVerifiedGuard`**: one **`@PreAuthorize("isAuthenticated() && principal.emailVerifiedAt != null")`** (Spring does not allow two `@PreAuthorize` on the same element via nested meta-annotations). **`@RequiresAuth`** stays for auth-only endpoints. Handle **`AccessDeniedException`** in **`GlobalExceptionHandler`** (401 vs 403 by principal type). **No** extra servlet filter for email verification. **No** `requestMatchers("/api/needsverified...").authenticated()` in `SecurityConfig` — `/api/**` stays `permitAll()` for this route; enforcement is `@PreAuthorize` at invocation time.

**401 vs 403 (no URL `authenticated()` rule):** For `permitAll` + failed `@PreAuthorize`, Spring often responds with **403** (access denied) rather than **401** when the user is anonymous. Task 5 documents asserting **403** for “no Bearer token” unless you add separate global `AuthenticationEntryPoint` customization (out of scope). Align the **Goal** tests with **actual** status codes.

**Tech stack:** Spring Boot 4, Spring Security 6 (`@PreAuthorize`), JJWT, JUnit 5, MockMvc, Testcontainers PostgreSQL (existing `SecureRouteIT` patterns).

---

## File map

| File | Responsibility |
|------|------------------|
| `app/src/main/java/com/authspring/api/security/UserPrincipal.java` | Hold `emailVerifiedAt` from `User`; getter for SpEL `principal.emailVerifiedAt`. |
| `app/src/main/java/com/authspring/api/security/RequiresAuth.java` (new) | Meta-annotation: `@PreAuthorize("isAuthenticated()")`. |
| `app/src/main/java/com/authspring/api/security/EmailVerifiedGuard.java` (new) | Meta-annotation: single `@PreAuthorize` with `isAuthenticated() && principal.emailVerifiedAt != null` (see Task 3). |
| `app/src/main/java/com/authspring/api/security/ProblemJsonAccessDeniedHandler.java` (new) | `AccessDeniedHandler` writing `ProblemDetail` JSON for **403**. |
| `app/src/main/java/com/authspring/api/security/SecurityConfig.java` | `@EnableMethodSecurity`; `accessDeniedHandler` + `authenticationEntryPoint`; **do not** add `/api/needsverified` to `requestMatchers`. |
| `app/src/main/java/com/authspring/api/web/NeedsVerifiedController.java` (new) | `GET /api/needsverified` with **`@EmailVerifiedGuard`** on the class (includes auth via composition). |
| `app/src/test/java/com/authspring/api/NeedsVerifiedRouteIT.java` (new) | IT: no Bearer → **403** (or document 401 if customized); PAT + unverified email → **403**; verified → **200**. |

---

### Task 1: Carry `email_verified_at` on `UserPrincipal`

**Files:**
- Modify: `app/src/main/java/com/authspring/api/security/UserPrincipal.java`
- Test: (covered in Task 6 integration tests)

- [ ] **Step 1: Add field and constructor wiring**

Add `private final Instant emailVerifiedAt;` and in the existing constructor `UserPrincipal(User user)` set `this.emailVerifiedAt = user.getEmailVerifiedAt();`. Add:

```java
public java.time.Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
}
```

Import `java.time.Instant`.

`JwtAuthenticationFilter` already does `new UserPrincipal(user)` after `findById`; no change there once the constructor accepts the new field from `User`.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileJava --no-daemon -q`  
Expected: BUILD SUCCESSFUL

---

### Task 2: `@RequiresAuth` meta-annotation

**Files:**
- Create: `app/src/main/java/com/authspring/api/security/RequiresAuth.java`

- [ ] **Step 1: Add the annotation**

```java
package com.authspring.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("isAuthenticated()")
public @interface RequiresAuth {}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileJava --no-daemon -q`  
Expected: BUILD SUCCESSFUL

---

### Task 3: `@EmailVerifiedGuard` (compose `@RequiresAuth` + email SpEL)

**Files:**
- Create: `app/src/main/java/com/authspring/api/security/EmailVerifiedGuard.java`

- [ ] **Step 1: Add the annotation**

Use **one** `@PreAuthorize` with a combined SpEL (`isAuthenticated() && principal.emailVerifiedAt != null`). Spring throws if you stack `@RequiresAuth` and a second `@PreAuthorize` on the same meta-annotation (“2 competing `@PreAuthorize`” on the same element).

```java
@PreAuthorize("isAuthenticated() && principal.emailVerifiedAt != null")
public @interface EmailVerifiedGuard {}
```

**Note:** **`@RequiresAuth`** remains for endpoints that only need authentication. **`@EmailVerifiedGuard`** encodes the same auth check plus email in one expression (semantically “RequiresAuth + verified”, not composable as two `@PreAuthorize` in Spring 6).

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileJava --no-daemon -q`  
Expected: BUILD SUCCESSFUL

---

### Task 4: Method security + 403 Problem JSON (`SecurityConfig` — **no** `/api/needsverified` matcher)

**Files:**
- Create: `app/src/main/java/com/authspring/api/security/ProblemJsonAccessDeniedHandler.java`
- Modify: `app/src/main/java/com/authspring/api/security/SecurityConfig.java`

- [ ] **Step 1: Implement `ProblemJsonAccessDeniedHandler`**

Implement `org.springframework.security.web.access.AccessDeniedHandler`. Use a field `private final ObjectMapper objectMapper = new ObjectMapper();` if the app has no `ObjectMapper` bean (matches `ProblemJsonAuthenticationEntryPoint` in this codebase).

On `handle`: status **403**, `Content-Type: application/problem+json`, `ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "...")`, `setTitle("Forbidden")`, optional `instance` from request URI, `objectMapper.writeValue(response.getOutputStream(), pd)`.

Annotate with `@Component`.

- [ ] **Step 2: Update `SecurityConfig`**

1. Add `@EnableMethodSecurity` to the configuration class.

2. Inject `ProblemJsonAccessDeniedHandler` into the `securityFilterChain` `@Bean` method.

3. Replace `exceptionHandling` with:

```java
http.exceptionHandling(ex -> ex
        .authenticationEntryPoint(authenticationEntryPoint)
        .accessDeniedHandler(accessDeniedHandler));
```

4. **Do not** add `.requestMatchers("/api/needsverified", "/api/needsverified/**").authenticated()` (or any new matcher for this route). Existing matchers such as `/api/secured/**` and `/api/logout` remain unchanged.

5. Keep `http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)` unchanged.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileJava --no-daemon -q`  
Expected: BUILD SUCCESSFUL

---

### Task 5: Controller — `GET /api/needsverified` with `@EmailVerifiedGuard`

**Files:**
- Create: `app/src/main/java/com/authspring/api/web/NeedsVerifiedController.java`

- [ ] **Step 1: Add controller**

```java
@EmailVerifiedGuard
@RestController
@RequestMapping(path = "/api/needsverified", version = "1")
public class NeedsVerifiedController {

    @GetMapping
    public Map<String, String> needsVerified() {
        return Map.of("status", "needsverified-ok");
    }
}
```

Import `com.authspring.api.security.EmailVerifiedGuard`. **Do not** add `@RequiresAuth` again on the class — it is already part of `@EmailVerifiedGuard`.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileJava --no-daemon -q`  
Expected: BUILD SUCCESSFUL

---

### Task 6: Integration tests (`NeedsVerifiedRouteIT`)

**Files:**
- Create: `app/src/test/java/com/authspring/api/NeedsVerifiedRouteIT.java`

Copy structure from `SecureRouteIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@Testcontainers`, PostgreSQL container, `@ActiveProfiles("test")`, `@Transactional`, `API_VERSION` header).

- [ ] **Step 1: Write failing tests first (TDD)**

1. **`withoutBearerReturns401WhenNotAuthenticated`**: `GET /api/needsverified` with only `API-Version: 1` → **`status().isUnauthorized()`** with Problem JSON from `ProblemJsonAuthenticationEntryPoint` (observed with Spring Security 6: `AuthorizationManagerBeforeMethodInterceptor` yields authentication exception before `@PreAuthorize` when anonymous).

2. **`withValidPatButUnverifiedEmailReturns403`**: `POST /api/register` → read `$.token` → `GET /api/needsverified` with Bearer → `status().isForbidden()` and assert Problem JSON / `jsonPath` as for Task 4 handler.

3. **`withValidPatAndVerifiedEmailReturns200`**: save `User` with bcrypt password and `setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"))`, `POST /api/login`, read `$.token`, `GET /api/needsverified` with Bearer → `status().isOk()`, `jsonPath("$.status").value("needsverified-ok")`.

- [ ] **Step 2: Run tests — expect failures until Tasks 1–5 are done**

Run: `./gradlew :app:test --tests 'com.authspring.api.NeedsVerifiedRouteIT' --no-daemon`  
Expected before implementation: compile errors or failing assertions.

- [ ] **Step 3: Run full suite after implementation**

Run: `./gradlew :app:test --no-daemon`  
Expected: all tests pass.

---

## Self-review

**Spec coverage**

| Requirement | Task |
|-------------|------|
| Route under `api/needsverified` | Task 5 |
| Requires authentication (declarative, not `SecurityConfig` chain for this route) | Tasks 2 + 3 (`@RequiresAuth` inside `@EmailVerifiedGuard`) |
| Requires verified email | Task 3 (`principal.emailVerifiedAt != null`) |
| No `/api/needsverified` in `SecurityConfig` | Task 4 |
| 403 Problem JSON | Task 4 (`ProblemJsonAccessDeniedHandler`) |
| Tests | Task 6 |

**Placeholder scan:** No TBD/TODO in tasks; commands and class names are concrete.

**Type consistency:** `UserPrincipal.getEmailVerifiedAt()` → SpEL `principal.emailVerifiedAt`; controller JSON `status` matches test `needsverified-ok`.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-api-needsverified-email-gate.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration (REQUIRED SUB-SKILL: superpowers:subagent-driven-development).

**2. Inline Execution** — Execute tasks in this session using checkpoints (REQUIRED SUB-SKILL: superpowers:executing-plans).

Which approach?
