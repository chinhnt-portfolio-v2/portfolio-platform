package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.CreateDebtGroupRequest;
import dev.chinh.portfolio.apps.wallet.dto.DebtGroupResponse;
import dev.chinh.portfolio.apps.wallet.dto.SettleDebtRequest;
import dev.chinh.portfolio.shared.error.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DebtGroupService covering B3 (lenient dueDate), B7 (null-safe wallet balance),
 * and the hardened settle path (over-payment + insufficient-balance rejection, atomic advance).
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

    // ── helpers ──────────────────────────────────────────────

    private DebtGroup group(long id, String total, String paid) {
        DebtGroup g = new DebtGroup();
        g.setId(id);
        g.setUserId(USER);
        g.setTitle("Loan");
        g.setTotalAmount(new BigDecimal(total));
        g.setPaidAmount(new BigDecimal(paid));
        return g;
    }

    private Wallet wallet(long id, String balance) {
        Wallet w = new Wallet();
        w.setId(id);
        w.setUserId(USER);
        w.setName("Cash");
        w.setType("CASH");
        w.setBalance(balance == null ? null : new BigDecimal(balance));
        return w;
    }

    private SettleDebtRequest settle(String amount) {
        return new SettleDebtRequest(new BigDecimal(amount), 10L, null);
    }

    // ── createGroup (B3) ─────────────────────────────────────

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

    // ── settleDebt: happy paths ─────────────────────────────

    @Test
    @DisplayName("settleDebt: partial repay debits wallet + writes PAYMENT txn linked to group, status PARTIAL")
    void settlePartialAdvancesGroupAndWritesPaymentTxn() {
        when(debtGroupRepo.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(group(3L, "1000", "0")));
        Wallet w = wallet(10L, "1000");
        when(walletRepo.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(w));

        DebtGroupResponse resp = service.settleDebt(USER, 3L, settle("400"));

        assertThat(w.getBalance()).isEqualByComparingTo("600");
        assertThat(resp.paidAmount()).isEqualByComparingTo("400");
        assertThat(resp.remaining()).isEqualByComparingTo("600");
        assertThat(resp.status()).isEqualTo("PARTIAL");

        ArgumentCaptor<Transaction> txCap = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepo).save(txCap.capture());
        Transaction tx = txCap.getValue();
        assertThat(tx.getType()).isEqualTo("EXPENSE");
        assertThat(tx.getTxnType()).isEqualTo("PAYMENT");
        assertThat(tx.getGroupId()).isEqualTo(3L);
        assertThat(tx.getAmount()).isEqualByComparingTo("400");
    }

    @Test
    @DisplayName("settleDebt: full repay transitions group to SETTLED with zero remaining")
    void settleFullTransitionsToSettled() {
        when(debtGroupRepo.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(group(3L, "500", "0")));
        when(walletRepo.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(wallet(10L, "500")));

        DebtGroupResponse resp = service.settleDebt(USER, 3L, settle("500"));

        assertThat(resp.status()).isEqualTo("SETTLED");
        assertThat(resp.remaining()).isEqualByComparingTo("0");
    }

    // ── settleDebt: validation ──────────────────────────────

    @Test
    @DisplayName("settleDebt: amount > remaining → 400 amount_exceeds_remaining, no clamp, no mutation")
    void settleOverRemainingRejected() {
        when(debtGroupRepo.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(group(3L, "1000", "800"))); // remaining 200
        when(walletRepo.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(wallet(10L, "999999")));

        assertThatThrownBy(() -> service.settleDebt(USER, 3L, settle("300")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount_exceeds_remaining");

        verify(transactionRepo, never()).save(any());
        verify(walletRepo, never()).save(any());
        verify(debtGroupRepo, never()).save(any());
    }

    @Test
    @DisplayName("settleDebt: wallet balance < amount → 400 insufficient_balance, no mutation")
    void settleInsufficientBalanceRejected() {
        when(debtGroupRepo.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(group(3L, "1000", "0")));
        when(walletRepo.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(wallet(10L, "100")));

        assertThatThrownBy(() -> service.settleDebt(USER, 3L, settle("400")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("insufficient_balance");

        verify(transactionRepo, never()).save(any());
        verify(walletRepo, never()).save(any());
        verify(debtGroupRepo, never()).save(any());
    }

    @Test
    @DisplayName("settleDebt: group not owned by user → EntityNotFoundException (→ 404)")
    void settleCrossUserGroupNotFound() {
        when(debtGroupRepo.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.settleDebt(USER, 99L, settle("10")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("B7: settleDebt with null wallet balance → insufficient_balance (null treated as ZERO, no NPE)")
    void settleDebtWithNullBalance() {
        when(debtGroupRepo.findByIdAndUserId(3L, USER)).thenReturn(Optional.of(group(3L, "1000", "0")));
        when(walletRepo.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(wallet(10L, null)));

        assertThatThrownBy(() -> service.settleDebt(USER, 3L, settle("400")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("insufficient_balance");
    }
}
