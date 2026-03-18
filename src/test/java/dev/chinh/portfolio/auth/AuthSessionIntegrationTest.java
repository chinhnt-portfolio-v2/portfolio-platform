package dev.chinh.portfolio.auth;

import dev.chinh.portfolio.TestcontainersConfiguration;
import dev.chinh.portfolio.auth.dto.LoginRequest;
import dev.chinh.portfolio.auth.dto.RefreshRequest;
import dev.chinh.portfolio.auth.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController - Session Refresh & Logout (Story 5.4)
 * Uses Testcontainers for PostgreSQL.
 * Requires Docker to be running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthSessionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String testEmail;
    private String testPassword = "Password1!";

    @BeforeEach
    void setUp() {
        testEmail = "integration-" + System.currentTimeMillis() + "@example.com";
    }

    // ==================== REFRESH TESTS ====================

    @Nested
    @DisplayName("POST /api/v1/auth/refresh - Integration")
    class RefreshTokenTests {

        @Test
        @DisplayName("4.1: End-to-end refresh flow - should return new tokens and rotate old one")
        void shouldRefreshTokenSuccessfully() throws Exception {
            // Step 1: Register a new user
            RegisterRequest registerRequest = new RegisterRequest(testEmail, testPassword);
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.email").value(testEmail));

            // Step 2: Login to get initial tokens
            LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andReturn();

            String initialAccessToken = extractJsonPath(loginResult, "accessToken");
            String initialRefreshToken = extractJsonPath(loginResult, "refreshToken");

            // Step 3: Refresh the token
            RefreshRequest refreshRequest = new RefreshRequest(initialRefreshToken);
            MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.refreshToken").exists())
                    .andReturn();

            String newAccessToken = extractJsonPath(refreshResult, "accessToken");
            String newRefreshToken = extractJsonPath(refreshResult, "refreshToken");

            // Verify new tokens are different from old tokens
            assertThat(newAccessToken).isNotEqualTo(initialAccessToken);
            assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);

            // Step 4: Verify old refresh token is INVALID (token rotation)
            RefreshRequest oldRefreshRequest = new RefreshRequest(initialRefreshToken);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(oldRefreshRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("4.1: Should return 401 for expired refresh token")
        void shouldReturn401ForExpiredToken() throws Exception {
            // Use a fake/expired token
            RefreshRequest request = new RefreshRequest("expired-or-fake-token");

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("4.1: Should return 400 for missing refresh token")
        void shouldReturn400ForMissingToken() throws Exception {
            String request = "{}";

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== LOGOUT TESTS ====================

    @Nested
    @DisplayName("POST /api/v1/auth/logout - Integration")
    class LogoutTests {

        @Test
        @DisplayName("4.2: End-to-end logout flow - should invalidate session and prevent replay")
        void shouldLogoutAndInvalidateSession() throws Exception {
            // Step 1: Register and login
            RegisterRequest registerRequest = new RegisterRequest(testEmail, testPassword);
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());

            LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String accessToken = extractJsonPath(loginResult, "accessToken");
            String refreshToken = extractJsonPath(loginResult, "refreshToken");

            // Step 2: Logout with access token
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logged out successfully"));

            // Step 3: Try to use old refresh token - should get 401 (replay attack prevented)
            RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refreshRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("4.2: Should return 401 when logout without authentication")
        void shouldReturn401WhenLogoutWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("4.2: Should return 401 when logout with invalid token")
        void shouldReturn401WhenLogoutWithInvalidToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== CONCURRENT TESTS ====================

    @Nested
    @DisplayName("Concurrent Refresh - Race Condition Tests")
    class ConcurrentRefreshTests {

        /**
         * Note: MockMvc is NOT thread-safe, so concurrent tests are unreliable.
         * This test verifies the session rotation logic works correctly.
         */
        @Test
        @DisplayName("4.3: Session rotation - after refresh, old token becomes invalid")
        void shouldRotateSessionOnRefresh() throws Exception {
            // Setup: Register and login
            RegisterRequest registerRequest = new RegisterRequest(testEmail, testPassword);
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());

            LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String refreshToken = extractJsonPath(loginResult, "refreshToken");

            // First refresh should succeed
            RefreshRequest request1 = new RefreshRequest(refreshToken);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request1)))
                    .andExpect(status().isOk());

            // Second refresh with same token should FAIL (token was rotated)
            RefreshRequest request2 = new RefreshRequest(refreshToken);
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request2)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("4.3: Concurrent refresh - verification via sequential test (MockMvc not thread-safe)")
        @Disabled("MockMvc is not thread-safe - use shouldRotateSessionOnRefresh for verification")
        void shouldHandleConcurrentRefreshRequests() throws Exception {
            // Setup: Register and login
            RegisterRequest registerRequest = new RegisterRequest(testEmail, testPassword);
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isCreated());

            LoginRequest loginRequest = new LoginRequest(testEmail, testPassword);
            MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            String refreshToken = extractJsonPath(loginResult, "refreshToken");

            // Concurrent refresh requests
            int concurrentRequests = 2;
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger unauthorizedCount = new AtomicInteger(0);

            for (int i = 0; i < concurrentRequests; i++) {
                executor.submit(() -> {
                    try {
                        RefreshRequest request = new RefreshRequest(refreshToken);
                        mockMvc.perform(post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                .andDo(result -> {
                                    int status = result.getResponse().getStatus();
                                    if (status == 200) {
                                        successCount.incrementAndGet();
                                    } else if (status == 401) {
                                        unauthorizedCount.incrementAndGet();
                                    }
                                });
                    } catch (Exception e) {
                        // Handle exception
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // Verify: Only 1 should succeed, 1 should fail (token was rotated by first request)
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(unauthorizedCount.get()).isEqualTo(1);
        }
    }

    // ==================== HELPER METHODS ====================

    private String extractJsonPath(MvcResult result, String path) throws Exception {
        String content = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(content);
        return node.at("/" + path.replace(".", "/")).asText();
    }
}
