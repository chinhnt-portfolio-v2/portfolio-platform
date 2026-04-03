package dev.chinh.portfolio.apps.vault.dto;

import dev.chinh.portfolio.apps.vault.BookmarkFolder;
import java.time.Instant;
import java.util.UUID;

public record BookmarkFolderResponse(
    Long id,
    UUID userId,
    String name,
    String color,
    Integer sortOrder,
    Instant createdAt,
    Instant updatedAt
) {
    public static BookmarkFolderResponse from(BookmarkFolder f) {
        return new BookmarkFolderResponse(
            f.getId(), f.getUserId(), f.getName(), f.getColor(),
            f.getSortOrder(), f.getCreatedAt(), f.getUpdatedAt()
        );
    }
}
