package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateBudgetJarRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100)
    String name,

    @NotNull(message = "Percentage is required")
    @DecimalMin(value = "0.01", message = "Percentage must be > 0")
    @DecimalMax(value = "100", message = "Percentage must be <= 100")
    java.math.BigDecimal percentage,

    List<Long> categoryIds,

    @Size(max = 50)
    String icon,

    @Size(max = 7)
    String color
) {}
