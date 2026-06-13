package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.dto.CategorySpendResponse;
import dev.chinh.portfolio.apps.wallet.service.StatsService;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** Spending grouped by category for a period (YYYY-MM; defaults to the current month). */
    @GetMapping("/by-category")
    public ResponseEntity<List<CategorySpendResponse>> spendingByCategory(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(statsService.spendingByCategory(userId, period));
    }
}
