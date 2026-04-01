package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WalletResponse(
    Long id,
    UUID userId,
    String name,
    String type,
    BigDecimal balance,
    String currency,
    String icon,
    String color,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
    public record Summary(
        BigDecimal totalAssets,
        BigDecimal totalDebt,
        BigDecimal totalReceivable,
        BigDecimal netWorth,
        String currency
    ) {}

    /**
     * Single month slice in the monthly comparison list.
     * label is formatted in Vietnamese, e.g. "Thg 3".
     */
    public record MonthlyComparison(
        String month,          // ISO year-month "2026-03"
        String label,          // Vietnamese "Thg M"
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netSavings,
        long transactionCount
    ) {}
}
