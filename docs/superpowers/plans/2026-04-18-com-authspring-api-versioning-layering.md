# Authspring — `com.authspring.api` packages, API v1 header, and layered services

This document was produced with the **writing-plans** skill (superpowers): bite-sized tasks, exact paths, and no placeholder steps.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the authspring codebase from `org.example.auth.*` to **`com.authspring.api.*`**, enable **Spring Framework 7 first-class API versioning** via the **`API-Version`** request header, map all REST controllers to **`version = "1"`**, enforce **layering** (repositories only in `@Service` classes), and keep **exactly one** `@RestControllerAdvice` (`GlobalExceptionHandler`) for **framework and persistence** `ProblemDetail` responses—while using **`Optional` / `ResponseEntity`** for business outcomes (no custom business-validation exception types).

**Architecture:** Introduce a **`WebMvcConfigurer`** that implements **`configureApiVersioning(ApiVersionConfigurer)`** with **`configurer.useRequestHeader("API-Version")`** ([Spring MVC – API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html)). Controllers live under **`com.authspring.api.web`** with **`@RequestMapping(path = "/api/...", version = "1")`** (class-level or consistent method-level). Application services under **`com.authspring.api.service`** own **`@Transactional`** boundaries and are the **only** components that inject **`com.authspring.api.repo`** `JpaRepository` beans. DTOs live in **`com.authspring.api.web.dto`**. JPA entities move to **`com.authspring.api.domain`**. Security and JWT wiring move to **`com.authspring.api.security`**; configuration properties to **`com.authspring.api.config`**. The Spring Boot entry point becomes **`com.authspring.AuthspringApplication`** with component scanning that includes **`com.authspring.api`**.

**Tech stack:** Gradle, Java **25**, Spring Boot **4.0.5** (Spring Framework **7**), Spring MVC, Spring Security, Spring Data JPA, Testcontainers (PostgreSQL) for ITs.

**Context:** Prefer a dedicated git worktree if your team uses the brainstorming workflow; otherwise implement on the main branch with frequent small commits.

---

## Spec coverage (self-review)

| Requirement | Task(s) |
|-------------|---------|
| `configureApiVersioning` + `API-Version` header | Task 1 |
| Move sources to `com.authspring.api.*` | Task 2 |
| `AuthspringApplication` + Gradle `mainClass` | Task 3 |
| Controllers under `com.authspring.api.web` + `version = "1"` | Task 4 |
| ITs send `API-Version: 1` | Task 4 |
| Repositories only in services | Task 5–7 |
| Single `GlobalExceptionHandler`; 400/409/500 for framework/persistence; remove business exception for duplicate email | Task 8 |
| `IllegalArgumentException` / `IllegalStateException` → 400 (existing handler) | Already in handler; verify after move |
| No second `@ControllerAdvice` | Enforced by convention |
| Package root `com.authspring.api` | Task 2–3, table below |

**Gap addressed:** Current code uses **`EmailAlreadyUsedException`** and a handler in **`GlobalExceptionHandler`**—the target architecture forbids custom exceptions for this case; Task 8 replaces it with **`RegistrationOutcome`** or **`Optional`/`ResponseEntity`** from **`RegisterService`** + thin controller mapping.

---

## File structure (before → after)

| Before | After | Responsibility |
|--------|--------|----------------|
| `org/example/AuthspringApplication.java` | `com/authspring/AuthspringApplication.java` | `@SpringBootApplication`, `scanBasePackages` |
| `org/example/auth/api/*.java` (controllers, GEH) | `com/authspring/api/web/*.java` | HTTP adapters |
| `org/example/auth/api/dto/*.java` | `com/authspring/api/web/dto/*.java` | Request/response DTOs |
| `org/example/auth/service/RegisterService.java` | `com/authspring/api/service/RegisterService.java` | Registration use-case |
| — | `com/authspring/api/service/SessionService.java` | **New:** login + logout use-case (delegates `UserRepository`, `PasswordEncoder`, `JwtService`) |
| — | `com/authspring/api/service/PasswordResetService.java` | **New:** reset-password use-case |
| `org/example/auth/repo/*.java` | `com/authspring/api/repo/*.java` | Spring Data repositories |
| `org/example/auth/domain/*.java` | `com/authspring/api/domain/*.java` | JPA entities |
| `org/example/auth/security/*.java` | `com/authspring/api/security/*.java` | Security filter chain, JWT, entry point |
| `org/example/auth/config/*.java` | `com/authspring/api/config/*.java` | `JwtProperties`, **new** `ApiVersioningConfig` |
| `org/example/auth/exception/EmailAlreadyUsedException.java` | **Delete** | Replaced by outcome-based API |
| `app/build.gradle` | `app/build.gradle` | `group`, `mainClass` |
| `app/src/test/java/org/example/auth/**` | `app/src/test/java/com/authspring/**` | Mirror package moves + API-Version header |

