package dev.chinh.portfolio.platform.webhook;

import dev.chinh.portfolio.platform.metrics.MetricsAggregationService;
import dev.chinh.portfolio.shared.config.DemoApp;
import dev.chinh.portfolio.shared.config.DemoAppRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubWebhookControllerTest {

    @Mock
    private MetricsAggregationService metricsService;

    @Mock
    private DemoAppRegistry demoAppRegistry;

    @Mock
    private HmacVerificationService hmacService;

    private GitHubWebhookController controller;

    @BeforeEach
    void setUp() {
        // Manually create controller to properly inject webhookSecret
        controller = new GitHubWebhookController(metricsService, demoAppRegistry, hmacService, "test-secret");
    }

    @Test
    void handleGitHubPush_validSignature_callsTriggerRefresh() {
        // Given
        String payload = "{\"repository\":{\"full_name\":\"chinhdev/wallet-app\"}}";
        String signature = "sha256=abc123";

        when(hmacService.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(demoAppRegistry.getApps()).thenReturn(List.of(app("wallet-app")));

        // When
        ResponseEntity<Void> response = controller.handleGitHubPush(payload, signature);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(metricsService).triggerRefresh("wallet-app");
    }

    @Test
    void handleGitHubPush_invalidSignature_returns401() {
        // Given
        String payload = "{}";
        String signature = "sha256=invalid";

        when(hmacService.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        // When
        ResponseEntity<Void> response = controller.handleGitHubPush(payload, signature);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(metricsService, never()).triggerRefresh(anyString());
    }

    @Test
    void handleGitHubPush_unknownRepository_returns200Silently() {
        // Given
        String payload = "{\"repository\":{\"full_name\":\"unknown/repo\"}}";
        String signature = "sha256=abc123";

        when(hmacService.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(demoAppRegistry.getApps()).thenReturn(List.of(app("wallet-app")));

        // When
        ResponseEntity<Void> response = controller.handleGitHubPush(payload, signature);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(metricsService, never()).triggerRefresh(anyString());
    }

    @Test
    void handleGitHubPush_debounceWithin5Seconds_skipsSecondCall() {
        // Given
        String payload = "{\"repository\":{\"full_name\":\"chinhdev/wallet-app\"}}";
        String signature = "sha256=abc123";

        when(hmacService.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(demoAppRegistry.getApps()).thenReturn(List.of(app("wallet-app")));

        // First call
        controller.handleGitHubPush(payload, signature);
        // Second call within 5 seconds (simulated via the debounce logic)

        // When - second call
        ResponseEntity<Void> response = controller.handleGitHubPush(payload, signature);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // First call triggered, second call should be debounced
        verify(metricsService, times(1)).triggerRefresh("wallet-app");
    }

    @Test
    void extractProjectSlug_validFullName_extractsCorrectly() {
        // When
        String slug = controller.extractProjectSlug("chinhdev/wallet-app");

        // Then
        assertThat(slug).isEqualTo("wallet-app");
    }

    @Test
    void extractProjectSlug_noSlash_returnsOriginal() {
        // When
        String slug = controller.extractProjectSlug("wallet-app");

        // Then
        assertThat(slug).isEqualTo("wallet-app");
    }

    @Test
    void handleGitHubPush_debounceAfter5Seconds_triggersRefresh() throws Exception {
        // Given
        String payload = "{\"repository\":{\"full_name\":\"chinhdev/wallet-app\"}}";
        String signature = "sha256=abc123";

        when(hmacService.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(demoAppRegistry.getApps()).thenReturn(List.of(app("wallet-app")));

        // Set last trigger time to 6 seconds ago (beyond debounce window)
        java.lang.reflect.Field field = GitHubWebhookController.class.getDeclaredField("lastTriggerTime");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, java.time.Instant> debounceMap =
            (ConcurrentHashMap<String, java.time.Instant>) field.get(controller);
        debounceMap.put("wallet-app", java.time.Instant.now().minusSeconds(6));

        // When - should trigger refresh since 6 seconds have passed
        ResponseEntity<Void> response = controller.handleGitHubPush(payload, signature);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(metricsService, times(1)).triggerRefresh("wallet-app");
    }

    @Test
    void handleGitHubPush_differentProjects_doNotInterfere() {
        // Given - two different projects
        String payload1 = "{\"repository\":{\"full_name\":\"chinhdev/wallet-app\"}}";
        String payload2 = "{\"repository\":{\"full_name\":\"chinhdev/portfolio-fe\"}}";
        String signature = "sha256=abc123";

        when(hmacService.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(demoAppRegistry.getApps()).thenReturn(List.of(app("wallet-app"), app("portfolio-fe")));

        // First call for wallet-app
        controller.handleGitHubPush(payload1, signature);

        // When - call for different project immediately
        ResponseEntity<Void> response = controller.handleGitHubPush(payload2, signature);

        // Then - both should trigger (different projects don't share debounce)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(metricsService).triggerRefresh("wallet-app");
        verify(metricsService).triggerRefresh("portfolio-fe");
    }

    private DemoApp app(String id) {
        DemoApp demoApp = new DemoApp();
        demoApp.setId(id);
        demoApp.setName(id);
        demoApp.setHealthEndpoint("http://localhost:8080/health");
        demoApp.setPollIntervalSeconds(60);
        return demoApp;
    }
}
