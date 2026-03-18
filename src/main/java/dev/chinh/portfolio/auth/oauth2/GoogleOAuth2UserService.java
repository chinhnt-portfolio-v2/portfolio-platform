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

        // Find or create user by email
        User user = findOrCreateUser(email);

        return new GoogleOAuth2UserPrincipal(user, attributes);
    }

    /**
     * Find existing user by email or create new one with GOOGLE provider.
     */
    private User findOrCreateUser(String email) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            // User exists - ensure provider is GOOGLE (or LOCAL with same email is fine)
            User user = existingUser.get();
            // Update provider to GOOGLE if it was LOCAL (user linked their Google account)
            if (user.getProvider() == AuthProvider.LOCAL) {
                user.setProvider(AuthProvider.GOOGLE);
                return userRepository.save(user);
            }
            return user;
        }

        // Create new user with GOOGLE provider
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setProvider(AuthProvider.GOOGLE);
        newUser.setRole(UserRole.USER);
        // passwordHash remains null for OAuth users

        return userRepository.save(newUser);
    }
}
