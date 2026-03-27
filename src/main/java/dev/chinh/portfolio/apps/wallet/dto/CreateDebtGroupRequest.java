package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;

public record CreateDebtGroupRequest(
    @NotBlank String title,
    @NotBlank String groupType,
    @NotNull @DecimalMin("0.01") Double totalAmount,
    Long walletId,
    String dueDate,
    Double interestRate,
    String counterparty
) {}
