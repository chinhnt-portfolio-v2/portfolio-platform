package dev.chinh.portfolio.apps.vault.dto;

import java.util.List;

public record VaultDashboardResponse(
    long totalBookmarks,
    long totalFolders,
    long totalFavorites,
    List<String> allTags,
    List<BookmarkResponse> recentBookmarks
) {}
