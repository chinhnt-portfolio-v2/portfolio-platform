package dev.chinh.portfolio.auth.service;

import dev.chinh.portfolio.shared.error.ForbiddenException;

import java.util.UUID;

/**
 * Utility class for verifying ownership of resources.
 * Throws ForbiddenException (HTTP 403) when a user attempts to access
 * a resource that belongs to another user.
 */
public final class OwnershipHelper {

    private OwnershipHelper() {
        // Utility class - prevent instantiation
    }

    /**
     * Verifies that the current user owns the resource.
     *
     * @param ownerId the ID of the resource owner
     * @param userId the ID of the current user
     * @param resourceDescription a description of the resource for error message
     * @throws ForbiddenException if the user does not own the resource
     */
    public static void verifyOwnership(UUID ownerId, UUID userId, String resourceDescription) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (!ownerId.equals(userId)) {
            throw new ForbiddenException("Access denied: " + resourceDescription + " does not belong to user");
        }
    }

    /**
     * Verifies that the current user owns the resource using String IDs.
     *
     * @param ownerId the ID of the resource owner (as String)
     * @param userId the ID of the current user (as String)
     * @param resourceDescription a description of the resource for error message
     * @throws ForbiddenException if the user does not own the resource
     */
    public static void verifyOwnership(String ownerId, String userId, String resourceDescription) {
        if (ownerId == null || userId == null) {
            throw new IllegalArgumentException("Owner ID and User ID cannot be null");
        }
        if (!ownerId.equals(userId)) {
            throw new ForbiddenException("Access denied: " + resourceDescription + " does not belong to user");
        }
    }
}
