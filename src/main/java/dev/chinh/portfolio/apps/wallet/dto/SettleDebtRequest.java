package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SettleDebtRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotNull Long walletId,
    String note
) {}
