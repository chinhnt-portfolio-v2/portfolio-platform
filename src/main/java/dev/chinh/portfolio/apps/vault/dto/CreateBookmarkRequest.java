package dev.chinh.portfolio.apps.vault.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateBookmarkRequest(
    @NotBlank String url,
    String title,
    String description,
    String favicon,
    Long folderId,
    List<String> tags
) {}
