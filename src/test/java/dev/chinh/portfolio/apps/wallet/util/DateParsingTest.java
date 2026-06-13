package dev.chinh.portfolio.apps.wallet.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the flexible wallet date parser (B2/B3 regression).
 */
class DateParsingTest {

    @Test
    @DisplayName("date-only string parses to start-of-day UTC instant")
    void dateOnlyParsesToStartOfDayUtc() {
        Instant result = DateParsing.parseFlexibleInstant("2026-04-28");
        assertThat(result).isEqualTo(Instant.parse("2026-04-28T00:00:00Z"));
    }

    @Test
    @DisplayName("ISO instant string parses unchanged")
    void isoInstantParsesUnchanged() {
        Instant result = DateParsing.parseFlexibleInstant("2026-04-28T13:45:30Z");
        assertThat(result).isEqualTo(Instant.parse("2026-04-28T13:45:30Z"));
    }

    @Test
    @DisplayName("garbage input throws IllegalArgumentException (maps to HTTP 400)")
    void garbageInputThrows() {
        assertThatThrownBy(() -> DateParsing.parseFlexibleInstant("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format");
    }

    @Test
    @DisplayName("null input throws IllegalArgumentException")
    void nullInputThrows() {
        assertThatThrownBy(() -> DateParsing.parseFlexibleInstant(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("blank input throws IllegalArgumentException")
    void blankInputThrows() {
        assertThatThrownBy(() -> DateParsing.parseFlexibleInstant("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end-exclusive: date-only dateTo maps to start of NEXT day (named day fully included)")
    void endExclusiveDateOnlyMapsToNextDayStart() {
        Instant result = DateParsing.parseFlexibleEndExclusive("2026-06-10");
        assertThat(result).isEqualTo(Instant.parse("2026-06-11T00:00:00Z"));
    }

    @Test
    @DisplayName("end-exclusive: ISO instant passes through unchanged")
    void endExclusiveInstantPassesThrough() {
        Instant result = DateParsing.parseFlexibleEndExclusive("2026-06-10T13:45:30Z");
        assertThat(result).isEqualTo(Instant.parse("2026-06-10T13:45:30Z"));
    }

    @Test
    @DisplayName("end-exclusive: garbage input throws IllegalArgumentException")
    void endExclusiveGarbageThrows() {
        assertThatThrownBy(() -> DateParsing.parseFlexibleEndExclusive("garbage"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format");
    }
}
