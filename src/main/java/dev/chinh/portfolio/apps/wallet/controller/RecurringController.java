package dev.chinh.portfolio.apps.wallet;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet/recurring")
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public ResponseEntity<?> getAll(@CurrentUser UUID userId) {
        return ResponseEntity.ok(recurringService.getAll(userId));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateRecurringRequest req) {
        return ResponseEntity.ok(recurringService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateRecurringRequest req) {
        return ResponseEntity.ok(recurringService.update(userId, id, req));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleStatus(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(recurringService.toggleStatus(userId, id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@CurrentUser UUID userId, @PathVariable Long id) {
        recurringService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}

