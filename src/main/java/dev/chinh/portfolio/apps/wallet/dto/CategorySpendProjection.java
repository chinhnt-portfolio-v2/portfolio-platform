package dev.chinh.portfolio.apps.wallet.dto;

import java.math.BigDecimal;

/** Spring Data projection: total expense aggregated for one category. */
public interface CategorySpendProjection {
    Long getCategoryId();

    BigDecimal getTotal();
}
