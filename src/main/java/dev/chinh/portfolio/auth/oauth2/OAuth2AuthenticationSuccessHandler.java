package dev.chinh.portfolio.auth.oauth2;

import dev.chinh.portfolio.auth.jwt.JwtTokenProvider;
import dev.chinh.portfolio.user.User;
import dev.chinh.portfolio.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

        // Extract redirect URL from state parameter (set as base64 by frontend)
        String state = request.getParameter("state");
        String frontendUrl = decodeState(state);

        System.out.println("[OAuth2Success] state=" + state);
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

        response.sendRedirect(redirectUrl);
    }

    /**
     * Decodes redirect URL from base64 state token.
     * Format: base64(redirectUrl|PART_SEPARATOR|timestamp)
     */
    private String decodeState(String state) {
        if (state == null || state.isBlank()) {
            return defaultFrontendUrl;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            int sepIndex = decoded.lastIndexOf('|');
            if (sepIndex > 0) {
                return decoded.substring(0, sepIndex);
            }
            return decoded;
        } catch (Exception e) {
            System.out.println("[OAuth2Success] Failed to decode state: " + e.getMessage());
            return defaultFrontendUrl;
        }
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
