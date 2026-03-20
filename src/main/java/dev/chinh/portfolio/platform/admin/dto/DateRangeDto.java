package dev.chinh.portfolio.platform.admin.dto;

import java.time.Instant;

/**
 * Date range of contact form submissions.
 *
 * @param earliest Earliest submission timestamp (nullable when no submissions exist)
 * @param latest   Latest submission timestamp (nullable when no submissions exist)
 */
public record DateRangeDto(
        Instant earliest,
        Instant latest
) {}
