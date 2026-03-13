package dev.chinh.portfolio.platform.webhook.github;

/**
 * DTO for repository information in GitHub push event.
 */
public record Repository(
    long id,
    String name,
    String full_name,
    String html_url
) {}
