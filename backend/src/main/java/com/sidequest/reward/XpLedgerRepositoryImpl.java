package com.sidequest.reward;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class XpLedgerRepositoryImpl implements XpLedgerRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public XpLedger save(XpLedger ledger) {
        if (ledger == null) throw new IllegalArgumentException("ledger is required");
        if (entityManager.find(XpLedger.class, ledger.getId()) != null) {
            throw new UnsupportedOperationException("XP ledger entries are append-only");
        }
        if (entityManager.createQuery(
                "select count(x) from XpLedger x where x.participation.id = :participationId", Long.class)
                .setParameter("participationId", ledger.getParticipationId())
                .getSingleResult() > 0) {
            throw new IllegalStateException("A participation can have only one XP ledger entry");
        }
        entityManager.persist(ledger);
        entityManager.flush();
        return ledger;
    }

    @Override
    public Optional<XpLedger> findByParticipationId(UUID participationId) {
        return entityManager.createQuery(
                "select x from XpLedger x where x.participation.id = :participationId", XpLedger.class)
                .setParameter("participationId", participationId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    @Override
    public boolean existsByParticipationId(UUID participationId) {
        return entityManager.createQuery(
                "select count(x) from XpLedger x where x.participation.id = :participationId", Long.class)
                .setParameter("participationId", participationId)
                .getSingleResult() > 0;
    }

    @Override
    public Page<XpTransactionRow> findOwnerTransactions(UUID userId, Pageable pageable) {
        var query = entityManager.createQuery("""
                select new com.sidequest.reward.XpTransactionRow(x.id, p.id, q.id, x.amount, p.rewardXp, p.completedAt,
                    x.awardedAt, q.title, cast(q.category as string), cast(q.difficulty as string), cast(q.status as string))
                from XpLedger x join x.participation p join p.quest q
                where p.user.id = :userId
                order by x.awardedAt desc, x.id desc
                """, XpTransactionRow.class).setParameter("userId", userId);
        var content = query.setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize()).getResultList();
        long count = entityManager.createQuery("select count(x) from XpLedger x where x.participation.user.id=:userId", Long.class)
                .setParameter("userId", userId).getSingleResult();
        return new PageImpl<>(content, pageable, count);
    }
}
