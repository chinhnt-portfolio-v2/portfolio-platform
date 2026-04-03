package dev.chinh.portfolio.apps.vault;

import dev.chinh.portfolio.apps.vault.dto.*;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vault")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    // ── Folders ──────────────────────────────────────────────

    @GetMapping("/folders")
    public ResponseEntity<List<BookmarkFolderResponse>> listFolders(@CurrentUser UUID userId) {
        return ResponseEntity.ok(vaultService.listFolders(userId));
    }

    @PostMapping("/folders")
    public ResponseEntity<BookmarkFolderResponse> createFolder(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateBookmarkFolderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vaultService.createFolder(userId, req));
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<BookmarkFolderResponse> updateFolder(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateBookmarkFolderRequest req) {
        return ResponseEntity.ok(vaultService.updateFolder(userId, id, req));
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@CurrentUser UUID userId, @PathVariable Long id) {
        vaultService.deleteFolder(userId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Bookmarks ──────────────────────────────────────────

    @GetMapping("/bookmarks")
    public ResponseEntity<Page<BookmarkResponse>> listBookmarks(
            @CurrentUser UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(vaultService.listBookmarks(userId, page, size));
    }

    @GetMapping("/bookmarks/folder/{folderId}")
    public ResponseEntity<List<BookmarkResponse>> listBookmarksByFolder(
            @CurrentUser UUID userId, @PathVariable Long folderId) {
        return ResponseEntity.ok(vaultService.listBookmarksByFolder(userId, folderId));
    }

    @GetMapping("/bookmarks/favorites")
    public ResponseEntity<List<BookmarkResponse>> listFavorites(@CurrentUser UUID userId) {
        return ResponseEntity.ok(vaultService.listFavorites(userId));
    }

    @GetMapping("/bookmarks/search")
    public ResponseEntity<Page<BookmarkResponse>> search(
            @CurrentUser UUID userId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(vaultService.search(userId, q, page, size));
    }

    @PostMapping("/bookmarks")
    public ResponseEntity<BookmarkResponse> createBookmark(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateBookmarkRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vaultService.createBookmark(userId, req));
    }

    @PutMapping("/bookmarks/{id}")
    public ResponseEntity<BookmarkResponse> updateBookmark(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateBookmarkRequest req) {
        return ResponseEntity.ok(vaultService.updateBookmark(userId, id, req));
    }

    @PatchMapping("/bookmarks/{id}/favorite")
    public ResponseEntity<BookmarkResponse> toggleFavorite(
            @CurrentUser UUID userId, @PathVariable Long id) {
        return ResponseEntity.ok(vaultService.toggleFavorite(userId, id));
    }

    @PatchMapping("/bookmarks/{id}/archive")
    public ResponseEntity<BookmarkResponse> archiveBookmark(
            @CurrentUser UUID userId, @PathVariable Long id) {
        return ResponseEntity.ok(vaultService.archiveBookmark(userId, id));
    }

    @DeleteMapping("/bookmarks/{id}")
    public ResponseEntity<Void> deleteBookmark(@CurrentUser UUID userId, @PathVariable Long id) {
        vaultService.deleteBookmark(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bookmarks/{id}/click")
    public ResponseEntity<Void> trackClick(@CurrentUser UUID userId, @PathVariable Long id) {
        vaultService.trackClick(userId, id);
        return ResponseEntity.ok().build();
    }

    // ── Dashboard ──────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<VaultDashboardResponse> getDashboard(@CurrentUser UUID userId) {
        return ResponseEntity.ok(vaultService.getDashboard(userId));
    }
}
