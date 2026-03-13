package dev.chinh.portfolio.platform.webhook.github;

import java.util.List;

/**
 * DTO for individual commit in GitHub push event.
 */
public record GitHubCommit(
    String id,
    String message,
    String url,
    List<String> added,
    List<String> modified,
    List<String> removed
) {}
