package dev.chinh.portfolio.apps.codebin;

import dev.chinh.portfolio.apps.codebin.dto.*;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import dev.chinh.portfolio.shared.error.ForbiddenException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.UUID;

@Service
public class CodeBinService {

    private final PasteRepository pasteRepo;

    public CodeBinService(PasteRepository pasteRepo) {
        this.pasteRepo = pasteRepo;
    }

    // ── Authenticated ────────────────────────────────────────

    public Page<PasteResponse> listMyPastes(UUID userId, int page, int size) {
        return pasteRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(p -> PasteResponse.from(p, false));
    }

    @Transactional
    public PasteResponse createPaste(UUID userId, CreatePasteRequest req) {
        Paste p = new Paste();
        p.setUserId(userId);
        p.setTitle(req.title() != null ? req.title() : "Untitled");
        p.setContent(req.content());
        p.setLanguage(req.language() != null ? req.language() : "plaintext");
        p.setIsPublic(req.isPublic() != null ? req.isPublic() : true);
        p.setExpiresAt(req.expiresAt());
        if (req.password() != null && !req.password().isBlank()) {
            p.setPasswordHash(hashPassword(req.password()));
        }
        return PasteResponse.from(pasteRepo.save(p), true);
    }

    @Transactional
    public PasteResponse updatePaste(UUID userId, Long pasteId, CreatePasteRequest req) {
        Paste p = pasteRepo.findByIdAndUserId(pasteId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Paste not found"));
        if (req.title() != null) p.setTitle(req.title());
        p.setContent(req.content());
        if (req.language() != null) p.setLanguage(req.language());
        if (req.isPublic() != null) p.setIsPublic(req.isPublic());
        if (req.expiresAt() != null) p.setExpiresAt(req.expiresAt());
        if (req.password() != null) {
            p.setPasswordHash(req.password().isBlank() ? null : hashPassword(req.password()));
        }
        return PasteResponse.from(pasteRepo.save(p), true);
    }

    @Transactional
    public void deletePaste(UUID userId, Long pasteId) {
        Paste p = pasteRepo.findByIdAndUserId(pasteId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Paste not found"));
        pasteRepo.delete(p);
    }

    // ── Public (anonymous) ─────────────────────────────────

    /**
     * View a public paste by ID.
     * Throws 404 for non-existent or non-public pastes.
     * Throws 403 if password-protected.
     */
    public PasteResponse viewPaste(Long pasteId) {
        Paste p = pasteRepo.findPublicById(pasteId)
                .orElseThrow(() -> new EntityNotFoundException("Paste not found"));
        pasteRepo.incrementViewCount(pasteId);
        return PasteResponse.fromPublic(p);
    }

    /**
     * Verify password for a protected paste.
     */
    public PasteResponse viewPasteWithPassword(Long pasteId, String password) {
        Paste p = pasteRepo.findById(pasteId)
                .orElseThrow(() -> new EntityNotFoundException("Paste not found"));
        if (p.getPasswordHash() == null) return PasteResponse.fromPublic(p);
        if (!MessageDigest.isEqual(
                p.getPasswordHash().getBytes(StandardCharsets.UTF_8),
                hashPassword(password).getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenException("Incorrect password");
        }
        pasteRepo.incrementViewCount(pasteId);
        return PasteResponse.fromPublic(p);
    }

    public Page<PasteResponse> listRecentPublic(int page, int size) {
        return pasteRepo.findByIsPublicTrueOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(PasteResponse::fromPublic);
    }

    // ── Scheduled cleanup ─────────────────────────────────

    @Scheduled(cron = "0 0 3 * * *") // daily at 03:00 — keeps DB idle so it can autosuspend
    @Transactional
    public void cleanupExpired() {
        pasteRepo.deleteExpired();
    }

    // ── Helpers ─────────────────────────────────────────────

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
