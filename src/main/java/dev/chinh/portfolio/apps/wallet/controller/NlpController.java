package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.dto.NlpParseRequest;
import dev.chinh.portfolio.apps.wallet.dto.NlpParseResult;
import dev.chinh.portfolio.apps.wallet.service.NlpRateLimiter;
import dev.chinh.portfolio.apps.wallet.service.NlpService;
import dev.chinh.portfolio.auth.annotation.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet/transactions")
public class NlpController {

    private final NlpService nlpService;
    private final NlpRateLimiter nlpRateLimiter;

    public NlpController(NlpService nlpService, NlpRateLimiter nlpRateLimiter) {
        this.nlpService = nlpService;
        this.nlpRateLimiter = nlpRateLimiter;
    }

    /**
     * Parse natural Vietnamese text into a structured transaction.
     * Rate limited: 20 calls per user per hour.
     * Input capped at 200 characters (validated in DTO).
     */
    @PostMapping("/nlp")
    public ResponseEntity<NlpParseResult> parseNlp(
            @Valid @RequestBody NlpParseRequest request,
            @CurrentUser UUID userId) {
        nlpRateLimiter.checkLimit(userId);
        return ResponseEntity.ok(nlpService.parse(request.text(), userId));
    }
}
