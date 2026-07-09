package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletService {

    @PersistenceContext
    private EntityManager em;

    private final WalletRepository walletRepo;
    private final CategoryRepository categoryRepo;
    private final DebtGroupRepository debtGroupRepo;
    private final TransactionRepository transactionRepo;

    public WalletService(WalletRepository walletRepo,
                         CategoryRepository categoryRepo,
                         DebtGroupRepository debtGroupRepo,
                         TransactionRepository transactionRepo) {
        this.walletRepo = walletRepo;
        this.categoryRepo = categoryRepo;
        this.debtGroupRepo = debtGroupRepo;
        this.transactionRepo = transactionRepo;
    }

    // ── Wallets ──────────────────────────────────────────────

    public List<WalletResponse> listWallets(UUID userId) {
        return walletRepo.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .stream().map(this::toWalletResponse).toList();
    }

    public WalletResponse getWallet(UUID userId, Long walletId) {
        Wallet w = walletRepo.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
        return toWalletResponse(w);
    }

    @Transactional
    public WalletResponse createWallet(UUID userId, CreateWalletRequest req) {
        Wallet w = new Wallet();
        w.setUserId(userId);
        w.setName(req.name());
        w.setType(req.type());
        w.setCurrency(req.currency() != null ? req.currency() : "VND");
        w.setIcon(req.icon() != null ? req.icon() : "💰");
        w.setColor(req.color() != null ? req.color() : "#0EA5E9");
        w.setBalance(req.initialBalance() != null ? req.initialBalance() : BigDecimal.ZERO);
        w = walletRepo.save(w);

        // Seed categories on first wallet
        seedCategories(userId);
        return toWalletResponse(w);
    }

    @Transactional
    public WalletResponse updateWallet(UUID userId, Long walletId, CreateWalletRequest req) {
        Wallet w = walletRepo.findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
        w.setName(req.name());
        w.setType(req.type());
        if (req.currency() != null) w.setCurrency(req.currency());
        if (req.icon() != null) w.setIcon(req.icon());
        if (req.color() != null) w.setColor(req.color());
        return toWalletResponse(walletRepo.save(w));
    }

    @Transactional
    public void deleteWallet(UUID userId, Long walletId) {
        if (!walletRepo.existsByIdAndUserId(walletId, userId))
            throw new EntityNotFoundException("Wallet not found");
        em.createNativeQuery("UPDATE wallets SET is_active = false WHERE id = :id")
                .setParameter("id", walletId).executeUpdate();
    }

    // ── Categories ───────────────────────────────────────────

    public List<?> listCategories(UUID userId) {
        return categoryRepo.findByUserIdAndIsActiveTrueOrderByIsDefaultDescNameAsc(userId);
    }

    @Transactional
    public Category createCategory(UUID userId, CreateCategoryRequest req) {
        Category c = new Category();
        c.setUserId(userId);
        c.setName(req.name());
        c.setIcon(req.icon() != null && !req.icon().isBlank() ? req.icon() : "📦");
        c.setColor(req.color() != null && !req.color().isBlank() ? req.color() : "#94A3B8");
        c.setType(req.type());
        c.setIsDefault(false);
        return categoryRepo.save(c);
    }

    @Transactional
    public Category updateCategory(UUID userId, Long id, CreateCategoryRequest req) {
        Category c = categoryRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Category not found"));
        c.setName(req.name());
        if (req.icon() != null && !req.icon().isBlank()) c.setIcon(req.icon());
        if (req.color() != null && !req.color().isBlank()) c.setColor(req.color());
        // type & isDefault are immutable post-creation
        return categoryRepo.save(c);
    }

    @Transactional
    public void deleteCategory(UUID userId, Long id) {
        Category c = categoryRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Category not found"));
        if (Boolean.TRUE.equals(c.getIsDefault())) {
            throw new IllegalArgumentException("Cannot delete default category");
        }
        // Soft delete — preserves transaction history references
        c.setIsActive(false);
        categoryRepo.save(c);
    }

    // ── Dashboard Summary ──────────────────────────────────

    public WalletResponse.Summary getSummary(UUID userId) {
        // Total assets: sum of CASH, BANK, E_WALLET balances
        BigDecimal assets = em.createQuery(
                "SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w " +
                "WHERE w.userId = :uid AND w.isActive = true AND w.type != 'POSTPAID'",
                BigDecimal.class).setParameter("uid", userId).getSingleResult();

        BigDecimal debt = debtGroupRepo.totalDebt(userId);
        BigDecimal receivable = debtGroupRepo.totalReceivable(userId);
        BigDecimal netWorth = assets.subtract(debt).add(receivable);

        return new WalletResponse.Summary(assets, debt, receivable, netWorth, "VND");
    }

    /**
     * Monthly income/expense comparison for the last N calendar months.
     * Groups transactions by year-month in Java to stay database-agnostic
     * (MySQL and PostgreSQL have incompatible date-formatting functions).
     */
    @SuppressWarnings("unchecked")
    public List<WalletResponse.MonthlyComparison> getMonthlyComparison(UUID userId, int months) {
        Instant since = LocalDate.now(ZoneOffset.UTC)
                .minusMonths(months - 1)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        // Fetch raw rows — let Java do the year-month grouping.
        // Exclude non-spend/non-income legs so they don't inflate monthly income/expense:
        // inter-wallet transfers (TRANSFER_OUT/IN) and balance reconciliations (ADJUSTMENT).
        // txn_type is nullable — most manual/legacy txns are NULL, so the clause must be
        // NULL-safe (`NOT IN` alone would silently drop NULL rows via SQL 3-valued logic).
        List<Object[]> rows = em.createQuery(
                "SELECT t.date, t.type, t.amount FROM Transaction t " +
                "WHERE t.userId = :uid AND t.date >= :since " +
                "AND (t.txnType IS NULL OR t.txnType NOT IN ('TRANSFER_OUT', 'TRANSFER_IN', 'ADJUSTMENT')) " +
                "ORDER BY t.date DESC",
                Object[].class)
                .setParameter("uid", userId)
                .setParameter("since", since)
                .getResultList();

        // Aggregate: month → {INCOME → {count, sum}, EXPENSE → {count, sum}}
        Map<String, Map<String, MonthAgg>> agg = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Instant date = (Instant) row[0];
            String type = (String) row[1];
            BigDecimal amount = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
            String month = date.toString().substring(0, 7); // "2026-03"

            agg.computeIfAbsent(month, k -> new java.util.HashMap<>())
               .computeIfAbsent(type, k -> new MonthAgg(0, BigDecimal.ZERO));
            MonthAgg existing = agg.get(month).get(type);
            agg.get(month).put(type, new MonthAgg(existing.count() + 1, existing.sum().add(amount)));
        }

        List<WalletResponse.MonthlyComparison> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, MonthAgg>> entry : agg.entrySet()) {
            String month = entry.getKey();
            Map<String, MonthAgg> types = entry.getValue();

            MonthAgg income  = types.get("INCOME");
            MonthAgg expense = types.get("EXPENSE");

            BigDecimal totalIncome   = income  != null ? income.sum()   : BigDecimal.ZERO;
            BigDecimal totalExpense  = expense != null ? expense.sum() : BigDecimal.ZERO;
            long incomeCount   = income  != null ? income.count() : 0;
            long expenseCount  = expense != null ? expense.count() : 0;

            String[] parts = month.split("-");
            int monthNum = Integer.parseInt(parts[1]);
            String label = "Thg " + monthNum;

            result.add(new WalletResponse.MonthlyComparison(
                    month,
                    label,
                    totalIncome,
                    totalExpense,
                    totalIncome.subtract(totalExpense),
                    incomeCount + expenseCount
            ));
        }
        return result;
    }

    private record MonthAgg(long count, BigDecimal sum) {}

    private void seedCategories(UUID userId) {
        List<Category> existing = categoryRepo.findByUserIdAndIsActiveTrueOrderByIsDefaultDescNameAsc(userId);
        if (!existing.isEmpty()) return;

        String[][] expense = {
            {"Ăn uống",   "🍜", "#F97316"},
            {"Di chuyển", "🚗", "#3B82F6"},
            {"Mua sắm",   "🛒", "#EC4899"},
            {"Giải trí",  "🎮", "#8B5CF6"},
            {"Tiện ích",  "💡", "#F59E0B"},
            {"Y tế",      "🏥", "#10B981"},
            {"Giáo dục", "📚", "#06B6D4"},
            {"Khác",      "📦", "#94A3B8"},
        };
        String[][] income = {
            {"Lương",    "💰", "#10B981"},
            {"Freelance", "💻", "#22C55E"},
            {"Đầu tư",   "📈", "#14B8A6"},
            {"Quà tặng", "🎁", "#F472B6"},
            {"Khác",     "📦", "#94A3B8"},
        };

        for (String[] c : expense) { saveCat(userId, c[0], c[1], c[2], "EXPENSE", true); }
        for (String[] c : income)  { saveCat(userId, c[0], c[1], c[2], "INCOME", true); }
    }

    private Category saveCat(UUID userId, String name, String icon, String color, String type, boolean isDefault) {
        Category c = new Category();
        c.setUserId(userId); c.setName(name); c.setIcon(icon); c.setColor(color);
        c.setType(type); c.setIsDefault(isDefault);
        return categoryRepo.save(c);
    }

    private WalletResponse toWalletResponse(Wallet w) {
        return new WalletResponse(
            w.getId(), w.getUserId(), w.getName(), w.getType(),
            w.getBalance(), w.getCurrency(), w.getIcon(), w.getColor(),
            w.getIsActive(), w.getCreatedAt(), w.getUpdatedAt()
        );
    }
}
