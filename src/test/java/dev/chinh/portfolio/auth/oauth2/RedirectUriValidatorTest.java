package dev.chinh.portfolio.auth.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedirectUriValidator - exact host match")
class RedirectUriValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://wallet.chinhnt.xyz",
            "https://wallet.chinhnt.xyz/oauth/callback",
            "https://chinh.dev",
            "https://wallet.chinh.dev",
            "http://localhost:5173",
            "http://localhost:62751/callback",
            "walletapp://callback",
            "walletapp://oauth/done"
    })
    @DisplayName("allows known hosts and the mobile app scheme")
    void allowsLegitimateTargets(String url) {
        assertThat(RedirectUriValidator.isAllowed(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://localhost.attacker.com",            // substring 'localhost' must NOT pass
            "https://wallet.chinhnt.xyz.evil.com",       // suffix attack
            "https://evilchinh.dev",                     // 'chinh.dev' substring must NOT pass
            "https://evil.com/?next=localhost",          // allowed token only in query
            "https://evil.com#wallet.chinhnt.xyz",       // allowed token only in fragment
            "http://127.0.0.1:9999/callback",            // not in allowlist (only 'localhost')
            "ftp://wallet.chinhnt.xyz",                  // non-web scheme rejected
            "javascript:alert(1)",                       // dangerous scheme rejected
            "not a url",
            ""
    })
    @DisplayName("rejects spoofed / out-of-list targets")
    void rejectsAttackTargets(String url) {
        assertThat(RedirectUriValidator.isAllowed(url)).isFalse();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThat(RedirectUriValidator.isAllowed(null)).isFalse();
    }
}
