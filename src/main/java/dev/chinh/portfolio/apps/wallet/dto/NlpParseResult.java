package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record NlpParseResult(
    Long walletId,             // null if unresolved
    String walletName,         // raw name from LLM
    Long categoryId,           // null if unresolved
    String categoryName,       // raw name from LLM
    BigDecimal amount,         // null if unparseable
    String type,               // INCOME or EXPENSE
    LocalDate date,            // null if unparseable
    String note,
    double confidence,         // 0.0-1.0 computed from resolved fields
    List<String> unresolvedFields  // e.g. ["walletId", "categoryId"]
) {}
