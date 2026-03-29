package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateWalletRequest(
    @NotBlank String name,
    @NotBlank String type,
    String currency,
    String icon,
    String color,
    BigDecimal initialBalance
) {}
