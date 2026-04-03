package dev.chinh.portfolio.apps.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerDashboardResponse(
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal netSavings,
    BigDecimal totalBalance,
    String currency,
    List<CategoryBreakdown> incomeBreakdown,
    List<CategoryBreakdown> expenseBreakdown,
    List<DailyPoint> dailyFlow
) {
    public record CategoryBreakdown(String category, String icon, String color, BigDecimal total) {}

    public record DailyPoint(LocalDate date, BigDecimal income, BigDecimal expense) {}
}
