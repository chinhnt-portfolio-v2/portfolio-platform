package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.WishlistItem;
import dev.chinh.portfolio.apps.wallet.WishlistItemRepository;
import dev.chinh.portfolio.apps.wallet.dto.CreateWishlistRequest;
import dev.chinh.portfolio.apps.wallet.dto.WishlistItemResponse;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class WishlistService {

    private static final java.util.regex.Pattern URL_PATTERN =
            java.util.regex.Pattern.compile("^https?://[^\\s]+$");

    private final WishlistItemRepository repo;

    public WishlistService(WishlistItemRepository repo) {
        this.repo = repo;
    }

    public List<WishlistItemResponse> list(UUID userId, String status) {
        List<WishlistItem> items = (status != null && !status.isBlank())
                ? repo.findByUserIdAndStatusOrderByPriorityAscCreatedAtDesc(userId, status.toUpperCase())
                : repo.findByUserIdOrderByPriorityAscCreatedAtDesc(userId);
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional
    public WishlistItemResponse create(UUID userId, CreateWishlistRequest req) {
        validateUrl(req.url());
        WishlistItem item = new WishlistItem();
        item.setUserId(userId);
        applyFields(item, req);
        return toResponse(repo.save(item));
    }

    @Transactional
    public WishlistItemResponse update(UUID userId, Long id, CreateWishlistRequest req) {
        WishlistItem item = findOwned(userId, id);
        validateUrl(req.url());
        applyFields(item, req);
        return toResponse(repo.save(item));
    }

    @Transactional
    public WishlistItemResponse updateStatus(UUID userId, Long id, String newStatus) {
        WishlistItem item = findOwned(userId, id);
        String normalized = newStatus == null ? "" : newStatus.toUpperCase();
        if (!normalized.equals("PURCHASED") && !normalized.equals("CANCELLED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status must be PURCHASED or CANCELLED");
        }
        item.setStatus(normalized);
        return toResponse(repo.save(item));
    }

    @Transactional
    public void delete(UUID userId, Long id) {
        WishlistItem item = findOwned(userId, id);
        repo.delete(item);
    }

    // ── Helpers ───────────────────────────────────────────────

    private WishlistItem findOwned(UUID userId, Long id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wishlist item not found"));
    }

    /** Whitelist https:// and http:// only; reject javascript:, data:, etc. */
    private void validateUrl(String url) {
        if (url == null || url.isBlank()) return;
        if (!URL_PATTERN.matcher(url).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL must start with https:// or http://");
        }
    }

    private void applyFields(WishlistItem item, CreateWishlistRequest req) {
        item.setName(req.name());
        item.setEstimatedPrice(req.estimatedPrice());
        item.setCurrency(req.currency() != null ? req.currency() : "VND");
        item.setPriority(req.priority() != null ? req.priority().toUpperCase() : "MEDIUM");
        item.setNotes(req.notes());
        item.setUrl(req.url());
        if (req.targetDate() != null && !req.targetDate().isBlank()) {
            item.setTargetDate(LocalDate.parse(req.targetDate()));
        } else {
            item.setTargetDate(null);
        }
    }

    private WishlistItemResponse toResponse(WishlistItem item) {
        return new WishlistItemResponse(
                item.getId(),
                item.getName(),
                item.getEstimatedPrice(),
                item.getCurrency(),
                item.getPriority(),
                item.getStatus(),
                item.getTargetDate(),
                item.getNotes(),
                item.getUrl(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
