package dev.chinh.portfolio.platform.admin.dto;

import java.util.List;

/**
 * Complete analytics response for the admin dashboard.
 *
 * <p>Shape matches the specification in Story 6.2 AC-1.
 *
 * @param summary            Summary counts (total, last 30 days, last 7 days)
 * @param byReferralSource   Referral source breakdown sorted by count descending
 * @param recentSubmissions  Last 20 submissions (privacy-safe, no message body)
 * @param dateRange          Earliest and latest submission timestamps (nullable)
 */
public record AdminAnalyticsDto(
        AnalyticsSummaryDto summary,
        List<ReferralSourceStatDto> byReferralSource,
        List<RecentSubmissionDto> recentSubmissions,
        DateRangeDto dateRange
) {}
