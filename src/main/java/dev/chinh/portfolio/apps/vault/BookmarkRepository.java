package dev.chinh.portfolio.apps.vault;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Page<Bookmark> findByUserIdAndIsArchivedFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Bookmark> findByUserIdAndFolderIdAndIsArchivedFalseOrderByCreatedAtDesc(UUID userId, Long folderId);

    List<Bookmark> findByUserIdAndIsFavoriteTrueAndIsArchivedFalseOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT b FROM Bookmark b WHERE b.userId = :uid AND b.isArchived = false " +
           "AND (LOWER(b.title) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(b.description) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(b.url) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<Bookmark> search(@Param("uid") UUID userId, @Param("q") String q, Pageable pageable);

    @Query("SELECT DISTINCT UNNEST(b.tags) FROM Bookmark b WHERE b.userId = :uid AND b.isArchived = false")
    List<String> allTags(@Param("uid") UUID userId);

    Optional<Bookmark> findByIdAndUserId(Long id, UUID userId);

    @Modifying
    @Query("UPDATE Bookmark b SET b.clickCount = b.clickCount + 1 WHERE b.id = :id")
    void incrementClickCount(@Param("id") Long id);

    void deleteByIdAndUserId(Long id, UUID userId);
}
