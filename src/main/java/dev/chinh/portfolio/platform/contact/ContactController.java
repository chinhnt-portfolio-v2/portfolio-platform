package dev.chinh.portfolio.platform.contact;

import dev.chinh.portfolio.platform.contact.dto.ContactSubmissionRequest;
import dev.chinh.portfolio.platform.contact.ContactSubmissionResponse;
import dev.chinh.portfolio.shared.error.RateLimitExceededException;
import dev.chinh.portfolio.shared.ratelimit.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private final ContactSubmissionRepository repository;
    private final RateLimitService rateLimitService;

    public ContactController(ContactSubmissionRepository repository,
                             RateLimitService rateLimitService) {
        this.repository = repository;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/contact-submissions")
    public ResponseEntity<ContactSubmissionResponse> submitContact(
            @Valid @RequestBody ContactSubmissionRequest request,
            HttpServletRequest httpRequest) {

        String honeypot = httpRequest.getParameter("website");
        if (honeypot != null && !honeypot.isEmpty()) {
            log.debug("Honeypot triggered - silent discard");
            return ResponseEntity.ok().build();
        }

        String clientIp = getClientIp(httpRequest);

        if (!rateLimitService.tryContact(clientIp)) {
            log.debug("Contact form rate limit exceeded for IP: {}", clientIp);
            throw new RateLimitExceededException(
                    "Rate limit exceeded. Maximum 3 submissions per day. Please try again tomorrow.");
        }

        ContactSubmission submission = new ContactSubmission();
        submission.setEmail(request.email());
        submission.setMessage(request.message());
        submission.setReferralSource(request.referralSource());
        submission.setIpAddress(clientIp);

        repository.save(submission);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ContactSubmissionResponse(submission.getId(), "Message sent successfully"));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
