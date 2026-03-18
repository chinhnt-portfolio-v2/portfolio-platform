package dev.chinh.portfolio.shared.error;

/**
 * Exception thrown when a user attempts to access a resource that belongs to another user.
 * Results in HTTP 403 Forbidden response.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
