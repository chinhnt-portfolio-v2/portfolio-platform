package dev.chinh.portfolio.auth.oauth2;

import dev.chinh.portfolio.auth.jwt.JwtTokenProvider;
import dev.chinh.portfolio.user.User;
import dev.chinh.portfolio.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Value("${app.frontend.url:https://chinhnt-portfolio.vercel.app}")
    private String defaultFrontendUrl;

    public OAuth2AuthenticationSuccessHandler(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = authToken.getPrincipal();
        String provider = authToken.getAuthorizedClientRegistrationId();
        String providerId = oauth2User.getName();

        // Get redirect URL from session (set by OAuth2Controller)
        HttpSession session = request.getSession(false);
        String frontendUrl = defaultFrontendUrl;
        if (session != null) {
            String stored = (String) session.getAttribute("oauth2_redirect_uri");
            if (stored != null && !stored.isBlank()) {
                frontendUrl = stored;
            }
        }
        System.out.println("[OAuth2Success] Redirecting to: " + frontendUrl);

        // Get or create user
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, providerId);
        User user = existingUser.orElseGet(() -> createUserFromOAuth2(oauth2User, provider, providerId));

        // Generate JWT tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // Build redirect URL with tokens
        String redirectUrl = frontendUrl
                + "/?accessToken=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&refreshToken=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                + "&tokenType=Bearer";

        // Invalidate session after use
        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(redirectUrl);
    }

    private User createUserFromOAuth2(OAuth2User oauth2User, String provider, String providerId) {
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");

        User user = new User();
        user.setEmail(email != null ? email : providerId + "@" + provider + ".oauth");
        user.setName(name != null ? name : "User");
        user.setProvider(provider);
        user.setProviderId(providerId);
        if (picture != null) user.setImageUrl(picture);
        user.setRole("USER");
        user.setIsActive(true);

        return userRepository.save(user);
    }
}
