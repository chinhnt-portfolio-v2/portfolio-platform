package dev.chinh.portfolio.apps.vault.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBookmarkFolderRequest(
    @NotBlank String name,
    String color,
    Integer sortOrder
) {}