**Design note:** **`GlobalExceptionHandler`** stays the **only** `@RestControllerAdvice` and lives in **`com.authspring.api.web.GlobalExceptionHandler`** next to controllers so all MVC exception handling remains in one class and package.

---

### Task 1: Enable API versioning (`API-Version` header)

**Files:**
- Create: `app/src/main/java/com/authspring/api/config/ApiVersioningConfig.java`

- [ ] **Step 1: Add configuration class**

Create `ApiVersioningConfig.java`:

```java
package com.authspring.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("API-Version");
    }
}
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew :app:compileJava --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL` (no version on controllers yet; unversioned mappings still match per Spring docs with lowest priority).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/config/ApiVersioningConfig.java
git commit -m "feat(config): enable Spring MVC API versioning via API-Version header"
```

---

### Task 2: Move packages (`org.example.auth` → `com.authspring.api.*`) — mechanical refactor

**Files:** All Java sources listed in the table in **File structure**; use IDE “Move package” / “Refactor → Move” to preserve history, or move files and fix packages in one commit.

- [ ] **Step 1: Move domain → `com.authspring.api.domain`**

For each entity (`User`, `PasswordReset`, `PasswordResetId`, `PasswordResetToken`), change `package` to `com.authspring.api.domain` and update **every** import across the repo.

- [ ] **Step 2: Move repo → `com.authspring.api.repo`**

`UserRepository`, `PasswordResetTokenRepository` → package `com.authspring.api.repo`.

- [ ] **Step 3: Move config → `com.authspring.api.config`**

`JwtProperties` → `com.authspring.api.config`.

- [ ] **Step 4: Move security → `com.authspring.api.security`**

All classes in `org.example.auth.security` → `com.authspring.api.security`; fix imports (`JwtProperties`, `User`, repositories if any).

- [ ] **Step 5: Move DTOs → `com.authspring.api.web.dto`**

`LoginRequest`, `LoginResponse`, `UserResponse`, `RegisterRequest`, `ResetPasswordRequest` → `com.authspring.api.web.dto`.

- [ ] **Step 6: Move web layer → `com.authspring.api.web`**

`RegisterController`, `AuthenticatedSessionController`, `ResetPasswordController`, `SecuredTestController`, `GlobalExceptionHandler` → `com.authspring.api.web` (one package is enough; no subpackage per controller unless you prefer).

- [ ] **Step 7: Move `RegisterService` → `com.authspring.api.service`**

`RegisterService` → `com.authspring.api.service`.

- [ ] **Step 8: Delete old `org/example` tree**

Remove empty `org/example/auth/**` and `org/example/AuthspringApplication.java` after the new main class compiles.

- [ ] **Step 9: Compile**

```bash
./gradlew :app:compileJava --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add -A app/src/main/java
git commit -m "refactor: migrate org.example.auth to com.authspring.api"
```

---

### Task 3: Add Spring Boot application class under `com.authspring` (scan `com.authspring.api`)

**Files:**
- Create: `app/src/main/java/com/authspring/AuthspringApplication.java`
- Modify: `app/build.gradle` (`mainClass`, `group`)
- Delete: `app/src/main/java/org/example/AuthspringApplication.java` (if it still exists after Task 2)

- [ ] **Step 1: Create `AuthspringApplication`**

`JwtProperties` must already live at `com.authspring.api.config.JwtProperties` after Task 2.

```java
package com.authspring;

import com.authspring.api.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.authspring.api")
@EnableConfigurationProperties(JwtProperties.class)
public class AuthspringApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthspringApplication.class, args);
    }
}
```

- [ ] **Step 2: Point Gradle at the new main class**

In `app/build.gradle`, set:

```gradle
group = 'com.authspring'

springBoot {
    mainClass = 'com.authspring.AuthspringApplication'
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileJava --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/authspring/AuthspringApplication.java app/build.gradle
git commit -m "feat: add com.authspring.AuthspringApplication with scan of com.authspring.api"
```

---

### Task 4: Version all REST mappings as `version = "1"` and fix ITs

**Files:**
- Modify: each `*Controller.java` under `com.authspring.api.web`
- Modify: `app/src/test/java/**/AuthLoginIT.java`, `AuthRegisterIT.java`, `AuthResetPasswordIT.java`, `AuthSchemaPersistenceIT.java` (package + imports + **`API-Version`** header)

- [ ] **Step 1: Class-level request mapping example**

For each `@RestController`, use a single class-level mapping (adjust paths to match existing endpoints):

```java
@RestController
@RequestMapping(path = "/api", version = "1")
public class AuthenticatedSessionController {
    @PostMapping("/login")
    public ResponseEntity<?> store(...) { ... }

