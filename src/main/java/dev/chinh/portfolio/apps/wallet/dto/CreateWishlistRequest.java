package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateWishlistRequest(
    @NotBlank @Size(max = 255) String name,
    @DecimalMin(value = "0.0", inclusive = true) BigDecimal estimatedPrice,
    String currency,
    /** HIGH | MEDIUM | LOW */
    @Pattern(regexp = "HIGH|MEDIUM|LOW", message = "Priority must be HIGH, MEDIUM, or LOW")
    String priority,
    String targetDate,   // ISO date "YYYY-MM-DD" — parsed by service
    String notes,
    /** Only https:// or http:// — validated by service URL guard */
    String url
) {}
