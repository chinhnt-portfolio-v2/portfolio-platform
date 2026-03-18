package dev.chinh.portfolio.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.portfolio.shared.error.ErrorDetail;
import dev.chinh.portfolio.shared.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String message = authException.getMessage() != null
                ? authException.getMessage()
                : "Authentication required";

        ErrorResponse errorResponse = new ErrorResponse(
                new ErrorDetail("UNAUTHORIZED", message)
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
