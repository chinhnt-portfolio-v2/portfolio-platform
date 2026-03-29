-- =============================================
-- wallet-fe v0.4.0 — new tables
-- =============================================

-- Budgets: monthly spending limits per category
CREATE TABLE IF NOT EXISTS budgets (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    monthly_limit DECIMAL(15,2) NOT NULL,
    alert_threshold INTEGER DEFAULT 80 CHECK (alert_threshold BETWEEN 1 AND 100),
    period VARCHAR(7) NOT NULL,  -- YYYY-MM
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, category_id, period)
);

CREATE INDEX IF NOT EXISTS idx_budgets_user_period ON budgets(user_id, period);

-- Recurring rules: templates for recurring transactions
CREATE TABLE IF NOT EXISTS recurring_rules (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    wallet_id BIGINT NOT NULL REFERENCES wallets(id),
    category_id BIGINT NOT NULL REFERENCES categories(id),
    amount DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    type VARCHAR(20) NOT NULL CHECK (type IN ('EXPENSE', 'INCOME')),
    frequency VARCHAR(20) NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    start_date DATE NOT NULL,
    end_date DATE,
    next_occurrence DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recurring_user ON recurring_rules(user_id);
CREATE INDEX IF NOT EXISTS idx_recurring_status ON recurring_rules(status);

-- Push subscriptions: Web Push API subscriptions
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    endpoint TEXT NOT NULL,
    p256dh TEXT,
    auth TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_push_user ON push_subscriptions(user_id);
