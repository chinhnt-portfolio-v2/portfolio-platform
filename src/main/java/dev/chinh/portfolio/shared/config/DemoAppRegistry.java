package dev.chinh.portfolio.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads and exposes demo app entries from {@code showcase.yml}.
 *
 * <p>Configuration is loaded programmatically from the classpath resource
 * ({@code classpath:showcase.yml}) in {@link #loadShowcase()}, bypassing
 * Spring's {@code spring.config.import} mechanism. This avoids profile-specific
 * import ordering issues in Spring Boot.
 *
 * <p>Schema (exact field names — no deviation):
 * <pre>
 * apps:
 *   - slug: wallet-app
 *     name: "Wallet App"
 *     healthEndpoint: "https://wallet.chinh.dev/health"
 *     demoUrl: "https://wallet.chinh.dev"
 * </pre>
 *
 * <p>Fail-fast: if the YAML is malformed or a required field is missing,
 * an error is logged and the app list remains empty. This is intentional —
 * a missing or misconfigured showcase.yml should not crash the application.
 */
@Component
public class DemoAppRegistry {

    private static final Logger log = LoggerFactory.getLogger(DemoAppRegistry.class);

    private final ResourceLoader resourceLoader;

    private List<DemoApp> apps = new ArrayList<>();

    @Autowired
    public DemoAppRegistry(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Load showcase.yml from the classpath.
     * Called once at startup via {@link PostConstruct}.
     */
    @PostConstruct
    public void loadShowcase() {
        try {
            Resource resource = resourceLoader.getResource("classpath:showcase.yml");
            if (!resource.exists()) {
                log.error("showcase.yml not found on classpath — DemoAppRegistry will have 0 apps. " +
                        "This should never happen. Check your JAR build.");
                this.apps = List.of();
                return;
            }

            try (InputStream input = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                ShowcasesDto dto = mapper.readValue(input, ShowcasesDto.class);
                if (dto.apps != null) {
                    this.apps = List.copyOf(dto.apps);
                    log.info("DemoAppRegistry loaded {} app(s): {}",
                            this.apps.size(),
                            this.apps.stream()
                                    .map(DemoApp::getSlug)
                                    .toList());
                } else {
                    this.apps = List.of();
                    log.info("DemoAppRegistry: showcase.yml has no 'apps' entries.");
                }
            }

        } catch (IOException e) {
            log.error("Failed to load showcase.yml — DemoAppRegistry will have 0 apps. " +
                    "Error: {}", e.getMessage());
            this.apps = List.of();
        }
    }

    /**
     * Return an immutable view of all registered demo apps.
     *
     * @return list of apps; never {@code null}, may be empty
     */
    public List<DemoApp> getApps() {
        return Collections.unmodifiableList(apps);
    }

    /**
     * Spring Boot calls this when binding {@code @ConfigurationProperties}.
     * Populated via {@link #loadShowcase()} instead — this is a no-op guard.
     */
    public void setApps(List<DemoApp> apps) {
        // Ignored — apps are loaded in @PostConstruct via ResourceLoader.
        // Spring may call this during config binding; our @PostConstruct overwrites it.
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /**
     * Root of the showcase.yml YAML tree.
     */
    private static class ShowcasesDto {
        public List<DemoApp> apps;
    }

    /**
     * Single demo app entry bound from {@code showcase.yml}.
     *
     * <p>Required fields ({@code @NotBlank}) are enforced at load time
     * — missing {@code slug} or {@code healthEndpoint} results in an
     * error log and an empty registry.
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
