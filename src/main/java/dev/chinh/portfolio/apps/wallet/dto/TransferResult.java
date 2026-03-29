package dev.chinh.portfolio.apps.wallet.dto;

public record TransferResult(
    dev.chinh.portfolio.apps.wallet.dto.TransactionResponse debitTx,
    dev.chinh.portfolio.apps.wallet.dto.TransactionResponse creditTx
) {}
