package dev.chinh.portfolio.apps.vault.dto;

import dev.chinh.portfolio.apps.vault.Bookmark;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record BookmarkResponse(
    Long id,
    UUID userId,
    Long folderId,
    String url,
    String title,
    String description,
    String favicon,
    String thumbnail,
    List<String> tags,
    Boolean isArchived,
    Boolean isFavorite,
    Integer clickCount,
    Instant createdAt,
    Instant updatedAt
) {
    public static BookmarkResponse from(Bookmark b) {
        return new BookmarkResponse(
            b.getId(), b.getUserId(), b.getFolderId(), b.getUrl(),
            b.getTitle(), b.getDescription(), b.getFavicon(), b.getThumbnail(),
            b.getTags() != null ? Arrays.asList(b.getTags()) : List.of(),
            b.getIsArchived(), b.getIsFavorite(), b.getClickCount(),
            b.getCreatedAt(), b.getUpdatedAt()
        );
    }
}
