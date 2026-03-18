package dev.chinh.portfolio.auth.jwt;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthenticationFilter(jwtService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);

        // Clear security context
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Valid Token Scenarios")
    class ValidTokenTests {

        @Test
        @DisplayName("should set authentication when valid Bearer token is provided")
        void shouldSetAuthenticationForValidToken() throws Exception {
            // Given
            String validToken = "valid.jwt.token";
            String userId = "12345678-1234-1234-1234-123456789abc";
            String email = "test@example.com";

            when(jwtService.validateToken(validToken)).thenReturn(true);
            when(jwtService.extractUsername(validToken)).thenReturn(userId);
            when(jwtService.extractEmail(validToken)).thenReturn(email);

            request.addHeader("Authorization", "Bearer " + validToken);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth);
            assertTrue(auth.getPrincipal() instanceof JwtAuthenticationFilter.JwtUserPrincipal);
        }

        @Test
        @DisplayName("should continue filter chain when token is valid")
        void shouldContinueChainWhenTokenIsValid() throws Exception {
            // Given
            String validToken = "valid.jwt.token";
            when(jwtService.validateToken(validToken)).thenReturn(true);
            when(jwtService.extractUsername(validToken)).thenReturn("user-id");
            when(jwtService.extractEmail(validToken)).thenReturn("test@example.com");

            request.addHeader("Authorization", "Bearer " + validToken);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Invalid Token Scenarios")
    class InvalidTokenTests {

        @Test
        @DisplayName("should not set authentication for invalid token")
        void shouldNotSetAuthenticationForInvalidToken() throws Exception {
            // Given
            String invalidToken = "invalid.token";
            when(jwtService.validateToken(invalidToken)).thenReturn(false);

            request.addHeader("Authorization", "Bearer " + invalidToken);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNull(auth);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should not set authentication when token is missing")
        void shouldNotSetAuthenticationWhenTokenIsMissing() throws Exception {
            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNull(auth);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should not set authentication for wrong Bearer prefix")
        void shouldNotSetAuthenticationForWrongPrefix() throws Exception {
            // Given
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNull(auth);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should not set authentication for empty token")
        void shouldNotSetAuthenticationForEmptyToken() throws Exception {
            // Given
            request.addHeader("Authorization", "Bearer ");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNull(auth);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should not set authentication for blank Authorization header")
        void shouldNotSetAuthenticationForBlankHeader() throws Exception {
            // Given - no Authorization header

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNull(auth);
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle null token gracefully")
        void shouldHandleNullToken() throws Exception {
            // Given
            when(jwtService.validateToken(null)).thenReturn(false);

            request.addHeader("Authorization", "Bearer null");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should extract token correctly from Authorization header")
        void shouldExtractTokenCorrectly() throws Exception {
            // Given
            String token = "my-secret-jwt-token";
            when(jwtService.validateToken(token)).thenReturn(true);
            when(jwtService.extractUsername(token)).thenReturn("user-id");
            when(jwtService.extractEmail(token)).thenReturn("test@test.com");

            request.addHeader("Authorization", "Bearer " + token);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            verify(jwtService).validateToken(token);
        }
    }
}
