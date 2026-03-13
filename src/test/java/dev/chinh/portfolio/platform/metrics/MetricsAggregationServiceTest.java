package dev.chinh.portfolio.platform.metrics;

import dev.chinh.portfolio.platform.websocket.MetricsWebSocketHandler;
import dev.chinh.portfolio.shared.config.DemoApp;
import dev.chinh.portfolio.shared.config.DemoAppRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsAggregationServiceTest {

    @Mock
    private ProjectHealthRepository repository;
    @Mock
    private DemoAppRegistry registry;
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestHeadersUriSpec<?> requestSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;
    @Mock
    private MetricsMapper mapper;
    @Mock
    private MetricsWebSocketHandler webSocketHandler;

    @InjectMocks
    private MetricsAggregationService service;

    private DemoApp walletApp;

    @BeforeEach
    void setUp() {
        walletApp = new DemoApp();
        walletApp.setId("wallet-app");
        walletApp.setHealthEndpoint("https://wallet.chinh.dev/health");
        // Note: registry.getApps() is NOT stubbed here — only tests that call pollAll()
        // or triggerRefresh() need this stub; tests calling pollApp() directly do not.
    }

    /** Configures the full RestClient chain to return a success response */
    private void stubRestClientSuccess(String url) {
        doReturn(requestSpec).when(restClient).get();
        doReturn(requestSpec).when(requestSpec).uri(url);
        doReturn(responseSpec).when(requestSpec).retrieve();
        doReturn(ResponseEntity.ok("{ \"status\": \"UP\" }")).when(responseSpec).toEntity(String.class);
    }

    /** Configures the RestClient chain matching any URI */
    private void stubRestClientSuccessAnyUri() {
        doReturn(requestSpec).when(restClient).get();
        doReturn(requestSpec).when(requestSpec).uri(anyString());
        doReturn(responseSpec).when(requestSpec).retrieve();
        doReturn(ResponseEntity.ok("{ \"status\": \"UP\" }")).when(responseSpec).toEntity(String.class);
    }

    /** Configures the RestClient chain to fail on any URI */
    private void stubRestClientFailureAnyUri(RuntimeException ex) {
        doReturn(requestSpec).when(restClient).get();
        doReturn(requestSpec).when(requestSpec).uri(anyString());
        doReturn(responseSpec).when(requestSpec).retrieve();
        doThrow(ex).when(responseSpec).toEntity(String.class);
    }

    @Test
    void pollApp_successfulResponse_upsertsStatusUp() {
        // Stub repository.save() to return the saved entity
        ProjectHealth savedEntity = new ProjectHealth();
        savedEntity.setProjectSlug("wallet-app");
        savedEntity.setStatus(HealthStatus.UP);
        when(repository.save(any(ProjectHealth.class))).thenReturn(savedEntity);

        // Stub mapper to return a valid DTO
        when(mapper.toDto(any(ProjectHealth.class))).thenReturn(
                new ProjectHealthDto("wallet-app", "UP", new BigDecimal("100.00"), 150, null, Instant.now())
        );
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.empty());
        stubRestClientSuccess("https://wallet.chinh.dev/health");

        service.pollApp(walletApp);

        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        ProjectHealth saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(HealthStatus.UP);
        assertThat(saved.getConsecutiveFailures()).isEqualTo(0);
        assertThat(saved.getUptimePercent()).isEqualByComparingTo("100.00");
        assertThat(saved.getResponseTimeMs()).isNotNull();
        assertThat(saved.getLastOnlineAt()).isNotNull();
        assertThat(saved.getLastPolledAt()).isNotNull();

        // Verify WebSocket broadcast was called
        verify(mapper).toDto(any(ProjectHealth.class));
        verify(webSocketHandler).broadcast(any(ProjectHealthDto.class));
    }

    @Test
    void pollApp_connectionError_logsWarnAndSetsStatusDown() {
        ProjectHealth existing = new ProjectHealth();
        existing.setProjectSlug("wallet-app");
        existing.setUptimePercent(new BigDecimal("95.50"));
        existing.setConsecutiveFailures(0);
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.of(existing));
        // Must stub repository.save() to return the entity (otherwise toDto receives null)
        when(repository.save(any(ProjectHealth.class))).thenReturn(existing);
        // Use lenient stubbing because we don't care about exact argument matching
        lenient().when(mapper.toDto(any(ProjectHealth.class))).thenReturn(
                new ProjectHealthDto("wallet-app", "DOWN", new BigDecimal("95.50"), 150, null, Instant.now())
        );
        stubRestClientFailureAnyUri(new RuntimeException("Connection refused"));

        service.pollApp(walletApp);

        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        ProjectHealth saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(HealthStatus.DOWN);
        assertThat(saved.getConsecutiveFailures()).isEqualTo(1);
        assertThat(saved.getUptimePercent()).isEqualByComparingTo("95.50"); // preserved
        assertThat(saved.getLastPolledAt()).isNotNull();

        // Verify WebSocket broadcast is called even on failure (AC2)
        verify(mapper).toDto(any(ProjectHealth.class));
        verify(webSocketHandler).broadcast(any(ProjectHealthDto.class));
    }

    @Test
    void pollApp_connectionError_doesNotUpdateLastOnlineAt() {
        Instant previousOnlineAt = Instant.parse("2026-01-01T00:00:00Z");
        ProjectHealth existing = new ProjectHealth();
        existing.setProjectSlug("wallet-app");
        existing.setLastOnlineAt(previousOnlineAt);
        existing.setConsecutiveFailures(0);
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.of(existing));
        stubRestClientFailureAnyUri(new RuntimeException("timeout"));

        service.pollApp(walletApp);

        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLastOnlineAt()).isEqualTo(previousOnlineAt); // preserved
    }

    @Test
    void pollAll_firstAppFails_secondAppStillPolled() {
        DemoApp app1 = new DemoApp();
        app1.setId("app-1");
        app1.setHealthEndpoint("https://app1.chinh.dev/health");
        DemoApp app2 = new DemoApp();
        app2.setId("app-2");
        app2.setHealthEndpoint("https://app2.chinh.dev/health");
        when(registry.getApps()).thenReturn(List.of(app1, app2));
        when(repository.findByProjectSlug(anyString())).thenReturn(Optional.empty());

        doReturn(requestSpec).when(restClient).get();
        doReturn(requestSpec).when(requestSpec).uri(anyString());
        doReturn(responseSpec).when(requestSpec).retrieve();
        doThrow(new RuntimeException("app1 down"))
                .doReturn(ResponseEntity.ok("{ \"status\": \"UP\" }"))
                .when(responseSpec).toEntity(String.class);

        service.pollAll();

        verify(repository, times(2)).save(any(ProjectHealth.class));
    }

    @Test
    void pollApp_idempotency_usesExistingEntityNotInsertNew() {
        ProjectHealth existing = new ProjectHealth();
        existing.setProjectSlug("wallet-app");
        existing.setStatus(HealthStatus.UP);
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.of(existing));
        stubRestClientSuccessAnyUri();

        service.pollApp(walletApp);

        verify(repository, times(1)).findByProjectSlug("wallet-app");
        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    void triggerRefresh_knownApp_callsPollApp() {
        when(registry.getApps()).thenReturn(List.of(walletApp));
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.empty());
        stubRestClientSuccessAnyUri();

        service.triggerRefresh("wallet-app");

        verify(repository).findByProjectSlug("wallet-app");
        verify(repository).save(any(ProjectHealth.class));
    }

    @Test
    void triggerRefresh_unknownApp_doesNotCallRepository() {
        when(registry.getApps()).thenReturn(List.of(walletApp));
        service.triggerRefresh("unknown-app");

        verify(repository, times(0)).findByProjectSlug(anyString());
        verify(repository, times(0)).save(any());
    }

    @Test
    void pollApp_consecutiveFailures_incrementsFromPreviousValue() {
        ProjectHealth existing = new ProjectHealth();
        existing.setProjectSlug("wallet-app");
        existing.setConsecutiveFailures(3);
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.of(existing));
        stubRestClientFailureAnyUri(new RuntimeException("still down"));

        service.pollApp(walletApp);

        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConsecutiveFailures()).isEqualTo(4);
    }

    @Test
    void pollApp_failure_doesNotUpdateResponseTimeMs() {
        // Arrange: existing record with responseTimeMs
        ProjectHealth existing = new ProjectHealth();
        existing.setProjectSlug("wallet-app");
        existing.setResponseTimeMs(150);  // Previous measurement
        existing.setConsecutiveFailures(0);
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.of(existing));
        stubRestClientFailureAnyUri(new RuntimeException("Connection refused"));

        // Act
        service.pollApp(walletApp);

        // Assert: responseTimeMs should be preserved (not updated to null or new value)
        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getResponseTimeMs()).isEqualTo(150); // preserved
    }

    @Test
    void pollAll_noExceptionPropagates_whenPollAppFails() {
        when(registry.getApps()).thenReturn(List.of(walletApp));
        when(repository.findByProjectSlug(anyString())).thenReturn(Optional.empty());
        stubRestClientFailureAnyUri(new RuntimeException("unexpected"));

        // Must not throw
        service.pollAll();
    }

    // ======= Story 3.7: Config-Only Demo App Registration =======

    /**
     * AC#2: When an app is removed from config, polling stops for that app.
     * This test verifies that when registry returns empty list, no polling occurs.
     * Note: getApps() is called twice - once for .size() in debug log, once for for-each loop
     */
    @Test
    void pollAll_emptyRegistry_noPollingOccurs() {
        when(registry.getApps()).thenReturn(List.of());

        // Should not throw and should not call repository or restClient
        service.pollAll();

        verify(registry, times(2)).getApps();
        verify(repository, times(0)).findByProjectSlug(anyString());
        verify(repository, times(0)).save(any());
    }
}
