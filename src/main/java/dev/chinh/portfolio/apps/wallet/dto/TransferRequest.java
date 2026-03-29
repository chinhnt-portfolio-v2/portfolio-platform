package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferRequest(
    @NotNull Long fromWalletId,
    @NotNull Long toWalletId,
    @NotNull @Positive BigDecimal amount,
    String note
) {}
