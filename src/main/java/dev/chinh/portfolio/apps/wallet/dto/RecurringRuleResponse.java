package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RecurringRuleResponse(
    Long id,
    java.util.UUID userId,
    Long walletId,
    Long categoryId,
    BigDecimal amount,
    String type,
    String frequency,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate nextOccurrence,
    String status,
    String note,
    TransactionResponse.WalletSummary wallet,
    TransactionResponse.CategorySummary category,
    Instant createdAt,
    Instant updatedAt
) {}
