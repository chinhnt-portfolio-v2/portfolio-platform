package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BudgetResponse(
    Long id,
    java.util.UUID userId,
    Long categoryId,
    BigDecimal monthlyLimit,
    Integer alertThreshold,
    String period,
    TransactionResponse.CategorySummary category,
    BigDecimal currentSpent,
    Integer percentage,
    String status, // ok | warning | exceeded
    Instant createdAt
) {}
