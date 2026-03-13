package dev.chinh.portfolio.shared.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for DemoAppRegistry - tests the POJO binding without Spring context.
 * This test runs without Docker/Testcontainers.
 */
class DemoAppRegistryTest {

    @Test
    void getApps_bindingProperties_correctlyMapsFields() {
        // Manually create and configure registry (simulates what @ConfigurationProperties does)
        DemoApp app1 = new DemoApp();
        app1.setId("wallet-app");
        app1.setName("Wallet App");
        app1.setHealthEndpoint("https://wallet.chinh.dev/health");
        app1.setPollIntervalSeconds(60);

        DemoAppRegistry registry = new DemoAppRegistry();
        registry.setApps(List.of(app1));

        assertThat(registry.getApps()).hasSize(1);
        DemoApp app = registry.getApps().get(0);
        assertThat(app.getId()).isEqualTo("wallet-app");
        assertThat(app.getName()).isEqualTo("Wallet App");
        assertThat(app.getHealthEndpoint()).isEqualTo("https://wallet.chinh.dev/health");
        assertThat(app.getPollIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void getApps_emptyList_defaultConstructor() {
        DemoAppRegistry registry = new DemoAppRegistry();

        assertThat(registry.getApps()).isEmpty();
    }

    @Test
    void getApps_defaultPollInterval_is60() {
        // Java field initializer runs on object creation: private int pollIntervalSeconds = 60;
        DemoApp app = new DemoApp();

        // Verify default value from field initializer
        assertThat(app.getPollIntervalSeconds()).isEqualTo(60);
    }
}
