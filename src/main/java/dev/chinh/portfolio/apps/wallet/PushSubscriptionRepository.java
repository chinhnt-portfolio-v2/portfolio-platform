package dev.chinh.portfolio.apps.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
