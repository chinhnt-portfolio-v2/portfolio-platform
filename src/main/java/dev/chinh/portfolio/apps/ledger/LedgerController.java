package dev.chinh.portfolio.apps.ledger;

import dev.chinh.portfolio.apps.ledger.dto.*;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // ── Wallets ─────────────────────────────────────────────

    @GetMapping("/wallets")
    public ResponseEntity<List<LedgerWalletResponse>> listWallets(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ledgerService.listWallets(userId));
    }

    @GetMapping("/wallets/{id}")
    public ResponseEntity<LedgerWalletResponse> getWallet(@CurrentUser UUID userId, @PathVariable Long id) {
        return ResponseEntity.ok(ledgerService.getWallet(userId, id));
    }

    @PostMapping("/wallets")
    public ResponseEntity<LedgerWalletResponse> createWallet(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateLedgerWalletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledgerService.createWallet(userId, req));
    }

    @PutMapping("/wallets/{id}")
    public ResponseEntity<LedgerWalletResponse> updateWallet(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateLedgerWalletRequest req) {
        return ResponseEntity.ok(ledgerService.updateWallet(userId, id, req));
    }

    @DeleteMapping("/wallets/{id}")
    public ResponseEntity<Void> deleteWallet(@CurrentUser UUID userId, @PathVariable Long id) {
        ledgerService.deleteWallet(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Entries ─────────────────────────────────────────────

    @GetMapping("/entries")
    public ResponseEntity<Page<LedgerEntryResponse>> listEntries(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ledgerService.listEntries(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/entries/wallet/{walletId}")
    public ResponseEntity<List<LedgerEntryResponse>> listEntriesByWallet(
            @CurrentUser UUID userId, @PathVariable Long walletId) {
        return ResponseEntity.ok(ledgerService.listEntriesByWallet(userId, walletId));
    }

    @PostMapping("/entries")
    public ResponseEntity<LedgerEntryResponse> createEntry(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateLedgerEntryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledgerService.createEntry(userId, req));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteEntry(@CurrentUser UUID userId, @PathVariable Long id) {
        ledgerService.deleteEntry(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Dashboard ──────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<LedgerDashboardResponse> getDashboard(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ledgerService.getDashboard(userId, days));
    }
}
