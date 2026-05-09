package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.TransferService;
import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(
            @CurrentUser UUID userId,
            @Valid @RequestBody TransferRequest req) {
        TransferResult result = transferService.transfer(userId, req);
        return ResponseEntity.ok(result);
    }
}

