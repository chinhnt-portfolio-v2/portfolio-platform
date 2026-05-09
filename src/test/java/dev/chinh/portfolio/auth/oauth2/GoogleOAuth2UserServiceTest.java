package dev.chinh.portfolio.auth.oauth2;

import dev.chinh.portfolio.auth.user.AuthProvider;
import dev.chinh.portfolio.auth.user.User;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleOAuth2UserService")
class GoogleOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Spy
    private GoogleOAuth2UserService service = new GoogleOAuth2UserService(null);

    private static final String EMAIL = "user@gmail.com";
    private static final String PROVIDER_ID = "google-12345";

    @BeforeEach
    void setUp() {
        // Re-create spy with real userRepository
        service = spy(new GoogleOAuth2UserService(userRepository));
    }

    private OAuth2User makeOAuth2User(String email, String sub) {
        Map<String, Object> attrs = Map.of("email", email, "sub", sub);
        return new DefaultOAuth2User(Collections.emptyList(), attrs, "sub");
    }

    @Nested
    @DisplayName("loadUser - OAuth2 Flow")
    class LoadUserTests {

        @Test
        @DisplayName("should create new user when providerId does not exist")
        void shouldCreateNewUserWhenEmailNotExists() {
            doReturn(makeOAuth2User(EMAIL, PROVIDER_ID)).when(service).fetchFromProvider(any());
            when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            OAuth2User result = service.loadUser(mock(OAuth2UserRequest.class));

            assertThat(result).isInstanceOf(GoogleOAuth2UserPrincipal.class);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(saved.getProviderId()).isEqualTo(PROVIDER_ID);
            assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("should return existing user when providerId already exists")
        void shouldReturnExistingUserWhenEmailExists() {
            User existing = createUser(EMAIL, AuthProvider.GOOGLE, PROVIDER_ID);
            doReturn(makeOAuth2User(EMAIL, PROVIDER_ID)).when(service).fetchFromProvider(any());
            when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID)).thenReturn(Optional.of(existing));

            OAuth2User result = service.loadUser(mock(OAuth2UserRequest.class));

            assertThat(result).isInstanceOf(GoogleOAuth2UserPrincipal.class);
            assertThat(((GoogleOAuth2UserPrincipal) result).getUser().getEmail()).isEqualTo(EMAIL);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should update provider to GOOGLE when LOCAL user links Google account")
        void shouldUpdateProviderToGoogleWhenLocalUserLinksAccount() {
            User localUser = createUser("local@gmail.com", AuthProvider.LOCAL, null);
            doReturn(makeOAuth2User("local@gmail.com", PROVIDER_ID)).when(service).fetchFromProvider(any());
            when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail("local@gmail.com")).thenReturn(Optional.of(localUser));
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            service.loadUser(mock(OAuth2UserRequest.class));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(captor.getValue().getProviderId()).isEqualTo(PROVIDER_ID);
        }

        @Test
        @DisplayName("should extract email and google ID from OAuth2 attributes")
        void shouldExtractEmailAndGoogleIdFromAttributes() {
            doReturn(makeOAuth2User(EMAIL, PROVIDER_ID)).when(service).fetchFromProvider(any());
            when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            OAuth2User result = service.loadUser(mock(OAuth2UserRequest.class));

            assertThat(((GoogleOAuth2UserPrincipal) result).getUser().getEmail()).isEqualTo(EMAIL);
        }
    }

    private User createUser(String email, AuthProvider provider, String providerId) {
        User user = new User();
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.randomUUID());
        } catch (Exception ignored) {}
        user.setEmail(email);
        user.setProvider(provider);
        user.setProviderId(providerId);
        user.setRole(UserRole.USER);
        return user;
    }
}
