package dev.chinh.portfolio.apps.wallet;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class RecurringService {

    private final RecurringRuleRepository repository;

    public RecurringService(RecurringRuleRepository repository) {
        this.repository = repository;
    }

    public List<RecurringRuleResponse> getAll(UUID userId) {
        List<RecurringRule> rules = repository.findByUserIdAndStatusNot(userId, "CANCELLED");
        List<RecurringRuleResponse> result = new ArrayList<>();
        for (RecurringRule r : rules) {
            result.add(toResponse(r));
        }
        return result;
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
        rule.setNextOccurrence(computeNext(rule.getStartDate(), rule.getFrequency()));
        RecurringRule saved = repository.save(rule);
        return toResponse(saved);
    }

    @Transactional
    public RecurringRuleResponse update(UUID userId, Long id, CreateRecurringRequest req) {
        RecurringRule rule = repository.findById(id).orElse(null);
        if (rule == null || !rule.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Recurring rule not found");
        }
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
        rule.setNextOccurrence(computeNext(rule.getStartDate(), rule.getFrequency()));
        RecurringRule saved = repository.save(rule);
        return toResponse(saved);
    }

    @Transactional
    public RecurringRuleResponse toggleStatus(UUID userId, Long id, String status) {
        RecurringRule rule = repository.findById(id).orElse(null);
        if (rule == null || !rule.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Recurring rule not found");
        }
        rule.setStatus(status);
        RecurringRule saved = repository.save(rule);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID userId, Long id) {
        RecurringRule rule = repository.findById(id).orElse(null);
        if (rule == null || !rule.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Recurring rule not found");
        }
        repository.delete(rule);
    }

    private RecurringRuleResponse toResponse(RecurringRule r) {
        dev.chinh.portfolio.apps.wallet.Wallet w = null;
        dev.chinh.portfolio.apps.wallet.Category c = null;
        try { w = r.getWallet(); } catch (Exception ignored) {}
        try { c = r.getCategory(); } catch (Exception ignored) {}

        dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.WalletSummary wSummary;
        if (w != null) {
            wSummary = new dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.WalletSummary(
                    w.getId(), w.getName(), w.getIcon(), w.getColor(), w.getType());
        } else {
            wSummary = new dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.WalletSummary(
                    r.getWalletId(), null, null, null, null);
        }

        dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.CategorySummary cSummary;
        if (c != null) {
            cSummary = new dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.CategorySummary(
                    c.getId(), c.getName(), c.getIcon(), c.getColor());
        } else {
            cSummary = new dev.chinh.portfolio.apps.wallet.dto.TransactionResponse.CategorySummary(
                    r.getCategoryId(), null, null, null);
        }

        return new RecurringRuleResponse(
                r.getId(), r.getUserId(), r.getWalletId(), r.getCategoryId(),
                r.getAmount(), r.getType(), r.getFrequency(),
                r.getStartDate(), r.getEndDate(), r.getNextOccurrence(),
                r.getStatus(), r.getNote(),
                wSummary, cSummary,
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    private static LocalDate computeNext(LocalDate from, String frequency) {
        LocalDate today = LocalDate.now();
        LocalDate next = from;
        while (!next.isAfter(today)) {
            next = switch (frequency) {
                case "DAILY"   -> next.plusDays(1);
                case "WEEKLY"  -> next.plusWeeks(1);
                case "MONTHLY" -> next.plusMonths(1);
                case "YEARLY"  -> next.plusYears(1);
                default -> next;
            };
        }
        return next;
    }
}
