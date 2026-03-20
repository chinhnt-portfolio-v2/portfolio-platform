package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.auth.jwt.JwtAuthenticationFilter.JwtUserPrincipal;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import dev.chinh.portfolio.shared.config.DemoAppRegistry;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin endpoint exposing registered demo apps from {@code showcase.yml}.
 *
 * <p>Provides the Owner a read-only view of which apps are currently registered
 * for health polling, without needing to inspect the config file directly.
 *
 * <p>Authentication: Valid Owner JWT required.
 *
 * @see DemoAppRegistry
 */
@RestController
@RequestMapping("/api/v1/admin/showcase")
public class AdminShowcaseController {

    private static final Logger log = LoggerFactory.getLogger(AdminShowcaseController.class);

    private final DemoAppRegistry demoAppRegistry;
    private final UserRepository userRepository;

    public AdminShowcaseController(DemoAppRegistry demoAppRegistry,
                                  UserRepository userRepository) {
        this.demoAppRegistry = demoAppRegistry;
        this.userRepository = userRepository;
    }

    /**
     * Return all demo app entries registered in {@code showcase.yml}.
     *
     * @return HTTP 200 with app list, or HTTP 403 if the caller is not the Owner.
     */
    @GetMapping("/apps")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> getApps() {

        if (!isCurrentUserOwner()) {
            log.warn("Non-owner user attempted to access admin showcase endpoint");
            throw new ForbiddenException("Access denied. Owner role required.");
        }

        List<DemoAppRegistry.DemoApp> apps = demoAppRegistry.getApps();

        List<Map<String, String>> response = apps.stream()
                .map(app -> Map.<String, String>of(
                        "slug", app.getSlug() != null ? app.getSlug() : "",
                        "name", app.getName() != null ? app.getName() : "",
                        "healthEndpoint", app.getHealthEndpoint() != null ? app.getHealthEndpoint() : "",
                        "demoUrl", app.getDemoUrl() != null ? app.getDemoUrl() : ""
                ))
                .toList();

        log.debug("Admin requested showcase apps list: {} entries", apps.size());

        return ResponseEntity.ok(Map.of("apps", response));
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
