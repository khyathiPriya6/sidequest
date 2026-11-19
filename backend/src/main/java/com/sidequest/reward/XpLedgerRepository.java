package com.sidequest.reward;

import java.util.Optional;
import java.util.UUID;

public interface XpLedgerRepository {
    XpLedger save(XpLedger ledger);

    Optional<XpLedger> findByParticipationId(UUID participationId);

    boolean existsByParticipationId(UUID participationId);
}
