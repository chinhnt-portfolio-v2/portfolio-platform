package dev.chinh.portfolio.platform.admin.dto;

import java.time.Instant;

/**
 * A recent contact form submission — privacy-safe view.
 *
 * <p>Exposes only: id, email, submittedAt, referralSource.
 * The message body is NEVER included for privacy reasons (AC-5).
 *
 * @param id             Submission UUID
 * @param email          Submitter's email address
 * @param submittedAt    Submission timestamp in ISO 8601 UTC
 * @param referralSource Referral source (nullable)
 */
public record RecentSubmissionDto(
        String id,
        String email,
        Instant submittedAt,
        String referralSource
) {}
