package dev.chinh.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the OpenAPI spec is accessible (AC#3 of Story 2.3).
 * Requires Docker Desktop running — Testcontainers starts a PostgreSQL container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiAvailabilityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiSpec_isAccessibleAtV3ApiDocs() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"openapi\"").contains("\"paths\"");
    }

    @Test
    void swaggerUI_isAccessibleAtApiDocs() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("swagger-ui");
    }
}
