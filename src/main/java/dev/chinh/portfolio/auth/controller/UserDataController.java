package dev.chinh.portfolio.auth.controller;

import dev.chinh.portfolio.auth.annotation.CurrentUser;
import dev.chinh.portfolio.auth.service.OwnershipHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demo controller demonstrating user data isolation pattern.
 * This controller shows how to use @CurrentUser and OwnershipHelper
 * to ensure users can only access their own data.
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserDataController {

    // Simulated user data store - in real app this would be a database
    private static final Map<UUID, List<String>> USER_DATA_STORE = Map.of(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), List.of("Project A", "Project B"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"), List.of("Project C", "Project D")
    );

    /**
     * Get current user's data - demonstrates @CurrentUser usage
     */
    @GetMapping("/data")
    public ResponseEntity<List<String>> getUserData(@CurrentUser UUID userId) {
        List<String> data = USER_DATA_STORE.get(userId);
        if (data == null) {
            data = List.of();
        }
        return ResponseEntity.ok(data);
    }

    /**
     * Get specific data item with ownership check - demonstrates OwnershipHelper usage
     * This endpoint shows how to protect individual resources
     */
    @GetMapping("/data/{dataId}")
    public ResponseEntity<Map<String, Object>> getUserDataItem(
            @CurrentUser UUID userId,
            @PathVariable String dataId) {

        // Parse dataId to UUID (in real app, fetch from DB)
        UUID dataUuid;
        try {
            dataUuid = UUID.fromString(dataId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        // Simulate fetching resource owner from database
        UUID resourceOwner = simulateGetResourceOwner(dataUuid);

        // Verify ownership - throws ForbiddenException if not owner
        OwnershipHelper.verifyOwnership(resourceOwner, userId, "Data item");

        String data = simulateGetData(dataUuid);
        return ResponseEntity.ok(Map.of(
                "id", dataId,
                "content", data,
                "owner", resourceOwner.toString()
        ));
    }

    /**
     * Simulate getting resource owner from database
     * In real application, this would query the database
     */
    private UUID simulateGetResourceOwner(UUID dataId) {
        // Demo: dataId determines owner for testing purposes
        if (dataId.toString().startsWith("1111")) {
            return UUID.fromString("11111111-1111-1111-1111-111111111111");
        }
        return UUID.fromString("22222222-2222-2222-2222-222222222222");
    }

    /**
     * Simulate getting data content from database
     */
    private String simulateGetData(UUID dataId) {
        return "Sample data for " + dataId;
    }
}
