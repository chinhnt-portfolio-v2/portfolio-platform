package dev.chinh.portfolio.auth.jwt;

import dev.chinh.portfolio.auth.config.JwtConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JwksController.
 * Tests that the JWKS endpoint returns valid JWKS format.
 */
@ExtendWith(MockitoExtension.class)
class JwksControllerTest {

    @Mock
    private JwtConfig jwtConfig;

    @InjectMocks
    private JwksController jwksController;

    @Nested
    @DisplayName("GET /api/v1/.well-known/jwks.json")
    class GetJwksTests {

        @Test
        @DisplayName("should return JWKS with valid format")
        void shouldReturnValidJwksFormat() throws Exception {
            ResponseEntity<Map<String, Object>> response = jwksController.getJwks();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("keys");

            @SuppressWarnings("unchecked")
            Map<String, Object> key = ((java.util.List<Map<String, Object>>) response.getBody().get("keys")).get(0);
            assertThat(key.get("kty")).isEqualTo("RSA");
            assertThat(key.get("use")).isEqualTo("sig");
            assertThat(key.get("alg")).isEqualTo("RS256");
            assertThat(key.get("kid")).isEqualTo("portfolio-rsa-key");
            assertThat(key).containsKey("n");
            assertThat(key).containsKey("e");
        }

        @Test
        @DisplayName("should return non-empty modulus and exponent")
        void shouldReturnNonEmptyModulusAndExponent() throws Exception {
            ResponseEntity<Map<String, Object>> response = jwksController.getJwks();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> key = ((java.util.List<Map<String, Object>>) response.getBody().get("keys")).get(0);
            assertThat(key.get("n")).isNotNull();
            assertThat(key.get("e")).isNotNull();
            assertThat(key.get("n").toString()).isNotEmpty();
            assertThat(key.get("e").toString()).isNotEmpty();
        }

        @Test
        @DisplayName("should return proper JWKS structure conforming to RFC 7517")
        void jwksShouldConformToRfc7517() throws Exception {
            ResponseEntity<Map<String, Object>> response = jwksController.getJwks();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("keys");

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> keys = (java.util.List<Map<String, Object>>) response.getBody().get("keys");
            assertThat(keys).hasSize(1);

            Map<String, Object> key = keys.get(0);
            // RFC 7517 required fields
            assertThat(key).containsEntry("kty", "RSA");
            assertThat(key).containsKey("n");
            assertThat(key).containsKey("e");
            // Recommended fields
            assertThat(key).containsEntry("use", "sig");
            assertThat(key).containsEntry("alg", "RS256");
            assertThat(key).containsKey("kid");
        }
    }
}
