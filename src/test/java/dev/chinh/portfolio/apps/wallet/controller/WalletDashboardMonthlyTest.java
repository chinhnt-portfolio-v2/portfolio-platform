package dev.chinh.portfolio.apps.wallet.controller;

import dev.chinh.portfolio.apps.wallet.dto.WalletResponse;
import dev.chinh.portfolio.apps.wallet.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GET /api/v1/wallet/dashboard/monthly (WalletController.getMonthlyComparison).
 */
class WalletDashboardMonthlyTest {

    private final WalletService walletServiceMock = mock(WalletService.class);
    private final WalletController walletController = new WalletController(walletServiceMock);

    private static final UUID TEST_USER_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Nested
    @DisplayName("GET /api/v1/wallet/dashboard/monthly")
    class GetMonthlyComparisonTests {

        @Test
        @DisplayName("should return monthly comparison list with default 3 months")
        void shouldReturnMonthlyComparisonWithDefaultMonths() {
            List<WalletResponse.MonthlyComparison> mockResult = List.of(
                    new WalletResponse.MonthlyComparison("2026-03", "Thg 3",
                            new BigDecimal("5000000"), new BigDecimal("3000000"),
                            new BigDecimal("2000000"), 15),
                    new WalletResponse.MonthlyComparison("2026-02", "Thg 2",
                            new BigDecimal("4500000"), new BigDecimal("2800000"),
                            new BigDecimal("1700000"), 12)
            );
            when(walletServiceMock.getMonthlyComparison(eq(TEST_USER_ID), eq(3)))
                    .thenReturn(mockResult);

            ResponseEntity<List<WalletResponse.MonthlyComparison>> response =
                    walletController.getMonthlyComparison(TEST_USER_ID, 3);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).month()).isEqualTo("2026-03");
            assertThat(response.getBody().get(0).label()).isEqualTo("Thg 3");
            assertThat(response.getBody().get(0).netSavings()).isEqualByComparingTo(new BigDecimal("2000000"));
            verify(walletServiceMock).getMonthlyComparison(TEST_USER_ID, 3);
        }

        @Test
        @DisplayName("should pass custom months parameter to service")
        void shouldPassCustomMonthsToService() {
            when(walletServiceMock.getMonthlyComparison(eq(TEST_USER_ID), eq(6)))
                    .thenReturn(List.of());

            walletController.getMonthlyComparison(TEST_USER_ID, 6);

            verify(walletServiceMock).getMonthlyComparison(TEST_USER_ID, 6);
        }

        @Test
        @DisplayName("should return empty list when user has no transactions")
        void shouldReturnEmptyListWhenNoTransactions() {
            when(walletServiceMock.getMonthlyComparison(eq(TEST_USER_ID), eq(3)))
                    .thenReturn(List.of());

            ResponseEntity<List<WalletResponse.MonthlyComparison>> response =
                    walletController.getMonthlyComparison(TEST_USER_ID, 3);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should correctly calculate netSavings as income minus expense")
        void shouldCalculateNetSavingsCorrectly() {
            List<WalletResponse.MonthlyComparison> mockResult = List.of(
                    new WalletResponse.MonthlyComparison("2026-01", "Thg 1",
                            new BigDecimal("10000000"), new BigDecimal("15000000"),
                            new BigDecimal("-5000000"), 20)
            );
            when(walletServiceMock.getMonthlyComparison(eq(TEST_USER_ID), eq(3)))
                    .thenReturn(mockResult);

            ResponseEntity<List<WalletResponse.MonthlyComparison>> response =
                    walletController.getMonthlyComparison(TEST_USER_ID, 3);

            assertThat(response.getBody().get(0).netSavings())
                    .isEqualByComparingTo(new BigDecimal("-5000000"));
        }

        @Test
        @DisplayName("should handle months with zero income or expense gracefully")
        void shouldHandleZeroIncomeOrExpense() {
            List<WalletResponse.MonthlyComparison> mockResult = List.of(
                    new WalletResponse.MonthlyComparison("2026-01", "Thg 1",
                            BigDecimal.ZERO, new BigDecimal("500000"),
                            new BigDecimal("-500000"), 5)
            );
            when(walletServiceMock.getMonthlyComparison(eq(TEST_USER_ID), eq(3)))
                    .thenReturn(mockResult);

            ResponseEntity<List<WalletResponse.MonthlyComparison>> response =
                    walletController.getMonthlyComparison(TEST_USER_ID, 3);

            assertThat(response.getBody().get(0).totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getBody().get(0).netSavings())
                    .isEqualByComparingTo(new BigDecimal("-500000"));
        }
    }
}
