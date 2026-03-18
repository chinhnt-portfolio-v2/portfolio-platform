package dev.chinh.portfolio.auth.jwt;

import dev.chinh.portfolio.auth.config.JwtConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWKS endpoint for public key distribution (RFC 7517).
 * This endpoint serves the public key in standard JWKS format,
 * allowing external services (e.g., demo apps) to verify JWT signatures
 * without calling back to Platform BE.
 */
@RestController
@RequestMapping("/api/v1/.well-known")
public class JwksController {

    private final JwtConfig jwtConfig;

    public JwksController(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @GetMapping("/jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() throws Exception {
        RSAPublicKey publicKey = loadPublicKey();

        // Build JWKS response
        Map<String, Object> jwks = new LinkedHashMap<>();
        List<Map<String, Object>> keys = new ArrayList<>();

        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("kid", "portfolio-rsa-key");
        key.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()));
        key.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray()));

        keys.add(key);
        jwks.put("keys", keys);

        return ResponseEntity.ok(jwks);
    }

    private RSAPublicKey loadPublicKey() throws Exception {
        String keyPath = "keys/public.pem";
        InputStream is = getClass().getClassLoader().getResourceAsStream(keyPath);

        String publicKeyPEM;
        if (is != null) {
            publicKeyPEM = new String(is.readAllBytes());
        } else {
            // Fallback to file system for development
            publicKeyPEM = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources", keyPath));
        }

        // Parse PEM to get raw public key bytes
        String keyContent = publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
}
