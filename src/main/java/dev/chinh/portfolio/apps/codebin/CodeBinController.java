package dev.chinh.portfolio.apps.codebin;

import dev.chinh.portfolio.apps.codebin.dto.*;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pastes")
public class CodeBinController {

    private final CodeBinService codeBinService;

    public CodeBinController(CodeBinService codeBinService) {
        this.codeBinService = codeBinService;
    }

    // ── Authenticated ────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<Page<PasteResponse>> listMyPastes(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(codeBinService.listMyPastes(userId, page, size));
    }

    @PostMapping
    public ResponseEntity<PasteResponse> createPaste(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreatePasteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(codeBinService.createPaste(userId, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PasteResponse> updatePaste(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreatePasteRequest req) {
        return ResponseEntity.ok(codeBinService.updatePaste(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaste(
            @CurrentUser UUID userId, @PathVariable Long id) {
        codeBinService.deletePaste(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Public ──────────────────────────────────────────────

    @GetMapping("/recent")
    public ResponseEntity<Page<PasteResponse>> listRecentPublic(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(codeBinService.listRecentPublic(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PasteResponse> viewPaste(@PathVariable Long id) {
        return ResponseEntity.ok(codeBinService.viewPaste(id));
    }

    @PostMapping("/{id}")
    public ResponseEntity<PasteResponse> viewPasteWithPassword(
            @PathVariable Long id,
            @RequestBody UnlockPasteRequest req) {
        return ResponseEntity.ok(codeBinService.viewPasteWithPassword(id, req.password()));
    }

    // Nested request record for password unlock
    public record UnlockPasteRequest(String password) {}
}
