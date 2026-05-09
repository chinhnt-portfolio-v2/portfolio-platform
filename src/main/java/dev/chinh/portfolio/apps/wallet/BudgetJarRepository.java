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
public interface BudgetJarRepository extends JpaRepository<BudgetJar, Long> {

    List<BudgetJar> findByUserIdOrderBySortOrderAscCreatedAtAsc(UUID userId);

    Optional<BudgetJar> findByIdAndUserId(Long id, UUID userId);

    boolean existsByUserIdAndIsPresetTrue(UUID userId);

    @Query("SELECT COALESCE(SUM(j.percentage), 0) FROM BudgetJar j WHERE j.userId = :userId")
    BigDecimal sumPercentageByUserId(@Param("userId") UUID userId);
}
