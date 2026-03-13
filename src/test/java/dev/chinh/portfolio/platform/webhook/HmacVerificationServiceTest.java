package dev.chinh.portfolio.platform.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacVerificationServiceTest {

    private HmacVerificationService service;

    @BeforeEach
    void setUp() {
        service = new HmacVerificationService();
    }

    @Test
    void isValid_validSignature_returnsTrue() throws Exception {
        String payload = "{\"test\":\"data\"}";
        String secret = "mysecret";
        String signature = "sha256=" + calculateHmac(payload, secret);

        assertThat(service.isValid(payload, signature, secret)).isTrue();
    }

    @Test
    void isValid_invalidSignature_returnsFalse() {
        String payload = "{\"test\":\"data\"}";
        String signature = "sha256=invalid";

        assertThat(service.isValid(payload, signature, "secret")).isFalse();
    }

    @Test
    void isValid_emptySecret_skipsVerification() {
        // In dev mode, empty secret skips verification
        assertThat(service.isValid("payload", "any-signature", "")).isTrue();
        assertThat(service.isValid("payload", "any-signature", null)).isTrue();
    }

    @Test
    void isValid_nullSignature_returnsFalse() {
        assertThat(service.isValid("payload", null, "secret")).isFalse();
    }

    @Test
    void isValid_wrongPrefix_returnsFalse() {
        assertThat(service.isValid("payload", "sha1=abc123", "secret")).isFalse();
        assertThat(service.isValid("payload", "abc123", "secret")).isFalse();
    }

    // Helper method to calculate HMAC for test verification
    private String calculateHmac(String data, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
            secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hmacBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
