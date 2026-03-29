package dev.chinh.portfolio.apps.wallet;

import dev.chinh.portfolio.apps.wallet.dto.*;
import dev.chinh.portfolio.shared.error.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransferService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransferResult transfer(UUID userId, TransferRequest req) {
        if (req.fromWalletId().equals(req.toWalletId())) {
            throw new IllegalArgumentException("Source and target wallet cannot be the same");
        }

        Wallet from = walletRepository.findByIdAndUserId(req.fromWalletId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Source wallet not found"));
        Wallet to = walletRepository.findByIdAndUserId(req.toWalletId(), userId)
                .orElseThrow(() -> new EntityNotFoundException("Target wallet not found"));

        if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        if (req.amount().compareTo(from.getBalance()) > 0) {
            throw new IllegalArgumentException("Amount exceeds source wallet balance");
        }

        Instant now = Instant.now();

        // Debit from source — type stays EXPENSE/INCOME per DB CHECK constraint
        Transaction debit = new Transaction();
        debit.setUserId(userId);
        debit.setWalletId(from.getId());
        debit.setAmount(req.amount());
        debit.setType("EXPENSE");
        debit.setTxnType("TRANSFER_OUT");
        debit.setNote("Chuyển đến " + to.getName() + (req.note() != null ? ": " + req.note() : ""));
        debit.setDate(now);
        debit.setCreatedAt(now);
        debit.setUpdatedAt(now);
        debit = transactionRepository.save(debit);

        // Credit to target
        Transaction credit = new Transaction();
        credit.setUserId(userId);
        credit.setWalletId(to.getId());
        credit.setAmount(req.amount());
        credit.setType("INCOME");
        credit.setTxnType("TRANSFER_IN");
        credit.setNote("Nhận từ " + from.getName() + (req.note() != null ? ": " + req.note() : ""));
        credit.setDate(now);
        credit.setCreatedAt(now);
        credit.setUpdatedAt(now);
        credit = transactionRepository.save(credit);

        // Update balances
        from.setBalance(from.getBalance().subtract(req.amount()));
        from.setUpdatedAt(now);
        to.setBalance(to.getBalance().add(req.amount()));
        to.setUpdatedAt(now);
        walletRepository.save(from);
        walletRepository.save(to);

        return new TransferResult(
                TransactionResponse.from(debit),
                TransactionResponse.from(credit)
        );
    }
}
