package dev.chinh.portfolio.auth;

import dev.chinh.portfolio.auth.jwt.JwtService;
import dev.chinh.portfolio.auth.session.Session;
import dev.chinh.portfolio.auth.session.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class OAuth2Controller {

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private static final String STATE_SEPARATOR = "|";

    @Value("${app.frontend.url:https://wallet.chinhnt.xyz}")
    private String defaultFrontendUrl;

    private static final Set<String> ALLOWED_REDIRECT_DOMAINS = Set.of(
            "wallet.chinhnt.xyz",
            "vault.chinhnt.xyz",
            "ledger.chinhnt.xyz",
            "codebin.chinhnt.xyz",
            "portfolio.chinhnt.xyz",
            "devquiz.chinhnt.xyz",
            "quiz.chinhnt.xyz",
            "chinh.dev",
            "wallet.chinh.dev",
            "localhost"
    );

    public OAuth2Controller(
            ClientRegistrationRepository clientRegistrationRepository,
            JwtService jwtService,
            SessionService sessionService) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
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

        String targetUrl = (redirect_uri != null && !redirect_uri.isBlank()) ? redirect_uri : defaultFrontendUrl;
        String state = encodeState(targetUrl);

        String authorizationUrl = authorizationUri
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=openid%20profile%20email"
                + "&state=" + state;

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", authorizationUrl)
                .build();
    }

    @GetMapping("/oauth2/callback/{provider}")
    public ResponseEntity<?> oauth2Callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {

        String frontendUrl = resolveFrontendUrl(state);

        if (error != null && !error.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendUrl + "?error=" + URLEncoder.encode(error, StandardCharsets.UTF_8))
                    .build();
        }

        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(provider);
        if (registration == null || code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", Map.of("code", "BAD_REQUEST", "message", "Missing code or provider")));
        }

        String tokenEndpoint = registration.getProviderDetails().getTokenUri();
        String clientId = registration.getClientId();
        String clientSecret = (String) registration.getClientSecret();
        String callbackUrl = request.getScheme() + "://" + request.getHeader("Host") + "/api/v1/auth/oauth2/callback/" + provider;

        try {
            java.net.URL url = new java.net.URL(tokenEndpoint);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            String params = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            conn.getOutputStream().write(params.getBytes(StandardCharsets.UTF_8));

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", Map.of("code", "TOKEN_EXCHANGE_FAILED", "message", "Token exchange failed: " + responseCode)));
            }

            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> tokenResponse = mapper.readValue(responseBody, Map.class);

            String accessToken = (String) tokenResponse.get("access_token");
            String tokenType = (String) tokenResponse.get("token_type");

            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", Map.of("code", "NO_ACCESS_TOKEN", "message", "No access token received")));
            }

            // Fetch user info
            String userInfoUri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
            java.net.URL userInfoUrl = new java.net.URL(userInfoUri);
            java.net.HttpURLConnection userInfoConn = (java.net.HttpURLConnection) userInfoUrl.openConnection();
            userInfoConn.setRequestProperty("Authorization", "Bearer " + accessToken);

            int userInfoCode = userInfoConn.getResponseCode();
            if (userInfoCode != 200) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", Map.of("code", "USER_INFO_FAILED", "message", "Could not fetch user info")));
            }

            String userInfoBody = new String(userInfoConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> userInfo = mapper.readValue(userInfoBody, Map.class);

            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String sub = (String) userInfo.get("sub");

            if (email == null || email.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", Map.of("code", "NO_EMAIL", "message", "No email in OAuth response")));
            }

            // Create or update user
            dev.chinh.portfolio.auth.user.User user = sessionService.findOrCreateOAuthUser(
                    email, name, sub, provider.toUpperCase());

            // Generate JWT + session
            String jwt = jwtService.generateAccessToken(user);
            Session session = sessionService.createSession(user);

            // Redirect to frontend with tokens
            String redirectUrl = frontendUrl
                    + "?accessToken=" + jwt
                    + "&refreshToken=" + session.getRefreshToken()
                    + "&tokenType=" + tokenType;

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", Map.of("code", "INTERNAL_ERROR", "message", e.getMessage())));
        }
    }

    private String resolveFrontendUrl(String state) {
        if (state == null || state.isBlank()) {
            return defaultFrontendUrl;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            int sepIndex = decoded.lastIndexOf(STATE_SEPARATOR);
            String url = sepIndex > 0 ? decoded.substring(0, sepIndex) : decoded;
            boolean allowed = ALLOWED_REDIRECT_DOMAINS.stream().anyMatch(url::contains);
            return allowed ? url : defaultFrontendUrl;
        } catch (Exception e) {
            return defaultFrontendUrl;
        }
    }

    private String encodeState(String redirectUrl) {
        String raw = redirectUrl + STATE_SEPARATOR + System.currentTimeMillis();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
