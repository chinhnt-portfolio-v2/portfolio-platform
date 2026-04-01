package dev.chinh.portfolio.auth;

import dev.chinh.portfolio.auth.jwt.JwtService;
import dev.chinh.portfolio.auth.resolver.CurrentUserArgumentResolver;
import dev.chinh.portfolio.auth.session.SessionService;
import dev.chinh.portfolio.auth.user.AuthProvider;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRole;
import dev.chinh.portfolio.auth.user.UserService;
import dev.chinh.portfolio.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for GET /api/v1/auth/me (AuthController.getCurrentUser).
 * Uses standalone MockMvc setup to control the HandlerMethodArgumentResolver.
 */
class AuthMeControllerTest {

    private final UserService userService = mock(UserService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final CurrentUserArgumentResolver mockResolver = mock(CurrentUserArgumentResolver.class);

    private MockMvc mockMvc;

    private static final UUID TEST_USER_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService, jwtService, sessionService))
                .setCustomArgumentResolvers(mockResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static User createTestUser(String email, UUID id) {
        try {
            var ctor = User.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            User u = ctor.newInstance();
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u, id);
            u.setEmail(email);
            u.setName("Chinh Phạm");
            u.setProvider(AuthProvider.LOCAL);
            u.setRole(UserRole.USER);
            return u;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/me")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return 200 with user profile when authenticated")
        void shouldReturnUserProfileWhenAuthenticated() throws Exception {
            when(mockResolver.supportsParameter(any(MethodParameter.class))).thenReturn(true);
            when(mockResolver.resolveArgument(any(), any(), any(), any())).thenReturn(TEST_USER_ID);

            User user = createTestUser("chinh@example.com", TEST_USER_ID);
            when(userService.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                    .andExpect(jsonPath("$.email").value("chinh@example.com"))
                    .andExpect(jsonPath("$.name").value("Chinh Phạm"))
                    .andExpect(jsonPath("$.provider").value("LOCAL"))
                    .andExpect(jsonPath("$.role").value("USER"));

            verify(userService).findById(TEST_USER_ID);
        }

        @Test
        @DisplayName("should return 200 with Google provider when user signed up via OAuth")
        void shouldReturnGoogleProviderForOAuthUser() throws Exception {
            when(mockResolver.supportsParameter(any(MethodParameter.class))).thenReturn(true);
            when(mockResolver.resolveArgument(any(), any(), any(), any())).thenReturn(TEST_USER_ID);

            User user = createTestUser("chinh@gmail.com", TEST_USER_ID);
            user.setProvider(AuthProvider.GOOGLE);
            user.setRole(UserRole.OWNER);
            when(userService.findById(TEST_USER_ID)).thenReturn(Optional.of(user));

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider").value("GOOGLE"))
                    .andExpect(jsonPath("$.role").value("OWNER"));
        }

        @Test
        @DisplayName("should return 404 when user not found in database")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(mockResolver.supportsParameter(any(MethodParameter.class))).thenReturn(true);
            when(mockResolver.resolveArgument(any(), any(), any(), any())).thenReturn(TEST_USER_ID);
            when(userService.findById(TEST_USER_ID)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
                    .andExpect(jsonPath("$.error.message").value("User not found"));
        }

        @Test
        @DisplayName("should propagate exception when resolver throws (no authenticated user)")
        void shouldPropagateExceptionWhenNotAuthenticated() throws Exception {
            when(mockResolver.supportsParameter(any(MethodParameter.class))).thenReturn(true);
            when(mockResolver.resolveArgument(any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("No authenticated user"));

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isInternalServerError());

            verify(userService, never()).findById(any());
        }
    }
}
