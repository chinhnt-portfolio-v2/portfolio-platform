package dev.chinh.portfolio.platform.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.portfolio.platform.metrics.MetricsAggregationService;
import dev.chinh.portfolio.shared.config.DemoAppRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for GitHub webhook integration.
 * Handles push events to trigger immediate health metrics refresh.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);
    private static final long DEBOUNCE_MS = 5000; // 5 seconds
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MetricsAggregationService metricsService;
    private final DemoAppRegistry demoAppRegistry;
    private final HmacVerificationService hmacService;
    private final String webhookSecret;

    // Debounce cache: projectSlug -> last trigger time
    private final ConcurrentHashMap<String, Instant> lastTriggerTime = new ConcurrentHashMap<>();

    public GitHubWebhookController(
            MetricsAggregationService metricsService,
            DemoAppRegistry demoAppRegistry,
            HmacVerificationService hmacService,
            @Value("${github.webhook.secret:}") String webhookSecret) {
        this.metricsService = metricsService;
        this.demoAppRegistry = demoAppRegistry;
        this.hmacService = hmacService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubPush(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        // Verify HMAC signature first (AC2)
        if (!hmacService.isValid(payload, signature, webhookSecret)) {
            log.warn("Invalid HMAC signature on GitHub webhook");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // Parse JSON to extract repository information
            JsonNode root = objectMapper.readTree(payload);
            String repoFullName = root.path("repository").path("full_name").asText("");

            if (repoFullName.isBlank()) {
                return ResponseEntity.ok().build();
            }

            // Extract project slug from "owner/repo" format (AC1)
            String projectSlug = extractProjectSlug(repoFullName);

            // Check if app is registered (AC3 - silent ignore if not found)
            boolean appFound = demoAppRegistry.getApps().stream()
                    .anyMatch(app -> app.getSlug().equals(projectSlug));

            if (!appFound) {
                log.debug("Webhook for unknown repository: {}", repoFullName);
                return ResponseEntity.ok().build();
            }

            // Debounce check (AC4)
            Instant lastTrigger = lastTriggerTime.get(projectSlug);
            if (lastTrigger != null) {
                long msSinceLastTrigger = ChronoUnit.MILLIS.between(lastTrigger, Instant.now());
                if (msSinceLastTrigger < DEBOUNCE_MS) {
                    log.debug("Debouncing webhook for {} ({}ms since last trigger)",
                            projectSlug, msSinceLastTrigger);
                    return ResponseEntity.ok().build();
                }
            }

            // Trigger metrics refresh (AC1)
            metricsService.triggerRefresh(projectSlug);
            lastTriggerTime.put(projectSlug, Instant.now());

            log.info("Triggered metrics refresh for {} via GitHub webhook", projectSlug);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing GitHub webhook: {}", e.getMessage());
            // Return 200 to GitHub to avoid retries for parse errors
            return ResponseEntity.ok().build();
        }
    }

    /**
     * Extracts project slug from repository full_name.
     * Example: "chinhdev/wallet-app" -> "wallet-app"
     */
    String extractProjectSlug(String repoFullName) {
        int slashIndex = repoFullName.indexOf('/');
        return slashIndex > 0 ? repoFullName.substring(slashIndex + 1) : repoFullName;
    }
}
