package dev.chinh.portfolio.platform.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsEventRepository repository;

    public AnalyticsService(AnalyticsEventRepository repository) {
        this.repository = repository;
    }

    // ── Event Recording (fire-and-forget) ─────────────────────────

    /**
     * Record a page-view event.
     * @param route     The requested path (e.g. "/" or "/projects/wallet-app")
     * @param visitorId SHA-256 hash of the browser fingerprint — NO raw PII stored
     * @param sessionId Opaque session token
     * @param deviceType Device category derived from User-Agent header
     */
    @Transactional
    public void recordPageView(String route, String visitorId, String sessionId, String deviceType) {
        try {
            AnalyticsEvent event = AnalyticsEvent.pageView(
                    normaliseRoute(route), visitorId, sessionId);
            if (deviceType != null) event.setDeviceType(deviceType);
            repository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record analytics page view: {}", e.getMessage());
        }
    }

    /**
     * Record a traffic-source attribution event.
     */
    @Transactional
    public void recordTrafficSource(String route, String source, String referrerDomain) {
        try {
            AnalyticsEvent event = AnalyticsEvent.trafficSource(
                    normaliseRoute(route), source, referrerDomain);
            repository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record analytics traffic source: {}", e.getMessage());
        }
    }

    // ── Dashboard Aggregation ────────────────────────────────────

    /**
     * Build a full analytics dashboard for the admin panel.
     * Period defaults to last 30 days if not specified.
     */
    @Transactional(readOnly = true)
    public AnalyticsDashboardDto getDashboard(Instant periodStart, Instant periodEnd) {
        Instant start = periodStart != null ? periodStart : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end   = periodEnd   != null ? periodEnd   : Instant.now();

        AnalyticsDashboardDto dto = new AnalyticsDashboardDto();
        dto.setGeneratedAt(Instant.now());
        dto.setPeriodStart(start);
        dto.setPeriodEnd(end);

        dto.setUniqueVisitors(repository.countUniqueVisitors(start));
        dto.setPageViewsByRoute(buildPageViewsByRoute(start));
        dto.setTrafficSources(buildTrafficSources(start));
        dto.setVisitorsByPeriod(buildVisitorsByDay(start));
        dto.setDeviceBreakdown(buildDeviceBreakdown(start));

        return dto;
    }

    // ── Private Helpers ──────────────────────────────────────────

    private List<AnalyticsDashboardDto.RoutePageViews> buildPageViewsByRoute(Instant since) {
        List<Object[]> rows = repository.aggregatePageViews(since);
        List<AnalyticsDashboardDto.RoutePageViews> result = new ArrayList<>();
        for (Object[] row : rows) {
            String route    = (String) row[0];
            long total      = (Long) row[1];
            long unique     = (Long) row[2];
            result.add(new AnalyticsDashboardDto.RoutePageViews(route, total, unique));
        }
        result.sort((a, b) -> Long.compare(b.getPageViews(), a.getPageViews()));
        return result;
    }

    private List<AnalyticsDashboardDto.TrafficSourceCount> buildTrafficSources(Instant since) {
        Object[][] raw = repository.countTrafficSources(since);
        long total = 0;
        for (Object[] row : raw) total += (Long) row[1];
        List<AnalyticsDashboardDto.TrafficSourceCount> result = new ArrayList<>();
        for (Object[] row : raw) {
            String source = (String) row[0];
            long count    = (Long) row[1];
            result.add(new AnalyticsDashboardDto.TrafficSourceCount(source, count, total));
        }
        result.sort((a, b) -> Long.compare(b.getCount(), a.getCount()));
        return result;
    }

    private List<AnalyticsDashboardDto.VisitorCountByPeriod> buildVisitorsByDay(Instant since) {
        List<AnalyticsDashboardDto.VisitorCountByPeriod> result = new ArrayList<>();
        Instant cursor = since.truncatedTo(ChronoUnit.DAYS);
        Instant now    = Instant.now();
        while (cursor.isBefore(now)) {
            Instant dayStart = cursor;
            Instant dayEnd   = cursor.plus(1, ChronoUnit.DAYS);
            long count  = repository.countUniqueVisitorsBetween(dayStart, dayEnd);
            String label = cursor.toString().substring(0, 10);
            result.add(new AnalyticsDashboardDto.VisitorCountByPeriod(label, count));
            cursor = dayEnd;
        }
        return result;
    }

    private List<AnalyticsDashboardDto.DeviceCount> buildDeviceBreakdown(Instant since) {
        Object[][] raw = repository.countByDeviceType(since);
        long total = 0;
        for (Object[] row : raw) total += (Long) row[1];
        List<AnalyticsDashboardDto.DeviceCount> result = new ArrayList<>();
        for (Object[] row : raw) {
            String deviceType = (String) row[0];
            long count = (Long) row[1];
            result.add(new AnalyticsDashboardDto.DeviceCount(deviceType, count, total));
        }
        return result;
    }

    private String normaliseRoute(String route) {
        if (route == null || route.isBlank()) return "/";
        String r = route.toLowerCase().trim();
        if (r.equals("/")) return "/";
        return r.length() > 255 ? r.substring(0, 255) : r;
    }
}
