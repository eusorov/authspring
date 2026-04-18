# authspring

A Spring Boot backend that provides authentication and account-management REST endpoints for a stateless SPA client.

Inspired by **Laravel + Sanctum** (endpoint shapes, JSON error envelopes, password-reset flow, email verification). Authentication is **stateless JWT** (HS256) plus **Sanctum-style personal access tokens**: each issued API JWT is stored as **SHA-256(jwt)** in `personal_access_tokens`. The filter accepts a Bearer JWT only if the signature is valid **and** that hash exists in the database, so the server can **revoke** access (`POST /api/logout` deletes the row). Clients still discard the JWT locally after logout.

> The frontend (e.g. React) lives in a separate repository. This repo is the API and persistence layer only.

## Table of contents

- [Why this project](#why-this-project)
- [Tech stack](#tech-stack)
- [API versioning](#api-versioning)
- [API endpoints](#api-endpoints)
- [Running locally](#running-locally)
- [Configuration](#configuration)
- [Tests](#tests)
- [Project layout](#project-layout)

## Why this project

authspring reimplements the authentication surface of a typical Laravel + Sanctum API in idiomatic Spring Boot:

- **Register / login / logout**, **forgot password / reset password**, and **email verification** — JSON and form-data REST endpoints.
- **Laravel-shaped error envelopes**: 422 responses use `ProblemDetail` with `message` and `errors: { field: [messages] }` so existing Laravel frontends can consume them with minimal changes.
- **JWT + PAT table**: `POST /api/login` and `POST /api/register` issue a signed JWT and persist **SHA-256(jwt)** in `personal_access_tokens` (Laravel Sanctum–compatible columns). `JwtAuthenticationFilter` requires a matching row so revoked tokens cannot authenticate. `POST /api/logout` removes the row for the current Bearer token.
- **API versioning** via the `API-Version` header (Spring MVC built-in), so breaking changes get a new major version instead of silently changing behavior.

## Tech stack

**Language & build**
- Java 25 (Gradle toolchain)
- Gradle (Kotlin-less Groovy DSL, version catalogue in `gradle/libs.versions.toml`)

**Runtime framework**
- Spring Boot 4.0.x
- Spring Web MVC (API versioning via `ApiVersioningConfig`)
- Spring Security (stateless; CSRF disabled; CORS configurable; method security with `@PreAuthorize` for email-verified routes)
- Spring Data JPA + Hibernate
- Spring Boot Actuator (health / liveness / readiness probes)
- Spring Boot Mail (JavaMail) — verification & password-reset emails
- Spring Boot Validation (Jakarta Bean Validation)

**Persistence**
- PostgreSQL 16
- Flyway (`spring-boot-starter-flyway` + `flyway-database-postgresql`) — migrations in `app/src/main/resources/db/migration/`

**Auth & crypto**
- JJWT 0.12.6 (HS256, `jjwt-api` / `jjwt-impl` / `jjwt-jackson`)
- BCrypt password hashing (`BCryptPasswordEncoder`)
- Signed email-verification URLs (Laravel-compatible HMAC-SHA256 signer)

**Resilience**
- Resilience4j Spring Boot 4 (global rate limiter `apiGlobal`: 300 req/min)

**Testing**
- JUnit 5, Spring Boot Test, Spring Security Test, Spring Boot `webmvc-test`
- Testcontainers (PostgreSQL) for integration tests
- Mockito for mail-sender mocks

**Local infra (optional)**
- Docker Compose — Postgres 16 + Mailpit (SMTP on `:1025`, UI on `:8025`)

## API versioning

All `/api/**` endpoints require the header:

```
API-Version: 1
```

Versioning is wired via Spring MVC's built-in API-version support (`ApiVersioningConfig`). Requests without this header (or with an unsupported version) are rejected before reaching the controller. We deliberately chose `API-Version` over `X-API-Version` per [RFC 6648](https://www.rfc-editor.org/rfc/rfc6648), which deprecates the `X-` prefix for new headers.

**Bearer JWT:** Protected routes expect `Authorization: Bearer <jwt>`. The JWT must be valid **and** have a corresponding row in `personal_access_tokens` (except dedicated flows that use other JWT types, e.g. password-reset emails).

## API endpoints

All endpoints below require `API-Version: 1`. **`Authorization: Bearer <jwt>`** is required for **`/api/secured/**`** (URL rule in `SecurityConfig`). **`GET /api/user`**, **`POST /api/logout`**, and **`POST /api/email/verification-notification`** use **`@RequiresAuth`** on the controller. **`GET /api/needsverified`** uses **`@EmailVerifiedGuard`** (authenticated + verified email). Other `/api/**` routes are public for the verbs listed.

### Authentication

| Method | Path           | Auth | Consumes                           | Purpose                                                                 |
|--------|----------------|------|------------------------------------|-------------------------------------------------------------------------|
| POST   | `/api/register`| No   | `multipart/form-data`, `x-www-form-urlencoded` | Create a new user. Sends verification email. Returns `{ message, token, user }` and records a PAT. |
| POST   | `/api/login`   | No   | `application/json`, `multipart/form-data`, `x-www-form-urlencoded` | Exchange `email` + `password` for a JWT. Returns `{ token, user }` and records a PAT. |
| GET    | `/api/user`    | Yes  | —                                  | Current user profile (`UserResponse`, same fields as login/register `user`). |
| POST   | `/api/logout`  | Yes  | —                                  | Deletes the PAT row for the current Bearer JWT (client should still discard the JWT). |

### Password reset (Laravel parity)

| Method | Path                    | Auth | Consumes                                           | Purpose                                                       |
|--------|-------------------------|------|----------------------------------------------------|---------------------------------------------------------------|
| POST   | `/api/forgot-password`  | No   | `multipart/form-data`, `x-www-form-urlencoded`     | Email a password-reset link for the given `email`.            |
| POST   | `/api/reset-password`   | No   | `multipart/form-data`, `x-www-form-urlencoded`     | Submit `token`, `email`, `password`, `confirmed` to reset.    |

### Email verification

| Method | Path                                      | Auth | Consumes                                       | Purpose                                                        |
|--------|-------------------------------------------|------|------------------------------------------------|----------------------------------------------------------------|
| GET    | `/api/email/verify/{id}/{hash}`           | No   | —                                              | Consume a signed Laravel-style verification URL.               |
| POST   | `/api/email/verification-notification` | Yes  | `multipart/form-data`, `x-www-form-urlencoded` | Re-send the verification email for the authenticated user.     |

### Diagnostics

| Method | Path                          | Auth | Purpose                                                        |
|--------|-------------------------------|------|----------------------------------------------------------------|
| GET    | `/api/secured/test`           | Yes  | Smoke-test endpoint that echoes the authenticated principal.   |
| GET    | `/api/needsverified`          | Yes* | Example route that requires **verified email** (`@EmailVerifiedGuard`). Returns `{ "status": "needsverified-ok" }` or 403 with detail `You must verify your email` if unverified. |
| GET    | `/actuator/health`            | No   | Aggregate health status.                                       |
| GET    | `/actuator/health/liveness`   | No   | Kubernetes liveness probe.                                     |
| GET    | `/actuator/health/readiness`  | No   | Kubernetes readiness probe.                                    |

\*Bearer JWT + PAT + `email_verified_at` set. Enforced via `@EmailVerifiedGuard` (`@PreAuthorize` → `EmailVerifiedGuardBean`). `AccessDeniedException` is mapped to RFC 9457 JSON in `GlobalExceptionHandler`. Use `@RequiresAuth` on other controllers when you only need an authenticated API user.

### Error envelope

Validation and domain failures return HTTP 422 with a `ProblemDetail` body shaped:

```json
{
  "type": "about:blank",
  "title": "…",
  "status": 422,
  "detail": "…",
  "message": "The given data was invalid.",
  "errors": { "email": ["We can't find a user with that e-mail address."] }
}
```

401 Unauthorized and 403 Forbidden responses use `application/problem+json` (`ProblemDetail`) from `ProblemJsonAuthenticationEntryPoint` and `GlobalExceptionHandler` (including failed `@PreAuthorize` / email verification).

## Running locally

**Prerequisites:** Java 25, Docker (for Postgres + Mailpit), and no other Postgres on port 5432.

1. Start infrastructure:
   ```bash
   docker compose up -d
   ```
   Postgres listens on `localhost:5432`; Mailpit's SMTP on `:1025` and its web UI on [http://localhost:8025](http://localhost:8025).

2. Run the app (Flyway migrates the schema on startup):
   ```bash
   ./gradlew :app:bootRun
   ```
   The API is served at [http://localhost:8080](http://localhost:8080).

3. Smoke-test the health probes:
   ```bash
   curl -s http://localhost:8080/actuator/health
   curl -s http://localhost:8080/actuator/health/readiness
   ```

4. Log in with a seeded user (see `scripts/postgres/seed-10-users.sql` to populate ten users `seed1@seed.local` … `seed10@seed.local` with passwords `password1` … `password10`):
   ```bash
   curl -s -X POST http://localhost:8080/api/login \
     -H "API-Version: 1" \
     -H "Content-Type: application/json" \
     -d '{"email":"seed1@seed.local","password":"password1"}'
   ```

## Configuration

All configuration lives in `app/src/main/resources/application.yml` and is overridable via environment variables.

| Variable                            | Default                                                   | Purpose                                               |
|-------------------------------------|-----------------------------------------------------------|-------------------------------------------------------|
| `SPRING_DATASOURCE_URL`             | `jdbc:postgresql://localhost:5432/authspring`             | JDBC URL                                              |
| `SPRING_DATASOURCE_USERNAME`        | `authspring`                                              | DB role                                               |
| `SPRING_DATASOURCE_PASSWORD`        | `authspring`                                              | DB password                                           |
| `JWT_SECRET`                        | dev-only placeholder (≥32 bytes required for HS256)       | **Set in production.** JWT signing key.               |
| `JWT_PASSWORD_RESET_EXPIRATION_MS`  | `3600000` (1 h)                                           | Password-reset token lifetime                         |
| `MAIL_HOST` / `MAIL_PORT`           | `localhost` / `1025` (Mailpit)                            | Outbound SMTP                                         |
| `MAIL_FROM_ADDRESS` / `MAIL_FROM_NAME` | `noreply@example.com` / `Team`                         | Envelope sender                                       |
| `APP_URL`                           | `http://localhost:8080`                                   | Base URL used when signing verification links         |
| `VERIFICATION_SIGNING_KEY`          | dev-only placeholder                                      | HMAC key for Laravel-compatible signed verify URLs    |
| `VERIFICATION_EXPIRE_MINUTES`       | `60`                                                      | Verification URL lifetime                             |
| `FRONTEND_CORS`                     | `http://localhost:3000`                                   | Allowed SPA origin                                    |

The global rate limiter (`resilience4j.ratelimiter.instances.apiGlobal`) is configured at 300 requests per minute with a 5 s wait timeout; tune it in `application.yml` if needed.

## Tests

```bash
./gradlew :app:test
```

Integration tests use Testcontainers with `postgres:16-alpine`, so **Docker must be running**. Mail-sending tests swap `JavaMailSender` for a Mockito mock, so no real SMTP is required.

Run a single test class:

```bash
./gradlew :app:test --tests "com.authspring.api.AuthLoginIT"
```

## Project layout

```
app/
  build.gradle
  src/
    main/
      java/com/authspring/
        AuthspringApplication.java          # Spring Boot entry point
        api/
          config/                           # Spring config (API versioning, CORS, …)
          domain/                           # JPA entities (User, PasswordResetToken, …)
          repo/                             # Spring Data repositories
          service/                          # Business logic (sealed interfaces for outcomes)
          security/                         # JWT + PAT filter, UserPrincipal, SecurityConfig, @RequiresAuth / @EmailVerifiedGuard, EmailVerifiedGuardBean
          web/                              # REST controllers
          web/dto/                          # Request/response records with Jakarta Validation
      resources/
        application.yml
        db/migration/                       # Flyway SQL (V1__…, V2__…)
docker-compose.yml                           # Postgres 16 + Mailpit
docs/superpowers/plans/                      # Implementation plans, one per feature slice
scripts/postgres/                            # init-authspring.sql, seed-10-users.sql
```

See [`AGENTS.md`](./AGENTS.md) for repo-wide conventions.
