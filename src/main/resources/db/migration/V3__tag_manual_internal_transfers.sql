-- Tag manually-logged inter-wallet transfers so they drop out of income/expense stats.
-- These were entered as plain EXPENSE/INCOME pairs (txn_type NULL, category "Khác") with an
-- explicit "(transfer OUT)" / "(transfer IN)" note marker, because the Transfer feature could
-- not persist its tags until V2 widened the CHECK. Wallet balances are already correct — this
-- only relabels txn_type so the existing transfer-exclusion filters catch them.
--
-- Guarded by `txn_type IS NULL` (idempotent, never re-tags) and matched to the correct
-- direction (EXPENSE->OUT, INCOME->IN). Verified against live data: 4 OUT + 4 IN, matched sums,
-- zero already-tagged rows, zero false positives.

UPDATE transactions
   SET txn_type = 'TRANSFER_OUT'
 WHERE type = 'EXPENSE'
   AND txn_type IS NULL
   AND note LIKE '%(transfer OUT)%';

UPDATE transactions
   SET txn_type = 'TRANSFER_IN'
 WHERE type = 'INCOME'
   AND txn_type IS NULL
   AND note LIKE '%(transfer IN)%';
