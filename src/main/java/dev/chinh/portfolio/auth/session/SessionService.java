package dev.chinh.portfolio.auth.session;

import dev.chinh.portfolio.auth.user.AuthProvider;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user sessions and refresh tokens.
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    // Refresh token TTL: 7 days (matches JWT config)
    private static final int REFRESH_TOKEN_TTL_DAYS = 7;

    public SessionService(SessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Find or create an OAuth user by email and provider ID.
     */
    @Transactional
    public User findOrCreateOAuthUser(String email, String name, String providerId, String provider) {
        AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase());

        // Try by provider + providerId first
        Optional<User> byProvider = userRepository.findByProviderAndProviderId(authProvider, providerId);
        if (byProvider.isPresent()) {
            User user = byProvider.get();
            if (name != null && !name.isBlank() && user.getName() == null) {
                user.setName(name);
                return userRepository.save(user);
            }
            return user;
        }

        // Try by email
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            user.setProvider(authProvider);
            user.setProviderId(providerId);
            if (name != null && !name.isBlank()) user.setName(name);
            return userRepository.save(user);
        }

        // Create new user
        User newUser = new User();
        newUser.setEmail(email);
        if (name != null && !name.isBlank()) newUser.setName(name);
        newUser.setProvider(authProvider);
        newUser.setProviderId(providerId);
        newUser.setRole(UserRole.USER);
        return userRepository.save(newUser);
    }

    /**
     * Create a new session with refresh token for the user.
     * @param user the user to create session for
     * @return the created session
     */
    @Transactional
    public Session createSession(User user) {
        // Generate opaque refresh token (UUID)
        String refreshToken = UUID.randomUUID().toString();

        // Calculate expiry
        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS);

        // Create session
        Session session = new Session();
        session.setUserId(user.getId());
        session.setRefreshToken(refreshToken);
        session.setExpiresAt(expiresAt);
        session.setRevoked(false);

        return sessionRepository.save(session);
    }

    /**
     * Validate a refresh token.
     * @param refreshToken the refresh token to validate
     * @return optional containing the session if valid
     */
    public Optional<Session> validateRefreshToken(String refreshToken) {
        Optional<Session> sessionOpt = sessionRepository.findByRefreshToken(refreshToken);

        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        Session session = sessionOpt.get();

        // Check if revoked
        if (session.isRevoked()) {
            return Optional.empty();
        }

        // Check if expired
        if (session.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        return Optional.of(session);
    }

    /**
     * Revoke a session (for logout).
     * @param refreshToken the refresh token to revoke
     * @return true if session was revoked
     */
    @Transactional
    public boolean revokeSession(String refreshToken) {
        Optional<Session> sessionOpt = sessionRepository.findByRefreshToken(refreshToken);

        if (sessionOpt.isEmpty()) {
            return false;
        }

        Session session = sessionOpt.get();
        session.setRevoked(true);
        sessionRepository.save(session);

        return true;
    }

    /**
     * Revoke all sessions for a user.
     * @param userId the user ID
     */
    @Transactional
    public void revokeAllUserSessions(UUID userId) {
        var sessions = sessionRepository.findAllByUserIdAndRevokedFalse(userId);
        for (Session session : sessions) {
            session.setRevoked(true);
        }
        sessionRepository.saveAll(sessions);
    }

    /**
     * Delete session for a user (used for logout).
     * @param userId the user ID
     */
    @Transactional
    public void deleteSession(UUID userId) {
        sessionRepository.deleteByUserId(userId);
    }

    /**
     * Delete a single session by identity (used for refresh-token rotation).
     * Unlike {@link #deleteSession(UUID)}, this leaves the user's other sessions
     * (e.g. web app vs MCP, or multiple devices) intact so they do not evict
     * each other on refresh.
     * @param session the session to delete
     */
    @Transactional
    public void deleteSession(Session session) {
        sessionRepository.deleteById(session.getId());
    }
}
