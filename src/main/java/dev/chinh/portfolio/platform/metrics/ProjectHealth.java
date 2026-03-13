package dev.chinh.portfolio.platform.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_health")
public class ProjectHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_slug", nullable = false, unique = true, length = 100)
    private String projectSlug;  // matches projects.ts config slug

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HealthStatus status = HealthStatus.UNKNOWN;

    @Column(name = "uptime_percent", precision = 5, scale = 2)
    private BigDecimal uptimePercent;  // nullable; e.g. 99.87

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;  // nullable; last p95 ms

    @Column(name = "last_deploy_at")
    private Instant lastDeployAt;  // nullable; from GitHub webhook

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;  // nullable; last poll attempt timestamp

    @Column(name = "last_online_at")
    private Instant lastOnlineAt;  // nullable; last time status was UP

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProjectHealth() {}

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }

    public String getProjectSlug() { return projectSlug; }
    public void setProjectSlug(String projectSlug) { this.projectSlug = projectSlug; }

    public HealthStatus getStatus() { return status; }
    public void setStatus(HealthStatus status) { this.status = status; }

    public BigDecimal getUptimePercent() { return uptimePercent; }
    public void setUptimePercent(BigDecimal uptimePercent) { this.uptimePercent = uptimePercent; }

    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public Instant getLastDeployAt() { return lastDeployAt; }
    public void setLastDeployAt(Instant lastDeployAt) { this.lastDeployAt = lastDeployAt; }

    public Instant getLastPolledAt() { return lastPolledAt; }
    public void setLastPolledAt(Instant lastPolledAt) { this.lastPolledAt = lastPolledAt; }

    public Instant getLastOnlineAt() { return lastOnlineAt; }
    public void setLastOnlineAt(Instant lastOnlineAt) { this.lastOnlineAt = lastOnlineAt; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
