package dev.chinh.portfolio.platform.metrics;

import dev.chinh.portfolio.platform.websocket.MetricsWebSocketHandler;
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
    private DemoAppRegistry demoAppRegistry;
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

    private DemoAppRegistry.DemoApp walletApp;

    @BeforeEach
    void setUp() {
        walletApp = new DemoAppRegistry.DemoApp();
        walletApp.setSlug("wallet-app");
        walletApp.setName("Wallet App");
        walletApp.setHealthEndpoint("https://wallet.chinh.dev/health");
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
        ProjectHealth savedEntity = new ProjectHealth();
        savedEntity.setProjectSlug("wallet-app");
        savedEntity.setStatus(HealthStatus.UP);
        when(repository.save(any(ProjectHealth.class))).thenReturn(savedEntity);
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
        when(repository.save(any(ProjectHealth.class))).thenReturn(existing);
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
        DemoAppRegistry.DemoApp app1 = new DemoAppRegistry.DemoApp();
        app1.setSlug("app-1");
        app1.setName("App One");
        app1.setHealthEndpoint("https://app1.chinh.dev/health");
        DemoAppRegistry.DemoApp app2 = new DemoAppRegistry.DemoApp();
        app2.setSlug("app-2");
        app2.setName("App Two");
        app2.setHealthEndpoint("https://app2.chinh.dev/health");
        when(demoAppRegistry.getApps()).thenReturn(List.of(app1, app2));
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
        when(demoAppRegistry.getApps()).thenReturn(List.of(walletApp));
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.empty());
        stubRestClientSuccessAnyUri();

        service.triggerRefresh("wallet-app");

        verify(repository).findByProjectSlug("wallet-app");
        verify(repository).save(any(ProjectHealth.class));
    }

    @Test
    void triggerRefresh_unknownApp_doesNotCallRepository() {
        when(demoAppRegistry.getApps()).thenReturn(List.of(walletApp));
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
        ProjectHealth existing = new ProjectHealth();
        existing.setProjectSlug("wallet-app");
        existing.setResponseTimeMs(150);
        existing.setConsecutiveFailures(0);
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.of(existing));
        stubRestClientFailureAnyUri(new RuntimeException("Connection refused"));

        service.pollApp(walletApp);

        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getResponseTimeMs()).isEqualTo(150); // preserved
    }

    @Test
    void pollAll_noExceptionPropagates_whenPollAppFails() {
        when(demoAppRegistry.getApps()).thenReturn(List.of(walletApp));
        when(repository.findByProjectSlug(anyString())).thenReturn(Optional.empty());
        stubRestClientFailureAnyUri(new RuntimeException("unexpected"));

        // Must not throw
        service.pollAll();
    }

    // ======= Story 3.7 / Story 6.4.2: Config-driven polling =======

    /**
     * AC#2 (Story 3.7): When an app is removed from config, polling stops.
     * AC#4 (Story 6.4.2): Removing entry from showcase.yml → restart → polling stops.
     *
     * Verifies that when the registry returns an empty list, no polling occurs.
     * Note: getApps() is called twice — once for .size() in debug log, once in for-each.
     */
    @Test
    void pollAll_emptyRegistry_noPollingOccurs() {
        when(demoAppRegistry.getApps()).thenReturn(List.of());

        service.pollAll();

        verify(demoAppRegistry, times(2)).getApps();
        verify(repository, times(0)).findByProjectSlug(anyString());
        verify(repository, times(0)).save(any());
    }

    // ======= Story 6.4.2: Showcase.yml config-driven polling =======

    /**
     * AC#1 (Story 6.4.2): showcase.yml drives demo app polling automatically.
     * Adding a new entry to showcase.yml and restarting BE starts polling.
     *
     * Verifies that 2-app config polls both apps.
     */
    @Test
    void pollAll_twoAppConfig_pollsBothApps() {
        DemoAppRegistry.DemoApp app1 = new DemoAppRegistry.DemoApp();
        app1.setSlug("wallet-app");
        app1.setName("Wallet App");
        app1.setHealthEndpoint("https://wallet.chinh.dev/health");
        DemoAppRegistry.DemoApp app2 = new DemoAppRegistry.DemoApp();
        app2.setSlug("portfolio-v2");
        app2.setName("Portfolio v2");
        app2.setHealthEndpoint("https://portfolio.chinh.dev/health");

        when(demoAppRegistry.getApps()).thenReturn(List.of(app1, app2));
        when(repository.findByProjectSlug(anyString())).thenReturn(Optional.empty());

        doReturn(requestSpec).when(restClient).get();
        doReturn(requestSpec).when(requestSpec).uri(anyString());
        doReturn(responseSpec).when(requestSpec).retrieve();
        doReturn(ResponseEntity.ok("{ \"status\": \"UP\" }")).when(responseSpec).toEntity(String.class);
        when(repository.save(any(ProjectHealth.class))).thenAnswer(inv -> inv.getArgument(0));

        service.pollAll();

        // Both apps should have been polled
        verify(repository, times(2)).save(any(ProjectHealth.class));
    }

    /**
     * AC#3 (Story 6.4.2): Graceful degradation — unreachable endpoint → DOWN,
     * no exception propagated, polling continues for other apps.
     */
    @Test
    void pollApp_unreachableEndpoint_recordsDownAndContinues() {
        ProjectHealth existing = new ProjectHealth();
        existing.setProjectSlug("wallet-app");
        existing.setConsecutiveFailures(0);
        when(repository.findByProjectSlug("wallet-app")).thenReturn(Optional.of(existing));
        when(repository.save(any(ProjectHealth.class))).thenReturn(existing);
        lenient().when(mapper.toDto(any(ProjectHealth.class))).thenReturn(
                new ProjectHealthDto("wallet-app", "DOWN", null, null, null, Instant.now())
        );
        stubRestClientFailureAnyUri(new RuntimeException("Connection refused"));

        // Must NOT throw
        service.pollApp(walletApp);

        ArgumentCaptor<ProjectHealth> captor = ArgumentCaptor.forClass(ProjectHealth.class);
        verify(repository).save(captor.capture());
        ProjectHealth saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(HealthStatus.DOWN);
        assertThat(saved.getConsecutiveFailures()).isEqualTo(1);
    }
}
