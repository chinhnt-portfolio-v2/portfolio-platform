package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.auth.jwt.JwtAuthenticationFilter.JwtUserPrincipal;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import dev.chinh.portfolio.shared.config.DemoAppRegistry;
import org.junit.jupiter.api.AfterEach;
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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdminShowcaseController} — GET /api/v1/admin/showcase/apps
 *
 * <p>Story 6.4.2 AC#1: Admin endpoint exposes all registered apps from showcase.yml.
 *
 * <p>Authorization matrix:
 * <ul>
 *   <li>200 — valid Owner JWT + User with OWNER role in DB</li>
 *   <li>403 — non-Owner JWT (authenticated but role is not OWNER)</li>
 *   <li>403 — user ID not found in database</li>
 *   <li>403 — unrecognized / malformed JWT principal type</li>
 * </ul>
 *
 * <p>HTTP 401 (unauthenticated) is handled by
 * {@link dev.chinh.portfolio.auth.jwt.JwtAuthenticationEntryPoint} at the Spring
 * Security filter level.
 *
 * <p>Uses pure Mockito — no Spring context, no Testcontainers, no Docker required.
 */
@ExtendWith(MockitoExtension.class)
class AdminShowcaseControllerTest {

    private static final UUID OWNER_USER_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NON_OWNER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private DemoAppRegistry demoAppRegistry;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminShowcaseController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    /**
     * Story 6.4.2 AC#1: Owner JWT → HTTP 200 with app list.
     * Response body: { "apps": [{ "slug", "name", "healthEndpoint", "demoUrl" }] }
     */
    @Test
    void getApps_asOwner_returns200WithAppList() {
        // Given
        DemoAppRegistry.DemoApp wallet = new DemoAppRegistry.DemoApp();
        wallet.setSlug("wallet-app");
        wallet.setName("Wallet App");
        wallet.setHealthEndpoint("https://wallet.chinh.dev/health");
        wallet.setDemoUrl("https://wallet.chinh.dev");

        DemoAppRegistry.DemoApp portfolio = new DemoAppRegistry.DemoApp();
        portfolio.setSlug("portfolio-v2");
        portfolio.setName("Portfolio v2");
        portfolio.setHealthEndpoint("https://portfolio.chinh.dev/health");
        portfolio.setDemoUrl("https://portfolio.chinh.dev");

        when(demoAppRegistry.getApps()).thenReturn(List.of(wallet, portfolio));
        when(userRepository.findById(OWNER_USER_ID)).thenReturn(Optional.of(ownerUser()));

        setSecurityContext(ownerPrincipal(OWNER_USER_ID));

        // When
        ResponseEntity<?> response = controller.getApps();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKey("apps");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> apps = (List<Map<String, String>>) body.get("apps");
        assertThat(apps).hasSize(2);
        assertThat(apps.get(0)).containsEntry("slug", "wallet-app");
        assertThat(apps.get(1)).containsEntry("slug", "portfolio-v2");

        verify(demoAppRegistry).getApps();
        verify(userRepository).findById(OWNER_USER_ID);
    }

    /**
     * Verifies the exact response shape: all 4 required fields present.
     */
    @Test
    void getApps_responseContainsAllRequiredFields() {
        DemoAppRegistry.DemoApp app = new DemoAppRegistry.DemoApp();
        app.setSlug("wallet-app");
        app.setName("Wallet App");
        app.setHealthEndpoint("https://wallet.chinh.dev/health");
        app.setDemoUrl("https://wallet.chinh.dev");

        when(demoAppRegistry.getApps()).thenReturn(List.of(app));
        when(userRepository.findById(OWNER_USER_ID)).thenReturn(Optional.of(ownerUser()));
        setSecurityContext(ownerPrincipal(OWNER_USER_ID));

        ResponseEntity<?> response = controller.getApps();

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> apps = (List<Map<String, String>>) body.get("apps");

        assertThat(apps.get(0)).containsKeys("slug", "name", "healthEndpoint", "demoUrl");
        assertThat(apps.get(0).get("slug")).isEqualTo("wallet-app");
        assertThat(apps.get(0).get("name")).isEqualTo("Wallet App");
        assertThat(apps.get(0).get("healthEndpoint")).isEqualTo("https://wallet.chinh.dev/health");
        assertThat(apps.get(0).get("demoUrl")).isEqualTo("https://wallet.chinh.dev");
    }

