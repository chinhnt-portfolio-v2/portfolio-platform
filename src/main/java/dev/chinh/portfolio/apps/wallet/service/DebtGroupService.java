package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DebtGroupService {

    private final DebtGroupRepository debtGroupRepo;
    private final WalletRepository walletRepo;
    private final TransactionRepository transactionRepo;

    public DebtGroupService(DebtGroupRepository debtGroupRepo,
                             WalletRepository walletRepo,
                             TransactionRepository transactionRepo) {
        this.debtGroupRepo = debtGroupRepo;
        this.walletRepo = walletRepo;
        this.transactionRepo = transactionRepo;
    }

    public List<DebtGroupResponse> listGroups(UUID userId, String status) {
        List<DebtGroup> groups;
        if (status != null && !status.isEmpty()) {
            groups = debtGroupRepo.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            groups = debtGroupRepo.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return groups.stream().map(this::toResponse).toList();
    }

    public DebtGroupResponse getGroup(UUID userId, Long groupId) {
        DebtGroup g = debtGroupRepo.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Debt group not found"));
        return toResponse(g);
    }

    @Transactional
    public DebtGroupResponse createGroup(UUID userId, CreateDebtGroupRequest req) {
        DebtGroup g = new DebtGroup();
        g.setUserId(userId);
        g.setWalletId(req.walletId());
        g.setTitle(req.title());
        g.setGroupType(req.groupType());
        g.setTotalAmount(BigDecimal.valueOf(req.totalAmount()));
        g.setCurrency("VND");
        g.setStatus("OPEN");
        if (req.dueDate() != null) g.setDueDate(Instant.parse(req.dueDate()));
        if (req.interestRate() != null) g.setInterestRate(BigDecimal.valueOf(req.interestRate()));
        g.setCounterparty(req.counterparty());
        g = debtGroupRepo.save(g);
        return toResponse(g);
    }

    @Transactional
    public DebtGroupResponse settleDebt(UUID userId, Long groupId, SettleDebtRequest req) {
        DebtGroup g = debtGroupRepo.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Debt group not found"));

        var wallet = walletRepo.findByIdAndUserId(req.walletId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));

        BigDecimal amount = BigDecimal.valueOf(req.amount());

        // Deduct from wallet
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepo.save(wallet);

        // Create payment transaction
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setWalletId(req.walletId());
        tx.setGroupId(groupId);
        tx.setAmount(amount);
        tx.setType("EXPENSE");
        tx.setTxnType("PAYMENT");
        tx.setNote(req.note() != null ? req.note() : "Thanh toán nợ: " + g.getTitle());
        tx.setDate(Instant.now());
        transactionRepo.save(tx);

        // Update group paid amount
        BigDecimal newPaid = g.getPaidAmount().add(amount);
        g.setPaidAmount(newPaid);
        if (newPaid.compareTo(g.getTotalAmount()) >= 0) {
            g.setStatus("SETTLED");
        } else {
            g.setStatus("PARTIAL");
        }
        debtGroupRepo.save(g);

        return toResponse(g);
    }

    private DebtGroupResponse toResponse(DebtGroup g) {
        var wallet = g.getWalletId() != null
                ? walletRepo.findById(g.getWalletId()).orElse(null) : null;

        return new DebtGroupResponse(
            g.getId(), g.getUserId(), g.getWalletId(), g.getTitle(),
            g.getGroupType(), g.getTotalAmount(), g.getPaidAmount(),
            g.getRemaining(), g.getCurrency(), g.getStatus(),
            g.getDueDate(), g.getInterestRate(), g.getCounterparty(),
            g.getNotes(),
            wallet != null ? new DebtGroupResponse.WalletSummary(
                    wallet.getId(), wallet.getName(), wallet.getIcon(), wallet.getColor(), wallet.getType()) : null,
            g.getCreatedAt(), g.getUpdatedAt()
        );
    }
}
