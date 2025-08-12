package com.fintech.ledger_service.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TransferResult {
    private final String transferId;
    private final boolean success;
    private final String message;
    private final BigDecimal fromBalanceAfter;
    private final BigDecimal toBalanceAfter;
    private final LocalDateTime timestamp;

    private TransferResult(String transferId, boolean success, String message,
                           BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter) {
        this.transferId = transferId;
        this.success = success;
        this.message = message;
        this.fromBalanceAfter = fromBalanceAfter;
        this.toBalanceAfter = toBalanceAfter;
        this.timestamp = LocalDateTime.now();
    }

    public static TransferResult success(String transferId, BigDecimal fromBalance, BigDecimal toBalance) {
        return new TransferResult(transferId, true, "Transfer completed successfully", fromBalance, toBalance);
    }

    public static TransferResult failure(String transferId, String message) {
        return new TransferResult(transferId, false, message, null, null);
    }

    public static TransferResult alreadyProcessed(String transferId, BigDecimal fromBalance, BigDecimal toBalance) {
        return new TransferResult(transferId, true, "Transfer already processed", fromBalance, toBalance);
    }
}

