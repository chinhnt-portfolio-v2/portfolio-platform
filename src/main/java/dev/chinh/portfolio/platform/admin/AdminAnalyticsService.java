package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.platform.admin.dto.*;
import dev.chinh.portfolio.platform.contact.ContactSubmission;
import dev.chinh.portfolio.platform.contact.ContactSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Aggregation logic for the admin analytics endpoint.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Compute summary counts (total, last-30-days, last-7-days)</li>
 *   <li>Build referral source breakdown with percentages (sorted descending)</li>
 *   <li>Build recent-submissions list (last 20, privacy-safe)</li>
 *   <li>Compute date range (earliest and latest submission timestamps)</li>
 * </ul>
 *
 * <p>All timestamps are handled in UTC. Percentages use {@link RoundingMode#HALF_UP}.
 */
@Service
public class AdminAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsService.class);

    private static final int RECENT_SUBMISSIONS_LIMIT = 20;

    private final ContactSubmissionRepository repository;
    private final AdminAnalyticsMapper mapper;

    public AdminAnalyticsService(ContactSubmissionRepository repository,
                                  AdminAnalyticsMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Build the complete analytics dashboard DTO.
     *
     * <p>Handles empty data gracefully: returns zero counts, empty recentSubmissions,
     * and null dateRange values when no submissions exist.
     *
     * @return a fully populated {@link AdminAnalyticsDto}
     */
    @Transactional(readOnly = true)
    public AdminAnalyticsDto getAnalytics() {
        Instant now = Instant.now();

        AnalyticsSummaryDto summary = buildSummary(now);
        List<ReferralSourceStatDto> byReferralSource = buildReferralSourceStats();
        Page<ContactSubmission> recentPage = repository.findAllByOrderBySubmittedAtDesc(
                PageRequest.of(0, RECENT_SUBMISSIONS_LIMIT, Sort.by("submittedAt").descending()));
        List<ContactSubmission> recentEntities = recentPage.getContent();
        List<RecentSubmissionDto> recentSubmissions = recentEntities.stream()
                .map(mapper::toRecentSubmissionDto)
                .toList();
        DateRangeDto dateRange = buildDateRange(recentEntities);

        log.debug("Analytics computed: {} total submissions", summary.totalSubmissions());

        return new AdminAnalyticsDto(summary, byReferralSource, recentSubmissions, dateRange);
    }

    // ── Summary helpers ────────────────────────────────────────────────────────────

    private AnalyticsSummaryDto buildSummary(Instant now) {
        Instant last30Days = now.atZone(ZoneOffset.UTC).minusDays(30).toInstant();
        Instant last7Days  = now.atZone(ZoneOffset.UTC).minusDays(7).toInstant();

        long total = repository.countAll();

        // countBySubmittedAtAfter returns 0 when called on empty table — no null risk
        long last30 = repository.countBySubmittedAtAfter(last30Days);
        long last7  = repository.countBySubmittedAtAfter(last7Days);

        return new AnalyticsSummaryDto(total, last30, last7);
    }

    // ── Referral source helpers ──────────────────────────────────────────────────

    private List<ReferralSourceStatDto> buildReferralSourceStats() {
        List<Object[]> rawRows = repository.countGroupByReferralSource();

        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }

        long totalCount = rawRows.stream()
                .mapToLong(row -> (Long) row[1])
                .sum();

        return rawRows.stream()
                .map(row -> {
                    String source = (String) row[0];   // nullable — null means direct/unknown
                    long count = (Long) row[1];
                    double percentage = totalCount > 0
                            ? BigDecimal.valueOf(count * 100.0 / totalCount)
                                    .setScale(1, RoundingMode.HALF_UP)
                                    .doubleValue()
                            : 0.0;
                    return new ReferralSourceStatDto(source, count, percentage);
                })
                .sorted(Comparator.comparingLong(ReferralSourceStatDto::count).reversed())
                .toList();
    }

    // ── Date range helpers ───────────────────────────────────────────────────────

    private DateRangeDto buildDateRange(List<ContactSubmission> recentEntities) {
        if (recentEntities == null || recentEntities.isEmpty()) {
            // AC-3: null dateRange values when no submissions exist
            return new DateRangeDto(null, null);
        }

        Instant earliest = recentEntities.stream()
                .map(ContactSubmission::getSubmittedAt)
                .min(Instant::compareTo)
                .orElse(null);

        Instant latest = recentEntities.stream()
                .map(ContactSubmission::getSubmittedAt)
                .max(Instant::compareTo)
                .orElse(null);

        return new DateRangeDto(earliest, latest);
    }
}
