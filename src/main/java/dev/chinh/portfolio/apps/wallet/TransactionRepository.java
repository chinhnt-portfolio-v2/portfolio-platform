package dev.chinh.portfolio.apps.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByUserIdOrderByDateDesc(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndTypeOrderByDateDesc(UUID userId, String type, Pageable pageable);

    Page<Transaction> findByUserIdAndWalletIdOrderByDateDesc(UUID userId, Long walletId, Pageable pageable);

    Page<Transaction> findByUserIdAndGroupIdOrderByDateDesc(UUID userId, Long groupId, Pageable pageable);

    List<Transaction> findByGroupIdOrderByDateAsc(Long groupId);

    Optional<Transaction> findByIdAndUserId(Long id, UUID userId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type = 'INCOME' AND t.date >= :since")
    BigDecimal sumIncomeSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type = 'EXPENSE' AND t.date >= :since")
    BigDecimal sumExpenseSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
