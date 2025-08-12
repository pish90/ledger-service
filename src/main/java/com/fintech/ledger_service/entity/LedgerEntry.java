package com.fintech.ledger_service.entity;

import com.fintech.ledger_service.data.EntryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ledger_entries",
        indexes = {
                @Index(name = "idx_transfer_id", columnList = "transferId"),
                @Index(name = "idx_account_id", columnList = "accountId"),
                @Index(name = "idx_created_at", columnList = "createdAt")
        })
public class LedgerEntry {

    @Id
    private String id;

    @NotNull
    @Column(name = "transfer_id")
    private String transferId;

    @NotNull
    @Column(name = "account_id")
    private Long accountId;

    @NotNull
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LedgerEntry(String id, String transferId, Long accountId, BigDecimal amount, EntryType type) {
        this.id = id;
        this.transferId = transferId;
        this.accountId = accountId;
        this.amount = amount;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }
}