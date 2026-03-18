package dev.chinh.portfolio.auth.service;

import dev.chinh.portfolio.shared.error.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OwnershipHelper.
 */
class OwnershipHelperTest {

    @Nested
    @DisplayName("verifyOwnership(UUID, UUID, String)")
    class VerifyOwnershipUuidTests {

        @Test
        @DisplayName("should pass when owner ID matches user ID")
        void shouldPassWhenOwnerMatchesUser() {
            UUID ownerId = UUID.randomUUID();
            UUID userId = ownerId;

            assertDoesNotThrow(() ->
                OwnershipHelper.verifyOwnership(ownerId, userId, "resource")
            );
        }

        @Test
        @DisplayName("should throw ForbiddenException when owner ID does not match")
        void shouldThrowWhenOwnerDoesNotMatch() {
            UUID ownerId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            ForbiddenException exception = assertThrows(ForbiddenException.class, () ->
                OwnershipHelper.verifyOwnership(ownerId, userId, "Wallet data")
            );

            assertTrue(exception.getMessage().contains("does not belong to user"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when owner ID is null")
        void shouldThrowWhenOwnerIdIsNull() {
            UUID userId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class, () ->
                OwnershipHelper.verifyOwnership(null, userId, "resource")
            );
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when user ID is null")
        void shouldThrowWhenUserIdIsNull() {
            UUID ownerId = UUID.randomUUID();

            assertThrows(IllegalArgumentException.class, () ->
                OwnershipHelper.verifyOwnership(ownerId, null, "resource")
            );
        }
    }

    @Nested
    @DisplayName("verifyOwnership(String, String, String)")
    class VerifyOwnershipStringTests {

        @Test
        @DisplayName("should pass when owner ID matches user ID")
        void shouldPassWhenOwnerMatchesUser() {
            String ownerId = UUID.randomUUID().toString();
            String userId = ownerId;

            assertDoesNotThrow(() ->
                OwnershipHelper.verifyOwnership(ownerId, userId, "resource")
            );
        }

        @Test
        @DisplayName("should throw ForbiddenException when owner ID does not match")
        void shouldThrowWhenOwnerDoesNotMatch() {
            String ownerId = UUID.randomUUID().toString();
            String userId = UUID.randomUUID().toString();

            ForbiddenException exception = assertThrows(ForbiddenException.class, () ->
                OwnershipHelper.verifyOwnership(ownerId, userId, "Session")
            );

            assertTrue(exception.getMessage().contains("does not belong to user"));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when owner ID is null")
        void shouldThrowWhenOwnerIdIsNull() {
            String userId = UUID.randomUUID().toString();

            assertThrows(IllegalArgumentException.class, () ->
                OwnershipHelper.verifyOwnership(null, userId, "resource")
            );
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when user ID is null")
        void shouldThrowWhenUserIdIsNull() {
            String ownerId = UUID.randomUUID().toString();

            assertThrows(IllegalArgumentException.class, () ->
                OwnershipHelper.verifyOwnership(ownerId, null, "resource")
            );
        }
    }
}
