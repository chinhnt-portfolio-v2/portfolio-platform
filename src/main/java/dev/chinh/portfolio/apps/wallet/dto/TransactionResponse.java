package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    Long id,
    UUID userId,
    Long walletId,
    Long categoryId,
    Long groupId,
    BigDecimal amount,
    String type,
    String txnType,
    String note,
    Instant date,
    WalletSummary wallet,
    CategorySummary category,
    DebtGroupSummary group,
    Instant createdAt,
    Instant updatedAt
) {
    public record WalletSummary(Long id, String name, String icon, String color, String type) {}
    public record CategorySummary(Long id, String name, String icon, String color) {}
    public record DebtGroupSummary(Long id, String title, String groupType) {}
}
