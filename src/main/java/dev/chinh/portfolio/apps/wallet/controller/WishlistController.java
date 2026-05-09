package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.dto.CreateWishlistRequest;
import dev.chinh.portfolio.apps.wallet.dto.WishlistItemResponse;
import dev.chinh.portfolio.apps.wallet.service.WishlistService;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import dev.chinh.portfolio.apps.wallet.dto.UpdateStatusRequest;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/wallet/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<List<WishlistItemResponse>> list(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(wishlistService.list(userId, status));
    }

    @PostMapping
    public ResponseEntity<WishlistItemResponse> create(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateWishlistRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wishlistService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WishlistItemResponse> update(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateWishlistRequest req) {
        return ResponseEntity.ok(wishlistService.update(userId, id, req));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<WishlistItemResponse> updateStatus(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest body) {
        return ResponseEntity.ok(wishlistService.updateStatus(userId, id, body.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @CurrentUser UUID userId,
            @PathVariable Long id) {
        wishlistService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
