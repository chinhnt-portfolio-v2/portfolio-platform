package dev.chinh.portfolio.platform.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to fetch GitHub contribution data from GitHub's API.
 * Results are cached for 1 hour via Caffeine.
 */
@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);
    private static final String GITHUB_CONTRIBUTIONS_URL = "https://github-contributions-api.jogruber.de/v4/%s";

    private final HttpClient httpClient;

    public GitHubService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Cacheable(value = "github-contributions", key = "#username")
    public GitHubContributions getContributions(String username) {
        log.info("Fetching GitHub contributions for user: {}", username);

        try {
            String url = String.format(GITHUB_CONTRIBUTIONS_URL, username);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseContributions(username, response.body());
            } else {
                log.warn("GitHub API returned status {} for user {}", response.statusCode(), username);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to fetch GitHub contributions for user {}: {}", username, e.getMessage());
            return null;
        }
    }

    private GitHubContributions parseContributions(String username, String json) {
        try {
            // Parse the JSON response from the contributions API
            // The API returns: { contributions: [...], total: N }
            int total = extractTotal(json);
            return new GitHubContributions(username, total, new GitHubContributions.ContributionWeek[0]);
        } catch (Exception e) {
            log.error("Failed to parse GitHub contributions JSON: {}", e.getMessage());
            return null;
        }
    }

    private int extractTotal(String json) {
        Pattern pattern = Pattern.compile("\"total\":\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }
}
