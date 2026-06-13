package dev.chinh.portfolio.apps.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for RecurringService.computeNext frequency handling (B6 regression).
 */
class RecurringServiceTest {

    @Test
    @DisplayName("unknown frequency throws instead of looping forever")
    void unknownFrequencyThrows() {
        LocalDate start = LocalDate.now().minusDays(1);
        assertThatThrownBy(() -> RecurringService.computeNext(start, "FORTNIGHTLY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown recurring frequency");
    }

    @Test
    @DisplayName("DAILY advances to a future date without looping")
    void dailyAdvancesToFuture() {
        LocalDate start = LocalDate.now().minusDays(5);
        assertThatCode(() -> {
            LocalDate next = RecurringService.computeNext(start, "DAILY");
            assertThat(next).isAfter(LocalDate.now());
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MONTHLY advances to a future date")
    void monthlyAdvancesToFuture() {
        LocalDate start = LocalDate.now().minusMonths(2);
        LocalDate next = RecurringService.computeNext(start, "MONTHLY");
        assertThat(next).isAfter(LocalDate.now());
    }
}
