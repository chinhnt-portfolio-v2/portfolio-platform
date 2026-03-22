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
 * <p>Allowed origins: production domain, wallet subdomain, and local dev server.
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
                        "https://chinhnt-portfolio.vercel.app",
                        "http://localhost:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
