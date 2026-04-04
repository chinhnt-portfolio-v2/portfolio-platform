package dev.chinh.portfolio.shared.config;

import dev.chinh.portfolio.auth.resolver.CurrentUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS configuration — explicit allow-list, no wildcards.
 *
 * <p>Allowed origins: production domains (chinhnt.xyz), legacy domains (chinh.dev),
 * and local dev server.
 * Never use "*" for allowedOrigins — architectural security constraint.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "https://chinh.dev",
                        "https://wallet.chinh.dev",
                        "https://portfolio.chinhnt.xyz",
                        "https://wallet.chinhnt.xyz",
                        "https://ledger.chinhnt.xyz",
                        "https://vault.chinhnt.xyz",
                        "https://codebin.chinhnt.xyz",
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "http://localhost:3001"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);

        // OAuth2 callback redirects
        registry.addMapping("/login/oauth2/**")
                .allowedOrigins(
                        "https://accounts.google.com"
                )
                .allowedMethods("GET")
                .allowedHeaders("*");

        registry.addMapping("/oauth2/**")
                .allowedOrigins(
                        "https://accounts.google.com",
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "http://localhost:3001"
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
