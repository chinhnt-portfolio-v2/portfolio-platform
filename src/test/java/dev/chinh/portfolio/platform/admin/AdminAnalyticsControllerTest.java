package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.auth.jwt.JwtAuthenticationFilter.JwtUserPrincipal;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import dev.chinh.portfolio.platform.admin.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdminAnalyticsController}.
 *
 * <p>Authorization matrix:
 * <ul>
 *   <li>200 — valid Owner JWT + User with OWNER role in DB</li>
 *   <li>403 — non-Owner JWT (authenticated but role is not OWNER)</li>
 *   <li>403 — user ID not found in database</li>
 *   <li>403 — unrecognized / malformed JWT principal type</li>
 * </ul>
 *
 * <p>Note: HTTP 401 (unauthenticated) is handled by
 * {@link dev.chinh.portfolio.auth.jwt.JwtAuthenticationEntryPoint} at the Spring
 * Security filter level, before the request reaches this controller.
 */
@ExtendWith(MockitoExtension.class)
class AdminAnalyticsControllerTest {

    private static final UUID OWNER_USER_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NON_OWNER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private AdminAnalyticsService analyticsService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminAnalyticsController controller;

    // ── Test fixtures ───────────────────────────────────────────────────────────

    /**
     * Creates a real (non-mock) {@link User} entity with the given role.
     * Uses the public setter — no reflection required.
     */
    private User realUser(UUID id, UserRole role) {
        User user = new User();
        user.setEmail(id + "@test.example.com");
        user.setRole(role);
        return user;
    }

    private void setSecurityContext(JwtUserPrincipal principal) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AdminAnalyticsDto emptyAnalyticsDto() {
        return new AdminAnalyticsDto(
                new AnalyticsSummaryDto(0L, 0L, 0L),
                List.of(),
                List.of(),
                new DateRangeDto(null, null)
        );
    }

    // ── AC-1: 200 for Owner JWT ────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-1: 200 for valid Owner JWT")
    class OwnerAccessTests {

        @Test
        @DisplayName("should return 200 with analytics DTO when caller is the Owner")
        void shouldReturn200ForOwner() {
            setSecurityContext(new JwtUserPrincipal(OWNER_USER_ID.toString(), "owner@example.com"));
            when(userRepository.findById(OWNER_USER_ID)).thenReturn(Optional.of(realUser(OWNER_USER_ID, UserRole.OWNER)));
            when(analyticsService.getAnalytics()).thenReturn(emptyAnalyticsDto());

            ResponseEntity<?> response = controller.getAnalytics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(AdminAnalyticsDto.class);
            verify(analyticsService).getAnalytics();
        }

        @Test
        @DisplayName("should return 200 with populated analytics DTO")
        void shouldReturn200WithPopulatedAnalytics() {
            setSecurityContext(new JwtUserPrincipal(OWNER_USER_ID.toString(), "owner@example.com"));
            when(userRepository.findById(OWNER_USER_ID)).thenReturn(Optional.of(realUser(OWNER_USER_ID, UserRole.OWNER)));

            AdminAnalyticsDto populated = new AdminAnalyticsDto(
                    new AnalyticsSummaryDto(42L, 17L, 5L),
                    List.of(
                            new ReferralSourceStatDto("linkedin", 18L, 42.9),
                            new ReferralSourceStatDto("cv-vn", 12L, 28.6),
                            new ReferralSourceStatDto(null, 4L, 9.5)
                    ),
                    List.of(new RecentSubmissionDto(
                            "00000000-0000-0000-0000-000000000001",
                            "recruiter@example.com",
                            Instant.parse("2026-03-03T14:00:00Z"),
                            "linkedin"
                    )),
                    new DateRangeDto(
                            Instant.parse("2026-01-15T08:23:00Z"),
                            Instant.parse("2026-03-03T14:00:00Z")
                    )
            );
            when(analyticsService.getAnalytics()).thenReturn(populated);

            ResponseEntity<?> response = controller.getAnalytics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            AdminAnalyticsDto body = (AdminAnalyticsDto) response.getBody();
            assertThat(body.summary().totalSubmissions()).isEqualTo(42L);
            assertThat(body.byReferralSource()).hasSize(3);
            assertThat(body.recentSubmissions()).hasSize(1);
            assertThat(body.dateRange().earliest()).isNotNull();
        }
    }

    // ── AC-2: 403 for non-Owner JWT ────────────────────────────────────────────

    @Nested
    @DisplayName("AC-2: 403 for non-Owner JWT")
    class NonOwnerAccessTests {

        @Test
        @DisplayName("should return 403 when user does not have Owner role")
        void shouldReturn403ForNonOwner() {
            setSecurityContext(new JwtUserPrincipal(NON_OWNER_USER_ID.toString(), "visitor@example.com"));
            when(userRepository.findById(NON_OWNER_USER_ID))
                    .thenReturn(Optional.of(realUser(NON_OWNER_USER_ID, UserRole.USER)));

            ResponseEntity<?> response = controller.getAnalytics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(analyticsService, never()).getAnalytics();
        }

        @Test
        @DisplayName("should return 403 with structured error body matching spec")
        void shouldReturn403WithStructuredError() {
            setSecurityContext(new JwtUserPrincipal(NON_OWNER_USER_ID.toString(), "visitor@example.com"));
            when(userRepository.findById(NON_OWNER_USER_ID))
                    .thenReturn(Optional.of(realUser(NON_OWNER_USER_ID, UserRole.USER)));

            ResponseEntity<?> response = controller.getAnalytics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("error");
            @SuppressWarnings("unchecked")
            Map<String, String> error = (Map<String, String>) body.get("error");
            assertThat(error.get("code")).isEqualTo("FORBIDDEN");
            assertThat(error.get("message")).isNotBlank();
        }

        @Test
        @DisplayName("should return 403 when user does not exist in database")
        void shouldReturn403WhenUserNotFound() {
            setSecurityContext(new JwtUserPrincipal(NON_OWNER_USER_ID.toString(), "visitor@example.com"));
            when(userRepository.findById(NON_OWNER_USER_ID)).thenReturn(Optional.empty());

            ResponseEntity<?> response = controller.getAnalytics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(analyticsService, never()).getAnalytics();
        }
    }

    // ── 403 for unrecognized principal ─────────────────────────────────────────

    @Nested
    @DisplayName("403 for unrecognized / malformed JWT principal")
    class UnrecognizedPrincipalTests {

        @Test
        @DisplayName("should return 403 when principal is unrecognized type (String instead of JwtUserPrincipal)")
        void shouldReturn403ForUnrecognizedPrincipal() {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("unknown-principal", null, Collections.emptyList());
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);

            ResponseEntity<?> response = controller.getAnalytics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(analyticsService, never()).getAnalytics();
        }

        @Test
        @DisplayName("should return 403 when SecurityContext has no authentication")
        void shouldReturn403WhenNoAuthentication() {
            clearSecurityContext();

            ResponseEntity<?> response = controller.getAnalytics();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(analyticsService, never()).getAnalytics();
        }
    }
}
