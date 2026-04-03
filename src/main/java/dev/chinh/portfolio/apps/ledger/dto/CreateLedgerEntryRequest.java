package dev.chinh.portfolio.apps.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLedgerEntryRequest(
    @NotNull Long walletId,
    @NotBlank String category,
    String categoryIcon,
    String categoryColor,
    @NotBlank String type,
    @NotNull @Positive BigDecimal amount,
    String note,
    LocalDate entryDate
) {}
