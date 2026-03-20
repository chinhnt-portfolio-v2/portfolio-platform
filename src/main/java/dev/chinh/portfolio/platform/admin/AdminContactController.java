package dev.chinh.portfolio.platform.admin;

import dev.chinh.portfolio.platform.admin.dto.AdminContactDetailDto;
import dev.chinh.portfolio.platform.admin.dto.AdminContactListDto;
import dev.chinh.portfolio.shared.error.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin endpoint for managing contact form submissions.
 *
 * <p>Owner-only access — requires valid JWT with OWNER role.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/admin/contacts} — list all submissions (paginated)</li>
 *   <li>{@code GET /api/v1/admin/contacts/{id}} — get single submission detail</li>
 *   <li>{@code PATCH /api/v1/admin/contacts/{id}/read} — mark submission as read</li>
 * </ul>
 *
 * @see AdminContactService
 * @see dev.chinh.portfolio.platform.contact.ContactSubmission
 */
@RestController
@RequestMapping("/api/v1/admin/contacts")
@Tag(name = "Admin — Contact Submissions", description = "Owner-only contact form management")
@SecurityRequirement(name = "bearerAuth")
public class AdminContactController {

    private static final Logger log = LoggerFactory.getLogger(AdminContactController.class);

    private final AdminContactService service;

    public AdminContactController(AdminContactService service) {
        this.service = service;
    }

    /**
     * List all contact submissions, newest first.
     *
     * @param page  page number (0-indexed, default 0)
     * @param size  page size (default 20, max 100)
     * @return paginated list of submissions
     */
    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "List contact submissions",
               description = "Returns all contact form submissions, paginated and ordered by newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated submission list"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the owner")
    })
    public ResponseEntity<AdminContactListDto> listSubmissions(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") int size) {

        ensureOwner();

        int safeSize = Math.min(Math.max(size, 1), 100);
        AdminContactListDto result = service.listSubmissions(page, safeSize);
        log.debug("Admin listed contact submissions: page={}, size={}, total={}",
                page, safeSize, result.totalElements());
        return ResponseEntity.ok(result);
    }

    /**
     * Get a single contact submission by ID.
     *
     * @param id submission UUID
     * @return submission detail (IP address excluded)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Get contact submission detail",
               description = "Returns full detail of a single submission. IP address is never included.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submission detail"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the owner"),
            @ApiResponse(responseCode = "404", description = "Submission not found")
    })
    public ResponseEntity<AdminContactDetailDto> getSubmission(
            @Parameter(description = "Submission UUID")
            @PathVariable UUID id) {

        ensureOwner();
        AdminContactDetailDto submission = service.getSubmission(id);
        return ResponseEntity.ok(submission);
    }

    /**
     * Mark a contact submission as read.
     *
     * @param id submission UUID
     * @return updated submission detail
     */
    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "Mark submission as read",
               description = "Marks a contact submission as read. Idempotent — calling multiple times is safe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Submission marked as read"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the owner"),
            @ApiResponse(responseCode = "404", description = "Submission not found")
    })
    public ResponseEntity<AdminContactDetailDto> markAsRead(
            @Parameter(description = "Submission UUID")
            @PathVariable UUID id) {

        ensureOwner();
        AdminContactDetailDto submission = service.markAsRead(id);
        log.debug("Admin marked contact submission {} as read", id);
        return ResponseEntity.ok(submission);
    }

    // ── Authorization ──────────────────────────────────────────────────────────

    private void ensureOwner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ForbiddenException("Access denied. Owner role required.");
        }
        // @PreAuthorize already verified the role via Spring Security expression;
        // this throw is a defensive fallback for any edge case.
        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"))) {
            throw new ForbiddenException("Access denied. Owner role required.");
        }
    }
}
