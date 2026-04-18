# GET current user (`/api/user`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose `GET /api/user` so an authenticated client can load the current user’s profile as JSON, using the same `UserResponse` shape as login/register and enforcing authentication with `@RequiresAuth`.

**Architecture:** Add a small `UserController` under `/api` (version `1`) with one handler: resolve `UserPrincipal` from the JWT-backed security context, load `User` by id via `UserRepository`, map with existing `UserResponse.fromEntity`. URL-level security stays `permitAll` for `/api/**` (see `SecurityConfig`); method security (`@RequiresAuth` → `@PreAuthorize("isAuthenticated()")`) rejects unauthenticated callers with `401` and `ProblemDetail` via `GlobalExceptionHandler`.

**Tech Stack:** Spring Boot 3, Spring Security method security (`@EnableMethodSecurity`), MockMvc + Testcontainers PostgreSQL (existing IT style).

**Prerequisite (optional):** This repo’s planning workflow often uses a git worktree from the brainstorming skill; not required to implement the feature.

---

## File map

| File | Action | Responsibility |
|------|--------|------------------|
| `app/src/main/java/com/authspring/api/web/UserController.java` | Create | `GET /api/user`, `@RequiresAuth`, returns `UserResponse` |
| `app/src/test/java/com/authspring/api/UserRouteIT.java` | Create | 401 without Bearer; 200 + JSON with Bearer after login |
| `README.md` | Modify | Endpoint table + auth bullet: document `GET /api/user` |

**Reuse (read-only):**

- `app/src/main/java/com/authspring/api/security/RequiresAuth.java` — meta-annotation for `isAuthenticated()`
- `app/src/main/java/com/authspring/api/security/UserPrincipal.java` — `getId()`, `getUsername()` (email)
- `app/src/main/java/com/authspring/api/web/dto/UserResponse.java` — JSON shape; excludes password
- `app/src/main/java/com/authspring/api/repo/UserRepository.java` — `findById(Long)`
- `app/src/test/java/com/authspring/api/NeedsVerifiedRouteIT.java` — copy container/MockMvc/transaction pattern

---

### Task 1: Failing integration test for authenticated `GET /api/user`

**Files:**

- Create: `app/src/test/java/com/authspring/api/UserRouteIT.java`

- [ ] **Step 1: Add `UserRouteIT` with a test that expects 200 and a user payload after login**

Mirror imports and structure from `NeedsVerifiedRouteIT`: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Testcontainers`, `@ActiveProfiles("test")`, `@AutoConfigureTestDatabase(replace = NONE)`, `@Transactional`, PostgreSQL `@Container` + `@ServiceConnection`, `API_VERSION = "API-Version"` header.

```java
package com.authspring.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class UserRouteIT {

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
    void getUser_withBearer_returnsCurrentUserJson() throws Exception {
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

        mockMvc.perform(get("/api/user").header(API_VERSION, "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.name").value("Ada"))
                .andExpect(jsonPath("$.role").value("user"));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```bash
./gradlew :app:test --tests 'com.authspring.api.UserRouteIT.getUser_withBearer_returnsCurrentUserJson' --no-daemon
```

Expected: **FAIL** — e.g. `NoResourceFoundException` / 404, or no handler for `GET /api/user`, until `UserController` exists.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/authspring/api/UserRouteIT.java
git commit -m "test: add failing IT for GET /api/user"
```

---

### Task 2: Implement `UserController`

**Files:**

- Create: `app/src/main/java/com/authspring/api/web/UserController.java`

- [ ] **Step 1: Add controller**

```java
package com.authspring.api.web;

import com.authspring.api.repo.UserRepository;
import com.authspring.api.security.RequiresAuth;
import com.authspring.api.security.UserPrincipal;
import com.authspring.api.web.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the authenticated user (same {@link UserResponse} shape as login/register).
     * Unauthenticated requests receive 401 via {@link RequiresAuth}.
     */
    @RequiresAuth
    @GetMapping("/user")
    public ResponseEntity<UserResponse> currentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return userRepository
                .findById(principal.getId())
                .map(UserResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 2: Run the Task 1 test — expect PASS**

```bash
./gradlew :app:test --tests 'com.authspring.api.UserRouteIT.getUser_withBearer_returnsCurrentUserJson' --no-daemon
```

Expected: **BUILD SUCCESSFUL**, test **passed**.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/authspring/api/web/UserController.java
git commit -m "feat(api): add GET /api/user for current user"
```

---

### Task 3: Integration test — unauthenticated request returns 401

**Files:**

- Modify: `app/src/test/java/com/authspring/api/UserRouteIT.java`

- [ ] **Step 1: Add test for missing `Authorization`**

Append:

```java
    @Test
    void getUser_withoutBearer_returns401() throws Exception {
        mockMvc.perform(get("/api/user").header(API_VERSION, "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required."));
    }
```

- [ ] **Step 2: Run only `UserRouteIT`**

```bash
./gradlew :app:test --tests 'com.authspring.api.UserRouteIT' --no-daemon
```

Expected: **BUILD SUCCESSFUL**, both tests **passed**.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/authspring/api/UserRouteIT.java
git commit -m "test: GET /api/user returns 401 without Bearer token"
```

---

### Task 4: Full module test suite + README

**Files:**

- Modify: `README.md` (endpoint table around the `/api/login` / `/api/logout` rows; and the paragraph that lists `@RequiresAuth` routes)

- [ ] **Step 1: Run full app tests**

```bash
./gradlew :app:test --no-daemon
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 2: Document the endpoint**

In the markdown table of API routes, add a row such as:

| GET | `/api/user` | Yes | — | Current user profile (`UserResponse`, same fields as login/register `user`). |

Update the narrative line that currently says only `POST /api/logout` and `POST /api/email/verification-notification` use `@RequiresAuth` so it **also** mentions **`GET /api/user`**.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document GET /api/user"
```

---

## Self-review

1. **Spec coverage:** `GET` user endpoint — Task 2. Secured with `@RequiresAuth` — Task 2 (`RequiresAuth` on handler). Tests — Tasks 1–3. Docs — Task 4.
2. **Placeholder scan:** None.
3. **Type consistency:** `UserResponse` and `UserPrincipal.getId()` match existing login/register flows; repository returns `Optional<User>` consistent with JPA.

---

## Execution handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-18-api-get-current-user.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**

If **Subagent-Driven** is chosen:

- **REQUIRED SUB-SKILL:** Use superpowers:subagent-driven-development  
- Fresh subagent per task + two-stage review

If **Inline Execution** is chosen:

- **REQUIRED SUB-SKILL:** Use superpowers:executing-plans  
- Batch execution with checkpoints for review