    @PostMapping("/logout")
    public Map<String, String> destroy() { ... }
}
```

Apply the same pattern so full paths remain **`/api/login`**, **`/api/register`**, **`/api/reset-password`**, **`/api/test/secured`** — only the **`version`** attribute is added.

- [ ] **Step 2: `SecurityConfig` request matchers**

Paths stay **`/api/login`** etc.; **no path prefix change**. Re-run security tests after Task 3.

- [ ] **Step 3: Integration tests — add header**

For every `mockMvc.perform(...)`, add:

```java
.header("API-Version", "1")
```

Example:

```java
mockMvc.perform(post("/api/login")
        .header("API-Version", "1")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"ada@example.com\",\"password\":\"secret\"}"))
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:test --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/com/authspring/api/web app/src/test/java
git commit -m "feat(api): require API-Version header and map controllers to version 1"
```

---

### Task 5: Introduce `SessionService` and slim `AuthenticatedSessionController`

**Files:**
- Create: `app/src/main/java/com/authspring/api/service/SessionService.java`
- Modify: `app/src/main/java/com/authspring/api/web/AuthenticatedSessionController.java`

- [ ] **Step 1: `SessionService`**

Inject **`UserRepository`**, **`PasswordEncoder`**, **`JwtService`** only in this service.

```java
package com.authspring.api.service;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import com.authspring.api.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public SessionService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return null;
        }
        String token = jwtService.createToken(user);
        return new LoginResponse(token, UserResponse.fromEntity(user));
    }
}
```

**Note:** Returning `null` for invalid credentials keeps the controller responsible for mapping to **422** `ProblemDetail` (same as today’s `AuthenticatedSessionController`).

- [ ] **Step 2: Controller delegates**

`AuthenticatedSessionController` injects only **`SessionService`** + builds **`ProblemDetail`** for failed login (no repository).

- [ ] **Step 3: Run tests**

```bash
./gradlew :app:test --tests '*AuthLoginIT' --no-configuration-cache
```

Expected: all tests in `AuthLoginIT` pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/authspring/api/service/SessionService.java \
        app/src/main/java/com/authspring/api/web/AuthenticatedSessionController.java
git commit -m "refactor: add SessionService; remove repo from login controller"
```

---

### Task 6: Introduce `PasswordResetService` and slim `ResetPasswordController`

**Files:**
- Create: `app/src/main/java/com/authspring/api/service/PasswordResetOutcome.java`
- Create: `app/src/main/java/com/authspring/api/service/PasswordResetService.java`
- Modify: `app/src/main/java/com/authspring/api/web/ResetPasswordController.java`

- [ ] **Step 1: Add sealed outcome (no HTTP types)**

Create `PasswordResetOutcome.java`:

```java
package com.authspring.api.service;

public sealed interface PasswordResetOutcome permits PasswordResetOutcome.Success, PasswordResetOutcome.UserNotFound,
        PasswordResetOutcome.InvalidToken {

    record Success() implements PasswordResetOutcome {}

    record UserNotFound() implements PasswordResetOutcome {}

    record InvalidToken() implements PasswordResetOutcome {}
}
```

- [ ] **Step 2: Implement `PasswordResetService`**

Inject **`UserRepository`**, **`PasswordResetTokenRepository`**, **`PasswordEncoder`**. Method signature:

```java
@Transactional
public PasswordResetOutcome reset(com.authspring.api.web.dto.ResetPasswordRequest request)
```

