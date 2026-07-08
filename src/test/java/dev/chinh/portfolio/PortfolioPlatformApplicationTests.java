package dev.chinh.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spring Boot application context load test.
 *
 * <p>Uses Testcontainers to spin up a PostgreSQL container so the full application
 * context (including JPA + Flyway) can be validated without an external database.
 *
 * <p>Requires Docker to be running. Docker Desktop or Colima works.
 *
 * DISABLED: Postgres-only — superseded by Turso/SQLite migration (Phase 3/4).
 */
@org.junit.jupiter.api.Disabled("Postgres-only — superseded by Turso migration")
@SpringBootTest
@Testcontainers
class PortfolioPlatformApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Verifies full Spring Boot context starts: JPA, Flyway, Security, WebSocket, Actuator
    }
}
