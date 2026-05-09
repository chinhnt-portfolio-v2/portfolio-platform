package dev.chinh.portfolio.apps.wallet.dto;

/**
 * Error response for NLP endpoint — deliberately hides internal details
 * (proxy URL, stack traces) to avoid leaking infrastructure info.
 */
public record NlpErrorResponse(
    String error,     // user-facing error code: RATE_LIMITED, PARSE_FAILED, SERVICE_UNAVAILABLE
    String message    // safe, user-readable message (no stack trace, no URL)
) {}
