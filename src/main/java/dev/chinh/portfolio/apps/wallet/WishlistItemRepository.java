package dev.chinh.portfolio.apps.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserIdAndStatusOrderByPriorityAscCreatedAtDesc(UUID userId, String status);

    List<WishlistItem> findByUserIdOrderByPriorityAscCreatedAtDesc(UUID userId);

    Optional<WishlistItem> findByIdAndUserId(Long id, UUID userId);

    boolean existsByIdAndUserId(Long id, UUID userId);
}
