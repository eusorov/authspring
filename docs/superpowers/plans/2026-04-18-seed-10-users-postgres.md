# Seed 10 test users (Postgres) implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a SQL script that inserts **10** rows into `users` with **known plaintext passwords**, so you can connect to the Postgres service (e.g. Docker) and run it with `psql`.

**Architecture:** Passwords in this app are **BCrypt** hashes stored in `users.password` (`VARCHAR(255)`). Spring uses **`BCryptPasswordEncoder`** with default strength **10** (`SecurityConfig`). The script uses PostgreSQL **`pgcrypto`** so hashes are generated **inside the database** with **`crypt(plaintext, gen_salt('bf', 10))`**, which is compatible with Spring Security’s `PasswordEncoder.matches`. Emails use a dedicated domain (`@seed.local`) to avoid collisions with real data. Re-runs use **`ON CONFLICT (email) DO NOTHING`** so the script is idempotent.

**Tech stack:** PostgreSQL 16, `pgcrypto`, `psql`, optional `docker compose exec`, Spring Boot login at `POST /api/login` with header `API-Version: 1`.

---

## File structure

| File | Responsibility |
|------|----------------|
| `scripts/postgres/seed-10-users.sql` | `CREATE EXTENSION IF NOT EXISTS pgcrypto`, then `INSERT` 10 users with `crypt(..., gen_salt('bf', 10))`. |
| `app/src/main/resources/db/migration/V1__create_users.sql` | Reference only — table `users`, unique `email`, `role` max length 8. |
| `app/src/main/java/com/authspring/api/security/SecurityConfig.java` | Reference — `BCryptPasswordEncoder()` matches `bf` cost 10. |

---

### Task 1: Add `scripts/postgres/seed-10-users.sql`

**Files:**

- Create: `scripts/postgres/seed-10-users.sql`

**Context:** Table shape (from `V1__create_users.sql`):

- Required columns for insert: `name`, `email`, `password`, `role`
- Optional: `email_verified_at`, `date_closed`, `remember_token` — omit (NULL)
- Set `created_at`, `updated_at` to `CURRENT_TIMESTAMP` for consistency with `RegisterService`

**Plaintext passwords (document these in a comment at top of the file):**

| Email | Password |
|-------|----------|
| `seed1@seed.local` | `password1` |
| `seed2@seed.local` | `password2` |
| … | … |
| `seed10@seed.local` | `password10` |

- [ ] **Step 1: Create the SQL file with header comment and transaction**

Create `scripts/postgres/seed-10-users.sql`:

```sql
-- Dev-only: inserts 10 users with BCrypt passwords (cost 10, matches Spring BCryptPasswordEncoder).
-- Plaintext: seedN@seed.local / passwordN  for N = 1..10
-- Requires: pgcrypto (CREATE EXTENSION). Docker image user for POSTGRES_USER is superuser and can create it.
-- Run:  psql -U authspring -d authspring -f scripts/postgres/seed-10-users.sql
-- Or:   docker compose exec -T postgres psql -U authspring -d authspring -f - < scripts/postgres/seed-10-users.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (name, email, password, role, created_at, updated_at)
VALUES
  ('Seed User 1', 'seed1@seed.local', crypt('password1', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 2', 'seed2@seed.local', crypt('password2', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 3', 'seed3@seed.local', crypt('password3', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 4', 'seed4@seed.local', crypt('password4', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 5', 'seed5@seed.local', crypt('password5', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 6', 'seed6@seed.local', crypt('password6', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 7', 'seed7@seed.local', crypt('password7', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 8', 'seed8@seed.local', crypt('password8', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 9', 'seed9@seed.local', crypt('password9', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 10', 'seed10@seed.local', crypt('password10', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

COMMIT;
```

- [ ] **Step 2: Run from host against Docker Postgres (repo root)**

```bash
docker compose exec -T postgres psql -U authspring -d authspring -f - < scripts/postgres/seed-10-users.sql
```

Expected: `CREATE EXTENSION`, `INSERT 0 10` or `INSERT 0 0` on second run (conflict skip), `COMMIT` — no `ERROR`.

---

### Task 2: Verify rows and extension (optional but recommended)

- [ ] **Step 1: Count rows**

```bash
docker compose exec postgres psql -U authspring -d authspring -c "SELECT email, left(password, 7) AS bcrypt_prefix FROM users WHERE email LIKE 'seed%@seed.local' ORDER BY email;"
```

Expected: 10 rows, `bcrypt_prefix` showing `$2a$` or `$2b$` (first 7 chars of hash).

- [ ] **Step 2: Login smoke test (app running on localhost:8080)**

```bash
curl -sS -X POST 'http://localhost:8080/api/login' \
  -H 'Content-Type: application/json' \
  -H 'API-Version: 1' \
  -d '{"email":"seed1@seed.local","password":"password1"}' | head -c 200
```

Expected: HTTP 200 with JSON containing `"token"` (and user fields). Wrong password should return **422** (see `AuthLoginIT`).

---

### Task 3: If `CREATE EXTENSION` fails (non-superuser)

**When:** Bare-metal Postgres where `authspring` cannot create extensions.

- [ ] **Step 1: One-time superuser grant**

As superuser (e.g. `postgres` or your OS user on Homebrew):

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

(Or `psql -U postgres -d authspring -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;"`)

- [ ] **Step 2: Re-run the seed script** as `authspring`

---

### Task 4: Commit

- [ ] **Step 1: Commit**

```bash
git add scripts/postgres/seed-10-users.sql
git commit -m "chore: add postgres seed script for 10 dev users"
```

---

## Self-review

**1. Spec coverage**

| Requirement | Task |
|-------------|------|
| Script writes 10 users | Task 1 |
| Passwords stored correctly (BCrypt) | Task 1 (`crypt` + `gen_salt('bf', 10)`) |
| Executable against DB service | Task 1 + Step 2 `docker compose exec` |
| Connect and run | Comments + Task 2 verification |

**2. Placeholder scan:** No TBD; SQL and commands are complete.

**3. Type consistency:** `role` is `'user'` (4 chars, ≤ 8). Emails unique. Matches `RegisterService`/`User` entity.

**Gaps:** None for “seed via SQL”. If you later need **no** `pgcrypto`, add a separate plan: generate BCrypt with `BCryptPasswordEncoder` in a small Java main on the classpath and paste static hashes — not required here.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-seed-10-users-postgres.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
