package dev.chinh.portfolio.shared.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimitService}.
 *
 * <p>Three tiers are tested:
 * <ul>
 *   <li>General: 100 req / min per IP</li>
 *   <li>Contact: 3 submissions / day per IP</li>
 *   <li>AI: no-op (always returns true — Phase 2 placeholder)</li>
 * </ul>
 */
class RateLimitServiceTest {

    private final RateLimitService service = new RateLimitService();

    // ── General tier ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("General tier (100 req / min)")
    class GeneralTierTests {

        @Test
        @DisplayName("should allow requests up to the limit")
        void allowWithinLimit() {
            String ip = "192.168.1.1";
            for (int i = 0; i < 100; i++) {
                assertThat(service.tryGeneral(ip))
                        .as("request %d should be allowed", i + 1)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("should reject requests exceeding the limit")
        void rejectOverLimit() {
            String ip = "192.168.1.2";
            // Exhaust the bucket
            for (int i = 0; i < 100; i++) {
                service.tryGeneral(ip);
            }
            // Next one should be blocked
            assertThat(service.tryGeneral(ip)).isFalse();
        }

        @Test
        @DisplayName("each IP gets its own independent bucket")
        void independentBuckets() {
            String ip1 = "10.0.0.1";
            String ip2 = "10.0.0.2";
            // Exhaust ip1
            for (int i = 0; i < 100; i++) {
                service.tryGeneral(ip1);
            }
            // ip2 should still work
            assertThat(service.tryGeneral(ip2)).isTrue();
        }

        @Test
        @DisplayName("should report remaining tokens correctly")
        void reportRemaining() {
            String ip = "10.0.0.3";
            assertThat(service.getGeneralRemaining(ip)).isEqualTo(100);
            service.tryGeneral(ip);
            assertThat(service.getGeneralRemaining(ip)).isEqualTo(99);
        }

        @Test
        @DisplayName("should return remaining tokens for unseen IP (not in map)")
        void remainingForUnseenIp_returnsFullCapacity() {
            assertThat(service.getGeneralRemaining("255.255.255.255")).isEqualTo(100);
        }
    }

    // ── Contact tier ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Contact tier (3 submissions / day)")
    class ContactTierTests {

        @Test
        @DisplayName("should allow exactly 3 submissions")
        void allowThreeSubmissions() {
            String ip = "203.0.113.1";
            assertThat(service.tryContact(ip)).isTrue();
            assertThat(service.tryContact(ip)).isTrue();
            assertThat(service.tryContact(ip)).isTrue();
        }

        @Test
        @DisplayName("should block the 4th submission")
        void blockFourthSubmission() {
            String ip = "203.0.113.2";
            service.tryContact(ip);
            service.tryContact(ip);
            service.tryContact(ip);
            assertThat(service.tryContact(ip)).isFalse();
        }

        @Test
        @DisplayName("each IP has independent contact bucket")
        void independentContactBuckets() {
            String ip1 = "203.0.113.10";
            String ip2 = "203.0.113.11";
            for (int i = 0; i < 3; i++) service.tryContact(ip1);
            // ip2 still has capacity
            assertThat(service.tryContact(ip2)).isTrue();
        }
    }

    // ── AI tier ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AI tier (Phase 2 placeholder — always returns true)")
    class AiTierTests {

        @Test
        @DisplayName("should always return true regardless of IP")
        void alwaysAllowed() {
            assertThat(service.tryAi("1.2.3.4")).isTrue();
            assertThat(service.tryAi("5.6.7.8")).isTrue();
            assertThat(service.tryAi(null)).isTrue();  // defensive null check
        }
    }
}
