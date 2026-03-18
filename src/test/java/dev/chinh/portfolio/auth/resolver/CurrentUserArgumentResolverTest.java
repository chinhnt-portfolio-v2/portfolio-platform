package dev.chinh.portfolio.auth.resolver;

import dev.chinh.portfolio.auth.annotation.CurrentUser;
import dev.chinh.portfolio.auth.jwt.JwtAuthenticationFilter.JwtUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CurrentUserArgumentResolver.
 */
class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("supportsParameter")
    class SupportsParameterTests {

        @Test
        @DisplayName("should return true for UUID parameter with @CurrentUser")
        void shouldReturnTrueForUuidWithAnnotation() throws NoSuchMethodException {
            Method method = TestController.class.getMethod("testMethod", UUID.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            assertTrue(resolver.supportsParameter(parameter));
        }

        @Test
        @DisplayName("should return false for parameter without @CurrentUser")
        void shouldReturnFalseWithoutAnnotation() throws NoSuchMethodException {
            Method method = TestController.class.getMethod("testMethodNoAnnotation", UUID.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            assertFalse(resolver.supportsParameter(parameter));
        }

        @Test
        @DisplayName("should return false for non-UUID parameter with @CurrentUser")
        void shouldReturnFalseForNonUuid() throws NoSuchMethodException {
            Method method = TestController.class.getMethod("testMethodString", String.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            assertFalse(resolver.supportsParameter(parameter));
        }
    }

    @Nested
    @DisplayName("resolveArgument")
    class ResolveArgumentTests {

        @Test
        @DisplayName("should extract UUID from JwtUserPrincipal")
        void shouldExtractUuidFromPrincipal() throws Exception {
            UUID userId = UUID.randomUUID();
            JwtUserPrincipal principal = new JwtUserPrincipal(userId.toString(), "test@example.com");
            Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            Method method = TestController.class.getMethod("testMethod", UUID.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            Object result = resolver.resolveArgument(parameter, null, null, null);

            assertEquals(userId, result);
        }

        @Test
        @DisplayName("should extract UUID from String principal")
        void shouldExtractUuidFromStringPrincipal() throws Exception {
            UUID userId = UUID.randomUUID();
            Authentication auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            Method method = TestController.class.getMethod("testMethod", UUID.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            Object result = resolver.resolveArgument(parameter, null, null, null);

            assertEquals(userId, result);
        }

        @Test
        @DisplayName("should throw when no authentication")
        void shouldThrowWhenNoAuthentication() throws NoSuchMethodException {
            Method method = TestController.class.getMethod("testMethod", UUID.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            assertThrows(IllegalStateException.class, () ->
                resolver.resolveArgument(parameter, null, null, null)
            );
        }

        @Test
        @DisplayName("should throw when authentication is null")
        void shouldThrowWhenAuthenticationNull() throws NoSuchMethodException {
            SecurityContextHolder.getContext().setAuthentication(null);

            Method method = TestController.class.getMethod("testMethod", UUID.class);
            MethodParameter parameter = new MethodParameter(method, 0);

            assertThrows(IllegalStateException.class, () ->
                resolver.resolveArgument(parameter, null, null, null)
            );
        }
    }

    // Test controller methods for reflection
    @SuppressWarnings("unused")
    private static class TestController {
        public void testMethod(@CurrentUser UUID userId) {}
        public void testMethodNoAnnotation(UUID userId) {}
        public void testMethodString(@CurrentUser String userId) {}
    }
}
