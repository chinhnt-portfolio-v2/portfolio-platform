package dev.chinh.portfolio.platform.webhook.github;

import java.util.List;

/**
 * DTO for GitHub push event webhook payload.
 * See: https://docs.github.com/en/webhooks/webhook-events-and-payloads#push
 */
public record GitHubPushEvent(
    String ref,
    String before,
    String after,
    List<GitHubCommit> commits,
    Repository repository
) {}
