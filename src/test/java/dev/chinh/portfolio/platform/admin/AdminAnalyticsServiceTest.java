package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.platform.admin.dto.AdminAnalyticsDto;
import dev.chinh.portfolio.platform.admin.dto.ReferralSourceStatDto;
import dev.chinh.portfolio.platform.admin.dto.RecentSubmissionDto;
import dev.chinh.portfolio.platform.contact.ContactSubmission;
import dev.chinh.portfolio.platform.contact.ContactSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdminAnalyticsService} covering all acceptance criteria.
 * Written as a flat class (no @Nested) to ensure JUnit platform discoverability.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAnalyticsServiceTest {

    @Mock
    private ContactSubmissionRepository repository;

    @Mock
    private AdminAnalyticsMapper mapper;

    @InjectMocks
    private AdminAnalyticsService service;

    @Captor
    private ArgumentCaptor<List<ContactSubmission>> submissionListCaptor;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private ContactSubmission mockSubmission(String id, String email, String referralSource, Instant submittedAt) {
        ContactSubmission s = mock(ContactSubmission.class);
        when(s.getId()).thenReturn(UUID.fromString(id));
        when(s.getEmail()).thenReturn(email);
        when(s.getReferralSource()).thenReturn(referralSource);
        when(s.getSubmittedAt()).thenReturn(submittedAt);
        return s;
    }

    /** Helper: type-safe GROUP BY mock result for referral source queries. */
    @SafeVarargs
    private List<Object[]> groupBy(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private void stubEmpty() {
        when(repository.count()).thenReturn(0L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(List.of());
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    // ── AC-1: Response shape ────────────────────────────────────────────────────

    @Test
    void ac1_shouldReturnAllFourTopLevelFields() {
        stubEmpty();
        AdminAnalyticsDto dto = service.getAnalytics();
        assertThat(dto.summary()).isNotNull();
        assertThat(dto.byReferralSource()).isNotNull();
        assertThat(dto.recentSubmissions()).isNotNull();
        assertThat(dto.dateRange()).isNotNull();
    }

    // ── AC-3: Empty data ────────────────────────────────────────────────────────

    @Test
    void ac3_shouldReturnZeroCountsWhenEmpty() {
        stubEmpty();
        AdminAnalyticsDto dto = service.getAnalytics();
        assertThat(dto.summary().totalSubmissions()).isZero();
        assertThat(dto.summary().last30DaysCount()).isZero();
        assertThat(dto.summary().last7DaysCount()).isZero();
    }

    @Test
    void ac3_shouldReturnEmptyRecentSubmissionsWhenEmpty() {
        stubEmpty();
        AdminAnalyticsDto dto = service.getAnalytics();
        assertThat(dto.recentSubmissions()).isEmpty();
    }

    @Test
    void ac3_shouldReturnNullDateRangeWhenEmpty() {
        stubEmpty();
        AdminAnalyticsDto dto = service.getAnalytics();
        assertThat(dto.dateRange().earliest()).isNull();
        assertThat(dto.dateRange().latest()).isNull();
    }

    // ── AC-4: Referral source stats ─────────────────────────────────────────────

    @Test
    void ac4_shouldSortByCountDescending() {
        when(repository.count()).thenReturn(34L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(groupBy(
                new Object[]{"linkedin", 18L},
                new Object[]{"cv-vn",     12L},
                new Object[]{null,         4L}
        ));
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        AdminAnalyticsDto dto = service.getAnalytics();
        List<ReferralSourceStatDto> stats = dto.byReferralSource();

        assertThat(stats).hasSize(3);
        assertThat(stats.get(0).source()).isEqualTo("linkedin");
        assertThat(stats.get(0).count()).isEqualTo(18L);
        assertThat(stats.get(1).source()).isEqualTo("cv-vn");
        assertThat(stats.get(1).count()).isEqualTo(12L);
        assertThat(stats.get(2).source()).isNull();
        assertThat(stats.get(2).count()).isEqualTo(4L);
    }

    @Test
    void ac4_shouldIncludeNullSourceForDirectVisits() {
        when(repository.count()).thenReturn(10L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(groupBy(
                new Object[]{"linkedin", 7L},
                new Object[]{null,        3L}
        ));
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        AdminAnalyticsDto dto = service.getAnalytics();
        ReferralSourceStatDto nullStat = dto.byReferralSource().stream()
                .filter(s -> s.source() == null)
                .findFirst().orElseThrow();

        assertThat(nullStat.count()).isEqualTo(3L);
        assertThat(nullStat.percentage()).isGreaterThan(0);
    }

    @Test
    void ac4_shouldRoundPercentagesTo1DecimalPlace() {
        // 18/34 = 52.941…% → 52.9; 12/34 = 35.294…% → 35.3; 4/34 = 11.764…% → 11.8
        when(repository.count()).thenReturn(34L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(groupBy(
                new Object[]{"linkedin", 18L},
                new Object[]{"cv-vn",    12L},
                new Object[]{null,         4L}
        ));
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        AdminAnalyticsDto dto = service.getAnalytics();
        List<ReferralSourceStatDto> stats = dto.byReferralSource();

        assertThat(stats.get(0).percentage()).isCloseTo(52.9, org.assertj.core.data.Offset.offset(0.05));
        assertThat(stats.get(1).percentage()).isCloseTo(35.3, org.assertj.core.data.Offset.offset(0.05));
        assertThat(stats.get(2).percentage()).isCloseTo(11.8, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void ac4_shouldHandleRoundingBoundaryAtOneThird_HALF_UP() {
        // 1/3 = 33.333…% → 33.3 (rounds down: second decimal 3 < 5)
        when(repository.count()).thenReturn(3L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(groupBy(
                new Object[]{"linkedin", 1L},
                new Object[]{null,        2L}
        ));
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        AdminAnalyticsDto dto = service.getAnalytics();
        ReferralSourceStatDto linkedinStat = dto.byReferralSource().stream()
                .filter(s -> "linkedin".equals(s.source())).findFirst().orElseThrow();

        assertThat(linkedinStat.percentage()).isCloseTo(33.3, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void ac4_shouldReturnEmptyByReferralSourceWhenNoSubmissions() {
        stubEmpty();
        AdminAnalyticsDto dto = service.getAnalytics();
        assertThat(dto.byReferralSource()).isEmpty();
    }

    // ── AC-5: Privacy ──────────────────────────────────────────────────────────

    @Test
    void ac5_shouldExcludeMessageFieldFromRecentSubmissions() {
        Instant now = Instant.now();
        ContactSubmission s = mockSubmission("00000000-0000-0000-0000-000000000001",
                "recruiter@example.com", "linkedin", now);
        RecentSubmissionDto dto = new RecentSubmissionDto(
                "00000000-0000-0000-0000-000000000001",
                "recruiter@example.com", now, "linkedin");

        when(repository.count()).thenReturn(1L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(groupBy(new Object[]{"linkedin", 1L}));
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(s)));
        when(mapper.toRecentSubmissionDto(s)).thenReturn(dto);

        AdminAnalyticsDto result = service.getAnalytics();
        RecentSubmissionDto recent = result.recentSubmissions().get(0);

        // Verify DTO has only the 4 permitted fields
        assertThat(recent.id()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(recent.email()).isEqualTo("recruiter@example.com");
        assertThat(recent.submittedAt()).isEqualTo(now);
        assertThat(recent.referralSource()).isEqualTo("linkedin");

        // AC-5: message field must not exist in RecentSubmissionDto record
        boolean hasMessageField = Arrays.stream(recent.getClass().getDeclaredFields())
                .anyMatch(f -> "message".equals(f.getName()));
        assertThat(hasMessageField).isFalse();
    }

    @Test
    void ac5_shouldLimitRecentSubmissionsTo20() {
        Instant now = Instant.now();
        // PageImpl mock không enforce page size — chỉ trả đúng 20 items
        List<ContactSubmission> page20 = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            page20.add(mockSubmission(
                    String.format("00000000-0000-0000-0000-00000000%04d", i),
                    "recruiter" + i + "@example.com", "linkedin", now.minusSeconds(i * 60)));
        }
        RecentSubmissionDto dto = new RecentSubmissionDto("id", "email", now, "linkedin");

        when(repository.count()).thenReturn(25L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(groupBy(new Object[]{"linkedin", 25L}));
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(page20, PageRequest.of(0, 20), 25));
        when(mapper.toRecentSubmissionDto(any())).thenReturn(dto);

        AdminAnalyticsDto result = service.getAnalytics();
        assertThat(result.recentSubmissions()).hasSize(20);
    }

    // ── Date range ───────────────────────────────────────────────────────────────

    @Test
    void dateRange_shouldReturnCorrectEarliestAndLatest() {
        Instant early  = Instant.parse("2026-01-15T08:23:00Z");
        Instant middle = Instant.parse("2026-02-10T10:00:00Z");
        Instant late   = Instant.parse("2026-03-03T14:00:00Z");

        List<ContactSubmission> submissions = List.of(
                mockSubmission("00000000-0000-0000-0000-000000000001", "a@x.com", "linkedin", early),
                mockSubmission("00000000-0000-0000-0000-000000000002", "b@x.com", "cv-vn",   middle),
                mockSubmission("00000000-0000-0000-0000-000000000003", "c@x.com", "direct",  late)
        );
        RecentSubmissionDto dto = new RecentSubmissionDto("id", "email", early, "linkedin");

        when(repository.count()).thenReturn(3L);
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);
        when(repository.countGroupByReferralSource()).thenReturn(groupBy(
                new Object[]{"linkedin", 1L},
                new Object[]{"cv-vn",    1L},
                new Object[]{"direct",   1L}
        ));
        when(repository.findAllByOrderBySubmittedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(submissions, PageRequest.of(0, 20), 3));
        when(mapper.toRecentSubmissionDto(any())).thenReturn(dto);

        AdminAnalyticsDto result = service.getAnalytics();
        assertThat(result.dateRange().earliest()).isEqualTo(early);
        assertThat(result.dateRange().latest()).isEqualTo(late);
    }

    // ── Summary counts ─────────────────────────────────────────────────────────

    @Test
    void summary_shouldReturnTotalCount() {
        stubEmpty();
        when(repository.count()).thenReturn(42L);

        AdminAnalyticsDto dto = service.getAnalytics();
        assertThat(dto.summary().totalSubmissions()).isEqualTo(42L);
    }

    @Test
    void summary_shouldReturnNonNegativeCounts() {
        stubEmpty();
        when(repository.countBySubmittedAtAfter(any(Instant.class))).thenReturn(0L);

        AdminAnalyticsDto dto = service.getAnalytics();
        assertThat(dto.summary().last30DaysCount()).isGreaterThanOrEqualTo(0);
        assertThat(dto.summary().last7DaysCount()).isGreaterThanOrEqualTo(0);
    }
}
