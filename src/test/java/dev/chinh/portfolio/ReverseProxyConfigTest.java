package dev.chinh.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.ForwardedHeaderFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies Spring Boot reverse proxy config (AC#1, AC#6 of Story 2.4.1).
 *
 * <p>Validates that {@code server.forward-headers-strategy=FRAMEWORK} correctly installs
 * {@link ForwardedHeaderFilter} so Nginx-proxied requests have scheme/host/IP correctly resolved.
 *
 * <p>Requires Docker Desktop running — Testcontainers starts a PostgreSQL container.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "server.forward-headers-strategy=FRAMEWORK",
                "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
                "server.tomcat.remoteip.remote-ip-header=x-forwarded-for",
                "server.tomcat.remoteip.protocol-header=x-forwarded-proto",
                "server.tomcat.redirect-context-root=false"
        }
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReverseProxyConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    void forwardedHeaderFilter_isRegisteredWhenStrategyIsFramework() {
        // In Spring Boot 3.x, server.forward-headers-strategy=FRAMEWORK registers the filter
        // via FilterRegistrationBean<ForwardedHeaderFilter>, not directly as a bean of that type.
        // Without this config, no FilterRegistrationBean wrapping ForwardedHeaderFilter exists.
        boolean hasForwardedHeaderFilter = applicationContext
                .getBeansOfType(FilterRegistrationBean.class)
                .values().stream()
                .anyMatch(reg -> reg.getFilter() instanceof ForwardedHeaderFilter);
        assertThat(hasForwardedHeaderFilter)
                .as("FilterRegistrationBean<ForwardedHeaderFilter> must be present when forward-headers-strategy=FRAMEWORK")
                .isTrue();
    }

    @Test
    void forwardedHeaderFilter_beanNamedForwardedHeaderFilterExists() {
        // Spring Boot registers this bean under the well-known name "forwardedHeaderFilter".
        // CI/CD and future config changes can rely on this name being stable.
        assertThat(applicationContext.containsBean("forwardedHeaderFilter"))
                .as("Bean 'forwardedHeaderFilter' must exist when forward-headers-strategy=FRAMEWORK")
                .isTrue();
    }

    @Test
    void actuatorHealth_withForwardedProtoHttps_returns200() throws Exception {
        // Simulate a request arriving from Nginx with X-Forwarded-Proto: https.
        // The ForwardedHeaderFilter rewrites the scheme; the app should process normally.
        mockMvc.perform(get("/actuator/health")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-For", "1.2.3.4")
                        .header("X-Real-IP", "1.2.3.4"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealth_withoutForwardedHeaders_stillReturns200() throws Exception {
        // Direct requests (e.g. health checks on localhost) must also work.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void internalProxies_isSetToLocalhostOnly_notWildcard() {
        // Security guardrail: internal-proxies must be restricted to 127.0.0.1 (localhost Nginx only).
        // A wildcard value (e.g., ".*") would allow clients to spoof X-Forwarded-For, impersonating
        // trusted IPs and bypassing IP-based rate limiting or access controls.
        String internalProxies = environment.getProperty("server.tomcat.remoteip.internal-proxies");
        assertThat(internalProxies)
                .as("internal-proxies must be set (not null) — prevents spoofing if Nginx moves to different host")
                .isNotNull();
        assertThat(internalProxies)
                .as("internal-proxies must not be a wildcard — would allow X-Forwarded-For spoofing")
                .doesNotMatch("^[.*]+$");
        assertThat(internalProxies)
                .as("internal-proxies should match localhost 127.0.0.1")
                .contains("127");
    }
}
