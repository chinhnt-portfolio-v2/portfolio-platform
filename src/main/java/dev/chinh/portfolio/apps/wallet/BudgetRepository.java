package dev.chinh.portfolio.apps.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndPeriod(UUID userId, String period);

    // Eager-load Category to avoid LazyInitializationException in toResponse
    @Query("select b from Budget b left join fetch b.category where b.userId = :userId and b.period = :period")
    List<Budget> findByUserIdAndPeriodWithCategory(@Param("userId") UUID userId, @Param("period") String period);

    @Query("select b from Budget b left join fetch b.category where b.id = :id")
    Optional<Budget> findByIdWithCategory(@Param("id") Long id);

    // Latest period (YYYY-MM) strictly before :period that this user has budgets for.
    // Used by auto-rollover: when user opens a month with no budgets, clone from this period.
    @Query("select max(b.period) from Budget b where b.userId = :userId and b.period < :period")
    Optional<String> findLatestPeriodBefore(@Param("userId") UUID userId, @Param("period") String period);

    Optional<Budget> findByUserIdAndCategoryIdAndPeriod(UUID userId, Long categoryId, String period);

    void deleteByIdAndUserId(Long id, UUID userId);

    boolean existsByIdAndUserId(Long id, UUID userId);
}
