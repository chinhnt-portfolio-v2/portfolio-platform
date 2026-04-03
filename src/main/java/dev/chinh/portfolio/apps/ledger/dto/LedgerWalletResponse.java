package dev.chinh.portfolio.apps.ledger.dto;

import dev.chinh.portfolio.apps.ledger.LedgerWallet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerWalletResponse(
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
    public static LedgerWalletResponse from(LedgerWallet w) {
        return new LedgerWalletResponse(
            w.getId(), w.getUserId(), w.getName(), w.getType(),
            w.getBalance(), w.getCurrency(), w.getIcon(), w.getColor(),
            w.getIsActive(), w.getCreatedAt(), w.getUpdatedAt()
        );
    }
}
