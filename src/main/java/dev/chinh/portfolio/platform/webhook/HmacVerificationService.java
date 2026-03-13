package dev.chinh.portfolio.platform.webhook;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service for HMAC-SHA256 signature verification.
 * Used for GitHub webhook payload verification.
 */
@Service
public class HmacVerificationService {

    private static final Logger log = LoggerFactory.getLogger(HmacVerificationService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @PostConstruct
    void init() {
        try {
            Mac.getInstance(HMAC_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize HMAC algorithm: " + HMAC_ALGORITHM, e);
        }
    }

    /**
     * Validates HMAC-SHA256 signature of a payload.
     *
     * @param payload   The raw request body
     * @param signature The signature from X-Hub-Signature-256 header (format: sha256={hex})
     * @param secret    The webhook secret
     * @return true if signature is valid, false otherwise
     */
    public boolean isValid(String payload, String signature, String secret) {
        // Check secret first - if blank, skip verification (dev mode)
        if (secret == null || secret.isBlank()) {
            return true;
        }
        // Then verify signature format
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }

        String providedSig = signature.substring(7); // strip "sha256="
        try {
            String computedSig = calculateHmacSha256(payload, secret);
            return MessageDigest.isEqual(
                providedSig.getBytes(StandardCharsets.UTF_8),
                computedSig.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.warn("HMAC verification failed: {}", e.getMessage());
            return false;
        }
    }

    private String calculateHmacSha256(String data, String secret) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
