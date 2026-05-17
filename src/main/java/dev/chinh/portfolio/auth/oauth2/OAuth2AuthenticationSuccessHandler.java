package dev.chinh.portfolio.auth.oauth2;

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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final SessionService sessionService;
    private static final String STATE_SEPARATOR = "|";

    @Value("${app.frontend.url:https://wallet.chinhnt.xyz}")
    private String defaultFrontendUrl;

    private static final Set<String> ALLOWED_REDIRECT_DOMAINS = Set.of(
            "wallet.chinhnt.xyz",
            "vault.chinhnt.xyz",
            "ledger.chinhnt.xyz",
            "codebin.chinhnt.xyz",
            "portfolio.chinhnt.xyz",
            "devquiz.chinhnt.xyz",
            "quiz.chinhnt.xyz",
            "chinh.dev",
            "wallet.chinh.dev",
            "localhost",
            "walletapp://"
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

        // Decode redirect URL from state param (frontend encoded it as base64)
        String state = request.getParameter("state");
        String targetUrl = resolveRedirectUriFromState(state);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        Session session = sessionService.createSession(user);
        String refreshToken = session.getRefreshToken();

        // Redirect to frontend with tokens in URL
        String redirectUrl = targetUrl
                + "?accessToken=" + accessToken
                + "&refreshToken=" + refreshToken
                + "&tokenType=Bearer";

        response.sendRedirect(redirectUrl);
    }

    /**
     * Decodes redirect URL from base64 state parameter.
     * State format: base64(redirectUrl|timestamp)
     */
    private String resolveRedirectUriFromState(String state) {
        if (state == null || state.isBlank()) {
            return defaultFrontendUrl;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            int sepIndex = decoded.lastIndexOf(STATE_SEPARATOR);
            if (sepIndex > 0) {
                String redirectUrl = decoded.substring(0, sepIndex);
                return validateRedirectUrl(redirectUrl) ? redirectUrl : defaultFrontendUrl;
            }
            return validateRedirectUrl(decoded) ? decoded : defaultFrontendUrl;
        } catch (Exception e) {
            return defaultFrontendUrl;
        }
    }

    private boolean validateRedirectUrl(String url) {
        return url != null && ALLOWED_REDIRECT_DOMAINS.stream()
                .anyMatch(url::contains);
    }
}
