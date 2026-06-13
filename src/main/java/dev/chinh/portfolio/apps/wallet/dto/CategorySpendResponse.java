package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;

/** One category's spending for a period, with its share (%) of total spend. */
public record CategorySpendResponse(
        Long categoryId,
        String name,
        String icon,
        String color,
        BigDecimal total,
        int pct
) {}
