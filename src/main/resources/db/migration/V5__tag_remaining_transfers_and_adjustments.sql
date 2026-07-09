-- Backfill the internal money-movements and balance-adjustments that V3's note-marker match
-- missed, so they stop inflating income/expense stats.
--
-- 1) TRANSFERS (structural): a manual inter-wallet transfer / postpaid-bill payment was logged
--    as a balanced EXPENSE(category "Khác"=8) + INCOME(category "Khác"=13) pair with the same
--    amount and date, both still txn_type NULL. This catches the "Transfer X->Y", "(noi bo)"
--    and "Tra SPayLater ky 1/6" pairs alike (the SPayLater one double-counted a BNPL purchase).
--    Already-tagged transfers have a non-NULL txn_type so are not re-matched.
--
-- 2) ADJUSTMENTS (one-sided): opening-balance / reconciliation entries ("align Day 0",
--    "Dieu chinh ...") are neither spend nor income. Tag them ADJUSTMENT so the stats filters
--    exclude them. Balances are unaffected — relabel only.

-- 1) transfer legs (both directions) via structural pair match on Khác categories (8 / 13)
UPDATE transactions
   SET txn_type = 'TRANSFER_OUT'
 WHERE type = 'EXPENSE' AND txn_type IS NULL AND category_id = 8
   AND EXISTS (
     SELECT 1 FROM transactions i
      WHERE i.type = 'INCOME' AND i.txn_type IS NULL AND i.category_id = 13
        AND i.amount = transactions.amount
        AND substr(i.date, 1, 10) = substr(transactions.date, 1, 10)
   );

-- NB: match the expense legs just tagged TRANSFER_OUT above (they are no longer NULL).
UPDATE transactions
   SET txn_type = 'TRANSFER_IN'
 WHERE type = 'INCOME' AND txn_type IS NULL AND category_id = 13
   AND EXISTS (
     SELECT 1 FROM transactions e
      WHERE e.type = 'EXPENSE' AND e.txn_type = 'TRANSFER_OUT' AND e.category_id = 8
        AND e.amount = transactions.amount
        AND substr(e.date, 1, 10) = substr(transactions.date, 1, 10)
   );

-- 2) balance-adjustment entries (both directions), catch NULL and the one mistagged PRINCIPAL
UPDATE transactions
   SET txn_type = 'ADJUSTMENT'
 WHERE (txn_type IS NULL OR txn_type = 'PRINCIPAL')
   AND (note LIKE '%align Day 0%' OR note LIKE '%Adjustment%'
        OR note LIKE '%ieu chinh%' OR note LIKE '%can doi so du%');
