package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;

public record CreateTransactionRequest(
    @NotNull Long walletId,
    Long categoryId,
    Long groupId,
    @NotNull @DecimalMin("0.01") Double amount,
    @NotBlank String type,
    String txnType,
    String note,
    String date
) {}
