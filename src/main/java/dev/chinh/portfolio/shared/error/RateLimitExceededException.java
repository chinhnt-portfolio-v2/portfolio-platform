package dev.chinh.portfolio.shared.error;

/**
 * Exception thrown when a client exceeds an IP-based rate limit.
 * Results in HTTP 429 Too Many Requests response.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
