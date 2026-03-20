package dev.chinh.portfolio.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DemoAppRegistry — YAML binding and field mapping.
 *
 * <p>These tests run WITHOUT Spring context and WITHOUT Docker/Testcontainers.
 * They parse the actual {@code showcase.yml} file directly using Jackson.
 */
class DemoAppRegistryTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    // ── showcase.yml schema ───────────────────────────────────────────────────

    @Test
    void showcaseYaml_twoEntries_parsedCorrectly() throws Exception {
        ShowcasesDto dto = YAML.readValue(
                getClass().getResource("/showcase.yml"),
                ShowcasesDto.class
        );

        assertThat(dto.apps).hasSizeGreaterThanOrEqualTo(2);

        DemoAppRegistry.DemoApp wallet = dto.apps.stream()
                .filter(a -> "wallet-app".equals(a.getSlug()))
                .findFirst()
                .orElseThrow();

        assertThat(wallet.getName()).isEqualTo("Wallet App");
        assertThat(wallet.getHealthEndpoint()).isEqualTo("https://wallet.chinh.dev/health");
        assertThat(wallet.getDemoUrl()).isEqualTo("https://wallet.chinh.dev");
    }

    @Test
    void showcaseYaml_slugsMatchProjectsTs() throws Exception {
        // Story 6.4.2 AC: slug values in showcase.yml MUST match projects.ts slugs.
        // Wallet App and Portfolio v2 are defined in both configs.
        ShowcasesDto dto = YAML.readValue(
                getClass().getResource("/showcase.yml"),
                ShowcasesDto.class
        );

        List<String> slugs = dto.apps.stream()
                .map(DemoAppRegistry.DemoApp::getSlug)
                .toList();

        assertThat(slugs).contains("wallet-app", "portfolio-v2");
    }

    // ── POJO construction ─────────────────────────────────────────────────────

    @Test
    void demoApp_setterGetter_roundTrip() {
        DemoAppRegistry.DemoApp app = new DemoAppRegistry.DemoApp();
        app.setSlug("test-app");
        app.setName("Test App");
        app.setHealthEndpoint("https://test.chinh.dev/health");
        app.setDemoUrl("https://test.chinh.dev");

        assertThat(app.getSlug()).isEqualTo("test-app");
        assertThat(app.getName()).isEqualTo("Test App");
        assertThat(app.getHealthEndpoint()).isEqualTo("https://test.chinh.dev/health");
        assertThat(app.getDemoUrl()).isEqualTo("https://test.chinh.dev");
    }

    @Test
    void demoApp_demoUrl_optionalNullByDefault() {
        DemoAppRegistry.DemoApp app = new DemoAppRegistry.DemoApp();
        assertThat(app.getDemoUrl()).isNull();
    }

    @Test
    void demoAppRegistry_getApps_returnsUnmodifiableList() {
        // Programmatic loading via ResourceLoader tests actual startup behavior
        DemoAppRegistry registry = new DemoAppRegistry(new DefaultResourceLoader());
        registry.loadShowcase(); // loads from classpath:showcase.yml

        List<DemoAppRegistry.DemoApp> apps = registry.getApps();
        assertThat(apps).isNotEmpty(); // showcase.yml has 2 entries

        // Verify the list is unmodifiable (copyOf in loadShowcase)
        assertThat(apps).isUnmodifiable();
    }

    @Test
    void demoAppRegistry_getApps_emptyByDefault_beforeLoadShowcase() {
        // @PostConstruct is NOT called in unit tests — apps stays empty ArrayList
        DemoAppRegistry registry = new DemoAppRegistry(new DefaultResourceLoader());
        assertThat(registry.getApps()).isEmpty();
    }

    // ── Malformed YAML handling ───────────────────────────────────────────────

    @Test
    void showcaseYaml_malformedYml_throwsException() {
        String badYaml = """
                apps:
                  - slug: wallet-app
                    name: "Wallet App"
                      healthEndpoint: "https://wallet.chinh.dev/health"  # bad indent
                """;

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> YAML.readValue(badYaml, ShowcasesDto.class)
        );
    }

    // ── DTO mirror (same as DemoAppRegistry inner class) ────────────────────────

    /** Must mirror ShowcasesDto from DemoAppRegistry for Jackson deserialization. */
    static class ShowcasesDto {
        public List<DemoAppRegistry.DemoApp> apps;
    }
}
