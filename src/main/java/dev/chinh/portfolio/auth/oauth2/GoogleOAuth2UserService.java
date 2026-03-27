package dev.chinh.portfolio.auth.oauth2;

import dev.chinh.portfolio.auth.user.AuthProvider;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Custom OAuth2UserService for handling Google OAuth2 user information.
 * Creates or retrieves user based on email from Google.
 */
@Service
public class GoogleOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public GoogleOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // Extract user info from Google
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = (String) attributes.get("email");
        String providerId = (String) attributes.get("sub");  // Google unique user ID

        // Find or create user by provider + providerId
        User user = findOrCreateUser(email, providerId);

        return new GoogleOAuth2UserPrincipal(user, attributes);
    }

    /**
     * Find existing user by provider + providerId, or create new one.
     */
    private User findOrCreateUser(String email, String providerId) {
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId);

        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        // No user with this providerId — check by email and link
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setProvider(AuthProvider.GOOGLE);
            user.setProviderId(providerId);
            return userRepository.save(user);
        }

        // Create new user
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setProvider(AuthProvider.GOOGLE);
        newUser.setProviderId(providerId);
        newUser.setRole(UserRole.USER);
        return userRepository.save(newUser);
    }
}
