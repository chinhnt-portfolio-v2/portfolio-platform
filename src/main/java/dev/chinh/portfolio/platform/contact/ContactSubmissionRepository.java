package dev.chinh.portfolio.platform.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface ContactSubmissionRepository extends JpaRepository<ContactSubmission, UUID> {
    // Rate limiting check (Story 4.x): count submissions by IP in time window
    long countByIpAddressAndSubmittedAtAfter(String ipAddress, Instant since);
    Page<ContactSubmission> findAllByOrderBySubmittedAtDesc(Pageable pageable);
}
