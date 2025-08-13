package com.fintech.ledger_service.service;

import com.fintech.ledger_service.data.EntryType;
import com.fintech.ledger_service.domain.TransferResult;
import com.fintech.ledger_service.entity.Account;
import com.fintech.ledger_service.entity.LedgerEntry;
import com.fintech.ledger_service.exception.InsufficientFundsException;
import com.fintech.ledger_service.repository.AccountRepository;
import com.fintech.ledger_service.repository.LedgerEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Create a new account with initial balance
     */
    @Transactional
    public Account createAccount(BigDecimal initialBalance) {
        log.info("Creating account with initial balance {}", initialBalance);

        // Create new account
        Account account = new Account(initialBalance);
        Account savedAccount = accountRepository.save(account);

        createInitialLedgerEntry(savedAccount.getId(), initialBalance);

        log.info("Successfully created account {}", savedAccount.getId());
        return savedAccount;
    }

    /**
     * Create account with specific ID (for testing)
     */
    @Transactional
    public Account createAccount(Long accountId, BigDecimal initialBalance) {
        String correlationId = MDC.get("correlationId");
        log.info("Creating account: {} with initial balance: {} [correlationId={}]",
                accountId, initialBalance, correlationId);

        // Validate input
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }

        if (initialBalance == null || initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial balance must be non-negative");
        }

        // Check if account already exists
        if (accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Account already exists: " + accountId);
        }

        // Create account
        Account account = new Account(accountId);
        Account savedAccount = accountRepository.save(account);

        // Create initial balance entry if needed
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            String transferId = "INITIAL-" + UUID.randomUUID();
            LedgerEntry initialEntry = new LedgerEntry(
                    UUID.randomUUID().toString(),
                    transferId,
                    accountId,
                    initialBalance,
                    EntryType.CREDIT
            );
            ledgerEntryRepository.save(initialEntry);

            log.info("Initial balance entry created for account: {} amount: {} [correlationId={}]",
                    accountId, initialBalance, correlationId);
        }

        log.info("Account created successfully: {} [correlationId={}]", accountId, correlationId);
        return savedAccount;
    }

    private void createInitialLedgerEntry(Long accountId, BigDecimal initialBalance) {
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            String entryId = UUID.randomUUID().toString();
            LedgerEntry initialEntry = new LedgerEntry(
                    entryId,
                    "INITIAL_BALANCE",
                    accountId,
                    initialBalance,
                    EntryType.CREDIT
            );
            ledgerEntryRepository.save(initialEntry);
        }
    }

    /**
     * Get account by ID
     */
    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    /**
     * Apply a transfer between two accounts atomically
     */
    @Transactional
    public TransferResult applyTransfer(String transferId, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Processing transfer {} from {} to {} amount {}", transferId, fromAccountId, toAccountId, amount);

        // Check for idempotency first
        if (ledgerEntryRepository.existsByTransferId(transferId)) {
            log.info("Transfer {} already processed, returning existing result", transferId);
            Account fromAccount = getAccount(fromAccountId);
            Account toAccount = getAccount(toAccountId);
            return TransferResult.alreadyProcessed(transferId, fromAccount.getBalance(), toAccount.getBalance());
        }

        // Validate inputs
        validateTransferInputs(transferId, fromAccountId, toAccountId, amount);

        try {
            // Lock accounts in consistent order (by ID) to prevent deadlocks
            List<Long> accountIds = Arrays.asList(fromAccountId, toAccountId);
            accountIds.sort(Long::compareTo); // Fixed comparison

            List<Account> lockedAccounts = accountRepository.findByIdsWithLock(accountIds);

            // Find the specific accounts
            Account fromAccount = lockedAccounts.stream()
                    .filter(acc -> acc.getId().equals(fromAccountId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("From account not found: " + fromAccountId));

            Account toAccount = lockedAccounts.stream()
                    .filter(acc -> acc.getId().equals(toAccountId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("To account not found: " + toAccountId));

            // Apply the transfer
            fromAccount.debit(amount);
            toAccount.credit(amount);

            // Save the updated accounts
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            // Create ledger entries
            String debitEntryId = UUID.randomUUID().toString();
            String creditEntryId = UUID.randomUUID().toString();

            LedgerEntry debitEntry = new LedgerEntry(
                    debitEntryId,
                    transferId,
                    fromAccountId,
                    amount.negate(),
                    EntryType.DEBIT
            );

            LedgerEntry creditEntry = new LedgerEntry(
                    creditEntryId,
                    transferId,
                    toAccountId,
                    amount,
                    EntryType.CREDIT
            );

            ledgerEntryRepository.save(debitEntry);
            ledgerEntryRepository.save(creditEntry);

            log.info("Transfer {} completed successfully. From balance: {}, To balance: {}",
                    transferId, fromAccount.getBalance(), toAccount.getBalance());

            return TransferResult.success(transferId, fromAccount.getBalance(), toAccount.getBalance());

        } catch (InsufficientFundsException e) {
            log.warn("Transfer {} failed due to insufficient funds: {}", transferId, e.getMessage());
            return TransferResult.failure(transferId, e.getMessage());
        } catch (Exception e) {
            log.error("Transfer {} failed unexpectedly", transferId, e);
            throw new RuntimeException("Transfer failed: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(Long accountId) {
        // Verify account exists
        getAccount(accountId);

        // Calculate balance from ledger entries
        return ledgerEntryRepository.calculateAccountBalance(accountId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountHistory(Long accountId) {
        // Verify account exists
        getAccount(accountId);

        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    public void processTransfer(String transferId, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        String correlationId = MDC.get("correlationId");
        log.info("Processing transfer: {} -> {} amount: {} transferId: {} [correlationId={}]",
                fromAccountId, toAccountId, amount, transferId, correlationId);

        // Validate input
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // Check if transfer already processed (idempotency at ledger level)
        if (ledgerEntryRepository.existsByTransferId(transferId)) {
            log.warn("Transfer already processed: {} [correlationId={}]", transferId, correlationId);
            throw new IllegalArgumentException("Transfer already processed: " + transferId);
        }

        // Verify accounts exist and get them with optimistic locking
        Account fromAccount = accountRepository.findByIdWithLock(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found: " + fromAccountId));

        Account toAccount = accountRepository.findByIdWithLock(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found: " + toAccountId));

        // Check sufficient balance
        BigDecimal fromAccountBalance = ledgerEntryRepository.calculateAccountBalance(fromAccountId);
        if (fromAccountBalance.compareTo(amount) < 0) {
            log.warn("Insufficient funds: account {} has balance {} but needs {} [correlationId={}]",
                    fromAccountId, fromAccountBalance, amount, correlationId);
            throw new IllegalArgumentException("Insufficient funds in account: " + fromAccountId);
        }

        // Create double-entry bookkeeping entries
        LedgerEntry debitEntry = new LedgerEntry(
                UUID.randomUUID().toString(),
                transferId,
                fromAccountId,
                amount,
                EntryType.DEBIT
        );

        LedgerEntry creditEntry = new LedgerEntry(
                UUID.randomUUID().toString(),
                transferId,
                toAccountId,
                amount,
                EntryType.CREDIT
        );

        // Save both entries atomically
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // Update account versions for optimistic locking
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        log.info("Transfer processed successfully: {} [correlationId={}]", transferId, correlationId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getTransferEntries(String transferId) {
        return ledgerEntryRepository.findByTransferIdOrderByCreatedAt(transferId);
    }

    @Transactional(readOnly = true)
    public boolean isTransferBalanced(String transferId) {
        BigDecimal totalDebits = ledgerEntryRepository.sumAmountByTransferAndType(transferId, EntryType.DEBIT);
        BigDecimal totalCredits = ledgerEntryRepository.sumAmountByTransferAndType(transferId, EntryType.CREDIT);

        return totalDebits.compareTo(totalCredits) == 0;
    }

    private void validateTransferInputs(String transferId, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (transferId == null || transferId.trim().isEmpty()) {
            throw new IllegalArgumentException("Transfer ID cannot be null or empty");
        }
        if (fromAccountId == null) {
            throw new IllegalArgumentException("From account ID cannot be null");
        }
        if (toAccountId == null) {
            throw new IllegalArgumentException("To account ID cannot be null");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }
}
