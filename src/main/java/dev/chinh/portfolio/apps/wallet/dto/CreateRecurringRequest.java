package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateRecurringRequest(
    @NotNull Long walletId,
    @NotNull Long categoryId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String type, // EXPENSE or INCOME
    @NotBlank String frequency, // DAILY, WEEKLY, MONTHLY, YEARLY
    @NotBlank String startDate, // YYYY-MM-DD
    String endDate, // YYYY-MM-DD (optional)
    String note
) {}
