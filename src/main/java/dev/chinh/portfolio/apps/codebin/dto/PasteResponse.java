package dev.chinh.portfolio.apps.codebin.dto;

import dev.chinh.portfolio.apps.codebin.Paste;
import java.time.Instant;
import java.util.UUID;

public record PasteResponse(
    Long id,
    UUID userId,
    String title,
    String language,
    String content,
    Boolean isPublic,
    Boolean hasPassword,
    Integer viewCount,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static PasteResponse from(Paste p, boolean includeContent) {
        return new PasteResponse(
            p.getId(), p.getUserId(), p.getTitle(), p.getLanguage(),
            includeContent ? p.getContent() : null,
            p.getIsPublic(), p.getPasswordHash() != null,
            p.getViewCount(), p.getExpiresAt(),
            p.getCreatedAt(), p.getUpdatedAt()
        );
    }

    public static PasteResponse fromPublic(Paste p) {
        return new PasteResponse(
            p.getId(), null, p.getTitle(), p.getLanguage(),
            p.getContent(), p.getIsPublic(), p.getPasswordHash() != null,
            p.getViewCount(), p.getExpiresAt(),
            p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
