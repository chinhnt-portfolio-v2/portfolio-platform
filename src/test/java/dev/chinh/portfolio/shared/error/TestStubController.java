package dev.chinh.portfolio.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only stub controller used by GlobalExceptionHandlerTest.
 * Deliberately throws each exception type to exercise GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/test")
class TestStubController {

    @GetMapping("/not-found")
    public void throwNotFound() {
        throw new EntityNotFoundException("Item not found");
    }

    @GetMapping("/server-error")
    public void throwServerError() {
        throw new RuntimeException("Unexpected failure");
    }

    @GetMapping("/access-denied")
    public void throwAccessDenied() {
        throw new AccessDeniedException("User does not have permission");
    }

    @PostMapping("/validate")
    public void validateBody(@Valid @RequestBody ValidatedRequest req) {
    }

    record ValidatedRequest(@Email @NotBlank String email) {}
}
