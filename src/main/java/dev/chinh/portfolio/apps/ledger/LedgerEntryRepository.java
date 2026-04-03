package dev.chinh.portfolio.apps.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByUserIdOrderByEntryDateDescCreatedAtDesc(UUID userId, Pageable pageable);

    List<LedgerEntry> findByUserIdAndWalletIdOrderByEntryDateDesc(UUID userId, Long walletId);

    List<LedgerEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(UUID userId, LocalDate start, LocalDate end);

    void deleteByIdAndUserId(Long id, UUID userId);

    @Query("SELECT l.category, SUM(l.amount) FROM LedgerEntry l " +
           "WHERE l.userId = :uid AND l.type = :type AND l.entryDate BETWEEN :start AND :end " +
           "GROUP BY l.category ORDER BY SUM(l.amount) DESC")
    List<Object[]> sumByCategory(@Param("uid") UUID userId,
                                  @Param("type") String type,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    @Query("SELECT l.entryDate, l.type, SUM(l.amount) FROM LedgerEntry l " +
           "WHERE l.userId = :uid AND l.entryDate BETWEEN :start AND :end " +
           "GROUP BY l.entryDate, l.type ORDER BY l.entryDate")
    List<Object[]> dailySummary(@Param("uid") UUID userId,
                                @Param("start") LocalDate start,
                                @Param("end") LocalDate end);
}
