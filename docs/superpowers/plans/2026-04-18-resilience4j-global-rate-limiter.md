# Resilience4j global RateLimiter implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add **Resilience4j** `RateLimiter` to the project and enforce a **global** HTTP rate limit on **all `/api/**` requests** (excluding `OPTIONS` preflight), returning **429** with JSON `{"message":"Too Many Attempts."}` when the limit is exceeded.

**Architecture:** Import the **Resilience4j BOM** and **`resilience4j-spring-boot4`** (Spring Boot **4.x** requires the Boot 4 starter, not `resilience4j-spring-boot3`). Declare a named instance (e.g. **`apiGlobal`**) in **`application.yml`**. Auto-configuration exposes **`RateLimiterRegistry`**. A **`OncePerRequestFilter`** (ordered early in the servlet chain) resolves `registry.rateLimiter("apiGlobal")` and calls **`tryAcquirePermission()`** before `filterChain.doFilter`; on failure, write **429** and **do not** invoke downstream filters. **Tests** use a very high limit in **`application-test.yml`** so existing integration tests stay stable. Add a **focused unit or slice test** that proves 429 after N calls with a tight limit.

**Tech stack:** Spring Boot 4.0.x, Resilience4j **2.4.0** (`io.github.resilience4j:resilience4j-bom`, `io.github.resilience4j:resilience4j-spring-boot4`), Gradle version catalog (`gradle/libs.versions.toml`).

**Note on semantics:** One **`RateLimiter`** instance is **shared by all clients** (single JVM bucket). Per-IP or per-user limits require extra design (e.g. keyed limiters); this plan stays **YAGNI** with one global instance.

---

## File structure

| File | Responsibility |
|------|----------------|
| `gradle/libs.versions.toml` | Version **2.4.0** for Resilience4j; library entries for BOM + `resilience4j-spring-boot4`. |
| `app/build.gradle` | `implementation platform(libs.resilience4j.bom)` + `implementation libs.resilience4j.spring.boot4`. |
| `app/src/main/resources/application.yml` | `resilience4j.ratelimiter.instances.apiGlobal` (limit, refresh period, timeout). |
| `app/src/test/resources/application-test.yml` | Very high `limitForPeriod` for `apiGlobal` so ITs do not flake. |
| `app/src/main/java/com/authspring/api/security/ApiRateLimiterFilter.java` | Servlet filter: skip `OPTIONS`; `tryAcquirePermission()`; 429 JSON body. |
| `app/src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java` | Remove **`RateLimited`** record if present (429 is handled only in the filter). |
| `app/src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java` | Remove **`RateLimited`** branch from `switch` if present. |
| `app/src/test/java/com/authspring/api/security/ApiRateLimiterFilterTest.java` | Unit test with **`RateLimiterRegistry`** + tight config: N allowed, (N+1)th → 429. |

---

### Task 1: Dependencies (BOM + `resilience4j-spring-boot4`)

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle`

- [ ] **Step 1: Extend `gradle/libs.versions.toml`**

Append:

```toml
[versions]
# ... existing ...
resilience4j = "2.4.0"

[libraries]
# ... existing ...
resilience4j-bom = { module = "io.github.resilience4j:resilience4j-bom", version.ref = "resilience4j" }
resilience4j-spring-boot4 = { module = "io.github.resilience4j:resilience4j-spring-boot4", version.ref = "resilience4j" }
```

(Merge `[versions]` and `[libraries]` with existing keys—do not duplicate `[versions]` headers.)

- [ ] **Step 2: Update `app/build.gradle` `dependencies` block**

Add after existing `implementation` lines (keep BOM first):

```gradle
    implementation platform(libs.resilience4j.bom)
    implementation libs.resilience4j.spring.boot4
```

- [ ] **Step 3: Verify resolution**

Run:

```bash
./gradlew :app:dependencies --configuration compileClasspath | rg resilience4j
```

Expected: lines listing `io.github.resilience4j:resilience4j-spring-boot4:2.4.0` and related modules.

If **`2.4.0` does not resolve**, try **`2.3.0`** only as a fallback after reading the resolver error; **do not** use `resilience4j-spring-boot3` on Spring Boot 4 without checking compatibility.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle
git commit -m "build: add Resilience4j BOM and spring-boot4 starter"
```

---

### Task 2: YAML configuration

**Files:**

- Modify: `app/src/main/resources/application.yml`
- Modify: `app/src/test/resources/application-test.yml`

- [ ] **Step 1: Production defaults in `application.yml`**

Append at top level (sibling to `spring:`, `jwt:`, `app:`):

```yaml
resilience4j:
  ratelimiter:
    instances:
      apiGlobal:
        limitForPeriod: 300
        limitRefreshPeriod: 1m
        timeoutDuration: 0s
```

Meaning: up to **300** calls per **minute** per **single global limiter** (tune for production). **`timeoutDuration: 0s`** keeps acquisition **non-blocking** so **`tryAcquirePermission()`** returns immediately when the slot is not available.

- [ ] **Step 2: Test profile in `application-test.yml`**

Append:

```yaml
resilience4j:
  ratelimiter:
    instances:
      apiGlobal:
        limitForPeriod: 1000000
        limitRefreshPeriod: 1m
        timeoutDuration: 0s
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/resources/application.yml app/src/test/resources/application-test.yml
git commit -m "config: Resilience4j apiGlobal rate limiter defaults"
```

