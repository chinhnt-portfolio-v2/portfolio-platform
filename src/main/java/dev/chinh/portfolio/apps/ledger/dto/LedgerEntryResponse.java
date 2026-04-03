package dev.chinh.portfolio.apps.ledger.dto;

import dev.chinh.portfolio.apps.ledger.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LedgerEntryResponse(
    Long id,
    UUID userId,
    Long walletId,
    String category,
    String categoryIcon,
    String categoryColor,
    String type,
    BigDecimal amount,
    String note,
    LocalDate entryDate,
    Instant createdAt,
    Instant updatedAt
) {
    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(
            e.getId(), e.getUserId(), e.getWalletId(),
            e.getCategory(), e.getCategoryIcon(), e.getCategoryColor(),
            e.getType(), e.getAmount(), e.getNote(),
            e.getEntryDate(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
