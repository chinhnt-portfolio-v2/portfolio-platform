package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.Category;
import dev.chinh.portfolio.apps.wallet.CategoryRepository;
import dev.chinh.portfolio.apps.wallet.TransactionRepository;
import dev.chinh.portfolio.apps.wallet.dto.CategorySpendProjection;
import dev.chinh.portfolio.apps.wallet.dto.CategorySpendResponse;
import dev.chinh.portfolio.apps.wallet.util.DateParsing;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read-only spending analytics (category breakdown) for the wallet dashboard. */
@Service
public class StatsService {

    private final TransactionRepository txRepo;
    private final CategoryRepository categoryRepo;

    public StatsService(TransactionRepository txRepo, CategoryRepository categoryRepo) {
        this.txRepo = txRepo;
        this.categoryRepo = categoryRepo;
    }

    /**
     * Expense grouped by category for a {@code "YYYY-MM"} period (defaults to the current month
     * in the app timezone), each row carrying its share (%) of total spend, sorted high→low.
     */
    public List<CategorySpendResponse> spendingByCategory(UUID userId, String period) {
        if (period == null || period.isBlank()) {
            period = YearMonth.now(DateParsing.APP_ZONE).toString();
        }
        Instant from = DateParsing.monthStart(period);
        Instant to = DateParsing.monthEndExclusive(period);

        List<CategorySpendProjection> rows = txRepo.sumExpenseByCategory(userId, from, to);
        BigDecimal total = rows.stream()
                .map(CategorySpendProjection::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategorySpendResponse> result = new ArrayList<>();
        for (CategorySpendProjection r : rows) {
            Category c = categoryRepo.findById(r.getCategoryId()).orElse(null);
            int pct = total.signum() > 0
                    ? r.getTotal().multiply(BigDecimal.valueOf(100))
                        .divide(total, 0, RoundingMode.HALF_UP).intValue()
                    : 0;
            result.add(new CategorySpendResponse(
                    r.getCategoryId(),
                    c != null ? c.getName() : null,
                    c != null ? c.getIcon() : null,
                    c != null ? c.getColor() : null,
                    r.getTotal(), pct));
        }
        result.sort((a, b) -> b.total().compareTo(a.total()));
        return result;
    }
}
