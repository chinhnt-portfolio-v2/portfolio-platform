package dev.chinh.portfolio.platform.metrics;

import java.math.BigDecimal;
import java.time.Instant;

public record ProjectHealthDto(
        String projectSlug,
        String status,
        BigDecimal uptimePercent,
        Integer responseTimeMs,
        Instant lastDeployAt,
        Instant lastPolledAt
) {}
