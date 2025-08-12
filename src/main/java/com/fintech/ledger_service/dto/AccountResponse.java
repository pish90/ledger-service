package com.fintech.ledger_service.dto;

import com.fintech.ledger_service.entity.Account;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AccountResponse {

    private Long id;
    private BigDecimal balance;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AccountResponse(Long id, BigDecimal balance, Long version,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.balance = balance;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static AccountResponse fromAccount(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getBalance(),
                account.getVersion(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
