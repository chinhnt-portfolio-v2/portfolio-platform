package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.platform.admin.dto.RecentSubmissionDto;
import dev.chinh.portfolio.platform.contact.ContactSubmission;
import org.springframework.stereotype.Component;

/**
 * Maps {@link ContactSubmission} entities to DTOs for the analytics endpoint.
 *
 * <p>This mapper exists purely to enforce the privacy boundary: only the 4 permitted
 * fields (id, email, submittedAt, referralSource) are ever exposed. The {@code message}
 * and {@code ipAddress} fields are deliberately omitted.
 */
@Component
public class AdminAnalyticsMapper {

    /**
     * Map a single {@link ContactSubmission} entity to a {@link RecentSubmissionDto}.
     * The message and ipAddress fields are NEVER included.
     */
    public RecentSubmissionDto toRecentSubmissionDto(ContactSubmission entity) {
        return new RecentSubmissionDto(
                entity.getId().toString(),
                entity.getEmail(),
                entity.getSubmittedAt(),
                entity.getReferralSource()
        );
    }
}
