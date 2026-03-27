package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WalletResponse(
    Long id,
    UUID userId,
    String name,
    String type,
    BigDecimal balance,
    String currency,
    String icon,
    String color,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
    public record Summary(
        BigDecimal totalAssets,
        BigDecimal totalDebt,
        BigDecimal totalReceivable,
        BigDecimal netWorth,
        String currency
    ) {}
}
