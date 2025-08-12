package com.fintech.ledger_service.controller;

import com.fintech.ledger_service.domain.TransferResult;
import com.fintech.ledger_service.dto.TransferRequest;
import com.fintech.ledger_service.dto.TransferResponse;
import com.fintech.ledger_service.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ledger")
@Tag(name = "Ledger Operations", description = "Core ledger operations for transfers")
public class LedgerController {

    private static final Logger log = LoggerFactory.getLogger(LedgerController.class);

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transfer")
    @Operation(summary = "Apply a transfer", description = "Execute a transfer between two accounts atomically")
    @ApiResponse(responseCode = "200", description = "Transfer processed (check success field for actual result)")
    @ApiResponse(responseCode = "400", description = "Invalid transfer request")
    public ResponseEntity<TransferResponse> applyTransfer(@Valid @RequestBody TransferRequest request) {
        try {
            TransferResult result = ledgerService.applyTransfer(
                    request.getTransferId(),
                    request.getFromAccountId(),
                    request.getToAccountId(),
                    request.getAmount()
            );

            TransferResponse response = TransferResponse.fromTransferResult(result);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid transfer request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error processing transfer", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
