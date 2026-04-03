package dev.chinh.portfolio.apps.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateLedgerWalletRequest(
    @NotBlank String name,
    @NotBlank String type,
    String currency,
    String icon,
    String color,
    @PositiveOrZero BigDecimal initialBalance
) {}
