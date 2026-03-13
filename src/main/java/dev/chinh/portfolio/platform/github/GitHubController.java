package dev.chinh.portfolio.platform.github;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for GitHub contribution data.
 * Proxies the GitHub Contributions API with caching.
 */
@RestController
@RequestMapping("/api/v1/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    /**
     * Get GitHub contribution data for a user.
     * Results are cached for 1 hour using Caffeine.
     * If GitHub API is unavailable and cache is empty, returns null (frontend hides section).
     *
     * @param username GitHub username
     * @return contribution data or null if unavailable
     */
    @GetMapping("/contributions")
    public ResponseEntity<GitHubContributions> getContributions(@RequestParam String username) {
        GitHubContributions contributions = gitHubService.getContributions(username);

        // AC4: If GitHub API unavailable AND cache empty -> hide section (return null)
        if (contributions == null) {
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok(contributions);
    }
}
