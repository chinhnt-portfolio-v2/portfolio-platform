package dev.chinh.portfolio.apps.wallet.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@Component
public class NlpRateLimiter {

    private static final int CAPACITY = 20;
    private static final Duration WINDOW = Duration.ofMinutes(60);

    private final Cache<UUID, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(10_000)
            .build();

    public void checkLimit(UUID userId) {
        Bucket bucket = buckets.get(userId, this::newBucket);
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "NLP rate limit exceeded: maximum 20 requests per hour"
            );
        }
    }

    private Bucket newBucket(UUID userId) {
        Bandwidth limit = Bandwidth.classic(CAPACITY, Refill.greedy(CAPACITY, WINDOW));
        return Bucket.builder().addLimit(limit).build();
    }
}
