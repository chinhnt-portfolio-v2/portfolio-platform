package dev.chinh.portfolio.apps.wallet;

import dev.chinh.portfolio.apps.wallet.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/wallet/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> getBudgets(
            @CurrentUser UUID userId,
            @RequestParam String period) {
        return ResponseEntity.ok(budgetService.getBudgets(userId, period));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateBudgetRequest req) {
        return ResponseEntity.ok(budgetService.createBudget(userId, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponse> updateBudget(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateBudgetRequest req) {
        return ResponseEntity.ok(budgetService.updateBudget(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBudget(
            @CurrentUser UUID userId,
            @PathVariable Long id) {
        budgetService.deleteBudget(userId, id);
        return ResponseEntity.noContent().build();
    }
}
