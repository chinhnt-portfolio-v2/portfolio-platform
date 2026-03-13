package dev.chinh.portfolio.platform.github;

/**
 * Response from GitHub contribution API.
 * GitHub's contribution graph API returns an HTML page, so we proxy and return
 * JSON data that the frontend can render.
 */
public record GitHubContributions(
        String username,
        int totalContributions,
        ContributionWeek[] weeks
) {
    public record ContributionWeek(
            String[] contributionDays
    ) {}
}
