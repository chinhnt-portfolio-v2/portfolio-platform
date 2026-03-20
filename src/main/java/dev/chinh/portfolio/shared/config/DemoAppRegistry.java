package dev.chinh.portfolio.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads and exposes demo app entries from {@code showcase.yml}.
 *
 * <p>Loaded automatically via {@code spring.config.import=classpath:showcase.yml} in
 * {@code application.yml} and bound by Spring Boot's {@code @ConfigurationProperties}
 * mechanism.
 *
 * <p>Schema (exact field names — no deviation):
 * <pre>
 * showcase:
 *   apps:
 *     - slug: wallet-app
 *       name: "Wallet App"
 *       healthEndpoint: "https://wallet.chinh.dev/health"
 *       demoUrl: "https://wallet.chinh.dev"
 * </pre>
 *
 * <p>Fail-fast: if a loaded entry is missing a required field
 * ({@code slug} or {@code healthEndpoint}), Spring throws a
 * {@code BeanCreationException} at startup with a clear validation message.
 * If the YAML itself is malformed, Spring's config import mechanism fails-fast
 * before this class is instantiated.
 */
@Component
@ConfigurationProperties(prefix = "showcase")
@Validated
public class DemoAppRegistry {

    private List<DemoApp> apps = new ArrayList<>();

    /**
     * Return an immutable view of all registered demo apps.
     *
     * @return list of apps; never {@code null}, may be empty
     */
    public List<DemoApp> getApps() {
        return Collections.unmodifiableList(apps);
    }

    public void setApps(List<DemoApp> apps) {
        this.apps = apps;
    }

    // ── Inner DTO ───────────────────────────────────────────────────────────────

    /**
     * Single demo app entry bound from {@code showcase.yml}.
     *
     * <p>Required fields ({@code @NotBlank}) are enforced by the {@code @Validated}
     * annotation on {@code DemoAppRegistry} — missing values cause a fail-fast
     * {@code BeanCreationException} at startup.
     */
    public static class DemoApp {

        @NotBlank(message = "slug is required in showcase.yml")
        private String slug;

        @NotBlank(message = "name is required in showcase.yml")
        private String name;

        @NotBlank(message = "healthEndpoint is required in showcase.yml")
        private String healthEndpoint;

        /** Optional — used for admin display only, not polled. */
        private String demoUrl;

        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getHealthEndpoint() { return healthEndpoint; }
        public void setHealthEndpoint(String healthEndpoint) { this.healthEndpoint = healthEndpoint; }

        public String getDemoUrl() { return demoUrl; }
        public void setDemoUrl(String demoUrl) { this.demoUrl = demoUrl; }
    }
}
