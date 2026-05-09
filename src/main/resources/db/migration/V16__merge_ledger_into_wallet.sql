-- V16__merge_ledger_into_wallet.sql
-- Merges ledger_wallets -> wallets and ledger_entries -> transactions
-- Adds SAVINGS, CREDIT wallet types

-- Step 0: Add migration_source column for rollback discrimination
ALTER TABLE wallets ADD COLUMN IF NOT EXISTS migration_source VARCHAR(20) DEFAULT NULL;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS migration_source VARCHAR(20) DEFAULT NULL;
ALTER TABLE categories ADD COLUMN IF NOT EXISTS migration_source VARCHAR(20) DEFAULT NULL;

-- Step 0b: Pre-flight audit
DO $$
DECLARE orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM ledger_entries le
    WHERE NOT EXISTS (
        SELECT 1 FROM ledger_wallets lw WHERE lw.id = le.wallet_id
    );
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight failed: % ledger entries reference non-existent ledger wallets', orphan_count;
    END IF;
END $$;

-- Step 1: Expand wallets type CHECK to include SAVINGS, CREDIT
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS wallets_type_check;
ALTER TABLE wallets ADD CONSTRAINT wallets_type_check
    CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'POSTPAID', 'SAVINGS', 'CREDIT'));

-- Step 2: Migrate ledger_wallets -> wallets (new IDs assigned by BIGSERIAL)
INSERT INTO wallets (user_id, name, type, balance, currency, icon, color, is_active, migration_source, created_at, updated_at)
SELECT user_id, name, type, balance, currency, icon, color, is_active, 'LEDGER_V16', created_at, updated_at
FROM ledger_wallets lw
WHERE NOT EXISTS (
    SELECT 1 FROM wallets w
    WHERE w.user_id = lw.user_id
    AND LOWER(w.name) = LOWER(lw.name)
    AND w.type = lw.type
);

-- Step 3: Create categories from ledger_entries inline categories
INSERT INTO categories (user_id, name, icon, color, type, is_default, is_active, migration_source)
SELECT DISTINCT le.user_id, le.category, le.category_icon, le.category_color, le.type, FALSE, TRUE, 'LEDGER_V16'
FROM ledger_entries le
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = le.user_id AND c.name = le.category AND c.type = le.type
);

-- Step 4: Migrate ledger_entries -> transactions (LEFT JOIN to detect orphans)
INSERT INTO transactions (user_id, wallet_id, category_id, amount, type, note, date, migration_source, created_at, updated_at)
SELECT
    le.user_id,
    COALESCE(w.id, (SELECT id FROM wallets WHERE user_id = le.user_id ORDER BY created_at LIMIT 1)),
    c.id,
    le.amount,
    le.type,
    CASE WHEN w.id IS NULL
        THEN COALESCE(le.note, '') || ' [migrated: wallet unresolved, assigned to default]'
        ELSE le.note END,
    le.entry_date::timestamptz,
    'LEDGER_V16',
    le.created_at,
    le.updated_at
FROM ledger_entries le
LEFT JOIN ledger_wallets lw ON lw.id = le.wallet_id
LEFT JOIN wallets w ON w.user_id = le.user_id
    AND LOWER(w.name) = LOWER(lw.name)
LEFT JOIN categories c ON c.user_id = le.user_id
    AND c.name = le.category AND c.type = le.type;

-- Step 5: Post-migration row count assertion
DO $$
DECLARE ledger_count INTEGER; migrated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO ledger_count FROM ledger_entries;
    SELECT COUNT(*) INTO migrated_count FROM transactions WHERE migration_source = 'LEDGER_V16';
    IF migrated_count < ledger_count THEN
        RAISE EXCEPTION 'Row count mismatch: % ledger entries, % migrated transactions', ledger_count, migrated_count;
    END IF;
END $$;

-- Step 6: Mark ledger tables as deprecated
COMMENT ON TABLE ledger_wallets IS 'DEPRECATED: migrated to wallets in V16';
COMMENT ON TABLE ledger_entries IS 'DEPRECATED: migrated to transactions in V16';
