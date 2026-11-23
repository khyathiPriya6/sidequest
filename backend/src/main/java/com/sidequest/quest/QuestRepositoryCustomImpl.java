package com.sidequest.quest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.sidequest.moderation.ModerationState;
import com.sidequest.moderation.QuestModeration;
import jakarta.persistence.criteria.AbstractQuery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class QuestRepositoryCustomImpl implements QuestRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Quest> searchPublished(
            String query, QuestCategory category, QuestDifficulty difficulty, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Quest> select = builder.createQuery(Quest.class);
        Root<Quest> root = select.from(Quest.class);
        var pattern = query == null ? null : builder.parameter(String.class, "searchPattern");
        select.select(root).where(builder.and(predicates(builder, select, root, category, difficulty, pattern)))
                .orderBy(builder.desc(root.get("createdAt")), builder.desc(root.get("id")));

        TypedQuery<Quest> results = entityManager.createQuery(select);
        if (pattern != null) results.setParameter("searchPattern", "%" + escapeLike(query) + "%");
        int offset;
        try {
            offset = Math.toIntExact(pageable.getOffset());
        } catch (ArithmeticException exception) {
            throw QuestApiFailure.badRequest("invalid_pagination", "The requested page is out of range.");
        }
        results.setFirstResult(offset);
        results.setMaxResults(pageable.getPageSize());

        CriteriaQuery<Long> count = builder.createQuery(Long.class);
        Root<Quest> countRoot = count.from(Quest.class);
        var countPattern = query == null ? null : builder.parameter(String.class, "searchPattern");
        count.select(builder.count(countRoot))
                .where(builder.and(predicates(builder, count, countRoot, category, difficulty, countPattern)));
        TypedQuery<Long> countQuery = entityManager.createQuery(count);
        if (countPattern != null) countQuery.setParameter("searchPattern", "%" + escapeLike(query) + "%");
        return new PageImpl<>(results.getResultList(), pageable, countQuery.getSingleResult());
    }

    private static Predicate[] predicates(
            CriteriaBuilder builder, AbstractQuery<?> query, Root<Quest> root, QuestCategory category,
            QuestDifficulty difficulty, jakarta.persistence.criteria.Expression<String> pattern) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(root.get("status"), QuestStatus.PUBLISHED));
        var approval = query.subquery(Integer.class);
        Root<QuestModeration> review = approval.from(QuestModeration.class);
        approval.select(builder.literal(1)).where(
                builder.equal(review.get("questId"), root.get("id")),
                builder.equal(review.get("state"), ModerationState.APPROVED));
        predicates.add(builder.exists(approval));
        if (category != null) predicates.add(builder.equal(root.get("category"), category));
        if (difficulty != null) predicates.add(builder.equal(root.get("difficulty"), difficulty));
        if (pattern != null) {
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("title")), pattern, '\\'),
                    builder.like(builder.lower(root.get("description")), pattern, '\\')));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private static String escapeLike(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
