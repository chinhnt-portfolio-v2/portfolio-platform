package dev.chinh.portfolio.platform.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated analytics data returned to the admin dashboard.
 * Derived server-side — no raw event data exposed to the client.
 */
public class AnalyticsDashboardDto {

    private Instant generatedAt;
    private Instant periodStart;
    private Instant periodEnd;

    // Unique visitors
    private long uniqueVisitors;

    // Page view breakdown per route
    private List<RoutePageViews> pageViewsByRoute;

    // Traffic source breakdown
    private List<TrafficSourceCount> trafficSources;

    // Unique visitors by period
    private List<VisitorCountByPeriod> visitorsByPeriod;

    // Device breakdown
    private List<DeviceCount> deviceBreakdown;

    // ── Nested DTOs ──────────────────────────────────────────────

    public static class RoutePageViews {
        private String route;
        private long pageViews;
        private long uniqueViews;

        public RoutePageViews(String route, long pageViews, long uniqueViews) {
            this.route = route;
            this.pageViews = pageViews;
            this.uniqueViews = uniqueViews;
        }

        public String getRoute() { return route; }
        public long getPageViews() { return pageViews; }
        public long getUniqueViews() { return uniqueViews; }
    }

    public static class TrafficSourceCount {
        private String source;
        private long count;
        private BigDecimal percentage;

        public TrafficSourceCount(String source, long count, long total) {
            this.source = source;
            this.count = count;
            this.percentage = total > 0
                    ? BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                                 .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        public String getSource() { return source; }
        public long getCount() { return count; }
        public BigDecimal getPercentage() { return percentage; }
    }

    public static class VisitorCountByPeriod {
        private String period;   // e.g. "2026-03-01" or "Mon"
        private long count;

        public VisitorCountByPeriod(String period, long count) {
            this.period = period;
            this.count = count;
        }

        public String getPeriod() { return period; }
        public long getCount() { return count; }
    }

    public static class DeviceCount {
        private String deviceType;
        private long count;
        private BigDecimal percentage;

        public DeviceCount(String deviceType, long count, long total) {
            this.deviceType = deviceType;
            this.count = count;
            this.percentage = total > 0
                    ? BigDecimal.valueOf(count * 100.0 / total).setScale(1, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        public String getDeviceType() { return deviceType; }
        public long getCount() { return count; }
        public BigDecimal getPercentage() { return percentage; }
    }

    // ── Getters / Setters ────────────────────────────────────────

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public Instant getPeriodStart() { return periodStart; }
    public void setPeriodStart(Instant periodStart) { this.periodStart = periodStart; }

    public Instant getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(Instant periodEnd) { this.periodEnd = periodEnd; }

    public long getUniqueVisitors() { return uniqueVisitors; }
    public void setUniqueVisitors(long uniqueVisitors) { this.uniqueVisitors = uniqueVisitors; }

    public List<RoutePageViews> getPageViewsByRoute() { return pageViewsByRoute; }
    public void setPageViewsByRoute(List<RoutePageViews> pageViewsByRoute) { this.pageViewsByRoute = pageViewsByRoute; }

    public List<TrafficSourceCount> getTrafficSources() { return trafficSources; }
    public void setTrafficSources(List<TrafficSourceCount> trafficSources) { this.trafficSources = trafficSources; }

    public List<VisitorCountByPeriod> getVisitorsByPeriod() { return visitorsByPeriod; }
    public void setVisitorsByPeriod(List<VisitorCountByPeriod> visitorsByPeriod) { this.visitorsByPeriod = visitorsByPeriod; }

    public List<DeviceCount> getDeviceBreakdown() { return deviceBreakdown; }
    public void setDeviceBreakdown(List<DeviceCount> deviceBreakdown) { this.deviceBreakdown = deviceBreakdown; }
}
