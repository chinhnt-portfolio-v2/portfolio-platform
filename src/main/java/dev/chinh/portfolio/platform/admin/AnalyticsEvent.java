package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRole;
import jakarta.persistence.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;  // "page_view" | "unique_visitor" | "traffic_source"

    @Column(name = "route", nullable = false, length = 255)
    private String route;  // e.g. "/", "/projects/wallet-app"

    @Column(name = "visitor_id", length = 64)
    private String visitorId;  // hashed browser fingerprint, no PII

    @Column(name = "traffic_source", length = 30)
    private String trafficSource;  // "direct" | "referral" | "organic"

    @Column(name = "referrer_domain", length = 255)
    private String referrerDomain;

    @Column(name = "country_code", length = 10)
    private String countryCode;  // derived from IP (no PII stored)

    @Column(name = "device_type", length = 20)
    private String deviceType;  // "desktop" | "mobile" | "tablet"

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected AnalyticsEvent() {}

    // Factory methods
    public static AnalyticsEvent pageView(String route, String visitorId, String sessionId) {
        AnalyticsEvent e = new AnalyticsEvent();
        e.eventType = "page_view";
        e.route = route;
        e.visitorId = visitorId;
        e.sessionId = sessionId;
        return e;
    }

    public static AnalyticsEvent trafficSource(String route, String trafficSource, String referrerDomain) {
        AnalyticsEvent e = new AnalyticsEvent();
        e.eventType = "traffic_source";
        e.route = route;
        e.trafficSource = trafficSource;
        e.referrerDomain = referrerDomain;
        return e;
    }

    // Getters
    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getRoute() { return route; }
    public String getVisitorId() { return visitorId; }
    public String getTrafficSource() { return trafficSource; }
    public String getReferrerDomain() { return referrerDomain; }
    public String getCountryCode() { return countryCode; }
    public String getDeviceType() { return deviceType; }
    public String getSessionId() { return sessionId; }
    public Instant getOccurredAt() { return occurredAt; }

    // Setters (for analytics tracking)
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
}
