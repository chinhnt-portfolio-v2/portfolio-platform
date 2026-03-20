package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.auth.jwt.JwtAuthenticationFilter.JwtUserPrincipal;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import dev.chinh.portfolio.platform.admin.dto.TrackEventRequest;
import dev.chinh.portfolio.shared.error.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Analytics event recording and admin dashboard API.
 *
 * Track endpoint (POST /api/v1/analytics/track) — public, fire-and-forget.
 * Dashboard endpoint (GET /api/v1/analytics/dashboard) — admin-only (OWNER role required).
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    public AnalyticsController(AnalyticsService analyticsService, UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
    }

    // ── Public: Track Event ───────────────────────────────────────

    /**
     * Record an analytics event.
     * Publicly accessible — no authentication required.
     * Called by the frontend as a fire-and-forget beacon.
     */
    @PostMapping("/track")
    public ResponseEntity<Void> track(
            @Valid @RequestBody TrackEventRequest request,
            @RequestHeader(value = "X-Device-Type", required = false) String deviceTypeHeader,
            HttpServletRequest httpRequest) {

        // Derive device type from header or User-Agent if not provided
        String deviceType = deviceTypeHeader;
        if ((deviceType == null || deviceType.isBlank())) {
            deviceType = inferDeviceType(httpRequest.getHeader("User-Agent"));
        }

        // Extract referrer info from the request
        String referrer = httpRequest.getHeader("Referer");
        String referrerDomain = referrer != null ? extractDomain(referrer) : null;

        if ("page_view".equals(request.eventType())) {
            analyticsService.recordPageView(
                    request.route(),
                    request.visitorId(),
                    request.sessionId(),
                    deviceType
            );
        } else if ("traffic_source".equals(request.eventType())) {
            analyticsService.recordTrafficSource(
                    request.route(),
                    request.trafficSource(),
                    referrerDomain
            );
        }

        return ResponseEntity.accepted().build();
    }

    // ── Admin-Only: Dashboard ────────────────────────────────────

    /**
     * Return aggregated analytics dashboard data.
     * Only accessible to authenticated users with the OWNER role.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(
            @RequestParam(required = false) Long periodStartEpoch,
            @RequestParam(required = false) Long periodEndEpoch) {

        // Verify the authenticated user has OWNER role
        if (!isCurrentUserOwner()) {
            log.warn("Non-owner user attempted to access analytics dashboard");
            throw new ForbiddenException("Access denied. Admin role required.");
        }

        Instant periodStart = periodStartEpoch != null ? Instant.ofEpochSecond(periodStartEpoch) : null;
        Instant periodEnd   = periodEndEpoch   != null ? Instant.ofEpochSecond(periodEndEpoch)   : null;

        AnalyticsDashboardDto dashboard = analyticsService.getDashboard(periodStart, periodEnd);
        return ResponseEntity.ok(dashboard);
    }

    // ── Helpers ─────────────────────────────────────────────────

    /**
     * Check whether the currently authenticated user holds the OWNER role.
     */
    private boolean isCurrentUserOwner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        Object principal = auth.getPrincipal();
        String userIdStr;

        if (principal instanceof JwtUserPrincipal jwtPrincipal) {
            userIdStr = jwtPrincipal.getUserId();
        } else if (principal instanceof String s) {
            userIdStr = s;
        } else {
            return false;
        }

        try {
            UUID userId = UUID.fromString(userIdStr);
            return userRepository.findById(userId)
                    .map(user -> user.getRole() == UserRole.OWNER)
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            log.debug("Could not parse userId from principal: {}", userIdStr);
            return false;
        }
    }

    private String inferDeviceType(String userAgent) {
        if (userAgent == null) return "desktop";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") && !ua.contains("tablet") || ua.contains("iphone")) {
            return "mobile";
        }
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        }
        return "desktop";
    }

    private String extractDomain(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return new java.net.URL(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
