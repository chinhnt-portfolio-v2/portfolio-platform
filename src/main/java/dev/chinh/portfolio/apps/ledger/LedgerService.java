package dev.chinh.portfolio.apps.ledger;

import dev.chinh.portfolio.apps.ledger.dto.*;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class LedgerService {

    @PersistenceContext
    private EntityManager em;

    private final LedgerWalletRepository walletRepo;
    private final LedgerEntryRepository entryRepo;

    public LedgerService(LedgerWalletRepository walletRepo, LedgerEntryRepository entryRepo) {
        this.walletRepo = walletRepo;
        this.entryRepo = entryRepo;
    }

    // ── Wallets ──────────────────────────────────────────────

    public List<LedgerWalletResponse> listWallets(UUID userId) {
        return walletRepo.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .stream().map(LedgerWalletResponse::from).toList();
    }

    public LedgerWalletResponse getWallet(UUID userId, Long walletId) {
        LedgerWallet w = walletRepo.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
        return LedgerWalletResponse.from(w);
    }

    @Transactional
    public LedgerWalletResponse createWallet(UUID userId, CreateLedgerWalletRequest req) {
        LedgerWallet w = new LedgerWallet();
        w.setUserId(userId);
        w.setName(req.name());
        w.setType(req.type());
        w.setCurrency(req.currency() != null ? req.currency() : "VND");
        w.setIcon(req.icon() != null ? req.icon() : "💰");
        w.setColor(req.color() != null ? req.color() : "#0EA5E9");
        w.setBalance(req.initialBalance() != null ? req.initialBalance() : BigDecimal.ZERO);
        return LedgerWalletResponse.from(walletRepo.save(w));
    }

    @Transactional
    public LedgerWalletResponse updateWallet(UUID userId, Long walletId, CreateLedgerWalletRequest req) {
        LedgerWallet w = walletRepo.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
        w.setName(req.name());
        w.setType(req.type());
        if (req.currency() != null) w.setCurrency(req.currency());
        if (req.icon() != null) w.setIcon(req.icon());
        if (req.color() != null) w.setColor(req.color());
        return LedgerWalletResponse.from(walletRepo.save(w));
    }

    @Transactional
    public void deleteWallet(UUID userId, Long walletId) {
        if (!walletRepo.existsByIdAndUserId(walletId, userId))
            throw new EntityNotFoundException("Wallet not found");
        em.createNativeQuery("UPDATE ledger_wallets SET is_active = false WHERE id = :id")
                .setParameter("id", walletId).executeUpdate();
    }

    // ── Entries ─────────────────────────────────────────────

    public Page<LedgerEntryResponse> listEntries(UUID userId, Pageable pageable) {
        return entryRepo.findByUserIdOrderByEntryDateDescCreatedAtDesc(userId, pageable)
                .map(LedgerEntryResponse::from);
    }

    public List<LedgerEntryResponse> listEntriesByWallet(UUID userId, Long walletId) {
        return entryRepo.findByUserIdAndWalletIdOrderByEntryDateDesc(userId, walletId)
                .stream().map(LedgerEntryResponse::from).toList();
    }

    @Transactional
    public LedgerEntryResponse createEntry(UUID userId, CreateLedgerEntryRequest req) {
        // Verify wallet ownership
        if (!walletRepo.existsByIdAndUserId(req.walletId(), userId))
            throw new EntityNotFoundException("Wallet not found");

        LedgerEntry e = new LedgerEntry();
        e.setUserId(userId);
        e.setWalletId(req.walletId());
        e.setCategory(req.category());
        e.setCategoryIcon(req.categoryIcon() != null ? req.categoryIcon() : "📦");
        e.setCategoryColor(req.categoryColor() != null ? req.categoryColor() : "#94A3B8");
        e.setType(req.type());
        e.setAmount(req.amount());
        e.setNote(req.note());
        e.setEntryDate(req.entryDate() != null ? req.entryDate() : LocalDate.now());

        // Update wallet balance
        LedgerWallet w = walletRepo.findById(req.walletId()).orElseThrow();
        BigDecimal delta = "INCOME".equals(req.type()) ? req.amount() : req.amount().negate();
        w.setBalance(w.getBalance().add(delta));
        walletRepo.save(w);

        return LedgerEntryResponse.from(entryRepo.save(e));
    }

    @Transactional
    public void deleteEntry(UUID userId, Long entryId) {
        LedgerEntry e = entryRepo.findById(entryId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Entry not found"));

        // Reverse balance impact
        LedgerWallet w = walletRepo.findById(e.getWalletId()).orElseThrow();
        BigDecimal delta = "INCOME".equals(e.getType()) ? e.getAmount().negate() : e.getAmount();
        w.setBalance(w.getBalance().add(delta));
        walletRepo.save(w);

        entryRepo.delete(e);
    }

    // ── Dashboard ──────────────────────────────────────────

    public LedgerDashboardResponse getDashboard(UUID userId, int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);

        List<Object[]> incomeRows = entryRepo.sumByCategory(userId, "INCOME", start, end);
        List<Object[]> expenseRows = entryRepo.sumByCategory(userId, "EXPENSE", start, end);

        BigDecimal totalIncome = incomeRows.stream()
                .map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = expenseRows.stream()
                .map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LedgerDashboardResponse.CategoryBreakdown> incomeBreakdown = incomeRows.stream()
                .map(r -> new LedgerDashboardResponse.CategoryBreakdown(
                        (String) r[0], "💰", "#10B981", (BigDecimal) r[1])).toList();

        List<LedgerDashboardResponse.CategoryBreakdown> expenseBreakdown = expenseRows.stream()
                .map(r -> new LedgerDashboardResponse.CategoryBreakdown(
                        (String) r[0], "📦", "#EF4444", (BigDecimal) r[1])).toList();

        List<Object[]> dailyRows = entryRepo.dailySummary(userId, start, end);
        Map<LocalDate, LedgerDashboardResponse.DailyPoint> dailyMap = new LinkedHashMap<>();
        for (Object[] row : dailyRows) {
            LocalDate date = (LocalDate) row[0];
            String type = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            dailyMap.computeIfAbsent(date, d -> new LedgerDashboardResponse.DailyPoint(d, BigDecimal.ZERO, BigDecimal.ZERO));
            LedgerDashboardResponse.DailyPoint pt = dailyMap.get(date);
            if ("INCOME".equals(type)) {
                dailyMap.put(date, new LedgerDashboardResponse.DailyPoint(date, amount, pt.expense()));
            } else {
                dailyMap.put(date, new LedgerDashboardResponse.DailyPoint(date, pt.income(), amount));
            }
        }

        BigDecimal totalBalance = walletRepo.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .stream().map(LedgerWallet::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new LedgerDashboardResponse(
                totalIncome, totalExpense,
                totalIncome.subtract(totalExpense), totalBalance, "VND",
                incomeBreakdown, expenseBreakdown,
                new ArrayList<>(dailyMap.values())
        );
    }
}
