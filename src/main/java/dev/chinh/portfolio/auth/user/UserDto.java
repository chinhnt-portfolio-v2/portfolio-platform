package dev.chinh.portfolio.auth.user;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for User — no passwordHash field exposed.
 */
public record UserDto(
        UUID id,
        String email,
        AuthProvider provider,
        UserRole role,
        Instant createdAt
) {}
