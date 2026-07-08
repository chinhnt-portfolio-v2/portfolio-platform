-- Widen transactions.txn_type CHECK to allow inter-wallet transfer tags
-- (TRANSFER_OUT / TRANSFER_IN). TransferService already writes these values, but the
-- original CHECK rejected them, so transfers could not persist / be filtered from stats.
--
-- SQLite cannot ALTER a CHECK constraint, so the table is rebuilt. No other table has a
-- foreign key REFERENCING transactions (it only points outward), so a plain drop+recreate
-- is safe and needs no PRAGMA foreign_keys toggling.

CREATE TABLE transactions_new (
    id               INTEGER     PRIMARY KEY AUTOINCREMENT,
    user_id          TEXT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id        INTEGER     NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    category_id      INTEGER     REFERENCES categories(id) ON DELETE SET NULL,
    group_id         INTEGER     REFERENCES debt_groups(id) ON DELETE SET NULL,
    amount           NUMERIC     NOT NULL CHECK (amount > 0),
    type             TEXT        NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    txn_type         TEXT        CHECK (txn_type IN ('PRINCIPAL', 'PAYMENT', 'FINAL_PAYMENT', 'INTEREST', 'TRANSFER_OUT', 'TRANSFER_IN')),
    note             TEXT,
    date             TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    migration_source TEXT        DEFAULT NULL,
    created_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO transactions_new
    (id, user_id, wallet_id, category_id, group_id, amount, type, txn_type, note, date, migration_source, created_at, updated_at)
SELECT
    id, user_id, wallet_id, category_id, group_id, amount, type, txn_type, note, date, migration_source, created_at, updated_at
FROM transactions;

DROP TABLE transactions;
ALTER TABLE transactions_new RENAME TO transactions;

-- Preserve the AUTOINCREMENT high-water mark so new inserts don't collide with existing ids.
DELETE FROM sqlite_sequence WHERE name IN ('transactions', 'transactions_new');
INSERT INTO sqlite_sequence (name, seq) VALUES ('transactions', (SELECT COALESCE(MAX(id), 0) FROM transactions));

-- Recreate indexes + updated_at trigger (dropped with the old table).
CREATE INDEX idx_tx_user_date ON transactions(user_id, date DESC);
CREATE INDEX idx_tx_wallet    ON transactions(wallet_id);
CREATE INDEX idx_tx_group     ON transactions(group_id) WHERE group_id IS NOT NULL;

CREATE TRIGGER trg_transactions_updated_at
    AFTER UPDATE ON transactions
    FOR EACH ROW
BEGIN
    UPDATE transactions SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
