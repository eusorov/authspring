-- Run as PostgreSQL superuser (e.g. psql -U postgres -d postgres -f scripts/postgres/init-authspring.sql)
-- macOS Homebrew PostgreSQL often uses your login name as superuser: psql -U "$(whoami)" -d postgres -f ...
-- Creates role authspring and database authspring to match application.yml defaults.
-- If CREATE DATABASE fails with "already exists", the database is already there — skip that line.

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'authspring') THEN
    CREATE ROLE authspring WITH LOGIN PASSWORD 'authspring';
  END IF;
END
$$;

CREATE DATABASE authspring OWNER authspring;
