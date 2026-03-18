package dev.chinh.portfolio.auth.user;

import dev.chinh.portfolio.auth.dto.LoginRequest;
import dev.chinh.portfolio.auth.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("should create user with bcrypt hash (cost factor 12+)")
        void shouldCreateUserWithBcryptHash() {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "Password1");
            when(userRepository.existsByEmail(request.email())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                // Simulate ID assignment
                try {
                    var idField = User.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(user, UUID.randomUUID());
                } catch (Exception e) {
                    // Ignore
                }
                return user;
            });

            // When
            User result = userService.register(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("test@example.com");
            assertThat(result.getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(result.getRole()).isEqualTo(UserRole.USER);
            assertThat(result.getPasswordHash()).isNotNull();

            // Verify bcrypt cost factor
            // BCrypt hash format: $2a$12$...
            String hash = result.getPasswordHash();
            assertThat(hash).startsWith("$2a$12$");
        }

        @Test
        @DisplayName("should throw EMAIL_ALREADY_EXISTS when email exists")
        void shouldThrowWhenEmailExists() {
            // Given
            RegisterRequest request = new RegisterRequest("existing@example.com", "Password1");
            when(userRepository.existsByEmail(request.email())).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(UserService.EmailAlreadyExistsException.class)
                    .hasMessage("Email already registered");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should save user with encoded password")
        void shouldSaveUserWithEncodedPassword() {
            // Given
            RegisterRequest request = new RegisterRequest("test@example.com", "MyPassword1");
            when(userRepository.existsByEmail(request.email())).thenReturn(false);

            User savedUser = new User();
            savedUser.setEmail(request.email());
            savedUser.setPasswordHash("encoded-hash");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // When
            userService.register(request);

            // Then
            verify(userRepository).save(argThat(user ->
                    user.getEmail().equals(request.email()) &&
                    user.getPasswordHash() != null &&
                    user.getProvider() == AuthProvider.LOCAL
            ));
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmailTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            // Given
            String email = "test@example.com";
            User user = new User();
            user.setEmail(email);
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // When
            Optional<User> result = userService.findByEmail(email);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            // Given
            String email = "notfound@example.com";
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.findByEmail(email);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByEmail")
    class ExistsByEmailTests {

        @Test
        @DisplayName("should return true when email exists")
        void shouldReturnTrueWhenExists() {
            // Given
            String email = "exists@example.com";
            when(userRepository.existsByEmail(email)).thenReturn(true);

            // When
            boolean result = userService.existsByEmail(email);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when email does not exist")
        void shouldReturnFalseWhenNotExists() {
            // Given
            String email = "notexists@example.com";
            when(userRepository.existsByEmail(email)).thenReturn(false);

            // When
            boolean result = userService.existsByEmail(email);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("validateCredentials")
    class ValidateCredentialsTests {

        @Test
        @DisplayName("should return user for valid credentials")
        void shouldReturnUserForValidCredentials() {
            // Given
            String email = "test@example.com";
            String password = "Password1";
            LoginRequest request = new LoginRequest(email, password);

            User user = createTestUser(email, password);
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // When
            Optional<User> result = userService.validateCredentials(request);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("should return empty for invalid password")
        void shouldReturnEmptyForInvalidPassword() {
            // Given
            String email = "test@example.com";
            LoginRequest request = new LoginRequest(email, "WrongPassword");

            User user = createTestUser(email, "Password1");
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // When
            Optional<User> result = userService.validateCredentials(request);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for non-existent email (timing-safe)")
        void shouldReturnEmptyForNonExistentEmail() {
            // Given
            String email = "notfound@example.com";
            LoginRequest request = new LoginRequest(email, "Password1");

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.validateCredentials(request);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty for OAuth user (no password)")
        void shouldReturnEmptyForOAuthUser() {
            // Given
            String email = "oauth@example.com";
            LoginRequest request = new LoginRequest(email, "Password1");

            User user = createTestUser(email, null);
            user.setProvider(AuthProvider.GOOGLE);
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            // When
            Optional<User> result = userService.validateCredentials(request);

            // Then
            assertThat(result).isEmpty();
        }
    }

    private User createTestUser(String email, String rawPassword) {
        User user = new User();
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());
        } catch (Exception e) {
            // Ignore
        }
        user.setEmail(email);
        if (rawPassword != null) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(UserRole.USER);
        return user;
    }
}
