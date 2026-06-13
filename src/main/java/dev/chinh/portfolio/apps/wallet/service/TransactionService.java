package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.apps.wallet.util.DateParsing;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
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

    public List<TransactionResponse> listTransactions(UUID userId, String type, Long walletId,
                                                      Long categoryId, Long groupId, String dateFrom, String dateTo,
                                                      String search, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        Instant from = (dateFrom != null && !dateFrom.isBlank()) ? DateParsing.parseFlexibleInstant(dateFrom) : null;
        // Exclusive upper bound: date-only dateTo includes the full named day (start of next day).
        Instant to = (dateTo != null && !dateTo.isBlank()) ? DateParsing.parseFlexibleEndExclusive(dateTo) : null;

        // Dynamic predicates: only non-null filters reach SQL. A single JPQL with
        // "(:param IS NULL OR ...)" guards breaks on Postgres null-parameter typing.
        Specification<Transaction> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (type != null && !type.isBlank())
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        if (walletId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("walletId"), walletId));
        if (categoryId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("categoryId"), categoryId));
        if (groupId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("groupId"), groupId));
        if (from != null) {
            Instant f = from;
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("date"), f));
        }
        if (to != null) {
            Instant t = to;
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("date"), t));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("note")), pattern));
        }

        var txs = txRepo.findAll(spec, pageable).getContent();
        return txs.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TransactionResponse createTransaction(UUID userId, CreateTransactionRequest req) {
        // Validate wallet belongs to user
        var wallet = walletRepo.findByIdAndUserId(req.walletId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));

        // Resolve groupId. For an EXPENSE on a POSTPAID (pay-later) wallet with no explicit group,
        // accumulate into ONE consolidated BNPL debt per wallet — find the active (OPEN/PARTIAL)
        // group or open a fresh one — instead of creating a separate debt per purchase.
        Long resolvedGroupId = req.groupId();
        if (resolvedGroupId == null && req.type().equals("EXPENSE") && wallet.getType().equals("POSTPAID")) {
            DebtGroup group = debtGroupRepo
                    .findFirstByUserIdAndWalletIdAndGroupTypeAndStatusInOrderByCreatedAtAsc(
                            userId, req.walletId(), "BNPL", java.util.List.of("OPEN", "PARTIAL"))
                    .orElseGet(() -> {
                        DebtGroup g = new DebtGroup();
                        g.setUserId(userId);
                        g.setWalletId(req.walletId());
                        g.setTitle(req.groupTitle() != null ? req.groupTitle() : "Trả sau");
                        g.setGroupType("BNPL");
                        g.setTotalAmount(BigDecimal.ZERO);
                        g.setPaidAmount(BigDecimal.ZERO);
                        g.setCurrency("VND");
                        g.setStatus("OPEN");
                        if (req.groupDueDate() != null) {
                            g.setDueDate(DateParsing.parseFlexibleInstant(req.groupDueDate()));
                        }
                        if (req.groupCounterparty() != null) {
                            g.setCounterparty(req.groupCounterparty());
                        }
                        return g;
                    });
            // Accumulate this purchase into the consolidated debt total.
            group.setTotalAmount(group.getTotalAmount().add(req.amount()));
            group = debtGroupRepo.save(group);
            resolvedGroupId = group.getId();
        } else if (resolvedGroupId != null) {
            // Validate provided group belongs to user
            if (!debtGroupRepo.existsByIdAndUserId(resolvedGroupId, userId))
                throw new EntityNotFoundException("Debt group not found");
        }

        // Build txnType label
        String txnType = req.txnType() != null ? req.txnType()
                : (resolvedGroupId != null ? "PRINCIPAL" : null);

        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setWalletId(req.walletId());
        tx.setCategoryId(req.categoryId());
        tx.setGroupId(resolvedGroupId);
        tx.setAmount(req.amount());
        tx.setType(req.type());
        tx.setTxnType(txnType);
        tx.setNote(req.note());
        tx.setDate(req.date() != null ? DateParsing.parseFlexibleInstant(req.date()) : Instant.now());

        tx = txRepo.save(tx);

        // Update wallet balance
        BigDecimal delta = req.type().equals("INCOME")
                ? req.amount()
                : req.amount().negate();
        wallet.setBalance(wallet.getBalance().add(delta));
        walletRepo.save(wallet);

        // If linked to a debt group, update paid amount (for INCOME = repayment)
        if (resolvedGroupId != null && req.type().equals("INCOME")) {
            debtGroupRepo.findByIdAndUserId(resolvedGroupId, userId).ifPresent(group -> {
                BigDecimal newPaid = group.getPaidAmount().add(req.amount());
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
        Transaction tx = txRepo.findByIdAndUserId(txId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));

        // 1. Reverse the OLD delta on the OLD wallet so balances do not drift.
        var oldWallet = walletRepo.findByIdAndUserId(tx.getWalletId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
        oldWallet.setBalance(balanceOf(oldWallet).subtract(signedDelta(tx.getType(), tx.getAmount())));

        // 2. Resolve the NEW field values (fall back to existing where omitted).
        Long newWalletId = req.walletId() != null ? req.walletId() : tx.getWalletId();
        if (req.walletId() != null && !req.walletId().equals(tx.getWalletId())
                && !walletRepo.findByIdAndUserId(req.walletId(), userId).isPresent()) {
            throw new EntityNotFoundException("Wallet not found");
        }
        BigDecimal newAmount = req.amount() != null ? req.amount() : tx.getAmount();
        String newType = req.type() != null && !req.type().isBlank() ? req.type() : tx.getType();

        tx.setWalletId(newWalletId);
        tx.setAmount(newAmount);
        tx.setType(newType);
        if (req.categoryId() != null) tx.setCategoryId(req.categoryId());
        if (req.note() != null) tx.setNote(req.note());
        if (req.date() != null) tx.setDate(DateParsing.parseFlexibleInstant(req.date()));

        // 3. Apply the NEW delta to the NEW wallet, then persist both wallets.
        Wallet targetWallet = newWalletId.equals(oldWallet.getId())
                ? oldWallet
                : walletRepo.findByIdAndUserId(newWalletId, userId)
                        .orElseThrow(() -> new EntityNotFoundException("Wallet not found"));
        if (targetWallet != oldWallet) {
            walletRepo.save(oldWallet); // persist reversal on old wallet
        }
        targetWallet.setBalance(balanceOf(targetWallet).add(signedDelta(newType, newAmount)));
        walletRepo.save(targetWallet);

        return toResponse(txRepo.save(tx));
    }

    /** Wallet balance treated as ZERO when null. */
    private static BigDecimal balanceOf(Wallet wallet) {
        return wallet.getBalance() != null ? wallet.getBalance() : BigDecimal.ZERO;
    }

    /** Signed effect of a transaction on a wallet balance: +amount for INCOME, -amount otherwise. */
    private static BigDecimal signedDelta(String type, BigDecimal amount) {
        return "INCOME".equals(type) ? amount : amount.negate();
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

        String dateStr = tx.getDate() != null ? tx.getDate().toString().substring(0, 10) : null;

        return new TransactionResponse(
            tx.getId(), tx.getUserId(), tx.getWalletId(), tx.getCategoryId(), tx.getGroupId(),
            tx.getAmount(), tx.getType(), tx.getTxnType(), tx.getNote(), dateStr,
            tx.getCreatedAt(), tx.getUpdatedAt(),
            group != null ? new TransactionResponse.GroupSummary(group.getId(), group.getTitle(), group.getGroupType()) : null,
            wallet != null ? new TransactionResponse.WalletSummary(
                    wallet.getId(), wallet.getName(), wallet.getIcon(), wallet.getColor(), wallet.getType()) : null,
            category != null ? new TransactionResponse.CategorySummary(
                    category.getId(), category.getName(), category.getIcon(), category.getColor()) : null
        );
    }
}
