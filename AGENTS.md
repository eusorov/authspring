# AGENT.md — authspring

## What this project is

**authspring** is a **Spring Boot** backend written in **Java**. It is intended to become a **REST API** that **authenticates users** and issues credentials for a **stateless client UI** (for example **React**): no server-side HTML sessions for the SPA; the browser holds tokens (typically JWT or similar) and calls the API over HTTPS.

The frontend is **out of this repository**; this repo is the API and persistence layer only.

## Stack (as implemented)

- **Java** 25 (Gradle toolchain)
- **Spring Boot** 4.0.x
- **Spring Data JPA** + **Hibernate**
- **PostgreSQL** (runtime)
- **Flyway** for schema migrations (`spring-boot-starter-flyway` + `flyway-database-postgresql`)
- **Tests:** Spring Boot Test, **Testcontainers** (PostgreSQL) for integration tests

Local database: optional **`docker-compose.yml`** (Postgres 16) aligned with `application.yml` defaults.

## Domain layout

- Main class: `com.authspring.AuthspringApplication` (scans `com.authspring.api`).
- API code: `com.authspring.api` — `web` (REST + `GlobalExceptionHandler`), `web.dto`, `service`, `repo`, `domain`, `security`, `config`.
- JPA entities: `com.authspring.api.domain` (e.g. `User`, password-reset types).
- Flyway SQL: `app/src/main/resources/db/migration/`

## API versioning

Clients must send **`API-Version: 1`** on requests to versioned endpoints (Spring MVC [API versioning](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/api-version.html) via `ApiVersioningConfig`).

## Auth API (implemented)

- **`POST /api/login`** — JSON `{ "email", "password" }`; success `{ "token", "user" }` (JWT + user payload). Wrong credentials: **422** with Laravel-shaped `{ "message", "errors": { "email": [...] } }`.
- **`POST /api/logout`** — `Authorization: Bearer <jwt>`; success `{ "message": "Logged out" }`. JWT is stateless (no server token store like Sanctum); clients drop the token after logout.

**Configuration:** `jwt.secret` (≥32 bytes for HS256) and `jwt.expiration-ms` in `application.yml`; override **`JWT_SECRET`** in production.

## Direction for future work

- **REST** for registration, refresh, password reset flows (Laravel parity).
- Optional **token revocation** (blocklist/allowlist) if you need server-side logout semantics beyond “discard JWT client-side”.

When adding features, keep API **JSON-first**, **stateless** from the SPA’s perspective, and avoid coupling this service to a specific React version or bundler.

## Conventions for agents

- Prefer **small, focused changes**; match existing package names and Gradle style.
- **Do not** add a React app inside this repo unless the user asks.
- After schema changes, add or adjust **Flyway** migrations and keep **JPA entities** in sync with the database.
- Run **`./gradlew :app:test`** (and targeted IT classes when touching persistence) before claiming green builds; integration tests need **Docker** for Testcontainers when applicable.

## Docs

- Implementation plan and history: `docs/superpowers/plans/2026-04-18-spring-auth-jwt-entities.md`
