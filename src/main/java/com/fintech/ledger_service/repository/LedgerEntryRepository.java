package com.fintech.ledger_service.repository;

import com.fintech.ledger_service.data.EntryType;
import com.fintech.ledger_service.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<LedgerEntry> findByTransferIdOrderByCreatedAt(String transferId);

    @Query("SELECT COALESCE(SUM(CASE WHEN le.type = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) FROM LedgerEntry le WHERE le.accountId = :accountId")
    BigDecimal calculateAccountBalance(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(le.amount), 0) FROM LedgerEntry le WHERE le.transferId = :transferId AND le.type = :type")
    BigDecimal sumAmountByTransferAndType(@Param("transferId") String transferId, @Param("type") EntryType type);

    boolean existsByTransferId(String transferId);
}
