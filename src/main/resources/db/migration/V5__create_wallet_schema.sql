-- ============================================================
-- WALLET APP — PostgreSQL Schema
-- Package: dev.chinh.portfolio.apps.wallet
-- Date: 2026-03-26
-- ============================================================

-- ── wallets ─────────────────────────────────────────────────
CREATE TABLE wallets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'POSTPAID')),
    balance     DECIMAL(15,2) DEFAULT 0,
    currency    VARCHAR(3)   DEFAULT 'VND',
    icon        VARCHAR(50)  DEFAULT '💰',
    color       VARCHAR(7)   DEFAULT '#0EA5E9',
    is_active   BOOLEAN     DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_wallets_user ON wallets(user_id, is_active);

-- ── categories ────────────────────────────────────────────────
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    icon        VARCHAR(50)  DEFAULT '📦',
    color       VARCHAR(7)   DEFAULT '#94A3B8',
    type        VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    is_default  BOOLEAN     DEFAULT FALSE,
    is_active   BOOLEAN     DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name, type)
);

-- ── debt_groups ──────────────────────────────────────────────
CREATE TABLE debt_groups (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id       BIGINT       REFERENCES wallets(id) ON DELETE SET NULL,
    title           VARCHAR(255) NOT NULL,
    group_type      VARCHAR(30)  NOT NULL CHECK (
                        group_type IN ('BNPL', 'DEBT', 'LOAN_GIVEN', 'PURCHASE_CREDIT')
                    ),
    total_amount    DECIMAL(15,2) NOT NULL,
    paid_amount     DECIMAL(15,2) DEFAULT 0,
    currency        VARCHAR(3)   DEFAULT 'VND',
    status          VARCHAR(20)  DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PARTIAL', 'SETTLED')),
    due_date        TIMESTAMPTZ,
    interest_rate   DECIMAL(5,4) DEFAULT 0,
    counterparty    VARCHAR(255),
    notes           JSONB        DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_debt_groups_user_status ON debt_groups(user_id, status);
CREATE INDEX idx_debt_groups_due_date   ON debt_groups(due_date) WHERE due_date IS NOT NULL;

-- ── transactions ─────────────────────────────────────────────
CREATE TABLE transactions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id       BIGINT       NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    category_id     BIGINT       REFERENCES categories(id) ON DELETE SET NULL,
    group_id        BIGINT       REFERENCES debt_groups(id) ON DELETE SET NULL,
    amount          DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    type            VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    txn_type        VARCHAR(20)  CHECK (
                        txn_type IN ('PRINCIPAL', 'PAYMENT', 'FINAL_PAYMENT', 'INTEREST')
                    ),
    note            TEXT,
    date            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tx_user_date   ON transactions(user_id, date DESC);
CREATE INDEX idx_tx_wallet      ON transactions(wallet_id);
CREATE INDEX idx_tx_group       ON transactions(group_id) WHERE group_id IS NOT NULL;

-- ── Trigger: auto-update updated_at ─────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER wallets_updated_at        BEFORE UPDATE ON wallets       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER categories_updated_at      BEFORE UPDATE ON categories    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER debt_groups_updated_at    BEFORE UPDATE ON debt_groups  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER transactions_updated_at    BEFORE UPDATE ON transactions  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── Seed: default categories ──────────────────────────────────
CREATE OR REPLACE FUNCTION seed_wallet_categories(p_user_id UUID)
RETURNS VOID AS $$
BEGIN
    INSERT INTO categories (user_id, name, icon, color, type, is_default) VALUES
        (p_user_id, 'Ăn uống',   '🍜', '#F97316', 'EXPENSE', TRUE),
        (p_user_id, 'Di chuyển', '🚗', '#3B82F6', 'EXPENSE', TRUE),
        (p_user_id, 'Mua sắm',   '🛒', '#EC4899', 'EXPENSE', TRUE),
        (p_user_id, 'Giải trí',  '🎮', '#8B5CF6', 'EXPENSE', TRUE),
        (p_user_id, 'Tiện ích',  '💡', '#F59E0B', 'EXPENSE', TRUE),
        (p_user_id, 'Y tế',       '🏥', '#10B981', 'EXPENSE', TRUE),
        (p_user_id, 'Giáo dục',  '📚', '#06B6D4', 'EXPENSE', TRUE),
        (p_user_id, 'Khác',       '📦', '#94A3B8', 'EXPENSE', TRUE),
        (p_user_id, 'Lương',      '💰', '#10B981', 'INCOME',  TRUE),
        (p_user_id, 'Freelance',  '💻', '#22C55E', 'INCOME',  TRUE),
        (p_user_id, 'Đầu tư',    '📈', '#14B8A6', 'INCOME',  TRUE),
        (p_user_id, 'Quà tặng',  '🎁', '#F472B6', 'INCOME',  TRUE),
        (p_user_id, 'Khác',      '📦', '#94A3B8', 'INCOME',  TRUE)
    ON CONFLICT DO NOTHING;
END;
$$ LANGUAGE plpgsql;
