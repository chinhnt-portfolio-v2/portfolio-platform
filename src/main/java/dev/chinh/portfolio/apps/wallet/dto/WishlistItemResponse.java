package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record WishlistItemResponse(
    Long id,
    String name,
    BigDecimal estimatedPrice,
    String currency,
    String priority,
    String status,
    LocalDate targetDate,
    String notes,
    String url,
    Instant createdAt,
    Instant updatedAt
) {}
