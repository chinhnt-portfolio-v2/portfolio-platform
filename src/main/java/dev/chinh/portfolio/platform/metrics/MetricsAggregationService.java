package dev.chinh.portfolio.platform.metrics;

import dev.chinh.portfolio.platform.websocket.MetricsWebSocketHandler;
import dev.chinh.portfolio.shared.config.DemoAppRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class MetricsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregationService.class);

    private final ProjectHealthRepository repository;
    private final DemoAppRegistry demoAppRegistry;
    private final RestClient restClient;
    private final MetricsMapper mapper;
    private final MetricsWebSocketHandler webSocketHandler;

    public MetricsAggregationService(ProjectHealthRepository repository,
                                     DemoAppRegistry demoAppRegistry,
                                     RestClient restClient,
                                     MetricsMapper mapper,
                                     MetricsWebSocketHandler webSocketHandler) {
        this.repository = repository;
        this.demoAppRegistry = demoAppRegistry;
        this.restClient = restClient;
        this.mapper = mapper;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedDelay = 60_000)
    public void pollAll() {
        log.debug("Starting health metrics poll for {} apps", demoAppRegistry.getApps().size());
        for (DemoAppRegistry.DemoApp app : demoAppRegistry.getApps()) {
            try {
                pollApp(app);
            } catch (Exception e) {
                // Should not reach here — pollApp handles its own exceptions
                log.warn("Unexpected error polling app '{}': {}", app.getSlug(), e.getMessage());
            }
        }
    }

    public void pollApp(DemoAppRegistry.DemoApp app) {
        ProjectHealth record = repository.findByProjectSlug(app.getSlug())
                .orElseGet(() -> {
                    ProjectHealth newRecord = new ProjectHealth();
                    newRecord.setProjectSlug(app.getSlug());
                    return newRecord;
                });

        long startMs = System.currentTimeMillis();
        try {
            restClient.get()
                    .uri(app.getHealthEndpoint())
                    .retrieve()
                    .toEntity(String.class);
            int responseTimeMs = (int) (System.currentTimeMillis() - startMs);

            record.setStatus(HealthStatus.UP);
            record.setResponseTimeMs(responseTimeMs);
            record.setUptimePercent(new BigDecimal("100.00"));
            record.setLastPolledAt(Instant.now());
            record.setLastOnlineAt(Instant.now());
            record.setConsecutiveFailures(0);
            ProjectHealth saved = repository.save(record);
            ProjectHealthDto dto = mapper.toDto(saved);
            webSocketHandler.broadcast(dto);
            log.debug("Poll success for '{}' ({}): {}ms", app.getSlug(), app.getName(), responseTimeMs);

        } catch (Exception e) {
            record.setStatus(HealthStatus.DOWN);
            record.setLastPolledAt(Instant.now());
            record.setConsecutiveFailures(record.getConsecutiveFailures() + 1);
            ProjectHealth saved = repository.save(record);
            ProjectHealthDto dto = mapper.toDto(saved);
            webSocketHandler.broadcast(dto);
            log.warn("Health poll failed for app '{}' ({}): {}", app.getSlug(), app.getName(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Stack trace", e);
            }
        }
    }

    /**
     * Trigger an immediate re-poll for the given project slug.
     * Called by the GitHub webhook handler (Story 3.2) after a deploy event.
     *
     * @param projectSlug the project slug to refresh
     */
    public void triggerRefresh(String projectSlug) {
        demoAppRegistry.getApps().stream()
                .filter(app -> app.getSlug().equals(projectSlug))
                .findFirst()
                .ifPresentOrElse(
                        this::pollApp,
                        () -> log.warn("triggerRefresh called for unknown project: {}", projectSlug)
                );
    }
}
