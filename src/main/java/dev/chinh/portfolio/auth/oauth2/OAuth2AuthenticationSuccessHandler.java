package dev.chinh.portfolio.auth.oauth2;

import dev.chinh.portfolio.auth.dto.AuthResponse;
import dev.chinh.portfolio.auth.jwt.JwtService;
import dev.chinh.portfolio.auth.session.Session;
import dev.chinh.portfolio.auth.session.SessionService;
import dev.chinh.portfolio.auth.user.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final JwtService jwtService;
    private final SessionService sessionService;

    public OAuth2AuthenticationSuccessHandler(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // Get the OAuth2 user from authentication
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Extract our custom User entity from the principal
        if (!(oAuth2User instanceof GoogleOAuth2UserPrincipal googlePrincipal)) {
            response.sendRedirect("/login?error=oauth2_failure");
            return;
        }

        User user = googlePrincipal.getUser();

        // Generate JWT access token
        String accessToken = jwtService.generateAccessToken(user);

        // Create session with refresh token
        Session session = sessionService.createSession(user);

        // Return tokens in same format as password login
        AuthResponse authResponse = AuthResponse.of(accessToken, session.getRefreshToken());

        // Set content type to JSON
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Write JSON response
        response.getWriter().write(String.format(
                "{\"accessToken\":\"%s\",\"refreshToken\":\"%s\",\"tokenType\":\"Bearer\"}",
                authResponse.accessToken(),
                authResponse.refreshToken()
        ));
    }
}
