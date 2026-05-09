package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.dto.CreateBudgetJarRequest;
import dev.chinh.portfolio.apps.wallet.service.BudgetJarService;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet/budget-jars")
public class BudgetJarController {

    private final BudgetJarService jarService;

    public BudgetJarController(BudgetJarService jarService) {
        this.jarService = jarService;
    }

    @GetMapping
    public ResponseEntity<?> getJars(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(jarService.getJarsWithMonthlyData(userId, period));
    }

    @PostMapping
    public ResponseEntity<?> createJar(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateBudgetJarRequest req) {
        return ResponseEntity.ok(jarService.createJar(userId, req));
    }

    @PostMapping("/preset")
    public ResponseEntity<?> createPreset(@CurrentUser UUID userId) {
        return ResponseEntity.ok(jarService.createPreset(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateJar(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateBudgetJarRequest req) {
        return ResponseEntity.ok(jarService.updateJar(id, userId, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJar(
            @CurrentUser UUID userId,
            @PathVariable Long id) {
        jarService.deleteJar(id, userId);
        return ResponseEntity.noContent().build();
    }
}
