package dev.chinh.portfolio.apps.wallet;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository budgetRepository, TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgets(UUID userId, String period) {
        List<Budget> budgets = budgetRepository.findByUserIdAndPeriod(userId, period);
        List<BudgetResponse> result = new ArrayList<>();
        for (Budget b : budgets) {
            result.add(toResponse(b, userId, period));
        }
        return result;
    }

    @Transactional
    public BudgetResponse createBudget(UUID userId, CreateBudgetRequest req) {
        if (budgetRepository.findByUserIdAndCategoryIdAndPeriod(userId, req.categoryId(), req.period()).isPresent()) {
            throw new IllegalArgumentException("Budget already exists for this category and period");
        }
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategoryId(req.categoryId());
        budget.setMonthlyLimit(BigDecimal.valueOf(req.monthlyLimit()));
        if (req.alertThreshold() != null) {
            budget.setAlertThreshold(req.alertThreshold());
        } else {
            budget.setAlertThreshold(80);
        }
        budget.setPeriod(req.period());
        Budget saved = budgetRepository.save(budget);
        return toResponse(saved, userId, req.period());
    }

    @Transactional
    public BudgetResponse updateBudget(UUID userId, Long id, UpdateBudgetRequest req) {
        Budget budget = budgetRepository.findById(id).orElse(null);
        if (budget == null || !budget.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Budget not found");
        }
        if (req.monthlyLimit() != null) {
            budget.setMonthlyLimit(BigDecimal.valueOf(req.monthlyLimit()));
        }
        if (req.alertThreshold() != null) {
            budget.setAlertThreshold(req.alertThreshold());
        }
        Budget saved = budgetRepository.save(budget);
        return toResponse(saved, userId, saved.getPeriod());
    }

    @Transactional
    public void deleteBudget(UUID userId, Long id) {
        Budget budget = budgetRepository.findById(id).orElse(null);
        if (budget == null || !budget.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Budget not found");
        }
        budgetRepository.delete(budget);
    }

    private BudgetResponse toResponse(Budget b, UUID userId, String period) {
        BigDecimal spent = calculateSpent(userId, b.getCategoryId(), period);
        BigDecimal limit = b.getMonthlyLimit();
        int pct = 0;
        if (limit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = spent.divide(limit, 4, RoundingMode.HALF_UP);
            pct = ratio.multiply(BigDecimal.valueOf(100)).intValue();
        }
        int threshold = b.getAlertThreshold() != null ? b.getAlertThreshold() : 80;
        String status = pct >= 100 ? "exceeded" : pct >= threshold ? "warning" : "ok";

        dev.chinh.portfolio.apps.wallet.Category cat = null;
        try { cat = b.getCategory(); } catch (Exception ignored) {}

        dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.CategorySummary catSummary;
        if (cat != null) {
            catSummary = new dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.CategorySummary(
                    cat.getId(), cat.getName(), cat.getIcon(), cat.getColor());
        } else {
            catSummary = new dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.CategorySummary(
                    b.getCategoryId(), null, null, null);
        }

        return new BudgetResponse(
                b.getId(), b.getUserId(), b.getCategoryId(),
                b.getMonthlyLimit(), b.getAlertThreshold(), b.getPeriod(),
                catSummary, spent, pct, status, b.getCreatedAt()
        );
    }

    private BigDecimal calculateSpent(UUID userId, Long categoryId, String period) {
        String[] parts = period.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate toDate = from.withDayOfMonth(from.lengthOfMonth());
        Instant fromInst = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInst = toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        try {
            return transactionRepository.sumExpenseSince(userId, categoryId, fromInst, toInst);
        } catch (Exception e) {
            // Fallback: manual calculation
            List<dev.chinh.portfolio.apps.wallet.Transaction> all =
                    transactionRepository.findByUserIdAndCategoryIdOrderByDateDesc(
                            userId, categoryId, org.springframework.data.domain.Pageable.unpaged());
            BigDecimal total = BigDecimal.ZERO;
            for (dev.chinh.portfolio.apps.wallet.Transaction t : all) {
                if ("EXPENSE".equals(t.getType()) && t.getDate() != null) {
                    LocalDate d = t.getDate().atZone(ZoneOffset.UTC).toLocalDate();
                    if (!d.isBefore(from) && !d.isAfter(toDate)) {
                        total = total.add(t.getAmount());
                    }
                }
            }
            return total;
        }
    }
}
