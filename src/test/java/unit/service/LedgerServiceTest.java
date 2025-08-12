package unit.service;

import com.fintech.ledger_service.domain.TransferResult;
import com.fintech.ledger_service.entity.Account;
import com.fintech.ledger_service.repository.AccountRepository;
import com.fintech.ledger_service.repository.LedgerEntryRepository;
import com.fintech.ledger_service.service.LedgerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    void createAccount_ShouldSucceed() {
        // Given
        Long accountId = 123456789L;
        BigDecimal initialBalance = new BigDecimal("1000.00");

        Account expectedAccount = new Account();
        expectedAccount.setId(accountId);
        expectedAccount.setBalance(initialBalance);
        expectedAccount.setVersion(1L);

        when(accountRepository.existsById(accountId)).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(expectedAccount);

        // When
        Account account = ledgerService.createAccount(accountId, initialBalance);

        // Then
        assertNotNull(account);
        assertEquals(accountId, account.getId());
        assertEquals(initialBalance, account.getBalance());
        assertNotNull(account.getVersion());

        verify(accountRepository).existsById(accountId);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_WithExistingId_ShouldThrowException() {
        // Given
        Long accountId = 123456789L;
        when(accountRepository.existsById(accountId)).thenReturn(true);

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.createAccount(accountId, new BigDecimal("1000.00")));

        verify(accountRepository).existsById(accountId);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void applyTransfer_HappyPath_ShouldSucceed() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        String transferId = "TXN_123";
        BigDecimal transferAmount = new BigDecimal("250.00");

        Account fromAccount = new Account();
        fromAccount.setId(fromAccountId);
        fromAccount.setBalance(new BigDecimal("1000.00"));
        fromAccount.setVersion(1L);

        Account toAccount = new Account();
        toAccount.setId(toAccountId);
        toAccount.setBalance(new BigDecimal("500.00"));
        toAccount.setVersion(1L);

        // Mock finding accounts with lock
        when(accountRepository.findByIdsWithLock(Arrays.asList(fromAccountId, toAccountId)))
                .thenReturn(Arrays.asList(fromAccount, toAccount));

        // Mock transfer ID check for idempotency
        when(ledgerEntryRepository.existsByTransferId(transferId)).thenReturn(false);

        // When
        TransferResult result = ledgerService.applyTransfer(transferId, fromAccountId, toAccountId, transferAmount);

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Transfer completed successfully", result.getMessage());
        assertEquals(new BigDecimal("750.00"), result.getFromBalanceAfter());
        assertEquals(new BigDecimal("750.00"), result.getToBalanceAfter());

        verify(accountRepository).findByIdsWithLock(Arrays.asList(fromAccountId, toAccountId));
        verify(ledgerEntryRepository).existsByTransferId(transferId);
    }

    @Test
    void applyTransfer_InsufficientFunds_ShouldFail() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        String transferId = "TXN_123";
        BigDecimal transferAmount = new BigDecimal("150.00");

        Account fromAccount = new Account();
        fromAccount.setId(fromAccountId);
        fromAccount.setBalance(new BigDecimal("100.00")); // Less than transfer amount
        fromAccount.setVersion(1L);

        Account toAccount = new Account();
        toAccount.setId(toAccountId);
        toAccount.setBalance(new BigDecimal("0.00"));
        toAccount.setVersion(1L);

        when(accountRepository.findByIdsWithLock(Arrays.asList(fromAccountId, toAccountId)))
                .thenReturn(Arrays.asList(fromAccount, toAccount));
        when(ledgerEntryRepository.existsByTransferId(transferId)).thenReturn(false);

        // When
        TransferResult result = ledgerService.applyTransfer(transferId, fromAccountId, toAccountId, transferAmount);

        // Then
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Insufficient funds"));

        verify(accountRepository).findByIdsWithLock(Arrays.asList(fromAccountId, toAccountId));
        verify(ledgerEntryRepository).existsByTransferId(transferId);
    }

    @Test
    void applyTransfer_Idempotent_ShouldReturnSameResult() {
        // Given
        String transferId = "TXN_123";
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;

        when(ledgerEntryRepository.existsByTransferId(transferId)).thenReturn(true);

        // Mock getAccount calls for idempotent response
        Account fromAccount = new Account();
        fromAccount.setId(fromAccountId);
        fromAccount.setBalance(new BigDecimal("750.00"));

        Account toAccount = new Account();
        toAccount.setId(toAccountId);
        toAccount.setBalance(new BigDecimal("750.00"));

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));

        // When
        TransferResult result = ledgerService.applyTransfer(transferId, fromAccountId, toAccountId, new BigDecimal("250.00"));

        // Then
        assertTrue(result.isSuccess());
        assertEquals("Transfer already processed", result.getMessage());

        verify(ledgerEntryRepository).existsByTransferId(transferId);
        verify(accountRepository).findById(fromAccountId);
        verify(accountRepository).findById(toAccountId);
    }

    @Test
    void applyTransfer_AccountNotFound_ShouldThrowException() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        String transferId = "TXN_123";
        BigDecimal transferAmount = new BigDecimal("100.00");

        when(ledgerEntryRepository.existsByTransferId(transferId)).thenReturn(false);
        when(accountRepository.findByIdsWithLock(Arrays.asList(fromAccountId, toAccountId)))
                .thenReturn(List.of()); // Empty list = accounts not found

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> ledgerService.applyTransfer(transferId, fromAccountId, toAccountId, transferAmount));

        assertTrue(exception.getMessage().contains("Transfer failed"));
        assertTrue(exception.getCause().getMessage().contains("From account not found"));

        verify(ledgerEntryRepository).existsByTransferId(transferId);
        verify(accountRepository).findByIdsWithLock(Arrays.asList(fromAccountId, toAccountId));
    }

    @Test
    void applyTransfer_InvalidInputs_ShouldThrowException() {
        // Test null/empty transfer ID
        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.applyTransfer(null, 123456789L, 987654321L, BigDecimal.TEN));

        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.applyTransfer("", 123456789L, 987654321L, BigDecimal.TEN));

        // Test same account transfer
        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.applyTransfer("txn1", 123456789L, 123456789L, BigDecimal.TEN));

        // Test negative amount
        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.applyTransfer("txn1", 123456789L, 987654321L, new BigDecimal("-10")));

        // Test zero amount
        assertThrows(IllegalArgumentException.class,
                () -> ledgerService.applyTransfer("txn1", 123456789L, 987654321L, BigDecimal.ZERO));
    }
}
