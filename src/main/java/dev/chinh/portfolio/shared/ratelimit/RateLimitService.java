package dev.chinh.portfolio.shared.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiting across three tiers, backed by in-memory Bucket4j buckets.
 *
 * <p>Implemented tiers per NFR-S9 and FR39:
 * <ul>
 *   <li><b>General:</b> 100 requests / minute per IP</li>
 *   <li><b>Contact form:</b> 3 submissions / day per IP</li>
 *   <li><b>AI endpoints:</b> 5 requests / 10 minutes per IP (Phase 2 — placeholder)</li>
 * </ul>
 *
 * <p>All buckets are lazily created per IP and held in a {@code ConcurrentHashMap}.
 * On server restart the counters reset — acceptable for free-tier deployment.
 *
 * <p>For AI endpoints, NFR-S9 and FR40 require this tier but the endpoints don't
 * exist yet. The AI bucket is implemented as a no-op (always-allowed) until
 * Phase 2 to avoid blocking legitimate requests.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // ── Tier definitions ──────────────────────────────────────────────────────

    private static final int GENERAL_REQUESTS = 100;
    private static final Duration GENERAL_WINDOW = Duration.ofMinutes(1);

    private static final int CONTACT_SUBMISSIONS = 3;
    private static final Duration CONTACT_WINDOW = Duration.ofDays(1);

    private static final int AI_REQUESTS = 5;
    private static final Duration AI_WINDOW = Duration.ofMinutes(10);

    // ── Bucket factories ──────────────────────────────────────────────────────

    private static Bucket createGeneralBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(GENERAL_REQUESTS,
                        Refill.greedy(GENERAL_REQUESTS, GENERAL_WINDOW)))
                .build();
    }

    private static Bucket createContactBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(CONTACT_SUBMISSIONS,
                        Refill.greedy(CONTACT_SUBMISSIONS, CONTACT_WINDOW)))
                .build();
    }

    private static Bucket createAiBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(AI_REQUESTS,
                        Refill.greedy(AI_REQUESTS, AI_WINDOW)))
                .build();
    }

    // ── Per-IP bucket maps ────────────────────────────────────────────────────

    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> contactBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> aiBuckets       = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Check and consume a general API request slot for the given IP.
     *
     * @param clientIp  client IP address (never null)
     * @return true if the request is allowed; false if rate limited
     */
    public boolean tryGeneral(String clientIp) {
        Bucket bucket = generalBuckets.computeIfAbsent(clientIp, k -> {
            log.debug("Creating general rate-limit bucket for IP: {}", maskIp(k));
            return createGeneralBucket();
        });
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.debug("General rate limit exceeded for IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Check and consume a contact-form submission slot for the given IP.
     *
     * @param clientIp  client IP address (never null)
     * @return true if the submission is allowed; false if rate limited
     */
    public boolean tryContact(String clientIp) {
        Bucket bucket = contactBuckets.computeIfAbsent(clientIp, k -> {
            log.debug("Creating contact rate-limit bucket for IP: {}", maskIp(k));
            return createContactBucket();
        });
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.debug("Contact form rate limit exceeded for IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Check and consume an AI endpoint request slot for the given IP.
     *
     * <p>Currently a no-op (always returns true) — Phase 2 placeholder.
     *
     * @param clientIp  client IP address (never null)
     * @return true (AI endpoints not yet active)
     */
    public boolean tryAi(String clientIp) {
        // Phase 2 placeholder — AI endpoints don't exist yet
        return true;
    }

    // ── Admin helpers ─────────────────────────────────────────────────────────

    /**
     * Return how many general request slots remain for the given IP.
     * Used for response headers (e.g. {@code X-RateLimit-Remaining}).
     */
    public long getGeneralRemaining(String clientIp) {
        Bucket bucket = generalBuckets.get(clientIp);
        return bucket != null ? bucket.getAvailableTokens() : GENERAL_REQUESTS;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Mask IP for logging — show only the first two octets to reduce PII in logs.
     */
    private static String maskIp(String ip) {
        if (ip == null) return "null";
        int dot = ip.indexOf('.');
        return dot > 0 ? ip.substring(0, dot) + ".xxx" : ip;
    }
}
