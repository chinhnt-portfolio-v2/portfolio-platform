package dev.chinh.portfolio.auth.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OAuth2AuthenticationSuccessHandler and OAuth2AuthenticationFailureHandler.
 */
@DisplayName("OAuth2 Authentication Handlers")
class OAuth2AuthenticationHandlersTest {

    @Test
    @DisplayName("OAuth2AuthenticationFailureHandler - should redirect with cancelled error for BadCredentialsException")
    void shouldRedirectWithCancelledErrorForBadCredentialsException() throws Exception {
        // Given
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.springframework.security.authentication.BadCredentialsException exception =
                new org.springframework.security.authentication.BadCredentialsException("User cancelled");

        // When
        handler.onAuthenticationFailure(request, response, exception);

        // Then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=cancelled");
    }

    @Test
    @DisplayName("OAuth2AuthenticationFailureHandler - should redirect with cancelled error for access_denied")
    void shouldRedirectWithCancelledErrorForAccessDenied() throws Exception {
        // Given
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception =
                new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error("access_denied"),
                        "Access denied"
                );

        // When
        handler.onAuthenticationFailure(request, response, exception);

        // Then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=cancelled");
    }

    @Test
    @DisplayName("OAuth2AuthenticationFailureHandler - should redirect with google_auth_failed for other errors")
    void shouldRedirectWithGoogleAuthFailedForOtherErrors() throws Exception {
        // Given
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.springframework.security.oauth2.core.OAuth2AuthenticationException exception =
                new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                        new org.springframework.security.oauth2.core.OAuth2Error("server_error"),
                        "Server error"
                );

        // When
        handler.onAuthenticationFailure(request, response, exception);

        // Then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=google_auth_failed");
    }

    @Test
    @DisplayName("OAuth2AuthenticationSuccessHandler - should return tokens in correct format")
    void shouldReturnTokensInCorrectFormat() throws Exception {
        // Given
        dev.chinh.portfolio.auth.jwt.JwtService jwtService = mock(dev.chinh.portfolio.auth.jwt.JwtService.class);
        dev.chinh.portfolio.auth.session.SessionService sessionService = mock(dev.chinh.portfolio.auth.session.SessionService.class);
        dev.chinh.portfolio.auth.session.Session session = mock(dev.chinh.portfolio.auth.session.Session.class);

        when(jwtService.generateAccessToken(any())).thenReturn("test-access-token");
        when(sessionService.createSession(any())).thenReturn(session);
        when(session.getRefreshToken()).thenReturn("test-refresh-token");

        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(jwtService, sessionService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Create mock authentication with GoogleOAuth2UserPrincipal
        dev.chinh.portfolio.auth.user.User testUser = new dev.chinh.portfolio.auth.user.User();
        testUser.setEmail("test@gmail.com");
        testUser.setProvider(dev.chinh.portfolio.auth.user.AuthProvider.GOOGLE);
        testUser.setRole(dev.chinh.portfolio.auth.user.UserRole.USER);

        GoogleOAuth2UserPrincipal principal = new GoogleOAuth2UserPrincipal(testUser, Map.of("email", "test@gmail.com"));

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        String content = response.getContentAsString();
        assertThat(content).contains("accessToken");
        assertThat(content).contains("refreshToken");
        assertThat(content).contains("tokenType");
        assertThat(content).contains("Bearer");
    }

    @Test
    @DisplayName("OAuth2AuthenticationSuccessHandler - should redirect to error when principal is not GoogleOAuth2UserPrincipal")
    void shouldRedirectToErrorWhenPrincipalIsNotGooglePrincipal() throws Exception {
        // Given
        dev.chinh.portfolio.auth.jwt.JwtService jwtService = mock(dev.chinh.portfolio.auth.jwt.JwtService.class);
        dev.chinh.portfolio.auth.session.SessionService sessionService = mock(dev.chinh.portfolio.auth.session.SessionService.class);

        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(jwtService, sessionService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Create mock authentication with wrong principal type
        OAuth2User wrongPrincipal = new DefaultOAuth2User(Collections.emptyList(), Map.of("email", "test@gmail.com"), "sub");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(wrongPrincipal);

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=oauth2_failure");
        verify(jwtService, never()).generateAccessToken(any());
        verify(sessionService, never()).createSession(any());
    }
}
