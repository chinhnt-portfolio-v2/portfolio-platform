-- ============================================================
-- LEDGER APP — PostgreSQL Schema
-- Package: dev.chinh.portfolio.apps.ledger
-- Date: 2026-04-03
-- ============================================================

-- ── ledger_wallets ──────────────────────────────────────────
CREATE TABLE ledger_wallets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'SAVINGS', 'CREDIT')),
    balance     DECIMAL(15,2) DEFAULT 0,
    currency    VARCHAR(3)   DEFAULT 'VND',
    icon        VARCHAR(50)  DEFAULT '💰',
    color       VARCHAR(7)   DEFAULT '#0EA5E9',
    is_active   BOOLEAN     DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ledger_wallets_user ON ledger_wallets(user_id, is_active);

-- ── ledger_entries ─────────────────────────────────────────
CREATE TABLE ledger_entries (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id   BIGINT       NOT NULL REFERENCES ledger_wallets(id) ON DELETE RESTRICT,
    category    VARCHAR(100) NOT NULL,
    category_icon VARCHAR(50) DEFAULT '📦',
    category_color VARCHAR(7) DEFAULT '#94A3B8',
    type        VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount      DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    note        TEXT,
    entry_date  DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ledger_entries_user_date ON ledger_entries(user_id, entry_date DESC);
CREATE INDEX idx_ledger_entries_wallet   ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_entries_category  ON ledger_entries(user_id, category);

-- ── Trigger: auto-update updated_at ─────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_wallets_updated_at  BEFORE UPDATE ON ledger_wallets  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER ledger_entries_updated_at   BEFORE UPDATE ON ledger_entries   FOR EACH ROW EXECUTE FUNCTION set_updated_at();
