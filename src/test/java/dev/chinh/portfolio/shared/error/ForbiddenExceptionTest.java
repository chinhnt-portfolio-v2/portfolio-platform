package dev.chinh.portfolio.shared.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ForbiddenException.
 */
class ForbiddenExceptionTest {

    @Test
    @DisplayName("should create exception with message")
    void shouldCreateWithMessage() {
        String message = "Access denied: resource does not belong to user";
        ForbiddenException exception = new ForbiddenException(message);

        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        ForbiddenException exception = new ForbiddenException("test");

        assertTrue(exception instanceof RuntimeException);
    }
}
