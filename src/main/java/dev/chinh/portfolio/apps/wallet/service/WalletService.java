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
import java.util.List;
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

    // ── Helpers ─────────────────────────────────────────────

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
