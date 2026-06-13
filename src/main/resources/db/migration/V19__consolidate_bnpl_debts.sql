-- Consolidate fragmented active (OPEN/PARTIAL) BNPL pay-later debts into ONE per wallet.
--
-- Background: a pay-later purchase used to auto-create a brand-new BNPL debt each time, so a
-- wallet accumulated many tiny debts. The app now accumulates purchases into a single
-- consolidated debt per pay-later wallet. This backfills existing data to match: per
-- (user_id, wallet_id), the OLDEST active BNPL group is the survivor; the others are merged
-- into it (totals summed, transactions re-pointed) and then removed.
--
-- Only OPEN/PARTIAL BNPL groups are touched. SETTLED debts and other group types
-- (DEBT, LOAN_GIVEN, PURCHASE_CREDIT) are left untouched. Runs in a single Flyway
-- transaction — any failure rolls the whole migration back.

-- 1) Roll each wallet's active-BNPL totals into that wallet's survivor (oldest group).
UPDATE debt_groups g
SET total_amount = agg.sum_total,
    paid_amount  = agg.sum_paid,
    status = CASE
        WHEN agg.sum_total > 0 AND agg.sum_paid >= agg.sum_total THEN 'SETTLED'
        WHEN agg.sum_paid > 0 THEN 'PARTIAL'
        ELSE 'OPEN' END
FROM (
    SELECT survivor_id,
           SUM(total_amount)              AS sum_total,
           SUM(COALESCE(paid_amount, 0))  AS sum_paid
    FROM (
        SELECT id, total_amount, paid_amount,
               first_value(id) OVER (
                   PARTITION BY user_id, wallet_id ORDER BY created_at, id
               ) AS survivor_id
        FROM debt_groups
        WHERE group_type = 'BNPL' AND status IN ('OPEN', 'PARTIAL')
    ) ranked
    GROUP BY survivor_id
) agg
WHERE g.id = agg.survivor_id;

-- 2) Re-point all transactions from non-survivor active BNPL groups onto the survivor.
UPDATE transactions t
SET group_id = m.survivor_id
FROM (
    SELECT id AS old_id,
           first_value(id) OVER (
               PARTITION BY user_id, wallet_id ORDER BY created_at, id
           ) AS survivor_id
    FROM debt_groups
    WHERE group_type = 'BNPL' AND status IN ('OPEN', 'PARTIAL')
) m
WHERE t.group_id = m.old_id AND m.old_id <> m.survivor_id;

-- 3) Delete the now-empty non-survivor active BNPL groups.
DELETE FROM debt_groups
WHERE group_type = 'BNPL'
  AND status IN ('OPEN', 'PARTIAL')
  AND id NOT IN (
      SELECT DISTINCT first_value(id) OVER (
          PARTITION BY user_id, wallet_id ORDER BY created_at, id
      )
      FROM debt_groups
      WHERE group_type = 'BNPL' AND status IN ('OPEN', 'PARTIAL')
  );
