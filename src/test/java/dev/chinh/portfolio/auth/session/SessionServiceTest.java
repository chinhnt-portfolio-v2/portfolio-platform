package dev.chinh.portfolio.auth.session;

import dev.chinh.portfolio.auth.user.AuthProvider;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionService.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(sessionRepository);
    }

    @Nested
    @DisplayName("createSession")
    class CreateSessionTests {

        @Test
        @DisplayName("should create session with opaque refresh token")
        void shouldCreateSessionWithRefreshToken() {
            // Given
            User user = createTestUser();
            when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
                Session session = invocation.getArgument(0);
                try {
                    var idField = Session.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(session, UUID.randomUUID());
                } catch (Exception e) {
                    // Ignore
                }
                return session;
            });

            // When
            Session result = sessionService.createSession(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(user.getId());
            assertThat(result.getRefreshToken()).isNotNull();
            assertThat(result.getRefreshToken()).hasSize(36); // UUID format
            assertThat(result.isRevoked()).isFalse();
            assertThat(result.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("should save session to repository")
        void shouldSaveSessionToRepository() {
            // Given
            User user = createTestUser();

            // When
            sessionService.createSession(user);

            // Then
            verify(sessionRepository).save(any(Session.class));
        }
    }

    @Nested
    @DisplayName("validateRefreshToken")
    class ValidateRefreshTokenTests {

        @Test
        @DisplayName("should return session for valid token")
        void shouldReturnSessionForValidToken() {
            // Given
            String refreshToken = "valid-token";
            Session session = createTestSession(refreshToken, false, Instant.now().plus(7, ChronoUnit.DAYS));
            when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(session));

            // When
            Optional<Session> result = sessionService.validateRefreshToken(refreshToken);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getRefreshToken()).isEqualTo(refreshToken);
        }

        @Test
        @DisplayName("should return empty for non-existent token")
        void shouldReturnEmptyForNonExistentToken() {
            // Given
            String refreshToken = "non-existent";
            when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.empty());

            // When
            Optional<Session> result = sessionService.validateRefreshToken(refreshToken);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for revoked token")
        void shouldReturnEmptyForRevokedToken() {
            // Given
            String refreshToken = "revoked-token";
            Session session = createTestSession(refreshToken, true, Instant.now().plus(7, ChronoUnit.DAYS));
            when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(session));

            // When
            Optional<Session> result = sessionService.validateRefreshToken(refreshToken);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for expired token")
        void shouldReturnEmptyForExpiredToken() {
            // Given
            String refreshToken = "expired-token";
            Session session = createTestSession(refreshToken, false, Instant.now().minus(1, ChronoUnit.DAYS));
            when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(session));

            // When
            Optional<Session> result = sessionService.validateRefreshToken(refreshToken);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("revokeSession")
    class RevokeSessionTests {

        @Test
        @DisplayName("should revoke session for valid token")
        void shouldRevokeSession() {
            // Given
            String refreshToken = "valid-token";
            Session session = createTestSession(refreshToken, false, Instant.now().plus(7, ChronoUnit.DAYS));
            when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(Session.class))).thenReturn(session);

            // When
            boolean result = sessionService.revokeSession(refreshToken);

            // Then
            assertThat(result).isTrue();
            verify(sessionRepository).save(argThat(s -> s.isRevoked()));
        }

        @Test
        @DisplayName("should return false for non-existent token")
        void shouldReturnFalseForNonExistentToken() {
            // Given
            String refreshToken = "non-existent";
            when(sessionRepository.findByRefreshToken(refreshToken)).thenReturn(Optional.empty());

            // When
            boolean result = sessionService.revokeSession(refreshToken);

            // Then
            assertThat(result).isFalse();
            verify(sessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeAllUserSessions")
    class RevokeAllUserSessionsTests {

        @Test
        @DisplayName("should revoke all active sessions for user")
        void shouldRevokeAllUserSessions() {
            // Given
            UUID userId = UUID.randomUUID();
            List<Session> sessions = List.of(
                    createTestSession("token1", false, Instant.now().plus(7, ChronoUnit.DAYS)),
                    createTestSession("token2", false, Instant.now().plus(7, ChronoUnit.DAYS))
            );
            when(sessionRepository.findAllByUserIdAndRevokedFalse(userId)).thenReturn(sessions);

            // When
            sessionService.revokeAllUserSessions(userId);

            // Then
            verify(sessionRepository).saveAll(argThat(list ->
                    ((List<Session>) list).stream().allMatch(Session::isRevoked)
            ));
        }
    }

    private User createTestUser() {
        try {
            var constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            User user = constructor.newInstance();

            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());

            user.setEmail("test@example.com");
            user.setProvider(AuthProvider.LOCAL);
            user.setRole(UserRole.USER);
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Session createTestSession(String refreshToken, boolean revoked, Instant expiresAt) {
        try {
            var constructor = Session.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Session session = constructor.newInstance();

            var idField = Session.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(session, UUID.randomUUID());

            session.setUserId(UUID.randomUUID());
            session.setRefreshToken(refreshToken);
            session.setRevoked(revoked);
            session.setExpiresAt(expiresAt);
            return session;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
