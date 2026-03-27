package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;

public record SettleDebtRequest(
    @NotNull @DecimalMin("0.01") Double amount,
    @NotNull Long walletId,
    String note
) {}
