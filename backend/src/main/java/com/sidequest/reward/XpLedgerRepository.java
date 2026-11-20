package com.sidequest.reward;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface XpLedgerRepository {
    XpLedger save(XpLedger ledger);

    Optional<XpLedger> findByParticipationId(UUID participationId);

    boolean existsByParticipationId(UUID participationId);

    Page<XpTransactionRow> findOwnerTransactions(UUID userId, Pageable pageable);
}
