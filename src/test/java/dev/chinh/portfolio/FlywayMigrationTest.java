package dev.chinh.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying Flyway V1__create_core_schema.sql creates all 4 tables
 * with correct constraints (FK, UNIQUE).
 *
 * Requires Docker to be running for Testcontainers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allTablesExist() {
        List<String> tables = List.of("users", "sessions", "contact_submissions", "project_health");
        for (String table : tables) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, table);
            assertThat(count).as("Table '%s' should exist after Flyway migration", table).isEqualTo(1);
        }
    }

    @Test
    void sessionsForeignKeyConstraintWorks() {
        // Insert a valid user
        jdbcTemplate.execute("""
            INSERT INTO users (id, email, provider, role)
            VALUES (gen_random_uuid(), 'fk-test@test.com', 'LOCAL', 'USER')
            """);
        // Attempt to insert a session referencing a non-existent user UUID — must fail FK constraint
        assertThatThrownBy(() ->
            jdbcTemplate.execute("""
                INSERT INTO sessions (id, user_id, refresh_token, expires_at)
                VALUES (gen_random_uuid(), gen_random_uuid(), 'orphan-token', NOW() + INTERVAL '7 days')
                """)
        ).isInstanceOf(Exception.class)
         .hasMessageContaining("sessions");
    }

    @Test
    void projectHealthUniqueSlugConstraintWorks() {
        jdbcTemplate.execute("""
            INSERT INTO project_health (id, project_slug) VALUES (gen_random_uuid(), 'wallet-app-unique-test')
            """);
        assertThatThrownBy(() ->
            jdbcTemplate.execute("""
                INSERT INTO project_health (id, project_slug) VALUES (gen_random_uuid(), 'wallet-app-unique-test')
                """)
        ).isInstanceOf(Exception.class);
    }

    @Test
    void usersEmailUniqueConstraintWorks() {
        jdbcTemplate.execute("""
            INSERT INTO users (id, email, provider, role)
            VALUES (gen_random_uuid(), 'unique-email@test.com', 'LOCAL', 'USER')
            """);
        assertThatThrownBy(() ->
            jdbcTemplate.execute("""
                INSERT INTO users (id, email, provider, role)
                VALUES (gen_random_uuid(), 'unique-email@test.com', 'LOCAL', 'USER')
                """)
        ).isInstanceOf(Exception.class);
    }

    @Test
    void sessionsCascadeDeleteWorks() {
        // Insert user and session
        jdbcTemplate.execute("""
            INSERT INTO users (id, email, provider, role)
            VALUES ('a1b2c3d4-0000-0000-0000-000000000001', 'cascade-test@test.com', 'LOCAL', 'USER')
            """);
        jdbcTemplate.execute("""
            INSERT INTO sessions (id, user_id, refresh_token, expires_at)
            VALUES (gen_random_uuid(), 'a1b2c3d4-0000-0000-0000-000000000001', 'cascade-token', NOW() + INTERVAL '7 days')
            """);
        // Delete user — sessions should cascade delete
        jdbcTemplate.execute("DELETE FROM users WHERE id = 'a1b2c3d4-0000-0000-0000-000000000001'");
        Integer sessionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessions WHERE user_id = 'a1b2c3d4-0000-0000-0000-000000000001'",
            Integer.class);
        assertThat(sessionCount).as("Sessions should be cascade-deleted when user is deleted").isZero();
    }
}
