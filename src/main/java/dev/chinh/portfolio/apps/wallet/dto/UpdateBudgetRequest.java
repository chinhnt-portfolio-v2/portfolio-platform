package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;

public record UpdateBudgetRequest(
    @Positive double monthlyLimit,
    @Positive Integer alertThreshold
) {}
