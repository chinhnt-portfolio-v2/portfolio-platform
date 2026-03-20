package dev.chinh.portfolio.shared.ratelimit;

import dev.chinh.portfolio.shared.error.ErrorDetail;
import dev.chinh.portfolio.shared.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter that enforces the general API rate limit (100 req / min / IP).
 *
 * <p>Applies to all requests. Excluded paths are handled via early return before
 * rate limit check.
 *
 * <p>Excluded paths (no rate limiting):
 * <ul>
 *   <li>Actuator health checks ({@code /actuator/health})</li>
 *   <li>WebSocket upgrades ({@code /ws/**})</li>
 *   <li>OpenAPI docs ({@code /api-docs/**}, {@code /swagger-ui/**})</li>
 * </ul>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Exclude health checks, WebSocket upgrades, and API docs
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        if (!rateLimitService.tryGeneral(clientIp)) {
            log.debug("General rate limit exceeded for IP: {} on path: {}", clientIp, path);
            writeRateLimitResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExcluded(String path) {
        return path.startsWith("/actuator/health")
                || path.startsWith("/ws/")
                || path.startsWith("/api-docs/")
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html");
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                new ErrorResponse(new ErrorDetail(
                        "RATE_LIMIT_EXCEEDED",
                        "Too many requests. Please wait a moment before retrying."
                )).toString()
        );
    }
}
