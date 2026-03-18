package dev.chinh.portfolio.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for OAuth2 login endpoints.
 */
@Controller
@RequestMapping("/api/v1/auth/oauth2")
@Tag(name = "OAuth2 Authentication", description = "OAuth2 login endpoints")
public class OAuth2Controller {

    /**
     * Initiate Google OAuth2 login flow.
     * Redirects to Google for authentication.
     */
    @Operation(summary = "Google OAuth2 Login", description = "Redirects to Google for OAuth2 authentication")
    @ApiResponse(responseCode = "302", description = "Redirects to Google login")
    @GetMapping("/login/google")
    public String loginGoogle() {
        // Spring Security will handle the redirect to Google
        // The actual redirect is handled by OAuth2AuthorizationRequestRedirectFilter
        // This endpoint exists for explicit clarity and documentation
        return "redirect:/oauth2/authorization/google";
    }
}
