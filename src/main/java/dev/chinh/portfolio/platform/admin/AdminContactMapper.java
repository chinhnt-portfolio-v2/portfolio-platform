package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.platform.admin.dto.AdminContactDetailDto;
import dev.chinh.portfolio.platform.contact.ContactSubmission;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Maps {@link ContactSubmission} entities to admin DTOs.
 * IP address is never included in any DTO — it is a PII field on the entity.
 */
@Component
public class AdminContactMapper {

    public AdminContactDetailDto toDetailDto(ContactSubmission entity) {
        return new AdminContactDetailDto(
                entity.getId(),
                entity.getEmail(),
                entity.getMessage(),
                entity.getReferralSource(),
                entity.getSubmittedAt(),
                entity.isRead()
        );
    }

    public Page<AdminContactDetailDto> toDetailDtoPage(Page<ContactSubmission> page) {
        return page.map(this::toDetailDto);
    }
}
