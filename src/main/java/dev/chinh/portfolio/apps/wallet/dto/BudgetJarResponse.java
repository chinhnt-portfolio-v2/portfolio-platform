package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetJarResponse(
    Long id,
    String name,
    BigDecimal percentage,
    String icon,
    String color,
    Boolean isPreset,
    Integer sortOrder,
    List<CategorySummary> categories,
    BigDecimal monthlyIncome,
    BigDecimal allocated,
    BigDecimal spent,
    BigDecimal remaining,
    String status  // ok | exceeded
) {
    public record CategorySummary(Long id, String name, String icon, String color) {}
}
