# Spring Auth (JWT) — Database Entities & Flyway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Flyway-managed SQL migrations and JPA entities for `users`, `password_resets`, and `password_reset_tokens`, matching the Laravel migrations from the PHP project—without any REST controllers or security endpoints (JWT wiring comes later). The `sessions` table and entity are **out of scope** (not modeled here).

**Architecture:** The database schema follows Laravel’s intent in order: (1) `users` including `remember_token`, `created_at`, and `updated_at` in **V1** (single `CREATE TABLE`), (2) legacy `password_resets` in **V2**, (3) `password_reset_tokens` in **V3** (no `sessions` table). JPA entities map tables 1:1; PostgreSQL is the reference dialect (Spring Boot default for production). The current Gradle project is a plain Java app (`org.example.App`); this plan converts the `app` module to **Spring Boot 4.0.5** with **Spring Data JPA** and **Flyway**. JWT and React integration are **out of scope** for this plan—only persistence layer artifacts.

**Tech stack:** Gradle 9.x, Java **25**, Spring Boot **4.0.5**, Spring Data JPA (Hibernate **7.x** from the Boot BOM), Flyway, PostgreSQL driver, Testcontainers (PostgreSQL) for `@DataJpaTest`.

**Note:** Spring Boot 4.0.x requires **Java 17+**; this plan pins the Gradle **Java toolchain to 25** (matches the generated `app` module). The Spring Boot Gradle plugin **4.0.5** pulls `io.spring.gradle:dependency-management-plugin` **1.1.7** transitively; the version catalog still declares that plugin explicitly for `alias(libs.plugins.dependency.management)`.

**Context:** Run implementation in a dedicated git worktree if your team uses the brainstorming workflow; otherwise use the main repo.

---

## File structure (create / modify)

| Path | Responsibility |
|------|----------------|
| `gradle/libs.versions.toml` | Version catalog: Spring Boot 4.0.5, `io.spring.dependency-management` 1.1.7 |
| `app/build.gradle` | Spring Boot plugin, JPA, Flyway, PostgreSQL, test deps; `mainClass` for Spring Boot |
| `app/src/main/java/org/example/AuthspringApplication.java` | `@SpringBootApplication` entry point (replaces CLI `App` as main) |
| `app/src/main/java/org/example/auth/domain/User.java` | `users` entity |
| `app/src/main/java/org/example/auth/domain/PasswordReset.java` | Legacy `password_resets` entity (composite key) |
| `app/src/main/java/org/example/auth/domain/PasswordResetToken.java` | `password_reset_tokens` entity |
| `app/src/main/java/org/example/auth/domain/PasswordResetId.java` | `@Embeddable` / `@IdClass` key for `PasswordReset` |
| `app/src/main/resources/application.yml` | Datasource, JPA (`ddl-auto: validate`), Flyway locations |
| `app/src/main/resources/application-dev.yml` | Optional local overrides (not required for tests) |
| `app/src/main/resources/db/migration/V1__create_users.sql` | Laravel `2014_10_12_000000_create_users_table` |
| `app/src/main/resources/db/migration/V2__create_password_resets.sql` | Laravel `2014_10_12_100000_create_password_resets_table` |
| `app/src/main/resources/db/migration/V3__create_password_reset_tokens.sql` | `password_reset_tokens` only (Laravel `password_reset_tokens` from `2025_04_18_000000_update_users_table`; no `sessions`) |
| `app/src/test/java/org/example/auth/domain/AuthSchemaPersistenceIT.java` | `@DataJpaTest` + Testcontainers: persist/load each entity |
| `settings.gradle` | `pluginManagement` repositories for Spring Boot plugin resolution |
| Delete or stop using | `app/src/main/java/org/example/App.java` (remove after `mainClass` points to Spring Boot app) |

---

