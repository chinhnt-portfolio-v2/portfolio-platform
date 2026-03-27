package dev.chinh.portfolio.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class OAuth2Controller {

    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${app.frontend.url:https://chinhnt-portfolio.vercel.app}")
    private String defaultFrontendUrl;

    public OAuth2Controller(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping("/oauth2/login/{provider}")
    public ResponseEntity<?> oauth2Login(
            @PathVariable String provider,
            @RequestParam(required = false) String redirect_uri,
            HttpServletRequest request) {

        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(provider);
        if (registration == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", Map.of("code", "NOT_FOUND", "message", "Provider not supported: " + provider)));
        }

        String baseUrl = request.getScheme() + "://" + request.getHeader("Host");
        String authorizationUri = registration.getProviderDetails().getAuthorizationUri();
        String clientId = registration.getClientId();
        String callbackUrl = baseUrl + "/api/v1/auth/oauth2/callback/" + provider;

        // Store redirect_uri in session or state parameter
        String state = redirect_uri != null ? redirect_uri : defaultFrontendUrl;

        String authorizationUrl = authorizationUri
                + "?client_id=" + clientId
                + "&redirect_uri=" + URI.encode(callbackUrl)
                + "&response_type=code"
                + "&scope=openid%20profile%20email"
                + "&state=" + URI.encode(state);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", authorizationUrl)
                .build();
    }
}