    /**
     * Empty showcase.yml → 200 with empty apps list (not an error).
     */
    @Test
    void getApps_emptyRegistry_returnsEmptyList() {
        when(demoAppRegistry.getApps()).thenReturn(List.of());
        when(userRepository.findById(OWNER_USER_ID)).thenReturn(Optional.of(ownerUser()));
        setSecurityContext(ownerPrincipal(OWNER_USER_ID));

        ResponseEntity<?> response = controller.getApps();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("apps")).isEqualTo(List.of());
    }

    // ── Authorization: non-owner ──────────────────────────────────────────────

    /**
     * Non-owner JWT → HTTP 403 Forbidden.
     */
    @Test
    void getApps_asMember_returns403() {
        when(userRepository.findById(NON_OWNER_USER_ID)).thenReturn(Optional.of(memberUser()));
        setSecurityContext(memberPrincipal(NON_OWNER_USER_ID));

        ResponseEntity<?> response = controller.getApps();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(demoAppRegistry);
    }

    /**
     * User ID not found in database → 403 (treat as non-owner).
     */
    @Test
    void getApps_unknownUser_returns403() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        setSecurityContext(ownerPrincipal(OWNER_USER_ID));

        ResponseEntity<?> response = controller.getApps();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * String principal (fallback) not parseable as UUID → 403.
     */
    @Test
    void getApps_unparseablePrincipal_returns403() {
        setSecurityContext(new UsernamePasswordAuthenticationToken("not-a-uuid", null, List.of()));

        ResponseEntity<?> response = controller.getApps();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Null authentication → 403 (should not happen via Spring Security).
     */
    @Test
    void getApps_noAuthentication_returns403() {
        SecurityContextHolder.clearContext();

        ResponseEntity<?> response = controller.getApps();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Structured error format ─────────────────────────────────────────────

    /**
     * 403 response uses the project's structured error format:
     * { "error": { "code": "FORBIDDEN", "message": "..." } }
     */
    @Test
    void getApps_nonOwner_errorFormatMatchesProjectStandard() {
        when(userRepository.findById(NON_OWNER_USER_ID)).thenReturn(Optional.of(memberUser()));
        setSecurityContext(memberPrincipal(NON_OWNER_USER_ID));

        ResponseEntity<?> response = controller.getApps();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("error");

        @SuppressWarnings("unchecked")
        Map<String, String> error = (Map<String, String>) body.get("error");
        assertThat(error).containsEntry("code", "FORBIDDEN");
        assertThat(error).containsEntry("message", "Access denied. Owner role required.");
    }

    // ── Security context helpers ─────────────────────────────────────────────

    private void setSecurityContext(Object principal) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private JwtUserPrincipal ownerPrincipal(UUID userId) {
        return new JwtUserPrincipal(userId.toString(), "owner@chinh.dev");
    }

    private JwtUserPrincipal memberPrincipal(UUID userId) {
        return new JwtUserPrincipal(userId.toString(), "member@chinh.dev");
    }

    private User ownerUser() {
        return makeUser(OWNER_USER_ID, "owner@chinh.dev", UserRole.OWNER);
    }

    private User memberUser() {
        return makeUser(NON_OWNER_USER_ID, "member@chinh.dev", UserRole.USER);
    }

    /** Creates a User with the given fields via reflection (id has no setter). */
    private User makeUser(UUID id, String email, UserRole role) {
        User user = new User();
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
