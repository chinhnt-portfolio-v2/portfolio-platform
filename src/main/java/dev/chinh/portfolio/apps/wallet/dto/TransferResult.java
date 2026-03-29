package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import dev.chinh.portfolio.apps.wallet.Transaction;
import dev.chinh.portfolio.apps.wallet.TransactionResponse;

public record TransferResult(
    TransactionResponse debitTx,
    TransactionResponse creditTx
) {}
