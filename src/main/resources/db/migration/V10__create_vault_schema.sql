-- ============================================================
-- VAULT APP — PostgreSQL Schema
-- Package: dev.chinh.portfolio.apps.vault
-- Date: 2026-04-03
-- ============================================================

-- ── bookmark_folders ─────────────────────────────────────────
CREATE TABLE bookmark_folders (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    color       VARCHAR(7)   DEFAULT '#6366F1',
    sort_order  INT          DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bookmark_folders_user ON bookmark_folders(user_id);

-- ── bookmarks ───────────────────────────────────────────────
CREATE TABLE bookmarks (
    id            BIGSERIAL PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id     BIGINT       REFERENCES bookmark_folders(id) ON DELETE SET NULL,
    url           TEXT         NOT NULL,
    title         VARCHAR(500),
    description   TEXT,
    favicon       TEXT,
    thumbnail     TEXT,
    tags          TEXT[]       DEFAULT '{}',
    is_archived   BOOLEAN     DEFAULT FALSE,
    is_favorite   BOOLEAN     DEFAULT FALSE,
    click_count   INT          DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bookmarks_user       ON bookmarks(user_id);
CREATE INDEX idx_bookmarks_folder    ON bookmarks(folder_id) WHERE folder_id IS NOT NULL;
CREATE INDEX idx_bookmarks_tags      ON bookmarks USING GIN(tags);
CREATE INDEX idx_bookmarks_archived  ON bookmarks(user_id, is_archived) WHERE is_archived = FALSE;
CREATE INDEX idx_bookmarks_favorite  ON bookmarks(user_id, is_favorite)  WHERE is_favorite = TRUE;

-- ── Trigger: auto-update updated_at ─────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER bookmark_folders_updated_at  BEFORE UPDATE ON bookmark_folders  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER bookmarks_updated_at        BEFORE UPDATE ON bookmarks        FOR EACH ROW EXECUTE FUNCTION set_updated_at();
