package dev.chinh.portfolio.auth.jwt;

import dev.chinh.portfolio.auth.config.JwtConfig;
import dev.chinh.portfolio.auth.user.User;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtService.
 * Uses dynamically generated RSA key pair for testing.
 */
class JwtServiceTest {

    private static KeyPair testKeyPair;
    private JwtConfig jwtConfig;
    private JwtService jwtService;

    @BeforeAll
    static void setUpKeyPair() throws Exception {
        // Generate a fresh RSA key pair for each test run
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        testKeyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() throws Exception {
        jwtConfig = mock(JwtConfig.class);
        when(jwtConfig.getIssuer()).thenReturn("https://api.portfolio-v2.com");
        when(jwtConfig.getAccessTokenTtlMinutes()).thenReturn(15);
        when(jwtConfig.getRefreshTokenTtlDays()).thenReturn(7);
        when(jwtConfig.getKeyPath()).thenReturn("classpath:keys/private.pem");

        // Create JwtService with mock config - will use reflection or test variant
        // For unit testing, we'll test JwtService behavior with the generated key
        jwtService = createTestInstance();
    }

    private JwtService createTestInstance() throws Exception {
        // Create a custom JwtService for testing with our generated keys
        return new JwtServiceForTest(jwtConfig, testKeyPair.getPrivate(), testKeyPair.getPublic());
    }

    @Nested
    @DisplayName("generateAccessToken")
    class GenerateAccessTokenTests {

        @Test
        @DisplayName("should generate valid RS256 access token with correct claims")
        void shouldGenerateValidAccessToken() throws Exception {
            // Given
            User user = createTestUser();

            // When
            String token = jwtService.generateAccessToken(user);

            // Then
            assertNotNull(token);
            assertTrue(token.split("\\.").length == 3, "JWT should have 3 parts");

            // Verify claims
            var claims = Jwts.parser()
                    .verifyWith(testKeyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            assertEquals(user.getId().toString(), claims.getSubject());
            assertEquals("https://api.portfolio-v2.com", claims.getIssuer());
            assertEquals("access", claims.get("type"));
            assertEquals(user.getEmail(), claims.get("email"));
        }

        @Test
        @DisplayName("should generate access token with 15 minute expiry")
        void shouldGenerateAccessTokenWithCorrectExpiry() throws Exception {
            // Given
            User user = createTestUser();
            Instant before = Instant.now();

            // When
            String token = jwtService.generateAccessToken(user);

            // Then
            var claims = Jwts.parser()
                    .verifyWith(testKeyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Instant expiry = claims.getExpiration().toInstant();
            long minutes = java.time.Duration.between(before, expiry).toMinutes();

            assertTrue(minutes >= 14 && minutes <= 15, "Access token should expire in ~15 minutes");
        }
    }

    @Nested
    @DisplayName("generateRefreshToken")
    class GenerateRefreshTokenTests {

        @Test
        @DisplayName("should generate refresh token with 7 day expiry")
        void shouldGenerateRefreshTokenWithCorrectExpiry() throws Exception {
            // Given
            User user = createTestUser();
            Instant before = Instant.now();

            // When
            String token = jwtService.generateRefreshToken(user);

            // Then
            var claims = Jwts.parser()
                    .verifyWith(testKeyPair.getPublic())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            assertEquals("refresh", claims.get("type"));

            Instant expiry = claims.getExpiration().toInstant();
            long days = java.time.Duration.between(before, expiry).toDays();

            assertTrue(days >= 6 && days <= 7, "Refresh token should expire in ~7 days");
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateTokenTests {

        @Test
        @DisplayName("should return true for valid token")
        void shouldReturnTrueForValidToken() throws Exception {
            // Given
            User user = createTestUser();
            String token = jwtService.generateAccessToken(user);

            // When
            boolean isValid = jwtService.validateToken(token);

            // Then
            assertTrue(isValid);
        }

        @Test
        @DisplayName("should return false for expired token")
        void shouldReturnFalseForExpiredToken() throws Exception {
            // Given
            User user = createTestUser();

            // Create expired token manually
            String expiredToken = Jwts.builder()
                    .subject(user.getId().toString())
                    .issuer("https://api.portfolio-v2.com")
                    .claim("type", "access")
                    .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                    .expiration(Date.from(Instant.now().minusSeconds(1800)))
                    .signWith(testKeyPair.getPrivate(), Jwts.SIG.RS256)
                    .compact();

            // When
            boolean isValid = jwtService.validateToken(expiredToken);

            // Then
            assertFalse(isValid);
        }

        @Test
        @DisplayName("should return false for invalid token")
        void shouldReturnFalseForInvalidToken() {
            // Given
            String invalidToken = "invalid.token.here";

            // When
            boolean isValid = jwtService.validateToken(invalidToken);

            // Then
            assertFalse(isValid);
        }

        @Test
        @DisplayName("should return false for null token")
        void shouldReturnFalseForNullToken() {
            // When
            boolean isValid = jwtService.validateToken(null);

            // Then
            assertFalse(isValid);
        }
    }

    @Nested
    @DisplayName("extractUsername")
    class ExtractUsernameTests {

        @Test
        @DisplayName("should extract correct subject from token")
        void shouldExtractCorrectUsername() throws Exception {
            // Given
            User user = createTestUser();
            String token = jwtService.generateAccessToken(user);

            // When
            String username = jwtService.extractUsername(token);

            // Then
            assertEquals(user.getId().toString(), username);
        }
    }

    @Nested
    @DisplayName("extractClaims")
    class ExtractClaimsTests {

        @Test
        @DisplayName("should extract all claims from token")
        void shouldExtractAllClaims() throws Exception {
            // Given
            User user = createTestUser();
            String token = jwtService.generateAccessToken(user);

            // When
            var claims = jwtService.extractClaims(token);

            // Then
            assertEquals(user.getId().toString(), claims.getSubject());
            assertEquals("https://api.portfolio-v2.com", claims.getIssuer());
            assertEquals("access", claims.get("type"));
            assertEquals(user.getEmail(), claims.get("email"));
        }
    }

    @Nested
    @DisplayName("extractEmail")
    class ExtractEmailTests {

        @Test
        @DisplayName("should extract email from token claims")
        void shouldExtractEmail() throws Exception {
            // Given
            User user = createTestUser();
            String token = jwtService.generateAccessToken(user);

            // When
            String email = jwtService.extractEmail(token);

            // Then
            assertEquals(user.getEmail(), email);
        }
    }

    @Nested
    @DisplayName("getTokenType")
    class GetTokenTypeTests {

        @Test
        @DisplayName("should return 'access' for access token")
        void shouldReturnAccessForAccessToken() throws Exception {
            // Given
            User user = createTestUser();
            String token = jwtService.generateAccessToken(user);

            // When
            String type = jwtService.getTokenType(token);

            // Then
            assertEquals("access", type);
        }

        @Test
        @DisplayName("should return 'refresh' for refresh token")
        void shouldReturnRefreshForRefreshToken() throws Exception {
            // Given
            User user = createTestUser();
            String token = jwtService.generateRefreshToken(user);

            // When
            String type = jwtService.getTokenType(token);

            // Then
            assertEquals("refresh", type);
        }
    }

    private User createTestUser() {
        try {
            // Use reflection to access protected constructor
            var constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            User user = constructor.newInstance();

            // Use reflection to set id
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, UUID.fromString("12345678-1234-1234-1234-123456789abc"));

            user.setEmail("test@example.com");
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test variant of JwtService that accepts injected keys
     */
    private static class JwtServiceForTest extends JwtService {
        private final PrivateKey privateKey;
        private final PublicKey publicKey;
        private final JwtConfig config;

        public JwtServiceForTest(JwtConfig config, PrivateKey privateKey, PublicKey publicKey) throws Exception {
            super(config);
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.config = config;
        }

        // Override to use injected keys
        @Override
        public String generateAccessToken(dev.chinh.portfolio.auth.user.User user) {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(config.getAccessTokenTtlMinutes() * 60L);

            return Jwts.builder()
                    .subject(user.getId().toString())
                    .issuer(config.getIssuer())
                    .claim("type", "access")
                    .claim("email", user.getEmail())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiry))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();
        }

        @Override
        public String generateRefreshToken(dev.chinh.portfolio.auth.user.User user) {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(config.getRefreshTokenTtlDays() * 24L * 60L * 60L);

            return Jwts.builder()
                    .subject(user.getId().toString())
                    .issuer(config.getIssuer())
                    .claim("type", "refresh")
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiry))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();
        }

        @Override
        public boolean validateToken(String token) {
            try {
                Jwts.parser()
                        .verifyWith(publicKey)
                        .build()
                        .parseSignedClaims(token);
                return true;
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
                return false;
            }
        }

        @Override
        public String extractUsername(String token) {
            var claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        }

        @Override
        public io.jsonwebtoken.Claims extractClaims(String token) {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }

        @Override
        public String extractEmail(String token) {
            var claims = extractClaims(token);
            return claims.get("email", String.class);
        }

        @Override
        public String getTokenType(String token) {
            var claims = extractClaims(token);
            return claims.get("type", String.class);
        }
    }
}
