package dev.chinh.portfolio.apps.codebin.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record CreatePasteRequest(
    String title,
    @NotBlank String content,
    String language,
    Boolean isPublic,
    String password,
    Instant expiresAt
) {}
