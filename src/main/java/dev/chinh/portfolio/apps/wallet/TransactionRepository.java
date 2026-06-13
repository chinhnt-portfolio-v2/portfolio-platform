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
public interface TransactionRepository
        extends JpaRepository<Transaction, Long>,
                org.springframework.data.jpa.repository.JpaSpecificationExecutor<Transaction> {

    Page<Transaction> findByUserIdOrderByDateDesc(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndTypeOrderByDateDesc(UUID userId, String type, Pageable pageable);

    Page<Transaction> findByUserIdAndWalletIdOrderByDateDesc(UUID userId, Long walletId, Pageable pageable);

    Page<Transaction> findByUserIdAndGroupIdOrderByDateDesc(UUID userId, Long groupId, Pageable pageable);

    List<Transaction> findByGroupIdOrderByDateAsc(Long groupId);

    Optional<Transaction> findByIdAndUserId(Long id, UUID userId);

    // Filtered listing uses JpaSpecificationExecutor.findAll(spec, pageable) — see
    // TransactionService.listTransactions. A single JPQL with "(:param IS NULL OR ...)"
    // guards fails on Postgres ("could not determine data type of parameter"), so
    // predicates are added dynamically only for non-null filters.

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type = 'INCOME' AND t.date >= :since")
    BigDecimal sumIncomeSince(@Param("userId") UUID userId, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type = 'EXPENSE' AND t.date >= :since")
    BigDecimal sumExpenseSince(@Param("userId") UUID userId, @Param("since") Instant since);

    // Budget "spent" for a category in [from, to): counts normal expenses AND pay-later
    // PRINCIPAL purchases (a buy counts at purchase time) but excludes repayment txns
    // (PAYMENT/INTEREST/FINAL_PAYMENT) so settling a debt does not double-count.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type = 'EXPENSE' " +
           "AND t.categoryId = :categoryId " +
           "AND (t.txnType IS NULL OR t.txnType = 'PRINCIPAL') " +
           "AND t.date >= :from AND t.date < :to")
    BigDecimal sumExpenseSince(@Param("userId") UUID userId,
                               @Param("categoryId") Long categoryId,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    List<Transaction> findByUserIdAndCategoryIdOrderByDateDesc(UUID userId, Long categoryId,
                                                             org.springframework.data.domain.Pageable pageable);

    /**
     * Monthly income excluding transfers (categoryId IS NULL) and debt txns (txnType IS NOT NULL).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type = 'INCOME' " +
           "AND t.categoryId IS NOT NULL AND t.txnType IS NULL " +
           "AND t.date >= :from AND t.date < :to")
    BigDecimal sumMonthlyIncome(@Param("userId") UUID userId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    /**
     * Monthly expense for a set of category IDs, excluding debt txns (txnType IS NOT NULL).
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.userId = :userId AND t.type = 'EXPENSE' " +
           "AND t.categoryId IN :categoryIds AND t.txnType IS NULL " +
           "AND t.date >= :from AND t.date < :to")
    BigDecimal sumExpenseForCategories(@Param("userId") UUID userId,
                                       @Param("categoryIds") java.util.Set<Long> categoryIds,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    /**
     * Total expense per category in [from, to), grouped by category. Mirrors the budget policy:
     * counts normal + PRINCIPAL buys, excludes repayment txns and uncategorized rows.
     */
    @Query("SELECT t.categoryId AS categoryId, COALESCE(SUM(t.amount), 0) AS total " +
           "FROM Transaction t WHERE t.userId = :userId AND t.type = 'EXPENSE' " +
           "AND (t.txnType IS NULL OR t.txnType = 'PRINCIPAL') AND t.categoryId IS NOT NULL " +
           "AND t.date >= :from AND t.date < :to GROUP BY t.categoryId")
    List<dev.chinh.portfolio.apps.wallet.dto.CategorySpendProjection> sumExpenseByCategory(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
