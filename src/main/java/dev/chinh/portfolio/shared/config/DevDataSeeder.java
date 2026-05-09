package dev.chinh.portfolio.shared.config;

import dev.chinh.portfolio.auth.dto.RegisterRequest;
import dev.chinh.portfolio.auth.user.UserRepository;
import dev.chinh.portfolio.auth.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Seeds a hardcoded test user on local profile startup.
 * Credentials: test@example.com / Test1234!
 * Idempotent — skips if user already exists.
 */
@Component
@Profile("local")
public class DevDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    static final String TEST_EMAIL = "test@example.com";
    static final String TEST_PASSWORD = "Test1234!";

    private final UserService userService;
    private final UserRepository userRepository;

    public DevDataSeeder(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void seed() {
        if (userRepository.existsByEmail(TEST_EMAIL)) {
            log.info("[DEV] Test user already exists: {}", TEST_EMAIL);
            return;
        }
        try {
            userService.register(new RegisterRequest(TEST_EMAIL, TEST_PASSWORD));
            log.info("[DEV] Test user created: {} / {}", TEST_EMAIL, TEST_PASSWORD);
        } catch (UserService.EmailAlreadyExistsException e) {
            log.info("[DEV] Test user already exists (race): {}", TEST_EMAIL);
        }
    }
}
