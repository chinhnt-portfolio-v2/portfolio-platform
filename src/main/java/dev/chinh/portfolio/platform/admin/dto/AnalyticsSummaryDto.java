package dev.chinh.portfolio.platform.admin.dto;

/**
 * Analytics summary counts for contact form submissions.
 *
 * @param totalSubmissions Total number of all submissions
 * @param last30DaysCount  Submissions in the last 30 days
 * @param last7DaysCount   Submissions in the last 7 days
 */
public record AnalyticsSummaryDto(
        long totalSubmissions,
        long last30DaysCount,
        long last7DaysCount
) {}
