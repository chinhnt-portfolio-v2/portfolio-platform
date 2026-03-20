package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.platform.admin.dto.AdminContactDetailDto;
import dev.chinh.portfolio.platform.admin.dto.AdminContactListDto;
import dev.chinh.portfolio.platform.contact.ContactSubmission;
import dev.chinh.portfolio.platform.contact.ContactSubmissionRepository;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin service for managing contact form submissions.
 *
 * <p>Only accessible to the OWNER. Provides read and mark-as-read operations
 * for contact submissions submitted via {@code POST /api/v1/contact-submissions}.
 *
 * @see ContactSubmissionRepository
 */
@Service
public class AdminContactService {

    private static final Logger log = LoggerFactory.getLogger(AdminContactService.class);

    private final ContactSubmissionRepository repository;
    private final AdminContactMapper mapper;

    public AdminContactService(ContactSubmissionRepository repository,
                               AdminContactMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Return a paginated list of all contact submissions, newest first.
     *
     * @param page  page number (0-indexed)
     * @param size  number of submissions per page
     */
    @Transactional(readOnly = true)
    public AdminContactListDto listSubmissions(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ContactSubmission> pageResult = repository.findAllByOrderBySubmittedAtDesc(pageable);
        return new AdminContactListDto(
                pageResult.getContent().stream().map(mapper::toDetailDto).toList(),
                page,
                size,
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }

    /**
     * Return a single contact submission by ID.
     *
     * @throws EntityNotFoundException if no submission exists with the given ID
     */
    @Transactional(readOnly = true)
    public AdminContactDetailDto getSubmission(UUID id) {
        ContactSubmission submission = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Contact submission not found: " + id));
        return mapper.toDetailDto(submission);
    }

    /**
     * Mark a contact submission as read.
     *
     * @throws EntityNotFoundException if no submission exists with the given ID
     */
    @Transactional
    public AdminContactDetailDto markAsRead(UUID id) {
        ContactSubmission submission = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Contact submission not found: " + id));

        if (!submission.isRead()) {
            submission.setIsRead(true);
            repository.save(submission);
            log.debug("Marked contact submission {} as read", id);
        }

        return mapper.toDetailDto(submission);
    }
}
