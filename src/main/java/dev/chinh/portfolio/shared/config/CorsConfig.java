package dev.chinh.portfolio.shared.config;

import dev.chinh.portfolio.auth.resolver.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS configuration — pattern-based allow-list, no broad wildcards.
 *
 * <p>Allowed origins: production domains (*.chinhnt.xyz, *.chinh.dev),
 * and local dev servers.
 * Uses allowedOriginPatterns for wildcard support with credentials.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "https://*.chinhnt.xyz",
                        "https://*.chinh.dev",
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "http://localhost:3000",
                        "http://localhost:3001"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600L);

        // OAuth2 callback redirects
        registry.addMapping("/login/oauth2/**")
                .allowedOriginPatterns(
                        "https://accounts.google.com"
                )
                .allowedMethods("GET")
                .allowedHeaders("*");

        registry.addMapping("/oauth2/**")
                .allowedOriginPatterns(
                        "https://accounts.google.com",
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "http://localhost:3000",
                        "http://localhost:3001",
                        "https://*.chinhnt.xyz",
                        "https://*.chinh.dev"
                )
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
