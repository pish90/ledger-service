package com.fintech.ledger_service.service;

import com.fintech.ledger_service.entity.Account;
import com.fintech.ledger_service.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createAccount(Long accountId, BigDecimal initialBalance) {
        String correlationId = MDC.get("correlationId");
        log.info("Creating account: {} with balance: {} [correlationId={}]", accountId, initialBalance, correlationId);

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

        // Create and save account
        Account account = new Account(initialBalance);
        Account savedAccount = accountRepository.save(account);

        log.info("Account created successfully: {} [correlationId={}]", accountId, correlationId);
        return savedAccount;
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        String correlationId = MDC.get("correlationId");
        log.debug("Retrieving account: {} [correlationId={}]", accountId, correlationId);

        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(Long accountId) {
        Account account = getAccount(accountId);
        return account.getBalance();
    }

    public void transferFunds(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        String correlationId = MDC.get("correlationId");
        log.info("Processing transfer: {} -> {} amount: {} [correlationId={}]",
                fromAccountId, toAccountId, amount, correlationId);

        // Validate input
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // Get accounts with optimistic locking
        Account fromAccount = accountRepository.findByIdWithLock(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + fromAccountId));

        Account toAccount = accountRepository.findByIdWithLock(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + toAccountId));

        // Check sufficient funds
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient funds for transfer: {} has {} but needs {} [correlationId={}]",
                    fromAccountId, fromAccount.getBalance(), amount, correlationId);
            throw new IllegalArgumentException("Insufficient funds");
        }

        // Perform transfer
        fromAccount.debit(amount);
        toAccount.credit(amount);

        // Save both accounts
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        log.info("Transfer completed successfully [correlationId={}]", correlationId);
    }
}
