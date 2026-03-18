package dev.chinh.portfolio.auth.dto;

/**
 * Response DTO for successful authentication (login).
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
        public static AuthResponse of(String accessToken, String refreshToken) {
                return new AuthResponse(accessToken, refreshToken, "Bearer");
        }
}
