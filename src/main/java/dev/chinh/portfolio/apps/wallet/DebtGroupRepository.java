package dev.chinh.portfolio.apps.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebtGroupRepository extends JpaRepository<DebtGroup, Long> {

    List<DebtGroup> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<DebtGroup> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    Optional<DebtGroup> findByIdAndUserId(Long id, UUID userId);

    boolean existsByIdAndUserId(Long id, UUID userId);

    @Query("SELECT COALESCE(SUM(d.totalAmount - d.paidAmount), 0) FROM DebtGroup d " +
           "WHERE d.userId = :userId AND d.status != 'SETTLED' AND d.groupType IN ('BNPL', 'DEBT', 'PURCHASE_CREDIT')")
    BigDecimal totalDebt(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(d.totalAmount - d.paidAmount), 0) FROM DebtGroup d " +
           "WHERE d.userId = :userId AND d.status != 'SETTLED' AND d.groupType = 'LOAN_GIVEN'")
    BigDecimal totalReceivable(@Param("userId") UUID userId);
}
