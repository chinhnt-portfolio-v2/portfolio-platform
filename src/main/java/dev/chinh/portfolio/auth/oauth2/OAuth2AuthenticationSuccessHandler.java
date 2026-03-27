package dev.chinh.portfolio.auth.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.portfolio.auth.jwt.JwtService;
import dev.chinh.portfolio.auth.session.Session;
import dev.chinh.portfolio.auth.session.SessionService;
import dev.chinh.portfolio.auth.user.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final SessionService sessionService;

    @Value("${app.frontend.url:https://wallet-fe-two.vercel.app}")
    private String defaultFrontendUrl;

    private static final Set<String> ALLOWED_REDIRECT_DOMAINS = Set.of(
            "wallet-fe-two.vercel.app",
            "chinhnt-portfolio.vercel.app",
            "chinh.dev",
            "wallet.chinh.dev",
            "localhost"
    );

    public OAuth2AuthenticationSuccessHandler(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof GoogleOAuth2UserPrincipal googlePrincipal)) {
            response.sendRedirect("/login?error=oauth2_failure");
            return;
        }

        User user = googlePrincipal.getUser();

        // Read redirect_uri directly (frontend sends plain URL, not base64 state)
        // Validation result stored for potential future use (e.g. logging/auditing)
        String redirectUri = request.getParameter("redirect_uri");
        resolveAndValidateRedirectUri(redirectUri);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        Session session = sessionService.createSession(user);
        String refreshToken = session.getRefreshToken();

        // Return tokens as JSON body
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        new ObjectMapper().writeValue(response.getWriter(), Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer"
        ));
    }

    private String resolveAndValidateRedirectUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return defaultFrontendUrl;
        }
        boolean allowed = ALLOWED_REDIRECT_DOMAINS.stream()
                .anyMatch(uri::contains);
        return allowed ? uri : defaultFrontendUrl;
    }
}
