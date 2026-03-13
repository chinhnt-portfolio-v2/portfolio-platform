package dev.chinh.portfolio.platform.metrics;

import dev.chinh.portfolio.platform.websocket.MetricsWebSocketHandler;
import dev.chinh.portfolio.shared.config.DemoApp;
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
    private final DemoAppRegistry registry;
    private final RestClient restClient;
    private final MetricsMapper mapper;
    private final MetricsWebSocketHandler webSocketHandler;

    public MetricsAggregationService(ProjectHealthRepository repository,
                                     DemoAppRegistry registry,
                                     RestClient restClient,
                                     MetricsMapper mapper,
                                     MetricsWebSocketHandler webSocketHandler) {
        this.repository = repository;
        this.registry = registry;
        this.restClient = restClient;
        this.mapper = mapper;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedDelay = 60000)
    public void pollAll() {
        log.debug("Starting health metrics poll for {} apps", registry.getApps().size());
        for (DemoApp app : registry.getApps()) {
            try {
                pollApp(app);
            } catch (Exception e) {
                // Should not reach here — pollApp handles its own exceptions
                log.warn("Unexpected error polling app {}: {}", app.getId(), e.getMessage());
            }
        }
    }

    public void pollApp(DemoApp app) {
        ProjectHealth record = repository.findByProjectSlug(app.getId())
                .orElseGet(() -> {
                    ProjectHealth newRecord = new ProjectHealth();
                    newRecord.setProjectSlug(app.getId());
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
            // Broadcast to all connected WebSocket clients
            ProjectHealthDto dto = mapper.toDto(saved);
            webSocketHandler.broadcast(dto);
            log.debug("Poll success for {}: {}ms", app.getId(), responseTimeMs);

        } catch (Exception e) {
            record.setStatus(HealthStatus.DOWN);
            record.setLastPolledAt(Instant.now());
            record.setConsecutiveFailures(record.getConsecutiveFailures() + 1);
            ProjectHealth saved = repository.save(record);
            // Broadcast to all connected WebSocket clients
            ProjectHealthDto dto = mapper.toDto(saved);
            webSocketHandler.broadcast(dto);
            log.warn("Health poll failed for app {}: {}", app.getId(), e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Stack trace", e);
            }
        }
    }

    /** Called by Story 3.2 webhook handler to trigger immediate re-poll */
    public void triggerRefresh(String projectSlug) {
        registry.getApps().stream()
                .filter(app -> app.getId().equals(projectSlug))
                .findFirst()
                .ifPresentOrElse(
                        this::pollApp,
                        () -> log.warn("triggerRefresh called for unknown project: {}", projectSlug)
                );
    }
}
