-- Dev-only: inserts 10 users with BCrypt passwords (cost 10, matches Spring BCryptPasswordEncoder).
-- Plaintext: seedN@seed.local / passwordN  for N = 1..10
-- Requires: pgcrypto (CREATE EXTENSION). Docker image user for POSTGRES_USER is superuser and can create it.
-- Run:  psql -U authspring -d authspring -f scripts/postgres/seed-10-users.sql
-- Or:   docker compose exec -T postgres psql -U authspring -d authspring -f - < scripts/postgres/seed-10-users.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (name, email, password, role, created_at, updated_at)
VALUES
  ('Seed User 1', 'user@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 2', 'user2@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 3', 'user3@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 4', 'user4@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 5', 'user5@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 6', 'user6@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 7', 'user7@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 8', 'user8@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 9', 'user9@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('Seed User 10', 'user10@example.com', crypt('password', gen_salt('bf', 10)), 'user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

COMMIT;
