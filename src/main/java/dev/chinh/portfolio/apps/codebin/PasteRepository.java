package dev.chinh.portfolio.apps.codebin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasteRepository extends JpaRepository<Paste, Long> {

    Page<Paste> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Paste> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    Optional<Paste> findByIdAndUserId(Long id, UUID userId);

    @Query("SELECT p FROM Paste p WHERE p.id = :id AND p.isPublic = true")
    Optional<Paste> findPublicById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Paste p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM Paste p WHERE p.expiresAt IS NOT NULL AND p.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
