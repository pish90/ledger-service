package unit.service;

import com.fintech.ledger_service.entity.Account;
import com.fintech.ledger_service.repository.AccountRepository;
import com.fintech.ledger_service.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = new Account(123456789L, BigDecimal.valueOf(1000));
    }

    @Test
    void createAccount_ValidInput_ReturnsAccount() {
        // Given
        Long accountId = 123456789L;
        BigDecimal initialBalance = new BigDecimal("500");

        Account savedAccount = new Account();
        savedAccount.setId(accountId);  // ✅ Set the ID
        savedAccount.setBalance(initialBalance);  // ✅ Set the balance
        savedAccount.setVersion(1L);

        when(accountRepository.existsById(accountId)).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        // When
        Account result = accountService.createAccount(accountId, initialBalance);

        // Then
        assertNotNull(result);
        assertEquals(accountId, result.getId());  // This should now work
        assertEquals(initialBalance, result.getBalance());

        verify(accountRepository).existsById(accountId);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_AccountAlreadyExists_ThrowsException() {
        // Given
        Long existingAccountId = 123456789L;
        BigDecimal initialBalance = BigDecimal.valueOf(500);

        when(accountRepository.existsById(existingAccountId)).thenReturn(true);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(existingAccountId, initialBalance)
        );

        assertEquals("Account already exists: " + existingAccountId, exception.getMessage());
        verify(accountRepository).existsById(existingAccountId);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createAccount_NegativeBalance_ThrowsException() {
        // Given
        Long accountId = 123456789L;
        BigDecimal negativeBalance = BigDecimal.valueOf(-100);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(accountId, negativeBalance)
        );

        assertEquals("Initial balance must be non-negative", exception.getMessage());
        verify(accountRepository, never()).existsById(anyLong());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void getAccount_ExistingAccount_ReturnsAccount() {
        // Given
        Long accountId = 123456789L;
        Account existingAccount = new Account();
        existingAccount.setId(accountId);  // ✅ Set the ID
        existingAccount.setBalance(new BigDecimal("1000"));  // ✅ Set the balance

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        // When
        Account result = accountService.getAccount(accountId);

        // Then
        assertNotNull(result);
        assertEquals(accountId, result.getId());
        assertEquals(new BigDecimal("1000"), result.getBalance());

        verify(accountRepository).findById(accountId);
    }

    @Test
    void getAccount_NonExistentAccount_ThrowsException() {
        // Given
        Long nonExistentId = 0L;
        when(accountRepository.findById(0L)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccount(nonExistentId)
        );

        assertEquals("Account not found: " + nonExistentId, exception.getMessage());
        verify(accountRepository).findById(0L);
    }

    @Test
    void transferFunds_SufficientBalance_UpdatesBothAccounts() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        BigDecimal transferAmount = new BigDecimal("200");

        Account fromAccount = new Account();
        fromAccount.setId(fromAccountId);
        fromAccount.setBalance(new BigDecimal("1000"));
        fromAccount.setVersion(1L);

        Account toAccount = new Account();
        toAccount.setId(toAccountId);
        toAccount.setBalance(new BigDecimal("500"));
        toAccount.setVersion(1L);

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));

        // Mock saving
        when(accountRepository.save(fromAccount)).thenReturn(fromAccount);
        when(accountRepository.save(toAccount)).thenReturn(toAccount);

        // When
        accountService.transferFunds(fromAccountId, toAccountId, transferAmount);

        // Then - Check balances were updated by debit/credit methods
        assertEquals(new BigDecimal("800"), fromAccount.getBalance());  // 1000 - 200
        assertEquals(new BigDecimal("700"), toAccount.getBalance());    // 500 + 200

        verify(accountRepository).findByIdWithLock(fromAccountId);
        verify(accountRepository).findByIdWithLock(toAccountId);
        verify(accountRepository).save(fromAccount);
        verify(accountRepository).save(toAccount);
    }

    @Test
    void transferFunds_InsufficientBalance_ThrowsException() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        BigDecimal transferAmount = new BigDecimal("1500");  // More than available

        Account fromAccount = new Account();
        fromAccount.setId(fromAccountId);
        fromAccount.setBalance(new BigDecimal("1000"));  // Less than transfer amount

        Account toAccount = new Account();
        toAccount.setId(toAccountId);
        toAccount.setBalance(new BigDecimal("500"));

        // ✅ Mock both accounts to exist so we reach the balance check
        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(toAccountId)).thenReturn(Optional.of(toAccount));

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> accountService.transferFunds(fromAccountId, toAccountId, transferAmount));

        // ✅ Check for exact message from your service
        assertEquals("Insufficient funds", exception.getMessage());

        verify(accountRepository).findByIdWithLock(fromAccountId);
        verify(accountRepository).findByIdWithLock(toAccountId);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void transferFunds_FromAccountNotFound_ThrowsException() {
        // Given
        Long nonExistentFromId = 0L;
        Long toAccountId = 123456789L;
        BigDecimal transferAmount = BigDecimal.valueOf(100);

        when(accountRepository.findByIdWithLock(nonExistentFromId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.transferFunds(nonExistentFromId, toAccountId, transferAmount)
        );

        assertEquals("Account not found: " + nonExistentFromId, exception.getMessage());
        verify(accountRepository).findByIdWithLock(nonExistentFromId);
        verify(accountRepository, never()).findByIdWithLock(toAccountId);
    }

    @Test
    void transferFunds_ToAccountNotFound_ThrowsException() {
        // Given
        Long fromAccountId = 123456789L;
        Long nonExistentToId = 0L;
        BigDecimal transferAmount = BigDecimal.valueOf(100);

        Account fromAccount = new Account(fromAccountId, BigDecimal.valueOf(1000));

        when(accountRepository.findByIdWithLock(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdWithLock(nonExistentToId)).thenReturn(Optional.empty());

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.transferFunds(fromAccountId, nonExistentToId, transferAmount)
        );

        assertEquals("Account not found: " + nonExistentToId, exception.getMessage());
        verify(accountRepository).findByIdWithLock(fromAccountId);
        verify(accountRepository).findByIdWithLock(nonExistentToId);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void transferFunds_ZeroAmount_ThrowsException() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        BigDecimal zeroAmount = BigDecimal.ZERO;

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.transferFunds(fromAccountId, toAccountId, zeroAmount)
        );

        assertEquals("Transfer amount must be positive", exception.getMessage());
        verify(accountRepository, never()).findByIdWithLock(anyString());
    }

    @Test
    void transferFunds_NegativeAmount_ThrowsException() {
        // Given
        Long fromAccountId = 123456789L;
        Long toAccountId = 987654321L;
        BigDecimal negativeAmount = BigDecimal.valueOf(-50);

        // When & Then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.transferFunds(fromAccountId, toAccountId, negativeAmount)
        );

        assertEquals("Transfer amount must be positive", exception.getMessage());
        verify(accountRepository, never()).findByIdWithLock(anyString());
    }
}
