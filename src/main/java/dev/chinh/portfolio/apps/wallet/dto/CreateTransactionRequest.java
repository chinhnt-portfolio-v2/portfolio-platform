package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateTransactionRequest(
    @NotNull Long walletId,
    Long categoryId,
    Long groupId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String type,
    String txnType,
    String note,
    String date,
    // Auto-create debt group (used when expense on POSTPAID wallet without groupId)
    String groupTitle,
    String groupDueDate,
    String groupCounterparty
) {}
