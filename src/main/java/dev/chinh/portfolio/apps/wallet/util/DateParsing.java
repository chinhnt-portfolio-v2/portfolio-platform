package dev.chinh.portfolio.apps.wallet.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Lenient date parsing for wallet date inputs.
 *
 * <p>Accepts either a date-only string ({@code "2026-04-28"}) interpreted as start-of-day UTC,
 * or a full ISO-8601 instant ({@code "2026-04-28T00:00:00Z"}). This is the single source of truth
 * for flexible date parsing in the wallet module (used by transaction and debt-group creation).
 */
public final class DateParsing {

    private DateParsing() {
    }

    /**
     * Application timezone for month/period bucketing. The wallet user is in Vietnam (UTC+7);
     * computing month boundaries in this zone (not UTC) keeps "spent this month" accurate for
     * transactions near a month edge. Single-user app → a fixed zone is acceptable.
     */
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Inclusive start instant of a {@code "YYYY-MM"} period, in {@link #APP_ZONE}. */
    public static Instant monthStart(String period) {
        return YearMonth.parse(period).atDay(1).atStartOfDay(APP_ZONE).toInstant();
    }

    /** Exclusive end instant of a {@code "YYYY-MM"} period (start of next month), in {@link #APP_ZONE}. */
    public static Instant monthEndExclusive(String period) {
        return YearMonth.parse(period).plusMonths(1).atDay(1).atStartOfDay(APP_ZONE).toInstant();
    }

    /**
     * Parse a flexible date string into an {@link Instant}.
     *
     * @param raw a date-only ("YYYY-MM-DD") or ISO instant string; must be non-null
     * @return the parsed instant (date-only inputs map to start-of-day UTC)
     * @throws IllegalArgumentException if {@code raw} is null/blank or cannot be parsed
     */
    public static Instant parseFlexibleInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Date must not be empty");
        }
        try {
            return raw.contains("T")
                    ? Instant.parse(raw)
                    : LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format: " + raw, ex);
        }
    }

    /**
     * Parse a flexible date string into an EXCLUSIVE upper bound for date-range filtering.
     *
     * <p>Date-only inputs ({@code "2026-06-10"}) map to start of the NEXT day UTC so the named
     * day is fully included when used with a {@code <} predicate. ISO instants pass through
     * unchanged (half-open interval semantics).
     *
     * @param raw a date-only ("YYYY-MM-DD") or ISO instant string; must be non-null
     * @return the exclusive upper-bound instant
     * @throws IllegalArgumentException if {@code raw} is null/blank or cannot be parsed
     */
    public static Instant parseFlexibleEndExclusive(String raw) {
        Instant parsed = parseFlexibleInstant(raw);
        return raw.contains("T") ? parsed : parsed.plus(1, java.time.temporal.ChronoUnit.DAYS);
    }
}
