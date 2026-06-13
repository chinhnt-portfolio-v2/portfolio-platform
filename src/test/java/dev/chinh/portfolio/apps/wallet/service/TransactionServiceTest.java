package dev.chinh.portfolio.apps.wallet.service;

import dev.chinh.portfolio.apps.wallet.*;
import dev.chinh.portfolio.apps.wallet.dto.CreateTransactionRequest;
import dev.chinh.portfolio.apps.wallet.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService covering B2 (lenient date), B5 (list filters),
 * and B8 (update reverses old balance delta then applies the new one).
 */
class TransactionServiceTest {

    private final TransactionRepository txRepo = mock(TransactionRepository.class);
    private final WalletRepository walletRepo = mock(WalletRepository.class);
    private final CategoryRepository categoryRepo = mock(CategoryRepository.class);
    private final DebtGroupRepository debtGroupRepo = mock(DebtGroupRepository.class);

    private final TransactionService service =
            new TransactionService(txRepo, walletRepo, categoryRepo, debtGroupRepo);

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
        wallet.setId(10L);
        wallet.setUserId(USER);
        wallet.setName("Cash");
        wallet.setType("CASH");
        wallet.setBalance(new BigDecimal("1000"));
        when(walletRepo.findByIdAndUserId(10L, USER)).thenReturn(Optional.of(wallet));
        when(txRepo.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepo.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateTransactionRequest expense(String date, String amount) {
        return new CreateTransactionRequest(10L, null, null, new BigDecimal(amount),
                "EXPENSE", null, "note", date, null, null, null);
    }

    @Test
    @DisplayName("B2: create accepts date-only string (YYYY-MM-DD)")
    void createAcceptsDateOnly() {
        TransactionResponse resp = service.createTransaction(USER, expense("2026-06-10", "200"));

        assertThat(resp).isNotNull();
        // EXPENSE → balance decreases by 200
        assertThat(wallet.getBalance()).isEqualByComparingTo("800");
        verify(txRepo).save(argThat(t ->
                t.getDate().equals(Instant.parse("2026-06-10T00:00:00Z"))));
    }

    @Test
    @DisplayName("B2: create with garbage date throws IllegalArgumentException (→ 400)")
    void createWithGarbageDateThrows() {
        assertThatThrownBy(() -> service.createTransaction(USER, expense("not-a-date", "200")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format");
        verify(txRepo, never()).save(any());
    }

    @Test
    @DisplayName("B5: list filter by type delegates to user-scoped filtered query")
    void listFilterByTypeUsesFilteredQuery() {
        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setUserId(USER);
        tx.setWalletId(10L);
        tx.setAmount(new BigDecimal("50"));
        tx.setType("EXPENSE");
        tx.setDate(Instant.parse("2026-06-01T00:00:00Z"));
        Page<Transaction> page = new PageImpl<>(List.of(tx));
        when(txRepo.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(page);

        List<TransactionResponse> result =
                service.listTransactions(USER, "EXPENSE", null, null, null, null, null, 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("EXPENSE");
        verify(txRepo).findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class));
        verify(txRepo, never()).findByUserIdOrderByDateDesc(any(), any());
    }

    @Test
    @DisplayName("B5: blank type/search still queries via user-scoped spec (no crash)")
    void listBlankFiltersNormalizedToNull() {
        when(txRepo.findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listTransactions(USER, "", null, null, null, null, "  ", 0, 20);

        verify(txRepo).findAll(ArgumentMatchers.<Specification<Transaction>>any(), any(Pageable.class));
    }

    @Test
    @DisplayName("B8: update reverses old delta then applies new — net balance correct on amount change")
    void updateReAdjustsBalanceOnAmountChange() {
        // Existing EXPENSE of 200 on wallet (balance already reflects it at 1000).
        Transaction existing = new Transaction();
        existing.setId(5L);
        existing.setUserId(USER);
        existing.setWalletId(10L);
        existing.setAmount(new BigDecimal("200"));
        existing.setType("EXPENSE");
        existing.setNote("old");
        existing.setDate(Instant.parse("2026-06-01T00:00:00Z"));
        when(txRepo.findByIdAndUserId(5L, USER)).thenReturn(Optional.of(existing));

        // Update to EXPENSE of 300. Reverse +200, apply -300 → net -100.
        CreateTransactionRequest req = new CreateTransactionRequest(10L, null, null,
                new BigDecimal("300"), "EXPENSE", null, "new", "2026-06-05", null, null, null);

        service.updateTransaction(USER, 5L, req);

        // 1000 +200 (reverse old) -300 (apply new) = 900
        assertThat(wallet.getBalance()).isEqualByComparingTo("900");
        assertThat(existing.getAmount()).isEqualByComparingTo("300");
        assertThat(existing.getNote()).isEqualTo("new");
        assertThat(existing.getDate()).isEqualTo(Instant.parse("2026-06-05T00:00:00Z"));
    }

    @Test
    @DisplayName("B8: update from EXPENSE to INCOME flips the sign correctly")
    void updateTypeChangeFlipsBalance() {
        Transaction existing = new Transaction();
        existing.setId(6L);
        existing.setUserId(USER);
        existing.setWalletId(10L);
        existing.setAmount(new BigDecimal("100"));
        existing.setType("EXPENSE");
        existing.setDate(Instant.parse("2026-06-01T00:00:00Z"));
        when(txRepo.findByIdAndUserId(6L, USER)).thenReturn(Optional.of(existing));

        CreateTransactionRequest req = new CreateTransactionRequest(10L, null, null,
                new BigDecimal("100"), "INCOME", null, null, null, null, null, null);

        service.updateTransaction(USER, 6L, req);

        // 1000 +100 (reverse old EXPENSE) +100 (apply new INCOME) = 1200
        assertThat(wallet.getBalance()).isEqualByComparingTo("1200");
        assertThat(existing.getType()).isEqualTo("INCOME");
    }

    @Test
    @DisplayName("B8: wallet switch reverses delta on old wallet and applies it to new wallet")
    void updateWalletSwitchMovesDeltaBetweenWallets() {
        Wallet other = new Wallet();
        other.setId(20L);
        other.setUserId(USER);
        other.setName("Bank");
        other.setType("BANK");
        other.setBalance(new BigDecimal("500"));
        when(walletRepo.findByIdAndUserId(20L, USER)).thenReturn(Optional.of(other));

        Transaction existing = new Transaction();
        existing.setId(7L);
        existing.setUserId(USER);
        existing.setWalletId(10L); // currently on "Cash"
        existing.setAmount(new BigDecimal("200"));
        existing.setType("EXPENSE");
        existing.setDate(Instant.parse("2026-06-01T00:00:00Z"));
        when(txRepo.findByIdAndUserId(7L, USER)).thenReturn(Optional.of(existing));

        // Move the EXPENSE 200 from wallet 10 to wallet 20.
        CreateTransactionRequest req = new CreateTransactionRequest(20L, null, null,
                new BigDecimal("200"), "EXPENSE", null, null, null, null, null, null);

        service.updateTransaction(USER, 7L, req);

        // Old wallet: 1000 +200 (reverse) = 1200. New wallet: 500 -200 (apply) = 300.
        assertThat(wallet.getBalance()).isEqualByComparingTo("1200");
        assertThat(other.getBalance()).isEqualByComparingTo("300");
        assertThat(existing.getWalletId()).isEqualTo(20L);
        verify(walletRepo, times(2)).save(any(Wallet.class)); // both wallets persisted
    }
}
