package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;

public record UpdateBudgetRequest(
    @Positive Double monthlyLimit,
    @Positive Integer alertThreshold
) {}
