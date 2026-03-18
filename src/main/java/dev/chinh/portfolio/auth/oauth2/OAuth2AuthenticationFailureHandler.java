package dev.chinh.portfolio.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Authentication failure handler for OAuth2 login.
 * Redirects to login page with user-friendly error messages.
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        log.error("OAuth2 authentication FAILED!", exception);

        String errorMessage;

        // Check specific error types for user-friendly messages
        if (exception instanceof BadCredentialsException) {
            errorMessage = "cancelled";
        } else if (exception instanceof OAuth2AuthenticationException oAuth2Ex) {
            String errorCode = oAuth2Ex.getError().getErrorCode();
            log.error("OAuth2 error code: {}, error: {}", errorCode, oAuth2Ex.getError());
            if ("access_denied".equals(errorCode) || "consent_required".equals(errorCode)) {
                errorMessage = "cancelled";
            } else {
                errorMessage = "google_auth_failed";
            }
        } else {
            errorMessage = "google_auth_failed";
        }

        // Redirect to login with error parameter (no internal details)
        String redirectUrl = "/login?error=" + errorMessage;
        response.sendRedirect(redirectUrl);
    }
}
