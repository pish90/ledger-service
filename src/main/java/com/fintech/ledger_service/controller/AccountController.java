package com.fintech.ledger_service.controller;

import com.fintech.ledger_service.dto.AccountResponse;
import com.fintech.ledger_service.dto.CreateAccountRequest;
import com.fintech.ledger_service.entity.Account;
import com.fintech.ledger_service.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/accounts")
@Tag(name = "Account Management", description = "Account creation and balance inquiries")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final LedgerService ledgerService;

    @PostMapping
    @Operation(summary = "Create a new account", description = "Create a new account with an initial balance")
    @ApiResponse(responseCode = "201", description = "Account created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request or account already exists")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        try {
            Account account = ledgerService.createAccount(request.getAccountId(), request.getInitialBalance());
            AccountResponse response = AccountResponse.fromAccount(account);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create account: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account details", description = "Retrieve account balance and metadata")
    @ApiResponse(responseCode = "200", description = "Account details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        try {
            Account account = ledgerService.getAccount(id);
            AccountResponse response = AccountResponse.fromAccount(account);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Account not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}