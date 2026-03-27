package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    @PersistenceContext
    private EntityManager em;

    private final TransactionRepository txRepo;
    private final WalletRepository walletRepo;
    private final CategoryRepository categoryRepo;
    private final DebtGroupRepository debtGroupRepo;

    public TransactionService(TransactionRepository txRepo,
                              WalletRepository walletRepo,
                              CategoryRepository categoryRepo,
                              DebtGroupRepository debtGroupRepo) {
        this.txRepo = txRepo;
        this.walletRepo = walletRepo;
        this.categoryRepo = categoryRepo;
        this.debtGroupRepo = debtGroupRepo;
    }

    public List<TransactionResponse> listTransactions(UUID userId, String type, Long walletId, Long groupId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        var txs = txRepo.findByUserIdOrderByDateDesc(userId, pageable).getContent();
        return txs.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TransactionResponse createTransaction(UUID userId, CreateTransactionRequest req) {
        // Validate wallet belongs to user
        var wallet = walletRepo.findByIdAndUserId(req.walletId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));

        // Validate group if provided
        if (req.groupId() != null) {
            if (!debtGroupRepo.existsByIdAndUserId(req.groupId(), userId))
                throw new EntityNotFoundException("Debt group not found");
        }

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setWalletId(req.walletId());
        tx.setCategoryId(req.categoryId());
        tx.setGroupId(req.groupId());
        tx.setAmount(BigDecimal.valueOf(req.amount()));
        tx.setType(req.type());
        tx.setTxnType(req.txnType() != null ? req.txnType() : "PRINCIPAL");
        tx.setNote(req.note());
        tx.setDate(req.date() != null ? Instant.parse(req.date()) : Instant.now());

        tx = txRepo.save(tx);

        // Update wallet balance
        BigDecimal delta = req.type().equals("INCOME")
                ? BigDecimal.valueOf(req.amount())
                : BigDecimal.valueOf(req.amount()).negate();
        wallet.setBalance(wallet.getBalance().add(delta));
        walletRepo.save(wallet);

        // If linked to a debt group, update paid amount
        if (req.groupId() != null && req.type().equals("INCOME")) {
            debtGroupRepo.findByIdAndUserId(req.groupId(), userId).ifPresent(group -> {
                BigDecimal newPaid = group.getPaidAmount().add(BigDecimal.valueOf(req.amount()));
                group.setPaidAmount(newPaid);
                if (newPaid.compareTo(group.getTotalAmount()) >= 0) {
                    group.setStatus("SETTLED");
                } else {
                    group.setStatus("PARTIAL");
                }
                debtGroupRepo.save(group);
            });
        }

        return toResponse(tx);
    }

    public TransactionResponse getTransaction(UUID userId, Long txId) {
        Transaction tx = txRepo.findByIdAndUserId(txId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
        return toResponse(tx);
    }

    @Transactional
    public TransactionResponse updateTransaction(UUID userId, Long txId, CreateTransactionRequest req) {
        // Simplified — full implementation would reverse old balance delta first
        Transaction tx = txRepo.findByIdAndUserId(txId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
        if (req.amount() != null) tx.setAmount(BigDecimal.valueOf(req.amount()));
        if (req.note() != null) tx.setNote(req.note());
        return toResponse(txRepo.save(tx));
    }

    @Transactional
    public void deleteTransaction(UUID userId, Long txId) {
        if (!txRepo.findByIdAndUserId(txId, userId).isPresent())
            throw new EntityNotFoundException("Transaction not found");
        txRepo.deleteById(txId);
    }

    // ── Helpers ──────────────────────────────────────────────

    private TransactionResponse toResponse(Transaction tx) {
        var wallet = tx.getWalletId() != null
                ? walletRepo.findById(tx.getWalletId()).orElse(null) : null;
        var category = tx.getCategoryId() != null
                ? categoryRepo.findById(tx.getCategoryId()).orElse(null) : null;
        var group = tx.getGroupId() != null
                ? debtGroupRepo.findById(tx.getGroupId()).orElse(null) : null;

        return new TransactionResponse(
            tx.getId(), tx.getUserId(), tx.getWalletId(), tx.getCategoryId(), tx.getGroupId(),
            tx.getAmount(), tx.getType(), tx.getTxnType(), tx.getNote(), tx.getDate(),
            wallet != null ? new TransactionResponse.WalletSummary(
                    wallet.getId(), wallet.getName(), wallet.getIcon(), wallet.getColor(), wallet.getType()) : null,
            category != null ? new TransactionResponse.CategorySummary(
                    category.getId(), category.getName(), category.getIcon(), category.getColor()) : null,
            group != null ? new TransactionResponse.DebtGroupSummary(
                    group.getId(), group.getTitle(), group.getGroupType()) : null,
            tx.getCreatedAt(), tx.getUpdatedAt()
        );
    }
}
