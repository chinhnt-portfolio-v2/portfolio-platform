package dev.chinh.portfolio.apps.wallet;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.*;

@Service
public class RecurringService {

    private final RecurringRuleRepository recurringRuleRepository;

    public RecurringService(RecurringRuleRepository repository) {
        this.recurringRuleRepository = repository;
    }

    public List<RecurringRuleResponse> getAll(UUID userId) {
        List<RecurringRule> rules = recurringRuleRepository.findByUserIdAndStatusNot(userId, "CANCELLED");
        return rules.stream().map(this::toResponse).toList();
    }

    @Transactional
    public RecurringRuleResponse create(UUID userId, CreateRecurringRequest req) {
        RecurringRule rule = new RecurringRule();
        rule.setUserId(userId);
        rule.setWalletId(req.walletId());
        rule.setCategoryId(req.categoryId());
        rule.setAmount(BigDecimal.valueOf(req.amount()));
        rule.setType(req.type());
        rule.setFrequency(req.frequency());
        rule.setStartDate(LocalDate.parse(req.startDate()));
        if (req.endDate() != null && !req.endDate().isBlank()) {
            rule.setEndDate(LocalDate.parse(req.endDate()));
        }
        rule.setStatus("ACTIVE");
        rule.setNote(req.note());
        rule.setNextOccurrence(computeNextOccurrence(rule.getStartDate(), rule.getFrequency()));
        rule = recurringRuleRepository.save(rule);
        return toResponse(rule);
    }

    @Transactional
    public RecurringRuleResponse update(UUID userId, Long id, CreateRecurringRequest req) {
        RecurringRule rule = recurringRuleRepository.findById(id)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Recurring rule not found"));
        rule.setWalletId(req.walletId());
        rule.setCategoryId(req.categoryId());
        rule.setAmount(BigDecimal.valueOf(req.amount()));
        rule.setType(req.type());
        rule.setFrequency(req.frequency());
        rule.setStartDate(LocalDate.parse(req.startDate()));
        if (req.endDate() != null && !req.endDate().isBlank()) {
            rule.setEndDate(LocalDate.parse(req.endDate()));
        } else {
            rule.setEndDate(null);
        }
        rule.setNote(req.note());
        rule.setNextOccurrence(computeNextOccurrence(rule.getStartDate(), rule.getFrequency()));
        rule = recurringRuleRepository.save(rule);
        return toResponse(rule);
    }

    @Transactional
    public RecurringRuleResponse toggleStatus(UUID userId, Long id, String status) {
        RecurringRule rule = recurringRuleRepository.findById(id)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Recurring rule not found"));
        rule.setStatus(status);
        rule = recurringRuleRepository.save(rule);
        return toResponse(rule);
    }

    @Transactional
    public void delete(UUID userId, Long id) {
        RecurringRule rule = recurringRuleRepository.findById(id)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Recurring rule not found"));
        recurringRuleRepository.delete(rule);
    }

    private RecurringRuleResponse toResponse(RecurringRule r) {
        Wallet w = null;
        Category c = null;
        try { w = r.getWallet(); } catch (Exception ignored) {}
        try { c = r.getCategory(); } catch (Exception ignored) {}

        TransactionResponse.WalletSummary wSummary = w != null
                ? new TransactionResponse.WalletSummary(w.getId(), w.getName(), w.getIcon(), w.getColor(), w.getType())
                : new TransactionResponse.WalletSummary(r.getWalletId(), null, null, null, null);
        TransactionResponse.CategorySummary cSummary = c != null
                ? new TransactionResponse.CategorySummary(c.getId(), c.getName(), c.getIcon(), c.getColor())
                : new TransactionResponse.CategorySummary(r.getCategoryId(), null, null, null);

        return new RecurringRuleResponse(
                r.getId(), r.getUserId(), r.getWalletId(), r.getCategoryId(),
                r.getAmount(), r.getType(), r.getFrequency(),
                r.getStartDate(), r.getEndDate(), r.getNextOccurrence(),
                r.getStatus(), r.getNote(),
                wSummary, cSummary,
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    /** Compute next occurrence from a given date based on frequency */
    public static LocalDate computeNextOccurrence(LocalDate from, String frequency) {
        LocalDate today = LocalDate.now();
        LocalDate next = from;
        while (!next.isAfter(today)) {
            next = switch (frequency) {
                case "DAILY"   -> next.plusDays(1);
                case "WEEKLY"  -> next.plusWeeks(1);
                case "MONTHLY" -> next.plusMonths(1);
                case "YEARLY"  -> next.plusYears(1);
                default        -> next;
            };
        }
        return next;
    }
}
