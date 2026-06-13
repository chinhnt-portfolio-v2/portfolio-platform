package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.CreateDebtGroupRequest;
import dev.chinh.portfolio.apps.wallet.dto.DebtGroupResponse;
import dev.chinh.portfolio.apps.wallet.dto.SettleDebtRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DebtGroupService covering B3 (lenient dueDate) and B7 (null-safe wallet balance).
 */
class DebtGroupServiceTest {

    private final DebtGroupRepository debtGroupRepo = mock(DebtGroupRepository.class);
    private final WalletRepository walletRepo = mock(WalletRepository.class);
    private final TransactionRepository transactionRepo = mock(TransactionRepository.class);

    private final DebtGroupService service =
            new DebtGroupService(debtGroupRepo, walletRepo, transactionRepo);

    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        when(debtGroupRepo.save(any(DebtGroup.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepo.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("B3: createGroup accepts date-only dueDate (YYYY-MM-DD)")
    void createGroupAcceptsDateOnlyDueDate() {
        CreateDebtGroupRequest req = new CreateDebtGroupRequest(
                "Loan", "DEBT", 5000.0, null, "2026-07-12", null, "Alice");

        DebtGroupResponse resp = service.createGroup(USER, req);

        assertThat(resp).isNotNull();
        assertThat(resp.dueDate()).isEqualTo(Instant.parse("2026-07-12T00:00:00Z"));
    }

    @Test
    @DisplayName("B3: createGroup with garbage dueDate throws IllegalArgumentException (→ 400)")
    void createGroupWithGarbageDueDateThrows() {
        CreateDebtGroupRequest req = new CreateDebtGroupRequest(
                "Loan", "DEBT", 5000.0, null, "garbage", null, "Alice");

        assertThatThrownBy(() -> service.createGroup(USER, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format");
    }

    @Test
    @DisplayName("B7: settleDebt tolerates null wallet balance (treated as ZERO)")
    void settleDebtWithNullBalance() {
        DebtGroup group = new DebtGroup();
        group.setId(3L);
        group.setUserId(USER);
        group.setTitle("Loan");
        group.setTotalAmount(new BigDecimal("1000"));
        group.setPaidAmount(BigDecimal.ZERO);
        when(debtGroupRepo.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(group));

        Wallet wallet = new Wallet();
        wallet.setId(10L);
        wallet.setUserId(USER);
        wallet.setName("Cash");
        wallet.setType("CASH");
        wallet.setBalance(null); // null balance must not NPE
        when(walletRepo.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(wallet));

        SettleDebtRequest req = new SettleDebtRequest(new BigDecimal("400"), 10L, null);

        DebtGroupResponse resp = service.settleDebt(USER, 3L, req);

        // ZERO - 400 = -400 (no NPE)
        assertThat(wallet.getBalance()).isEqualByComparingTo("-400");
        assertThat(resp.paidAmount()).isEqualByComparingTo("400");
        assertThat(resp.status()).isEqualTo("PARTIAL");
        verify(transactionRepo).save(any(Transaction.class));
    }
}
