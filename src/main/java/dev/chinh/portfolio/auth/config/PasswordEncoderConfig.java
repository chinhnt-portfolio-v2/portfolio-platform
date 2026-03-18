package dev.chinh.portfolio.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration for authentication.
 * Provides PasswordEncoder with bcrypt cost factor 12.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Password encoder with bcrypt strength 12 (NFR-S4 requirement).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
