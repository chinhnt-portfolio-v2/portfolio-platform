package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.PushSubscription;
import dev.chinh.portfolio.apps.wallet.PushSubscriptionRepository;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class PushController {

    private final PushSubscriptionRepository repository;

    public PushController(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    /**
     * Body from PushSubscription.toJSON() — standard Web Push API.
     * {
     *   "endpoint": "https://fcm.googleapis.com/...",
     *   "keys": { "p256dh": "...", "auth": "..." }
     * }
     */
    @PostMapping("/push")
    public ResponseEntity<?> subscribe(@CurrentUser UUID userId, @RequestBody Map<String, Object> body) {
        String endpoint = (String) body.get("endpoint");
        @SuppressWarnings("unchecked")
        Map<String, String> keys = (Map<String, String>) body.get("keys");

        // Upsert — replace old subscription
        repository.deleteByUserId(userId);

        PushSubscription sub = new PushSubscription();
        sub.setUserId(userId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(keys != null ? keys.get("p256dh") : null);
        sub.setAuth(keys != null ? keys.get("auth") : null);
        repository.save(sub);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/push")
    public ResponseEntity<?> unsubscribe(@CurrentUser UUID userId) {
        repository.deleteByUserId(userId);
        return ResponseEntity.noContent().build();
    }
}

