package dev.chinh.portfolio.platform.admin.dto;

/**
 * Referral source breakdown with count and percentage.
 *
 * @param source     The referral source string, or null for direct/unknown visits
 * @param count      Number of submissions from this source
 * @param percentage Percentage of total submissions, rounded to 1 decimal place
 */
public record ReferralSourceStatDto(
        String source,
        long count,
        double percentage
) {}