### Task 1: Add Spring Boot, JPA, Flyway, PostgreSQL, Testcontainers

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle`
- Modify: `settings.gradle` — prepend `pluginManagement { gradlePluginPortal(); mavenCentral() }` (see Step 2)

- [ ] **Step 1: Extend version catalog**

Replace the contents of `gradle/libs.versions.toml` with:

```toml
[versions]
guava = "33.4.6-jre"
junit-jupiter = "5.12.1"
spring-boot = "4.0.5"

[libraries]
guava = { module = "com.google.guava:guava", version.ref = "guava" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit-jupiter" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
```

This matches the `dependency-management-plugin` version that the Spring Boot **4.0.5** Gradle plugin depends on (see `spring-boot-gradle-plugin-4.0.5.pom` on Maven Central).

- [ ] **Step 2: Add plugin repositories to `settings.gradle`**

Prepend this block to `settings.gradle` (keep the existing `plugins { id 'org.gradle.toolchains.foojay-resolver-convention' ... }` and `rootProject.name` / `include`):

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

- [ ] **Step 3: Replace `app/build.gradle` with Spring Boot layout**

Use the Spring Boot Gradle plugin and BOM via `io.spring.dependency-management`:

```gradle
plugins {
    id 'java'
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

group = 'org.example'
version = '0.0.1-SNAPSHOT'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'

    implementation libs.guava

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation libs.junit.jupiter
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named('test') {
    useJUnitPlatform()
}

tasks.named('jar') {
    enabled = false
}

tasks.named('bootJar') {
    archiveClassifier = ''
}

springBoot {
    mainClass = 'org.example.AuthspringApplication'
}
```

- [ ] **Step 4: Sync and compile (no sources yet except next tasks)**

Run:

```bash
cd /Users/evgenyusorov/workspace/java/authspring
./gradlew :app:dependencies --configuration compileClasspath
```

Expected: resolves without error.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle settings.gradle
git commit -m "build: add Spring Boot 4.0.5, JPA, Flyway, PostgreSQL, Testcontainers"
```

---

### Task 2: Spring Boot main class

**Files:**
- Create: `app/src/main/java/org/example/AuthspringApplication.java`
- Delete: `app/src/main/java/org/example/App.java` (after tests no longer reference it)

- [ ] **Step 1: Add application class**

```java
package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthspringApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthspringApplication.class, args);
    }
}
```

- [ ] **Step 2: Remove obsolete `App.java` and update test**

If `AppTest` references `App`, change it to a no-op or delete—Task 8 adds `AuthSchemaPersistenceIT` as the primary test.

Delete `app/src/main/java/org/example/App.java`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/example/AuthspringApplication.java
git rm app/src/main/java/org/example/App.java
git commit -m "feat: add Spring Boot application entry point"
```

---

### Task 3: Configuration — datasource, JPA validate, Flyway

**Files:**
- Create: `app/src/main/resources/application.yml`

- [ ] **Step 1: Add `application.yml`**

```yaml
spring:
  application:
    name: authspring
  datasource:
    url: jdbc:postgresql://localhost:5432/authspring
    username: authspring
    password: authspring
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

logging:
  level:
    org.hibernate.SQL: DEBUG
```

Local developers create DB/user or override env vars; tests use Testcontainers (Task 8), not this file.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/resources/application.yml
git commit -m "chore: configure JPA validate and Flyway"
```

---

### Task 4: Flyway V1 — `users` (Laravel `CreateUsersTable`)

**Files:**
- Create: `app/src/main/resources/db/migration/V1__create_users.sql`

- [ ] **Step 1: Add migration**

Maps: `id`, `name`, `email` (unique), `email_verified_at`, `date_closed`, `password`, `role` (8), plus `remember_token`, `created_at`, `updated_at` (same migration as Laravel’s follow-up `update_users_table`, inlined here).

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    email_verified_at TIMESTAMP NULL,
    date_closed DATE NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(8) NOT NULL,
    remember_token VARCHAR(100) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/resources/db/migration/V1__create_users.sql
git commit -m "db(flyway): V1 create users"
```

---

### Task 5: Flyway V2 — `password_resets` (Laravel `CreatePasswordResetsTable`)

**Files:**
- Create: `app/src/main/resources/db/migration/V2__create_password_resets.sql`

- [ ] **Step 1: Add migration**

Laravel indexes `email` only. For PostgreSQL + JPA, add a **composite primary key** `(email, token)` so the legacy table has a stable primary key without adding a surrogate column.

```sql
CREATE TABLE password_resets (
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL,
    CONSTRAINT pk_password_resets PRIMARY KEY (email, token)
);

CREATE INDEX idx_password_resets_email ON password_resets (email);
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/resources/db/migration/V2__create_password_resets.sql
git commit -m "db(flyway): V2 create password_resets"
```

---

### Task 6: Flyway V3 — `password_reset_tokens` only

**Files:**
- Create: `app/src/main/resources/db/migration/V3__create_password_reset_tokens.sql`

- [ ] **Step 1: Add migration**

Maps Laravel `password_reset_tokens` from the same Laravel migration file as before; `remember_token` / timestamps live on `users` in **V1**. No `sessions` table.

```sql
CREATE TABLE password_reset_tokens (
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (email)
);
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/resources/db/migration/V3__create_password_reset_tokens.sql
git commit -m "db(flyway): V3 password_reset_tokens"
```

---

### Task 7: JPA entities — `User`, `PasswordResetId`, `PasswordReset`, `PasswordResetToken`

**Files:**
- Create: `app/src/main/java/org/example/auth/domain/User.java`
- Create: `app/src/main/java/org/example/auth/domain/PasswordResetId.java`
- Create: `app/src/main/java/org/example/auth/domain/PasswordReset.java`
- Create: `app/src/main/java/org/example/auth/domain/PasswordResetToken.java`

- [ ] **Step 1: Write `User.java`**

```java
package org.example.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "date_closed")
    private LocalDate dateClosed;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 8)
    private String role;

    @Column(name = "remember_token", length = 100)
    private String rememberToken;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected User() {
    }

    public User(
            String name,
            String email,
            String password,
            String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public LocalDate getDateClosed() {
        return dateClosed;
    }

    public void setDateClosed(LocalDate dateClosed) {
        this.dateClosed = dateClosed;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRememberToken() {
        return rememberToken;
    }

    public void setRememberToken(String rememberToken) {
        this.rememberToken = rememberToken;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

- [ ] **Step 2: Write `PasswordResetId.java`**

```java
package org.example.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PasswordResetId implements Serializable {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "token", nullable = false, length = 255)
    private String token;

    protected PasswordResetId() {
    }

    public PasswordResetId(String email, String token) {
        this.email = email;
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PasswordResetId that = (PasswordResetId) o;
        return Objects.equals(email, that.email) && Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, token);
    }
}
```

- [ ] **Step 3: Write `PasswordReset.java`**

```java
package org.example.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_resets")
public class PasswordReset {

    @EmbeddedId
    private PasswordResetId id;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PasswordReset() {
    }

    public PasswordReset(PasswordResetId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public PasswordResetId getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- [ ] **Step 4: Write `PasswordResetToken.java`**

```java
package org.example.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String token;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(String email, String token, Instant createdAt) {
        this.email = email;
        this.token = token;
        this.createdAt = createdAt;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/example/auth/domain/
git commit -m "feat: add JPA entities for users and password resets"
```

---

### Task 8: Integration test — schema + entity round-trip

**Files:**
- Create: `app/src/test/java/org/example/auth/domain/AuthSchemaPersistenceIT.java`
- Create: `app/src/test/resources/application-test.yml`
- Delete or rewrite: `app/src/test/java/org/example/AppTest.java` if it still references removed `App`

- [ ] **Step 1: Add test profile datasource (Testcontainers overrides URL)**

```yaml
# app/src/test/resources/application-test.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

- [ ] **Step 2: Write failing test first (TDD)**

```java
package org.example.auth.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthSchemaPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EntityManager entityManager;

    @Test
    void user_roundTrip() {
        User user = new User("Ada", "ada@example.com", "hash", "admin");
        user.setEmailVerifiedAt(Instant.parse("2025-01-01T00:00:00Z"));
        user.setDateClosed(LocalDate.of(2026, 1, 1));
        user.setRememberToken("remember");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();

        User loaded = entityManager.find(User.class, user.getId());
        assertThat(loaded.getEmail()).isEqualTo("ada@example.com");
        assertThat(loaded.getRole()).isEqualTo("admin");
        assertThat(loaded.getRememberToken()).isEqualTo("remember");
    }

    @Test
    void passwordReset_roundTrip() {
        PasswordResetId id = new PasswordResetId("u@example.com", "tok");
        PasswordReset row = new PasswordReset(id, Instant.parse("2025-06-01T12:00:00Z"));
        entityManager.persist(row);
        entityManager.flush();
        entityManager.clear();

        PasswordReset loaded = entityManager.find(PasswordReset.class, id);
        assertThat(loaded.getCreatedAt()).isEqualTo(Instant.parse("2025-06-01T12:00:00Z"));
    }

    @Test
    void passwordResetToken_roundTrip() {
        PasswordResetToken prt = new PasswordResetToken(
                "u@example.com",
                "secret",
                Instant.parse("2025-06-02T08:00:00Z"));
        entityManager.persist(prt);
        entityManager.flush();
        entityManager.clear();

        PasswordResetToken loaded = entityManager.find(PasswordResetToken.class, "u@example.com");
        assertThat(loaded.getToken()).isEqualTo("secret");
    }
}
```

- [ ] **Step 3: Run tests**

Run:

```bash
cd /Users/evgenyusorov/workspace/java/authspring
./gradlew :app:test --tests 'org.example.auth.domain.AuthSchemaPersistenceIT'
```

Expected: **BUILD SUCCESSFUL** (Docker must be running for Testcontainers).

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/org/example/auth/domain/AuthSchemaPersistenceIT.java app/src/test/resources/application-test.yml
git rm -f app/src/test/java/org/example/AppTest.java 2>/dev/null || true
git commit -m "test: add DataJpaTest for auth schema entities"
```

---

## Self-review

1. **Spec coverage**
   - Flyway versioning: Tasks 4–6.
   - User fields matching `2014_10_12_000000_create_users_table` plus `remember_token` / timestamps (inlined in V1): Task 4 + Task 7 `User`.
   - `password_reset_tokens`: Task 6 + Task 7.
   - Legacy `password_resets`: Task 5 + Task 7.
   - No REST/API: no controller tasks.
   - JWT + React: explicitly deferred (persistence only).

2. **Placeholder scan:** No TBD/TODO; each step has concrete SQL or Java.

3. **Type consistency:** Table/column names match across Flyway and entities; composite PK for `password_resets` is consistent between V2 SQL and `PasswordReset` / `PasswordResetId`.

4. **Stack versions:** Spring Boot **4.0.5** (`gradle/libs.versions.toml`), Java toolchain **25** (`app/build.gradle`), Hibernate **7.x** managed by the Boot BOM (no explicit Hibernate coordinate in the plan). Entity code uses `jakarta.persistence` as before.

**Gap note:** Laravel’s `password_resets` migration does **not** declare a primary key. This plan adds `PRIMARY KEY (email, token)` for PostgreSQL + JPA. If you must match MySQL legacy **exactly** (no PK), switch to a surrogate `id` column in a new Flyway revision and adjust `PasswordReset`—that is a deliberate schema change.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-spring-auth-jwt-entities.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**

If Subagent-Driven: **REQUIRED SUB-SKILL:** `superpowers:subagent-driven-development`.

If Inline Execution: **REQUIRED SUB-SKILL:** `superpowers:executing-plans`.