Logic (same as current controller): normalize email; **`findByEmail`** → if missing return **`UserNotFound()`**; load **`PasswordResetToken`** by email; if row missing or **`passwordEncoder.matches`** fails → **`InvalidToken()`**; else update **`User`** (encoded password, **`rememberToken`**, **`updatedAt`**), **`deleteById(email)`**, return **`Success()`**). Keep **`randomRememberToken()`** as **`private static`** helper inside the service (move from controller).

- [ ] **Step 3: Controller maps `switch (outcome)` → 200 vs 422**

Reuse the same **`ProblemDetail`** text and **`errors.email`** arrays as today:

- **`UserNotFound`** → detail `"We can't find a user with that email address."`
- **`InvalidToken`** → detail `"This password reset token is invalid."`
- **`Success`** → **`Map.of("status", "Your password has been reset.")`**

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:test --tests '*AuthResetPasswordIT' --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/authspring/api/service/PasswordResetOutcome.java \
        app/src/main/java/com/authspring/api/service/PasswordResetService.java \
        app/src/main/java/com/authspring/api/web/ResetPasswordController.java
git commit -m "refactor: add PasswordResetService for reset-password flow"
```

---

### Task 7: Align `RegisterService` with outcome-based API (remove `EmailAlreadyUsedException`)

**Files:**
- Create: `app/src/main/java/com/authspring/api/service/RegistrationOutcome.java` (sealed interface)
- Modify: `app/src/main/java/com/authspring/api/service/RegisterService.java`
- Modify: `app/src/main/java/com/authspring/api/web/RegisterController.java`
- Modify: `app/src/main/java/com/authspring/api/web/GlobalExceptionHandler.java` (remove `EmailAlreadyUsedException` handler)
- Delete: `app/src/main/java/com/authspring/api/exception/EmailAlreadyUsedException.java` (path after package move)

- [ ] **Step 1: Restore sealed outcome**

```java
package com.authspring.api.service;

public sealed interface RegistrationOutcome permits RegistrationOutcome.Registered, RegistrationOutcome.EmailAlreadyTaken {
    record Registered() implements RegistrationOutcome {}
    record EmailAlreadyTaken() implements RegistrationOutcome {}
}
```

- [ ] **Step 2: `RegisterService.register` returns `RegistrationOutcome`**

On duplicate email → `new EmailAlreadyTaken()`; on success → `new Registered()`.

- [ ] **Step 3: `RegisterController` uses `switch` → 200 vs 422 `ProblemDetail`**

Duplicate-email **`ProblemDetail`** body must match the current test expectation: **`errors.email[0]`** = `"The email has already been taken."`.

- [ ] **Step 4: Remove `@ExceptionHandler(EmailAlreadyUsedException)`** from **`GlobalExceptionHandler`**.

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:test --tests '*AuthRegisterIT' --no-configuration-cache
```

- [ ] **Step 6: Commit**

```bash
git commit -am "refactor(register): use RegistrationOutcome; remove EmailAlreadyUsedException"
```

---

### Task 8: Optional — map framework API version exceptions to `ProblemDetail`

**Files:**
- Modify: `app/src/main/java/com/authspring/api/web/GlobalExceptionHandler.java`

If integration tests or manual calls show **unhandled** **`InvalidApiVersionException`** / **`NotAcceptableApiVersionException`** (packages under `org.springframework.web` / versioning), add **`@ExceptionHandler`** methods that return **`ProblemDetail`** with **400** and run:

```bash
./gradlew :app:test --no-configuration-cache
```

Only add handlers that are required so tests pass and responses stay consistent.

---

### Task 9: Documentation and cleanup

- [ ] **Step 1: Update `AGENTS.md` or project README** (if present) with: clients must send **`API-Version: 1`** for versioned endpoints.

- [ ] **Step 2: Final full test run**

```bash
./gradlew :app:test --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git commit -am "docs: document API-Version header for v1 clients"
```

---

## Execution handoff

**Plan complete and saved to** `docs/superpowers/plans/2026-04-18-com-authspring-api-versioning-layering.md`.

**Two execution options:**

1. **Subagent-driven (recommended)** — dispatch a fresh subagent per task; review between tasks. **Required sub-skill:** superpowers:subagent-driven-development.

2. **Inline execution** — run tasks in one session with checkpoints. **Required sub-skill:** superpowers:executing-plans.

**Which approach?**
