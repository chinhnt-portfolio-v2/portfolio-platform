package dev.chinh.portfolio.apps.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 50) String icon,
    @Size(max = 7) String color,
    @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type
) {}
