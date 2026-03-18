package dev.chinh.portfolio.auth.controller;

import dev.chinh.portfolio.shared.error.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserDataController demonstrating user data isolation.
 */
@ExtendWith(MockitoExtension.class)
class UserDataControllerTest {

    @InjectMocks
    private UserDataController controller;

    @Nested
    @DisplayName("GET /api/v1/user/data")
    class GetUserDataTests {

        @Test
        @DisplayName("should return user's data when authenticated")
        void shouldReturnUserData() {
            UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

            ResponseEntity<List<String>> response = controller.getUserData(userId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsExactly("Project A", "Project B");
        }

        @Test
        @DisplayName("should return empty list when user has no data")
        void shouldReturnEmptyListForUnknownUser() {
            UUID userId = UUID.randomUUID();

            ResponseEntity<List<String>> response = controller.getUserData(userId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/user/data/{dataId}")
    class GetUserDataItemTests {

        @Test
        @DisplayName("should return data when user owns the resource")
        void shouldReturnDataWhenOwner() {
            UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            String dataId = "11111111-1111-1111-1111-111111111111";

            ResponseEntity<Map<String, Object>> response = controller.getUserDataItem(userId, dataId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("id")).isEqualTo(dataId);
        }

        @Test
        @DisplayName("should throw ForbiddenException when user does not own the resource")
        void shouldThrowForbiddenWhenNotOwner() {
            UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            String dataId = "22222222-2222-2222-2222-222222222222"; // Belongs to different user

            assertThatThrownBy(() -> controller.getUserDataItem(userId, dataId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("does not belong to user");
        }

        @Test
        @DisplayName("should return 404 for invalid dataId format")
        void shouldReturn404ForInvalidDataId() {
            UUID userId = UUID.randomUUID();
            String invalidDataId = "not-a-uuid";

            ResponseEntity<Map<String, Object>> response = controller.getUserDataItem(userId, invalidDataId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