---

### Task 3: `ApiRateLimiterFilter`

**Files:**

- Create: `app/src/main/java/com/authspring/api/security/ApiRateLimiterFilter.java`

- [ ] **Step 1: Implement the filter**

Create `app/src/main/java/com/authspring/api/security/ApiRateLimiterFilter.java`:

```java
package com.authspring.api.security;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimiterFilter extends OncePerRequestFilter {

    static final String RATE_LIMITER_NAME = "apiGlobal";

    private final RateLimiterRegistry rateLimiterRegistry;

    public ApiRateLimiterFilter(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiter limiter = rateLimiterRegistry.rateLimiter(RATE_LIMITER_NAME);
        if (!limiter.tryAcquirePermission()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too Many Attempts.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/authspring/api/security/ApiRateLimiterFilter.java
git commit -m "feat: global API rate limit filter (Resilience4j)"
```

---

### Task 4: Remove dead `RateLimited` outcome (if still present)

**Files:**

- Modify: `app/src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java`
- Modify: `app/src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java`

If **`EmailVerificationNotificationOutcome`** still contains **`record RateLimited()`**, remove it so the sealed interface has only **`AlreadyVerified`** and **`Sent`**.

Update **`EmailVerificationNotificationController.store`** `switch` to remove the **`RateLimited`** case; **`RateLimited`** can no longer be returned from the service because throttling happens in the filter **before** the controller.

If these types are already clean in your branch, skip file changes and mark steps done.

- [ ] **Step 1: Commit (only if files changed)**

```bash
git add app/src/main/java/com/authspring/api/service/EmailVerificationNotificationOutcome.java \
  app/src/main/java/com/authspring/api/web/EmailVerificationNotificationController.java
git commit -m "refactor: drop unreachable RateLimited outcome (429 from global filter)"
```

---

### Task 5: `ApiRateLimiterFilterTest`

**Files:**

- Create: `app/src/test/java/com/authspring/api/security/ApiRateLimiterFilterTest.java`

- [ ] **Step 1: Unit test with in-memory registry**

Create `app/src/test/java/com/authspring/api/security/ApiRateLimiterFilterTest.java`:

```java
package com.authspring.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimiterFilterTest {

    private ApiRateLimiterFilter filter;

    @BeforeEach
    void setUp() {
        RateLimiterConfig config =
                RateLimiterConfig.custom()
                        .limitForPeriod(2)
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .timeoutDuration(Duration.ZERO)
                        .build();
        RateLimiter rateLimiter =
                RateLimiter.of(ApiRateLimiterFilter.RATE_LIMITER_NAME, config);
        RateLimiterRegistry registry = RateLimiterRegistry.of(rateLimiter);
        filter = new ApiRateLimiterFilter(registry);
    }

    @Test
    void thirdRequestToApiReturns429() throws ServletException, IOException {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        for (int i = 0; i < 2; i++) {
            filter.doFilter(apiRequest(), res, chain);
            assertEquals(200, res.getStatus());
            res = new MockHttpServletResponse();
            chain = new MockFilterChain();
        }

        filter.doFilter(apiRequest(), res, chain);
        assertEquals(429, res.getStatus());
        assertEquals("{\"message\":\"Too Many Attempts.\"}", res.getContentAsString());
    }

    @Test
    void nonApiPathNotLimited() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        for (int i = 0; i < 5; i++) {
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }
    }

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/api/test");
        return req;
    }
}
```

- [ ] **Step 2: Run tests**

Run:

```bash
./gradlew :app:test --tests com.authspring.api.security.ApiRateLimiterFilterTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full suite**

Run:

```bash
./gradlew :app:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/authspring/api/security/ApiRateLimiterFilterTest.java
git commit -m "test: ApiRateLimiterFilter 429 after limit"
```

---

## Self-review

**1. Spec coverage**

| Requirement | Task |
|-------------|------|
| Resilience4j on classpath | Task 1 |
| `io.github.resilience4j.ratelimiter.RateLimiter` via registry | Tasks 1–3 |
| Protect **all** API routes | Task 3 `shouldNotFilter` only skips non-`/api/**` and `OPTIONS` inside `/api/**` is still skipped in `doFilterInternal` |

**2. Placeholder scan:** No TBD; limits are concrete; fallback version note is explicit.

**3. Type consistency:** `RATE_LIMITER_NAME` constant shared; YAML instance name **`apiGlobal`** matches `ApiRateLimiterFilter.RATE_LIMITER_NAME` — **must be the same string**. The filter uses **`apiGlobal`** in code as constant **`"apiGlobal"`** — align YAML key **`apiGlobal`** with constant value **`"apiGlobal"`**.

**Correction:** In Task 3 code, `static final String RATE_LIMITER_NAME = "apiGlobal";` matches YAML `instances.apiGlobal`. Good.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-resilience4j-global-rate-limiter.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Fresh subagent per task, review between tasks. **REQUIRED SUB-SKILL:** superpowers:subagent-driven-development.

**2. Inline Execution** — Execute tasks in this session using superpowers:executing-plans, batch execution with checkpoints.

Which approach?
