package dev.chinh.portfolio.apps.vault;

import dev.chinh.portfolio.apps.vault.dto.*;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class VaultService {

    private final BookmarkRepository bookmarkRepo;
    private final BookmarkFolderRepository folderRepo;

    public VaultService(BookmarkRepository bookmarkRepo, BookmarkFolderRepository folderRepo) {
        this.bookmarkRepo = bookmarkRepo;
        this.folderRepo = folderRepo;
    }

    // ── Folders ──────────────────────────────────────────────

    public List<BookmarkFolderResponse> listFolders(UUID userId) {
        return folderRepo.findByUserIdOrderBySortOrderAscNameAsc(userId)
                .stream().map(BookmarkFolderResponse::from).toList();
    }

    @Transactional
    public BookmarkFolderResponse createFolder(UUID userId, CreateBookmarkFolderRequest req) {
        BookmarkFolder f = new BookmarkFolder();
        f.setUserId(userId);
        f.setName(req.name());
        f.setColor(req.color() != null ? req.color() : "#6366F1");
        f.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        return BookmarkFolderResponse.from(folderRepo.save(f));
    }

    @Transactional
    public BookmarkFolderResponse updateFolder(UUID userId, Long folderId, CreateBookmarkFolderRequest req) {
        BookmarkFolder f = folderRepo.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Folder not found"));
        f.setName(req.name());
        if (req.color() != null) f.setColor(req.color());
        if (req.sortOrder() != null) f.setSortOrder(req.sortOrder());
        return BookmarkFolderResponse.from(folderRepo.save(f));
    }

    @Transactional
    public void deleteFolder(UUID userId, Long folderId) {
        folderRepo.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Folder not found"));
        folderRepo.deleteByIdAndUserId(folderId, userId);
    }

    // ── Bookmarks ──────────────────────────────────────────

    public Page<BookmarkResponse> listBookmarks(UUID userId, int page, int size) {
        return bookmarkRepo.findByUserIdAndIsArchivedFalseOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(BookmarkResponse::from);
    }

    public List<BookmarkResponse> listBookmarksByFolder(UUID userId, Long folderId) {
        return bookmarkRepo.findByUserIdAndFolderIdAndIsArchivedFalseOrderByCreatedAtDesc(userId, folderId)
                .stream().map(BookmarkResponse::from).toList();
    }

    public List<BookmarkResponse> listFavorites(UUID userId) {
        return bookmarkRepo.findByUserIdAndIsFavoriteTrueAndIsArchivedFalseOrderByCreatedAtDesc(userId)
                .stream().map(BookmarkResponse::from).toList();
    }

    public Page<BookmarkResponse> search(UUID userId, String q, int page, int size) {
        return bookmarkRepo.search(userId, q, PageRequest.of(page, size))
                .map(BookmarkResponse::from);
    }

    @Transactional
    public BookmarkResponse createBookmark(UUID userId, CreateBookmarkRequest req) {
        Bookmark b = new Bookmark();
        b.setUserId(userId);
        b.setUrl(req.url());
        b.setTitle(req.title() != null ? req.title() : req.url());
        b.setDescription(req.description());
        b.setFavicon(req.favicon() != null ? req.favicon() : deriveFavicon(req.url()));
        b.setFolderId(req.folderId());
        b.setTags(req.tags() != null ? req.tags().toArray(new String[0]) : new String[]{});
        return BookmarkResponse.from(bookmarkRepo.save(b));
    }

    @Transactional
    public BookmarkResponse updateBookmark(UUID userId, Long bookmarkId, CreateBookmarkRequest req) {
        Bookmark b = bookmarkRepo.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found"));
        b.setUrl(req.url());
        if (req.title() != null) b.setTitle(req.title());
        if (req.description() != null) b.setDescription(req.description());
        if (req.favicon() != null) b.setFavicon(req.favicon());
        if (req.folderId() != null) b.setFolderId(req.folderId());
        if (req.tags() != null) b.setTags(req.tags().toArray(new String[0]));
        return BookmarkResponse.from(bookmarkRepo.save(b));
    }

    @Transactional
    public BookmarkResponse toggleFavorite(UUID userId, Long bookmarkId) {
        Bookmark b = bookmarkRepo.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found"));
        b.setIsFavorite(!b.getIsFavorite());
        return BookmarkResponse.from(bookmarkRepo.save(b));
    }

    @Transactional
    public BookmarkResponse archiveBookmark(UUID userId, Long bookmarkId) {
        Bookmark b = bookmarkRepo.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found"));
        b.setIsArchived(true);
        return BookmarkResponse.from(bookmarkRepo.save(b));
    }

    @Transactional
    public void deleteBookmark(UUID userId, Long bookmarkId) {
        bookmarkRepo.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found"));
        bookmarkRepo.deleteByIdAndUserId(bookmarkId, userId);
    }

    @Transactional
    public void trackClick(UUID userId, Long bookmarkId) {
        bookmarkRepo.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Bookmark not found"));
        bookmarkRepo.incrementClickCount(bookmarkId);
    }

    // ── Dashboard ──────────────────────────────────────────

    public VaultDashboardResponse getDashboard(UUID userId) {
        Page<BookmarkResponse> recent = bookmarkRepo
                .findByUserIdAndIsArchivedFalseOrderByCreatedAtDesc(userId, PageRequest.of(0, 5))
                .map(BookmarkResponse::from);
        List<String> allTags = bookmarkRepo.allTags(userId);
        return new VaultDashboardResponse(
                bookmarkRepo.count(),
                folderRepo.count(),
                bookmarkRepo.findByUserIdAndIsFavoriteTrueAndIsArchivedFalseOrderByCreatedAtDesc(userId).size(),
                allTags,
                recent.getContent()
        );
    }

    // ── Helpers ─────────────────────────────────────────────

    private String deriveFavicon(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return "https://www.google.com/s2/favicons?domain=" + host + "&sz=64";
        } catch (Exception e) {
            return null;
        }
    }
}
