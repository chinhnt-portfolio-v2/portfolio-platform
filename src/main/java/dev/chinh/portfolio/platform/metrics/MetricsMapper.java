package dev.chinh.portfolio.platform.metrics;

import org.springframework.stereotype.Component;

@Component
public class MetricsMapper {

    public ProjectHealthDto toDto(ProjectHealth entity) {
        return new ProjectHealthDto(
                entity.getProjectSlug(),
                entity.getStatus().name(),
                entity.getUptimePercent(),
                entity.getResponseTimeMs(),
                entity.getLastDeployAt(),
                entity.getLastPolledAt()
        );
    }
}
