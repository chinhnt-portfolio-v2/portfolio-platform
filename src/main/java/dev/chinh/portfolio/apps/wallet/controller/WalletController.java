package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.apps.wallet.service.WalletService;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    // ── Wallets ─────────────────────────────────────────────

    @GetMapping("/wallets")
    public ResponseEntity<List<WalletResponse>> listWallets(@CurrentUser UUID userId) {
        return ResponseEntity.ok(walletService.listWallets(userId));
    }

    @GetMapping("/wallets/{id}")
    public ResponseEntity<WalletResponse> getWallet(@CurrentUser UUID userId, @PathVariable Long id) {
        return ResponseEntity.ok(walletService.getWallet(userId, id));
    }

    @PostMapping("/wallets")
    public ResponseEntity<WalletResponse> createWallet(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateWalletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.createWallet(userId, req));
    }

    @PutMapping("/wallets/{id}")
    public ResponseEntity<WalletResponse> updateWallet(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateWalletRequest req) {
        return ResponseEntity.ok(walletService.updateWallet(userId, id, req));
    }

    @DeleteMapping("/wallets/{id}")
    public ResponseEntity<Void> deleteWallet(@CurrentUser UUID userId, @PathVariable Long id) {
        walletService.deleteWallet(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Categories ─────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<?>> listCategories(@CurrentUser UUID userId) {
        return ResponseEntity.ok(walletService.listCategories(userId));
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.createCategory(userId, req));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateCategoryRequest req) {
        return ResponseEntity.ok(walletService.updateCategory(userId, id, req));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@CurrentUser UUID userId, @PathVariable Long id) {
        walletService.deleteCategory(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Dashboard ──────────────────────────────────────────

    @GetMapping("/dashboard/summary")
    public ResponseEntity<WalletResponse.Summary> getSummary(@CurrentUser UUID userId) {
        return ResponseEntity.ok(walletService.getSummary(userId));
    }

    @GetMapping("/dashboard/monthly")
    public ResponseEntity<List<WalletResponse.MonthlyComparison>> getMonthlyComparison(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "3") int months) {
        return ResponseEntity.ok(walletService.getMonthlyComparison(userId, months));
    }
}
