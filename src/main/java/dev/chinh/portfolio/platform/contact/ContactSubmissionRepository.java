package dev.chinh.portfolio.platform.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ContactSubmissionRepository extends JpaRepository<ContactSubmission, UUID> {

    // Rate limiting check (Story 4.x): count submissions by IP in time window
    long countByIpAddressAndSubmittedAtAfter(String ipAddress, Instant since);

    Page<ContactSubmission> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    // ── Story 6.2: Analytics Aggregation Queries ────────────────────────────────────

    /**
     * Count submissions submitted after the given instant.
     */
    long countBySubmittedAtAfter(Instant after);

    /**
     * Find all submissions ordered by submittedAt descending (for recentSubmissions).
     * Results are ordered in the query; pagination is applied in the service layer.
     */
    List<ContactSubmission> findAllByOrderBySubmittedAtDesc();

    /**
     * Find submissions within a date range, ordered by submittedAt descending.
     */
    List<ContactSubmission> findBySubmittedAtBetweenOrderBySubmittedAtDesc(Instant from, Instant to);

    /**
     * Count submissions grouped by referralSource, including NULL values.
     * Returns a list of Object[2] where [0] = referralSource (nullable String) and [1] = count (Long).
     */
    @Query("SELECT c.referralSource, COUNT(c) FROM ContactSubmission c GROUP BY c.referralSource")
    List<Object[]> countGroupByReferralSource();

    /**
     * Count submissions for a specific referral source.
     */
    long countByReferralSource(String referralSource);
}
