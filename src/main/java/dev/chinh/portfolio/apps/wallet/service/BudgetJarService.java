package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.BudgetJarResponse;
import dev.chinh.portfolio.apps.wallet.dto.CreateBudgetJarRequest;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BudgetJarService {

    private final BudgetJarRepository jarRepo;
    private final CategoryRepository categoryRepo;
    private final TransactionRepository txRepo;

    public BudgetJarService(BudgetJarRepository jarRepo,
                             CategoryRepository categoryRepo,
                             TransactionRepository txRepo) {
        this.jarRepo = jarRepo;
        this.categoryRepo = categoryRepo;
        this.txRepo = txRepo;
    }

    // ── Query ────────────────────────────────────────────────────────────────

    public Map<String, Object> getJarsWithMonthlyData(UUID userId, String period) {
        String p = (period != null && !period.isBlank()) ? period : currentPeriod();
        Instant[] bounds = periodBounds(p);
        Instant fromInst = bounds[0];
        Instant toInst = bounds[1];

        // Monthly income: INCOME transactions, exclude transfers (categoryId IS NULL)
        // and debt-linked transactions (txnType IS NOT NULL)
        BigDecimal monthlyIncome = txRepo.sumMonthlyIncome(userId, fromInst, toInst);
        if (monthlyIncome == null) monthlyIncome = BigDecimal.ZERO;

        List<BudgetJar> jars = jarRepo.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        BigDecimal totalPct = BigDecimal.ZERO;

        List<BudgetJarResponse> responses = new ArrayList<>();
        for (BudgetJar jar : jars) {
            Set<Long> catIds = jar.getCategories().stream()
                    .map(Category::getId)
                    .collect(Collectors.toSet());

            BigDecimal allocated = monthlyIncome
                    .multiply(jar.getPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal spent = catIds.isEmpty()
                    ? BigDecimal.ZERO
                    : txRepo.sumExpenseForCategories(userId, catIds, fromInst, toInst);
            if (spent == null) spent = BigDecimal.ZERO;

            BigDecimal remaining = allocated.subtract(spent);
            String status = remaining.compareTo(BigDecimal.ZERO) >= 0 ? "ok" : "exceeded";

            totalPct = totalPct.add(jar.getPercentage());

            List<BudgetJarResponse.CategorySummary> catSummaries = jar.getCategories().stream()
                    .map(c -> new BudgetJarResponse.CategorySummary(c.getId(), c.getName(), c.getIcon(), c.getColor()))
                    .sorted(Comparator.comparing(BudgetJarResponse.CategorySummary::name))
                    .collect(Collectors.toList());

            responses.add(new BudgetJarResponse(
                    jar.getId(), jar.getName(),
                    jar.getPercentage(), jar.getIcon(), jar.getColor(),
                    jar.getIsPreset(), jar.getSortOrder(),
                    catSummaries,
                    monthlyIncome, allocated, spent, remaining, status
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jars", responses);
        result.put("totalPercentage", totalPct);
        result.put("monthlyIncome", monthlyIncome);
        return result;
    }

    // ── Preset ───────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createPreset(UUID userId) {
        if (jarRepo.existsByUserIdAndIsPresetTrue(userId)) {
            // Idempotent: return existing jars without duplicating
            return getJarsWithMonthlyData(userId, currentPeriod());
        }

        // Fetch user's categories by name for mapping
        List<Category> userCats = categoryRepo.findByUserIdAndIsActiveTrueOrderByIsDefaultDescNameAsc(userId);
        Map<String, Category> catByName = userCats.stream()
                .collect(Collectors.toMap(Category::getName, c -> c, (a, b) -> a));

        List<Object[]> presets = buildPresetDefinitions();
        for (int i = 0; i < presets.size(); i++) {
            Object[] def = presets.get(i);
            String name = (String) def[0];
            BigDecimal pct = (BigDecimal) def[1];
            String icon = (String) def[2];
            String color = (String) def[3];
            @SuppressWarnings("unchecked")
            List<String> catNames = (List<String>) def[4];

            BudgetJar jar = new BudgetJar();
            jar.setUserId(userId);
            jar.setName(name);
            jar.setPercentage(pct);
            jar.setIcon(icon);
            jar.setColor(color);
            jar.setIsPreset(true);
            jar.setSortOrder(i);

            Set<Category> mappedCats = new HashSet<>();
            for (String catName : catNames) {
                Category cat = catByName.get(catName);
                if (cat != null) mappedCats.add(cat);
            }
            jar.setCategories(mappedCats);

            jarRepo.save(jar);
        }

        return getJarsWithMonthlyData(userId, currentPeriod());
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public BudgetJarResponse createJar(UUID userId, CreateBudgetJarRequest req) {
        validatePercentageHeadroom(userId, null, req.percentage());

        BudgetJar jar = new BudgetJar();
        jar.setUserId(userId);
        applyRequest(jar, req, userId);
        BudgetJar saved = jarRepo.save(jar);
        return toResponse(saved, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public BudgetJarResponse updateJar(Long id, UUID userId, CreateBudgetJarRequest req) {
        BudgetJar jar = jarRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Budget jar not found"));

        if (!jar.getPercentage().equals(req.percentage())) {
            validatePercentageHeadroom(userId, id, req.percentage());
        }

        applyRequest(jar, req, userId);
        BudgetJar saved = jarRepo.save(jar);
        return toResponse(saved, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Transactional
    public void deleteJar(Long id, UUID userId) {
        BudgetJar jar = jarRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Budget jar not found"));
        jarRepo.delete(jar);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void applyRequest(BudgetJar jar, CreateBudgetJarRequest req, UUID userId) {
        jar.setName(req.name());
        jar.setPercentage(req.percentage());
        if (req.icon() != null) jar.setIcon(req.icon());
        if (req.color() != null) jar.setColor(req.color());

        Set<Category> cats = new HashSet<>();
        if (req.categoryIds() != null) {
            for (Long catId : req.categoryIds()) {
                categoryRepo.findByIdAndUserId(catId, userId).ifPresent(cats::add);
            }
        }
        jar.setCategories(cats);
    }

    private void validatePercentageHeadroom(UUID userId, Long excludeJarId, BigDecimal newPct) {
        BigDecimal current = jarRepo.sumPercentageByUserId(userId);
        if (excludeJarId != null) {
            // Subtract the existing jar's percentage so the update headroom is correct
            BigDecimal existing = jarRepo.findById(excludeJarId)
                    .map(BudgetJar::getPercentage)
                    .orElse(BigDecimal.ZERO);
            current = current.subtract(existing);
        }
        if (current.add(newPct).compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Total jar percentage would exceed 100% (current: " + current + "%, adding: " + newPct + "%)");
        }
    }

    private BudgetJarResponse toResponse(BudgetJar jar, BigDecimal allocated, BigDecimal spent) {
        BigDecimal remaining = allocated.subtract(spent);
        String status = remaining.compareTo(BigDecimal.ZERO) >= 0 ? "ok" : "exceeded";
        List<BudgetJarResponse.CategorySummary> cats = jar.getCategories().stream()
                .map(c -> new BudgetJarResponse.CategorySummary(c.getId(), c.getName(), c.getIcon(), c.getColor()))
                .sorted(Comparator.comparing(BudgetJarResponse.CategorySummary::name))
                .collect(Collectors.toList());
        return new BudgetJarResponse(
                jar.getId(), jar.getName(),
                jar.getPercentage(), jar.getIcon(), jar.getColor(),
                jar.getIsPreset(), jar.getSortOrder(),
                cats, BigDecimal.ZERO, allocated, spent, remaining, status
        );
    }

    private String currentPeriod() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.getYear() + "-" + String.format("%02d", now.getMonthValue());
    }

    private Instant[] periodBounds(String period) {
        String[] parts = period.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return new Instant[]{
            from.atStartOfDay().toInstant(ZoneOffset.UTC),
            to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        };
    }

    /**
     * 6-jar JARS method preset definitions.
     * Category names MUST use Vietnamese diacritics to match V5 seed data.
     */
    private List<Object[]> buildPresetDefinitions() {
        return List.of(
            new Object[]{"Nhu cầu thiết yếu", new BigDecimal("55"), "🏠", "#0EA5E9",
                List.of("Ăn uống", "Di chuyển", "Tiện ích", "Y tế")},
            new Object[]{"Giáo dục", new BigDecimal("10"), "📚", "#8B5CF6",
                List.of("Giáo dục")},
            new Object[]{"Tiết kiệm", new BigDecimal("10"), "💰", "#10B981",
                Collections.emptyList()},
            new Object[]{"Giải trí", new BigDecimal("10"), "🎮", "#F97316",
                List.of("Giải trí", "Mua sắm")},
            new Object[]{"Đầu tư", new BigDecimal("10"), "📈", "#14B8A6",
                Collections.emptyList()},  // Đầu tư is an INCOME category, not expense
            new Object[]{"Cho tặng", new BigDecimal("5"), "🎁", "#F472B6",
                Collections.emptyList()}   // Quà tặng is also INCOME; jar tracks spending by intent
        );
    }
}
