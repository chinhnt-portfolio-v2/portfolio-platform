-- ============================================================
-- CODEBIN APP — PostgreSQL Schema
-- Package: dev.chinh.portfolio.apps.codebin
-- Date: 2026-04-03
-- ============================================================

CREATE TABLE pastes (
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID         REFERENCES users(id) ON DELETE SET NULL,
    title         VARCHAR(255) DEFAULT 'Untitled',
    language      VARCHAR(50)  DEFAULT 'plaintext',
    content       TEXT         NOT NULL,
    is_public     BOOLEAN     DEFAULT TRUE,
    password_hash VARCHAR(255),
    view_count    INT          DEFAULT 0,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pastes_user_id    ON pastes(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_pastes_public     ON pastes(created_at DESC) WHERE is_public = TRUE;
CREATE INDEX idx_pastes_expires   ON pastes(expires_at)       WHERE expires_at IS NOT NULL;

-- ── Trigger: auto-update updated_at ─────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pastes_updated_at BEFORE UPDATE ON pastes FOR EACH ROW EXECUTE FUNCTION set_updated_at();
