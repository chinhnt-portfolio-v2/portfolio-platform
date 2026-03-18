package dev.chinh.portfolio.auth.jwt;

import dev.chinh.portfolio.auth.config.JwtConfig;
import dev.chinh.portfolio.auth.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtConfig jwtConfig;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtService(JwtConfig jwtConfig) throws Exception {
        this.jwtConfig = jwtConfig;
        KeyPair keyPair = loadOrGenerateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
    }

    private KeyPair loadOrGenerateKeyPair() throws Exception {
        String keyPath = jwtConfig.getKeyPath().replace("classpath:", "");
        Path privateKeyPath = Path.of("src/main/resources", keyPath);

        // Try to load existing keys
        if (Files.exists(privateKeyPath)) {
            log.info("Loading existing JWT keys from {}", privateKeyPath);
            PrivateKey privateKey = loadPrivateKey(Files.readString(privateKeyPath));
            PublicKey publicKey = loadPublicKey(privateKey);
            return new KeyPair(publicKey, privateKey);
        }

        // Generate new keys if not exist (for production or first run)
        log.warn("JWT keys not found - generating new key pair. This should NOT happen in production!");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        // Save keys for next run
        try {
            Files.createDirectories(privateKeyPath.getParent());
            Files.writeString(privateKeyPath, encodePrivateKey(keyPair.getPrivate()));
            log.info("Generated and saved new JWT keys to {}", privateKeyPath);
        } catch (Exception e) {
            log.warn("Could not save JWT keys to file system: {}", e.getMessage());
        }

        return keyPair;
    }

    private String encodePrivateKey(PrivateKey privateKey) {
        byte[] encoded = privateKey.getEncoded();
        return "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(encoded) +
                "\n-----END PRIVATE KEY-----";
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String keyContent = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    private PublicKey loadPublicKey(PrivateKey privateKey) throws Exception {
        String keyPath = "keys/public.pem";
        Path path = Path.of("src/main/resources", keyPath);

        if (!Files.exists(path)) {
            // Generate public key from private key if public key doesn't exist
            log.warn("Public key not found - deriving from private key");
            RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;
            return extractPublicFromPrivate(rsaPrivateKey);
        }

        String pem = Files.readString(path);
        return parsePublicKey(pem);
    }

    private PublicKey extractPublicFromPrivate(RSAPrivateKey privateKey) throws Exception {
        // Extract public key parameters from private key
        BigInteger modulus = privateKey.getModulus();
        BigInteger publicExponent = BigInteger.valueOf(65537L); // Standard RSA exponent

        java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(modulus, publicExponent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private PublicKey parsePublicKey(String pem) throws Exception {
        String keyContent = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtConfig.getAccessTokenTtlMinutes() * 60L);

        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // Unique JWT ID for each token
                .subject(user.getId().toString())
                .issuer(jwtConfig.getIssuer())
                .claim("type", "access")
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtConfig.getRefreshTokenTtlDays() * 24L * 60L * 60L);

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(jwtConfig.getIssuer())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        Claims claims = extractClaims(token);
        return claims.get("email", String.class);
    }

    public String getTokenType(String token) {
        Claims claims = extractClaims(token);
        return claims.get("type", String.class);
    }
}
