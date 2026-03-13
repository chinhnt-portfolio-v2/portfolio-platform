package dev.chinh.portfolio.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(DemoAppRegistry.class)
public class AppConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .defaultHeader("User-Agent", "portfolio-platform/1.0")
                .build();
    }
}
