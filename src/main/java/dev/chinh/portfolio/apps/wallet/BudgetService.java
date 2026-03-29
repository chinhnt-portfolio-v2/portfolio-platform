package dev.chinh.portfolio.apps.wallet;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<BudgetResponse> getBudgets(UUID userId, String period) {
        List<Budget> budgets = budgetRepository.findByUserIdAndPeriod(userId, period);
        return budgets.stream().map(b -> toResponse(b, userId, period)).toList();
    }

    @Transactional
    public BudgetResponse createBudget(UUID userId, CreateBudgetRequest req) {
        // Check for duplicate
        if (budgetRepository.findByUserIdAndCategoryIdAndPeriod(userId, req.categoryId(), req.period()).isPresent()) {
            throw new IllegalArgumentException("Budget already exists for this category and period");
        }

        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategoryId(req.categoryId());
        budget.setMonthlyLimit(BigDecimal.valueOf(req.monthlyLimit()));
        budget.setAlertThreshold(req.alertThreshold() != null ? req.alertThreshold() : 80);
        budget.setPeriod(req.period());
        budget = budgetRepository.save(budget);
        return toResponse(budget, userId, req.period());
    }

    @Transactional
    public BudgetResponse updateBudget(UUID userId, Long id, UpdateBudgetRequest req) {
        Budget budget = budgetRepository.findById(id)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Budget not found"));
        if (req.monthlyLimit() != null) {
            budget.setMonthlyLimit(BigDecimal.valueOf(req.monthlyLimit()));
        }
        if (req.alertThreshold() != null) {
            budget.setAlertThreshold(req.alertThreshold());
        }
        budget = budgetRepository.save(budget);
        return toResponse(budget, userId, budget.getPeriod());
    }

    @Transactional
    public void deleteBudget(UUID userId, Long id) {
        Budget budget = budgetRepository.findById(id)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Budget not found"));
        budgetRepository.delete(budget);
    }

    private BudgetResponse toResponse(Budget b, UUID userId, String period) {
        // Calculate spending for this category in the period
        BigDecimal spent = calculateSpent(userId, b.getCategoryId(), period);
        BigDecimal limit = b.getMonthlyLimit();
        int pct = limit.compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(limit, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .intValue()
                : 0;
        int threshold = b.getAlertThreshold() != null ? b.getAlertThreshold() : 80;
        String status = pct >= 100 ? "exceeded" : pct >= threshold ? "warning" : "ok";

        Category cat = null;
        try {
            cat = b.getCategory();
        } catch (Exception ignored) {}

        TransactionResponse.CategorySummary catSummary = cat != null
                ? new TransactionResponse.CategorySummary(cat.getId(), cat.getName(), cat.getIcon(), cat.getColor())
                : new TransactionResponse.CategorySummary(b.getCategoryId(), null, null, null);

        return new BudgetResponse(
                b.getId(), b.getUserId(), b.getCategoryId(),
                b.getMonthlyLimit(), b.getAlertThreshold(), b.getPeriod(),
                catSummary, spent, pct, status, b.getCreatedAt()
        );
    }

    private BigDecimal calculateSpent(UUID userId, Long categoryId, String period) {
        // Parse YYYY-MM
        String[] parts = period.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        Instant fromInst = from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        Instant toInst = to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

        try {
            return transactionRepository.sumExpenseSince(userId, categoryId, fromInst, toInst);
        } catch (Exception e) {
            // Fallback: query all transactions
            var all = transactionRepository.findByUserIdAndCategoryIdOrderByDateDesc(userId, categoryId,
                    org.springframework.data.domain.Pageable.unpaged());
            return all.getContent().stream()
                    .filter(t -> "EXPENSE".equals(t.getType()))
                    .filter(t -> t.getDate() != null)
                    .filter(t -> {
                        LocalDate d = t.getDate().atZone(java.time.ZoneOffset.UTC).toLocalDate();
                        return !d.isBefore(from) && !d.isAfter(to);
                    })
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
