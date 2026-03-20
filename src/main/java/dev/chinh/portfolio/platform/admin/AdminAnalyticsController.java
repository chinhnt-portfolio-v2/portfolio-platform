package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.auth.jwt.JwtAuthenticationFilter.JwtUserPrincipal;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import dev.chinh.portfolio.platform.admin.dto.AdminAnalyticsDto;
import dev.chinh.portfolio.shared.error.ForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin analytics endpoint for the portfolio owner.
 *
 * <p>Exposes {@code GET /api/v1/admin/analytics} — only accessible to authenticated
 * users with the OWNER role.
 *
 * <p>Authentication: Valid Owner JWT required.
 * <ul>
 *   <li>Valid Owner JWT → HTTP 200 with {@link AdminAnalyticsDto}</li>
 *   <li>No auth / non-Owner JWT → HTTP 403</li>
 * </ul>
 *
 * @see AdminAnalyticsService
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsController.class);

    private final AdminAnalyticsService analyticsService;
    private final UserRepository userRepository;

    public AdminAnalyticsController(AdminAnalyticsService analyticsService,
                                    UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
    }

    /**
     * Return aggregated contact-form analytics data.
     *
     * @return HTTP 200 with analytics DTO, or HTTP 403 if the caller is not the Owner.
     */
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> getAnalytics() {

        if (!isCurrentUserOwner()) {
            log.warn("Non-owner user attempted to access admin analytics endpoint");
            throw new ForbiddenException("Access denied. Owner role required.");
        }

        AdminAnalyticsDto analytics = analyticsService.getAnalytics();
        return ResponseEntity.ok(analytics);
    }

    // ── Authorization helpers ──────────────────────────────────────────────────

    /**
     * Verify the currently authenticated user holds the OWNER role.
     *
     * <p>Strategy: extract userId from the JWT principal, then look up the user's
     * role in the database. This ensures the role check is authoritative and not
     * solely dependent on JWT claims.
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
            log.debug("Could not parse userId from JWT principal: {}", userIdStr);
            return false;
        }
    }
}
