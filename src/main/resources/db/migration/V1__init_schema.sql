-- ============================================================
-- V1__init_schema.sql
-- Consolidated SQLite/libSQL baseline schema
-- Derived from PG migrations V1 through V19 (cumulative end-state)
-- Dialect: Hibernate community SQLiteDialect; ddl-auto=validate
-- Generated: 2026-06-23
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- CORE: users
-- Final state: V1 (base) + V7 (name, provider_id columns)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            TEXT        PRIMARY KEY,              -- UUID, app-generated
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT,                                 -- NULL for OAuth-only users
    provider      TEXT        NOT NULL DEFAULT 'LOCAL', -- 'LOCAL' | 'GOOGLE'
    role          TEXT        NOT NULL DEFAULT 'USER',  -- 'USER' | 'OWNER'
    name          TEXT,                                 -- V7: display name
    provider_id   TEXT,                                 -- V7: OAuth2 provider sub
    created_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_users_email ON users(email);
-- V7: partial unique index: same (provider, provider_id) must not repeat
CREATE UNIQUE INDEX idx_users_provider_provider_id ON users(provider, provider_id)
    WHERE provider_id IS NOT NULL;

CREATE TRIGGER trg_users_updated_at
    AFTER UPDATE ON users
    FOR EACH ROW
BEGIN
    UPDATE users SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- CORE: sessions
