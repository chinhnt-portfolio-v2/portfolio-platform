package dev.chinh.portfolio.auth.user;

import dev.chinh.portfolio.auth.dto.LoginRequest;
import dev.chinh.portfolio.auth.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for user registration and authentication.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user with email and password.
     * @param request the registration request
     * @return the created user
     * @throws EmailAlreadyExistsException if the email is already registered
     */
    @Transactional
    public User register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        // Create new user
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(UserRole.USER);

        return userRepository.save(user);
    }

    /**
     * Find user by email.
     * @param email the email to search for
     * @return optional containing the user if found
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Check if email already exists.
     * @param email the email to check
     * @return true if email exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Validate login credentials.
     * Uses timing-safe comparison to prevent timing attacks.
     * @param request the login request
     * @return optional containing the user if credentials are valid
     */
    public Optional<User> validateCredentials(LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.email());

        if (userOpt.isEmpty()) {
            // Timing-safe: still perform password hash computation even when user not found
            passwordEncoder.encode(request.password());
            return Optional.empty();
        }

        User user = userOpt.get();

        // Only validate password for LOCAL provider users
        if (user.getProvider() != AuthProvider.LOCAL) {
            return Optional.empty();
        }

        // Timing-safe password comparison
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

    /**
     * Exception thrown when attempting to register with an existing email.
     */
    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String message) {
            super(message);
        }
    }

    /**
     * Find user by ID.
     * @param id the user ID
     * @return optional containing the user if found
     */
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }
}
