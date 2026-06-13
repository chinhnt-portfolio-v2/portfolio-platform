package dev.chinh.portfolio.auth.oauth2;

import java.net.URI;
import java.util.Set;

/**
 * Validates OAuth post-login redirect targets by EXACT host match.
 *
 * <p>The login flow lets the caller pass a {@code redirect_uri}, and the backend
 * appends the freshly-issued access/refresh tokens to that URL. A loose match
 * (e.g. substring {@code contains}) would let an attacker pass
 * {@code https://localhost.attacker.com} or {@code https://wallet.chinhnt.xyz.evil.com}
 * and have live tokens delivered to their host — an account-takeover primitive.
 * This validator therefore matches the parsed URI host exactly against an allowlist
 * (plus the mobile {@code walletapp://} custom scheme).
 */
public final class RedirectUriValidator {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
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

    /** Custom scheme for the mobile app deep link (host part is app-controlled). */
    private static final String ALLOWED_APP_SCHEME = "walletapp";

    private RedirectUriValidator() {
    }

    /**
     * @param url the redirect target to validate
     * @return true only if the URL's host exactly matches an allowed host, or the
     *         scheme is the mobile app's custom scheme. Malformed URLs return false.
     */
    public static boolean isAllowed(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (ALLOWED_APP_SCHEME.equalsIgnoreCase(scheme)) {
                return true;
            }
            // Host allowlist applies only to web redirects.
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            return host != null && ALLOWED_HOSTS.contains(host.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
