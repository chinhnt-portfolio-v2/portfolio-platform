package dev.chinh.portfolio.auth.oauth2;

import dev.chinh.portfolio.auth.dto.AuthResponse;
import dev.chinh.portfolio.auth.jwt.JwtService;
import dev.chinh.portfolio.auth.session.Session;
import dev.chinh.portfolio.auth.session.SessionService;
import dev.chinh.portfolio.auth.user.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Authentication success handler for OAuth2 login.
 * Generates JWT tokens and returns them to the client.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final JwtService jwtService;
    private final SessionService sessionService;

    public OAuth2AuthenticationSuccessHandler(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        log.info("OAuth2 authentication success! Authentication: {}", authentication);

        // Get the OAuth2 user from authentication
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        log.info("OAuth2 user principal: {}", oAuth2User);
        log.info("OAuth2 user attributes: {}", oAuth2User.getAttributes());

        // Extract our custom User entity from the principal
        if (!(oAuth2User instanceof GoogleOAuth2UserPrincipal googlePrincipal)) {
            log.error("OAuth2 user is not GoogleOAuth2UserPrincipal, redirecting to error");
            response.sendRedirect("/login?error=oauth2_failure");
            return;
        }

        User user = googlePrincipal.getUser();
        log.info("Google user from principal: {}", user.getEmail());

        // Generate JWT access token
        String accessToken = jwtService.generateAccessToken(user);
        log.info("Generated access token for user: {}", user.getEmail());

        // Create session with refresh token
        Session session = sessionService.createSession(user);
        log.info("Created session for user: {}", user.getEmail());

        // Return tokens in same format as password login
        AuthResponse authResponse = AuthResponse.of(accessToken, session.getRefreshToken());

        // Redirect to frontend with tokens as query parameters
        String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "https://portfolio-fe-omega-gold.vercel.app");
        String redirectUrl = String.format("%s?accessToken=%s&refreshToken=%s&tokenType=Bearer",
                frontendUrl,
                authResponse.accessToken(),
                authResponse.refreshToken());

        log.info("Redirecting to frontend with tokens");
        response.sendRedirect(redirectUrl);
    }
}
