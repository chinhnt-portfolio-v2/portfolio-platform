package dev.chinh.portfolio.platform.admin.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Single contact submission detail for admin view.
 * IP address is intentionally excluded — never expose in API responses.
 *
 * @param id       submission UUID
 * @param email    submitter email
 * @param message  message content
 * @param referralSource  value of ?from= param at time of submission (nullable)
 * @param submittedAt     submission timestamp (ISO 8601 UTC)
 * @param isRead  whether the owner has viewed this submission
 */
public record AdminContactDetailDto(
        UUID id,
        String email,
        String message,
        String referralSource,
        Instant submittedAt,
        boolean isRead
) {}
