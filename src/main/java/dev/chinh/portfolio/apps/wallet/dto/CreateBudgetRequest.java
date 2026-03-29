package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;

public record CreateBudgetRequest(
    @NotNull Long categoryId,
    @NotNull @Positive double monthlyLimit,
    @Positive Integer alertThreshold, // default 80
    @NotBlank String period // YYYY-MM
) {}
