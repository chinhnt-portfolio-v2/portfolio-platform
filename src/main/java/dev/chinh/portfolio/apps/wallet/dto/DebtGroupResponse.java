package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DebtGroupResponse(
    Long id,
    UUID userId,
    Long walletId,
    String title,
    String groupType,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal remaining,
    String currency,
    String status,
    Instant dueDate,
    BigDecimal interestRate,
    String counterparty,
    Map<String, Object> notes,
    WalletSummary wallet,
    Instant createdAt,
    Instant updatedAt
) {
    public record WalletSummary(Long id, String name, String icon, String color, String type) {}
}
