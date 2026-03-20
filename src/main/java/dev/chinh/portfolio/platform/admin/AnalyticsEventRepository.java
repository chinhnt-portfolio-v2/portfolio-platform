package dev.chinh.portfolio.platform.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    // Count unique page views per route within a time range
    @Query("SELECT COUNT(DISTINCT e.sessionId) FROM AnalyticsEvent e " +
           "WHERE e.route = :route AND e.eventType = 'page_view' " +
           "AND e.occurredAt >= :since")
    long countUniquePageViewsByRoute(@Param("route") String route, @Param("since") Instant since);

    // Count total page views per route within a time range
    @Query("SELECT COUNT(e) FROM AnalyticsEvent e " +
           "WHERE e.route = :route AND e.eventType = 'page_view' " +
           "AND e.occurredAt >= :since")
    long countPageViewsByRoute(@Param("route") String route, @Param("since") Instant since);

    // Count unique visitors (distinct visitorId) since a point in time
    @Query("SELECT COUNT(DISTINCT e.visitorId) FROM AnalyticsEvent e " +
           "WHERE e.eventType = 'page_view' AND e.occurredAt >= :since")
    long countUniqueVisitors(@Param("since") Instant since);

    // Count unique visitors within a specific time window (fixes buildVisitorsByDay)
    @Query("SELECT COUNT(DISTINCT e.visitorId) FROM AnalyticsEvent e " +
           "WHERE e.eventType = 'page_view' " +
           "AND e.occurredAt >= :start AND e.occurredAt < :end")
    long countUniqueVisitorsBetween(@Param("start") Instant start, @Param("end") Instant end);

    // Count traffic source occurrences
    @Query("SELECT e.trafficSource, COUNT(e) FROM AnalyticsEvent e " +
           "WHERE e.eventType = 'traffic_source' AND e.occurredAt >= :since " +
           "GROUP BY e.trafficSource")
    Object[][] countTrafficSources(@Param("since") Instant since);

    // Get all distinct routes that have been visited
    @Query("SELECT DISTINCT e.route FROM AnalyticsEvent e " +
           "WHERE e.eventType = 'page_view' AND e.occurredAt >= :since")
    String[] findDistinctRoutes(@Param("since") Instant since);

    // Aggregated page views — single query replaces N+1 problem (fixes buildPageViewsByRoute)
    @Query("SELECT e.route, COUNT(e), COUNT(DISTINCT e.sessionId) " +
           "FROM AnalyticsEvent e " +
           "WHERE e.eventType = 'page_view' AND e.occurredAt >= :since " +
           "GROUP BY e.route")
    List<Object[]> aggregatePageViews(@Param("since") Instant since);

    // Device breakdown (fixes buildDeviceBreakdown stub)
    @Query("SELECT e.deviceType, COUNT(e) FROM AnalyticsEvent e " +
           "WHERE e.eventType = 'page_view' AND e.occurredAt >= :since " +
           "AND e.deviceType IS NOT NULL " +
           "GROUP BY e.deviceType")
    Object[][] countByDeviceType(@Param("since") Instant since);
}
