package dev.chinh.portfolio.auth.dto;

import dev.chinh.portfolio.auth.user.AuthProvider;
import dev.chinh.portfolio.auth.user.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO returned by GET /api/v1/auth/me — current authenticated user profile.
 * Excludes sensitive fields such as passwordHash.
 */
public record UserProfileResponse(
        UUID id,
        String email,
        String name,
        String picture,
        AuthProvider provider,
        UserRole role,
        Instant createdAt
) {}
