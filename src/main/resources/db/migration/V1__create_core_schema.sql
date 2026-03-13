-- V1__create_core_schema.sql
-- Portfolio v2 — Initial schema: users, sessions, contact_submissions, project_health
-- NOTE: provider column added per Story 2.2 AC#4 (not in original epics.md DDL)

-- ─────────────────────────────────────────────────────────
-- users
-- ─────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),                              -- NULL for OAuth-only users
    provider      VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',     -- 'LOCAL' | 'GOOGLE'
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',      -- 'USER' | 'OWNER'
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);

-- ─────────────────────────────────────────────────────────
-- sessions (auth boundary — Session entity ONLY in auth.session.*)
-- ─────────────────────────────────────────────────────────
CREATE TABLE sessions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token VARCHAR(512) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_refresh_token ON sessions(refresh_token);

-- ─────────────────────────────────────────────────────────
-- contact_submissions
-- ─────────────────────────────────────────────────────────
CREATE TABLE contact_submissions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    message         TEXT         NOT NULL,          -- min 10 chars, max 2000 chars (validated at service layer)
    referral_source VARCHAR(100),                   -- value of ?from= param; NULL if not present
    ip_address      INET         NOT NULL,          -- for rate limiting; NEVER returned in API responses
    submitted_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_contact_submitted_at ON contact_submissions(submitted_at DESC);
CREATE INDEX idx_contact_referral_source ON contact_submissions(referral_source);

-- ─────────────────────────────────────────────────────────
-- project_health
-- ─────────────────────────────────────────────────────────
CREATE TABLE project_health (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_slug         VARCHAR(100) NOT NULL UNIQUE,   -- matches projects.ts config slug
    status               VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN', -- 'UP'|'DOWN'|'DEGRADED'|'UNKNOWN'
    uptime_percent       DECIMAL(5,2),                  -- e.g. 99.87; NULL if no data
    response_time_ms     INTEGER,                       -- last p95 ms; NULL if no data
    last_deploy_at       TIMESTAMPTZ,                   -- from GitHub webhook; NULL if no webhook received
    last_polled_at       TIMESTAMPTZ,                   -- timestamp of last poll attempt
    last_online_at       TIMESTAMPTZ,                   -- last time status was UP; NULL if never
    consecutive_failures INTEGER      NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_project_health_slug ON project_health(project_slug);
CREATE INDEX idx_project_health_last_polled ON project_health(last_polled_at DESC);
