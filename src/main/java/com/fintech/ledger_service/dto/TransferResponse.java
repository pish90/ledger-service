package com.fintech.ledger_service.dto;

import com.fintech.ledger_service.domain.TransferResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TransferResponse {
    private String transferId;
    private boolean success;
    private String message;
    private BigDecimal fromBalanceAfter;
    private BigDecimal toBalanceAfter;
    private LocalDateTime timestamp;

    public TransferResponse(String transferId, boolean success, String message,
                            BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, LocalDateTime timestamp) {
        this.transferId = transferId;
        this.success = success;
        this.message = message;
        this.fromBalanceAfter = fromBalanceAfter;
        this.toBalanceAfter = toBalanceAfter;
        this.timestamp = timestamp;
    }

    public static TransferResponse fromTransferResult(TransferResult result) {
        return new TransferResponse(
                result.getTransferId(),
                result.isSuccess(),
                result.getMessage(),
                result.getFromBalanceAfter(),
                result.getToBalanceAfter(),
                result.getTimestamp()
        );
    }
}
