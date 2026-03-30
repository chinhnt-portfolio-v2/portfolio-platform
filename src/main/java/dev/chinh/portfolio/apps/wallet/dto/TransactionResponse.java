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
    String date,
    Instant createdAt,
    Instant updatedAt,
    GroupSummary group,
    WalletSummary wallet,
    CategorySummary category
) {
    public record WalletSummary(Long id, String name, String icon, String color, String type) {}
    public record CategorySummary(Long id, String name, String icon, String color) {}
    public record GroupSummary(Long id, String title) {}

    /** Factory from JPA entity (uses FK IDs only — relations loaded separately) */
    public static TransactionResponse from(dev.chinh.portfolio.apps.wallet.Transaction t) {
        String dateStr = t.getDate() != null ? t.getDate().toString().substring(0, 10) : null;
        return new TransactionResponse(
                t.getId(), t.getUserId(), t.getWalletId(), t.getCategoryId(), t.getGroupId(),
                t.getAmount(), t.getType(), t.getTxnType(), t.getNote(),
                dateStr, t.getCreatedAt(), t.getUpdatedAt(),
                null, null, null);
    }
}
