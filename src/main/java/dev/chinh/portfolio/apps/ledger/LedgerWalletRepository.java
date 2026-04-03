package dev.chinh.portfolio.apps.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerWalletRepository extends JpaRepository<LedgerWallet, Long> {

    List<LedgerWallet> findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(UUID userId);

    Optional<LedgerWallet> findByIdAndUserId(Long id, UUID userId);

    boolean existsByIdAndUserId(Long id, UUID userId);
}