-- ─────────────────────────────────────────────────────────────
CREATE TABLE sessions (
    id            TEXT        PRIMARY KEY,              -- UUID
    user_id       TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token TEXT        NOT NULL UNIQUE,
    expires_at    TEXT        NOT NULL,
    revoked       INTEGER     NOT NULL DEFAULT 0,       -- BOOLEAN: 0=false, 1=true
    created_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sessions_user_id       ON sessions(user_id);
CREATE INDEX idx_sessions_refresh_token ON sessions(refresh_token);

-- ─────────────────────────────────────────────────────────────
-- CORE: contact_submissions
-- Final state: V1 (base) + V2_1 (ip_address TEXT) + V4 (is_read)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE contact_submissions (
    id              TEXT        PRIMARY KEY,            -- UUID
    email           TEXT        NOT NULL,
    message         TEXT        NOT NULL,
    referral_source TEXT,
    ip_address      TEXT        NOT NULL,               -- V2_1: was INET, now TEXT(45)
    submitted_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_read         INTEGER     NOT NULL DEFAULT 0      -- V4: BOOLEAN 0/1
);
CREATE INDEX idx_contact_submitted_at    ON contact_submissions(submitted_at DESC);
CREATE INDEX idx_contact_referral_source ON contact_submissions(referral_source);
CREATE INDEX idx_contact_is_read         ON contact_submissions(is_read);

-- ─────────────────────────────────────────────────────────────
-- CORE: project_health
-- ─────────────────────────────────────────────────────────────
CREATE TABLE project_health (
    id                   TEXT        PRIMARY KEY,       -- UUID
    project_slug         TEXT        NOT NULL UNIQUE,
    status               TEXT        NOT NULL DEFAULT 'UNKNOWN',
    uptime_percent       NUMERIC,
    response_time_ms     INTEGER,
    last_deploy_at       TEXT,
    last_polled_at       TEXT,
    last_online_at       TEXT,
    consecutive_failures INTEGER     NOT NULL DEFAULT 0,
    updated_at           TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_project_health_slug        ON project_health(project_slug);
CREATE INDEX idx_project_health_last_polled ON project_health(last_polled_at DESC);

CREATE TRIGGER trg_project_health_updated_at
    AFTER UPDATE ON project_health
    FOR EACH ROW
BEGIN
    UPDATE project_health SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- ANALYTICS: analytics_events
-- ─────────────────────────────────────────────────────────────
CREATE TABLE analytics_events (
    id              TEXT        PRIMARY KEY,            -- UUID
    event_type      TEXT        NOT NULL,
    route           TEXT        NOT NULL,
    visitor_id      TEXT,
    traffic_source  TEXT,
    referrer_domain TEXT,
    country_code    TEXT,
    device_type     TEXT,
    session_id      TEXT,
    occurred_at     TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_analytics_occurred_at         ON analytics_events(occurred_at);
CREATE INDEX idx_analytics_route_occurred_at   ON analytics_events(route, occurred_at);
CREATE INDEX idx_analytics_event_type_occurred ON analytics_events(event_type, occurred_at);

-- ─────────────────────────────────────────────────────────────
-- WALLET: wallets
-- Final state: V5 (base) + V16 (type CHECK expanded + migration_source)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE wallets (
    id               INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id          TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             TEXT        NOT NULL,
    type             TEXT        NOT NULL CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'POSTPAID', 'SAVINGS', 'CREDIT')),
    balance          NUMERIC     DEFAULT 0,
    currency         TEXT        DEFAULT 'VND',
    icon             TEXT        DEFAULT '💰',
    color            TEXT        DEFAULT '#0EA5E9',
    is_active        INTEGER     DEFAULT 1,             -- BOOLEAN
    migration_source TEXT        DEFAULT NULL,          -- V16
    created_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wallets_user ON wallets(user_id, is_active);

CREATE TRIGGER trg_wallets_updated_at
    AFTER UPDATE ON wallets
    FOR EACH ROW
BEGIN
    UPDATE wallets SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- WALLET: categories
-- Final state: V5 (base) + V16 (migration_source)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE categories (
    id               INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id          TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             TEXT        NOT NULL,
    icon             TEXT        DEFAULT '📦',
    color            TEXT        DEFAULT '#94A3B8',
    type             TEXT        NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    is_default       INTEGER     DEFAULT 0,             -- BOOLEAN
    is_active        INTEGER     DEFAULT 1,             -- BOOLEAN
    migration_source TEXT        DEFAULT NULL,          -- V16
    created_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, name, type)
);

CREATE TRIGGER trg_categories_updated_at
    AFTER UPDATE ON categories
    FOR EACH ROW
BEGIN
    UPDATE categories SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- WALLET: debt_groups
-- ─────────────────────────────────────────────────────────────
CREATE TABLE debt_groups (
    id            INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id       TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id     INTEGER     REFERENCES wallets(id) ON DELETE SET NULL,
    title         TEXT        NOT NULL,
    group_type    TEXT        NOT NULL CHECK (group_type IN ('BNPL', 'DEBT', 'LOAN_GIVEN', 'PURCHASE_CREDIT')),
    total_amount  NUMERIC     NOT NULL,
    paid_amount   NUMERIC     DEFAULT 0,
    currency      TEXT        DEFAULT 'VND',
    status        TEXT        DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PARTIAL', 'SETTLED')),
    due_date      TEXT,
    interest_rate NUMERIC     DEFAULT 0,
    counterparty  TEXT,
    notes         TEXT        DEFAULT '{}',             -- JSONB stored as TEXT
    created_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_debt_groups_user_status ON debt_groups(user_id, status);
CREATE INDEX idx_debt_groups_due_date    ON debt_groups(due_date) WHERE due_date IS NOT NULL;

CREATE TRIGGER trg_debt_groups_updated_at
    AFTER UPDATE ON debt_groups
    FOR EACH ROW
BEGIN
    UPDATE debt_groups SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- WALLET: transactions
-- Final state: V5 (base) + V16 (migration_source)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id               INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id          TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id        INTEGER     NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    category_id      INTEGER     REFERENCES categories(id) ON DELETE SET NULL,
    group_id         INTEGER     REFERENCES debt_groups(id) ON DELETE SET NULL,
    amount           NUMERIC     NOT NULL CHECK (amount > 0),
    type             TEXT        NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    txn_type         TEXT        CHECK (txn_type IN ('PRINCIPAL', 'PAYMENT', 'FINAL_PAYMENT', 'INTEREST')),
    note             TEXT,
    date             TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    migration_source TEXT        DEFAULT NULL,          -- V16
    created_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tx_user_date ON transactions(user_id, date DESC);
CREATE INDEX idx_tx_wallet    ON transactions(wallet_id);
CREATE INDEX idx_tx_group     ON transactions(group_id) WHERE group_id IS NOT NULL;

CREATE TRIGGER trg_transactions_updated_at
    AFTER UPDATE ON transactions
    FOR EACH ROW
BEGIN
    UPDATE transactions SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- WALLET: budgets
-- ─────────────────────────────────────────────────────────────
CREATE TABLE budgets (
    id              INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id         TEXT        NOT NULL,
    category_id     INTEGER     NOT NULL REFERENCES categories(id),
    monthly_limit   NUMERIC     NOT NULL,
    alert_threshold INTEGER     DEFAULT 80 CHECK (alert_threshold BETWEEN 1 AND 100),
    period          TEXT        NOT NULL,               -- YYYY-MM
    created_at      TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, category_id, period)
);
CREATE INDEX idx_budgets_user_period ON budgets(user_id, period);

CREATE TRIGGER trg_budgets_updated_at
    AFTER UPDATE ON budgets
    FOR EACH ROW
BEGIN
    UPDATE budgets SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- WALLET: recurring_rules
-- ─────────────────────────────────────────────────────────────
CREATE TABLE recurring_rules (
    id              INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id         TEXT        NOT NULL,
    wallet_id       INTEGER     NOT NULL REFERENCES wallets(id),
    category_id     INTEGER     NOT NULL REFERENCES categories(id),
    amount          NUMERIC     NOT NULL CHECK (amount > 0),
    type            TEXT        NOT NULL CHECK (type IN ('EXPENSE', 'INCOME')),
    frequency       TEXT        NOT NULL CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    start_date      TEXT        NOT NULL,
    end_date        TEXT,
    next_occurrence TEXT,
    status          TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    note            TEXT,
    created_at      TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_recurring_user   ON recurring_rules(user_id);
CREATE INDEX idx_recurring_status ON recurring_rules(status);

CREATE TRIGGER trg_recurring_rules_updated_at
    AFTER UPDATE ON recurring_rules
    FOR EACH ROW
BEGIN
    UPDATE recurring_rules SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- WALLET: push_subscriptions
-- ─────────────────────────────────────────────────────────────
CREATE TABLE push_subscriptions (
    id         INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT        NOT NULL UNIQUE,
    endpoint   TEXT        NOT NULL,
    p256dh     TEXT,
    auth       TEXT,
    created_at TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_push_user ON push_subscriptions(user_id);

-- ─────────────────────────────────────────────────────────────
-- WALLET: budget_jars  (V17)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE budget_jars (
    id          INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id     TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    percentage  NUMERIC     NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    icon        TEXT        DEFAULT '🏺',
    color       TEXT        DEFAULT '#0EA5E9',
    is_preset   INTEGER     DEFAULT 0,                  -- BOOLEAN
    sort_order  INTEGER     DEFAULT 0,
    created_at  TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_budget_jars_user ON budget_jars(user_id);

-- TODO: enforce jar percentage SUM <= 100 in app layer (BudgetJarService).
-- The PG trigger used a DECLARE variable in plpgsql which does not translate
-- cleanly to a single SQLite BEFORE trigger without risk of off-by-one on UPDATE
-- (COALESCE(NEW.id,-1) pattern works but is fragile). App must validate on write.
CREATE TRIGGER trg_budget_jars_updated_at
    AFTER UPDATE ON budget_jars
    FOR EACH ROW
BEGIN
    UPDATE budget_jars SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- WALLET: budget_jar_categories  (V17, many-to-many)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE budget_jar_categories (
    jar_id      INTEGER     NOT NULL REFERENCES budget_jars(id) ON DELETE CASCADE,
    category_id INTEGER     NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    PRIMARY KEY (jar_id, category_id)
);

-- ─────────────────────────────────────────────────────────────
-- WALLET: wishlist_items  (V18)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE wishlist_items (
    id              INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id         TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            TEXT        NOT NULL,
    estimated_price NUMERIC,
    currency        TEXT        DEFAULT 'VND',
    priority        TEXT        NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    status          TEXT        NOT NULL DEFAULT 'SAVING' CHECK (status IN ('SAVING', 'PURCHASED', 'CANCELLED')),
    target_date     TEXT,
    notes           TEXT,
    url             TEXT,
    created_at      TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wishlist_user_status   ON wishlist_items(user_id, status);
CREATE INDEX idx_wishlist_user_priority ON wishlist_items(user_id, priority);

CREATE TRIGGER trg_wishlist_items_updated_at
    AFTER UPDATE ON wishlist_items
    FOR EACH ROW
BEGIN
    UPDATE wishlist_items SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- LEDGER: ledger_wallets  (V9 — DEPRECATED by V16 but NOT dropped)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE ledger_wallets (
    id         INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    type       TEXT        NOT NULL CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'SAVINGS', 'CREDIT')),
    balance    NUMERIC     DEFAULT 0,
    currency   TEXT        DEFAULT 'VND',
    icon       TEXT        DEFAULT '💰',
    color      TEXT        DEFAULT '#0EA5E9',
    is_active  INTEGER     DEFAULT 1,                   -- BOOLEAN
    created_at TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ledger_wallets_user ON ledger_wallets(user_id, is_active);

CREATE TRIGGER trg_ledger_wallets_updated_at
    AFTER UPDATE ON ledger_wallets
    FOR EACH ROW
BEGIN
    UPDATE ledger_wallets SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- LEDGER: ledger_entries  (V9 — DEPRECATED by V16 but NOT dropped)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE ledger_entries (
    id             INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id        TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id      INTEGER     NOT NULL REFERENCES ledger_wallets(id) ON DELETE RESTRICT,
    category       TEXT        NOT NULL,
    category_icon  TEXT        DEFAULT '📦',
    category_color TEXT        DEFAULT '#94A3B8',
    type           TEXT        NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount         NUMERIC     NOT NULL CHECK (amount > 0),
    note           TEXT,
    entry_date     TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ledger_entries_user_date ON ledger_entries(user_id, entry_date DESC);
CREATE INDEX idx_ledger_entries_wallet    ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_entries_category  ON ledger_entries(user_id, category);

CREATE TRIGGER trg_ledger_entries_updated_at
    AFTER UPDATE ON ledger_entries
    FOR EACH ROW
BEGIN
    UPDATE ledger_entries SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- VAULT: bookmark_folders  (V10)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE bookmark_folders (
    id         INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    color      TEXT        DEFAULT '#6366F1',
    sort_order INTEGER     DEFAULT 0,
    created_at TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_bookmark_folders_user ON bookmark_folders(user_id);

CREATE TRIGGER trg_bookmark_folders_updated_at
    AFTER UPDATE ON bookmark_folders
    FOR EACH ROW
BEGIN
    UPDATE bookmark_folders SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- VAULT: bookmarks  (V10)
-- tags: TEXT[] -> TEXT (comma-separated; app handles serialization)
-- GIN index on tags dropped (no SQLite equivalent)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE bookmarks (
    id          INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id     TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id   INTEGER     REFERENCES bookmark_folders(id) ON DELETE SET NULL,
    url         TEXT        NOT NULL,
    title       TEXT,
    description TEXT,
    favicon     TEXT,
    thumbnail   TEXT,
    tags        TEXT        DEFAULT '[]',               -- was TEXT[] (PostgreSQL array); stored as JSON array
    is_archived INTEGER     DEFAULT 0,                  -- BOOLEAN
    is_favorite INTEGER     DEFAULT 0,                  -- BOOLEAN
    click_count INTEGER     DEFAULT 0,
    created_at  TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_bookmarks_user     ON bookmarks(user_id);
CREATE INDEX idx_bookmarks_folder   ON bookmarks(folder_id) WHERE folder_id IS NOT NULL;
-- GIN(tags) index omitted — SQLite has no GIN; app does in-process tag filtering
CREATE INDEX idx_bookmarks_archived ON bookmarks(user_id, is_archived) WHERE is_archived = 0;
CREATE INDEX idx_bookmarks_favorite ON bookmarks(user_id, is_favorite)  WHERE is_favorite = 1;

CREATE TRIGGER trg_bookmarks_updated_at
    AFTER UPDATE ON bookmarks
    FOR EACH ROW
BEGIN
    UPDATE bookmarks SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- CODEBIN: pastes  (V11)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE pastes (
    id            INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id       TEXT        REFERENCES users(id) ON DELETE SET NULL,
    title         TEXT        DEFAULT 'Untitled',
    language      TEXT        DEFAULT 'plaintext',
    content       TEXT        NOT NULL,
    is_public     INTEGER     DEFAULT 1,                -- BOOLEAN
    password_hash TEXT,
    view_count    INTEGER     DEFAULT 0,
    expires_at    TEXT,
    created_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pastes_user_id  ON pastes(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_pastes_public   ON pastes(created_at DESC) WHERE is_public = 1;
CREATE INDEX idx_pastes_expires  ON pastes(expires_at)      WHERE expires_at IS NOT NULL;

CREATE TRIGGER trg_pastes_updated_at
    AFTER UPDATE ON pastes
    FOR EACH ROW
BEGIN
    UPDATE pastes SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- QUIZ: quiz_questions  (V12 + V14 constraints + V15 lang)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE quiz_questions (
    id             INTEGER     PRIMARY KEY AUTOINCREMENT,
    topic_slug     TEXT        NOT NULL,
    level_tag      TEXT        NOT NULL CHECK (level_tag IN ('JUNIOR', 'MIDDLE', 'SENIOR')),
    question_text  TEXT        NOT NULL,
    question_type  TEXT        NOT NULL DEFAULT 'MULTIPLE_CHOICE'
                               CHECK (question_type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'MULTIPLE_ANSWER', 'CODE_OUTPUT')),
    options        TEXT,                                -- JSONB stored as TEXT
    correct_key    TEXT        NOT NULL,
    explanation    TEXT,
    lang           TEXT        NOT NULL DEFAULT 'en',   -- V15: 'en' | 'vi'
    created_at     TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_questions_topic       ON quiz_questions(topic_slug);
CREATE INDEX idx_questions_level       ON quiz_questions(level_tag);
CREATE INDEX idx_questions_topic_level ON quiz_questions(topic_slug, level_tag);
CREATE INDEX idx_questions_updated     ON quiz_questions(updated_at DESC);

CREATE TRIGGER trg_quiz_questions_updated_at
    AFTER UPDATE ON quiz_questions
    FOR EACH ROW
BEGIN
    UPDATE quiz_questions SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;

-- ─────────────────────────────────────────────────────────────
-- QUIZ: quiz_attempts  (V12)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE quiz_attempts (
    id           INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id      TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id  INTEGER     NOT NULL REFERENCES quiz_questions(id) ON DELETE CASCADE,
    given_key    TEXT,
    is_correct   INTEGER     NOT NULL,                  -- BOOLEAN
    response_ms  INTEGER,
    attempted_at TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_attempts_user          ON quiz_attempts(user_id);
CREATE INDEX idx_attempts_question      ON quiz_attempts(question_id);
CREATE INDEX idx_attempts_user_question ON quiz_attempts(user_id, question_id);
CREATE INDEX idx_attempts_user_recent   ON quiz_attempts(user_id, attempted_at DESC);

-- ─────────────────────────────────────────────────────────────
-- QUIZ: user_quiz_progress  (V12 — composite PK)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE user_quiz_progress (
    user_id              TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_slug           TEXT        NOT NULL,
    ease_factor          NUMERIC     NOT NULL DEFAULT 2.50,
    interval_days        INTEGER     NOT NULL DEFAULT 1,
    repetitions          INTEGER     NOT NULL DEFAULT 0,
    consecutive_correct  INTEGER     NOT NULL DEFAULT 0,
    consecutive_wrong    INTEGER     NOT NULL DEFAULT 0,
    is_relearning        INTEGER     NOT NULL DEFAULT 0,    -- BOOLEAN
    next_review_at       TEXT,
    total_correct        INTEGER     NOT NULL DEFAULT 0,
    total_attempted      INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at      TEXT,
    streak_days          INTEGER     NOT NULL DEFAULT 0,
    longest_streak       INTEGER     NOT NULL DEFAULT 0,
    created_at           TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, topic_slug)
);
CREATE INDEX idx_progress_next_review ON user_quiz_progress(next_review_at)
    WHERE next_review_at IS NOT NULL;
CREATE INDEX idx_progress_user_streak ON user_quiz_progress(user_id, streak_days DESC);

CREATE TRIGGER trg_user_quiz_progress_updated_at
    AFTER UPDATE ON user_quiz_progress
    FOR EACH ROW
BEGIN
    UPDATE user_quiz_progress SET updated_at = CURRENT_TIMESTAMP
    WHERE user_id = NEW.user_id AND topic_slug = NEW.topic_slug;
END;
