package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.apps.wallet.service.DebtGroupService;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
public class DebtGroupController {

    private final DebtGroupService debtGroupService;

    public DebtGroupController(DebtGroupService debtGroupService) {
        this.debtGroupService = debtGroupService;
    }

    @GetMapping("/groups")
    public ResponseEntity<List<DebtGroupResponse>> listGroups(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(debtGroupService.listGroups(userId, status));
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<DebtGroupResponse> getGroup(
            @CurrentUser UUID userId, @PathVariable Long id) {
        return ResponseEntity.ok(debtGroupService.getGroup(userId, id));
    }

    @PostMapping("/groups")
    public ResponseEntity<DebtGroupResponse> createGroup(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateDebtGroupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(debtGroupService.createGroup(userId, req));
    }

    @PostMapping("/groups/{id}/settle")
    public ResponseEntity<DebtGroupResponse> settleDebt(
            @CurrentUser UUID userId,
            @PathVariable Long id,
            @Valid @RequestBody SettleDebtRequest req) {
        return ResponseEntity.ok(debtGroupService.settleDebt(userId, id, req));
    }
}
