package com.sidequest.moderation;

import com.sidequest.quest.Quest;
import com.sidequest.quest.QuestRepository;
import com.sidequest.quest.QuestStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moderation write boundary. Staff permission is checked against an independently
 * provisioned database table, NOT a request parameter or self-assigned session role.
 */
@Service
class ModerationOperations {
    enum ReportReason {
        DANGEROUS_ACT, SELF_HARM, ILLEGAL, VIOLENCE_HARASSMENT, HATE,
        EXPLOITATION, FRAUD_SPAM, PRIVACY, OTHER
    }
    enum Action { APPROVE, REJECT, HIDE, DISMISS_REPORTS }
    record ReportResult(UUID id, boolean created) { }
    record AppealResult(UUID id, String status) { }
    record ReviewItem(UUID questId, String title, String description, String questStatus, ModerationState state,
                      String reasonCode, long version, long reports) { }
    record ReviewPage(List<ReviewItem> content, int page, int size, long totalElements,
                      int totalPages, boolean hasNext, boolean hasPrevious) { }

    private final JdbcTemplate jdbc;
    private final QuestRepository quests;
    private final QuestModerationRepository reviews;
    private final Clock clock;
    private final AccountAbuseQuota abuseQuota;

    ModerationOperations(JdbcTemplate jdbc, QuestRepository quests,
                         QuestModerationRepository reviews, Clock clock, AccountAbuseQuota abuseQuota) {
        this.jdbc = jdbc;
        this.quests = quests;
        this.reviews = reviews;
        this.clock = clock;
        this.abuseQuota = abuseQuota;
    }

    @Transactional
    ReportResult report(UUID reporter, UUID questId, ReportReason reason) {
        if (reason == null) throw ModerationApiFailure.invalid();
        // Public approval predicate also restricts reporting; no private Quest disclosure.
        Quest quest = quests.findApprovedPublished(questId, QuestStatus.PUBLISHED, ModerationState.APPROVED)
                .orElseThrow(ModerationApiFailure::notFound);
        if (quest.getCreatorId().equals(reporter)) throw ModerationApiFailure.invalid();
        UUID prior = jdbc.query("""
                SELECT id FROM quest_reports WHERE quest_id = ? AND reporter_id = ?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, questId, reporter);
        if (prior != null) return new ReportResult(prior, false);
        abuseQuota.requireNewReport(reporter, questId);
        UUID reportId = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO quest_reports (id, quest_id, reporter_id, reason, created_at)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (quest_id, reporter_id) DO NOTHING
                """, reportId, questId, reporter, reason.name(), Timestamp.from(clock.instant()));
        if (inserted == 1) return new ReportResult(reportId, true);
        UUID existing = jdbc.queryForObject("""
                SELECT id FROM quest_reports WHERE quest_id = ? AND reporter_id = ?
                """, UUID.class, questId, reporter);
        return new ReportResult(existing, false);
    }

    @Transactional(readOnly = true)
    ReviewPage queue(UUID moderator, int page, int size) {
        requireModerator(moderator);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw ModerationApiFailure.invalid();
        String predicate = """
                FROM quest_moderation m JOIN quests q ON q.id = m.quest_id
                WHERE m.state IN ('PENDING_REVIEW','UNDER_REVIEW')
                OR (m.state = 'APPROVED' AND EXISTS
                    (SELECT 1 FROM quest_reports report WHERE report.quest_id = m.quest_id AND report.resolution = 'OPEN'))
                """;
        Long count = jdbc.queryForObject("SELECT count(*) " + predicate, Long.class);
        long total = count == null ? 0 : count;
        List<ReviewItem> content = jdbc.query("""
                SELECT m.quest_id, q.title, q.description, q.status, m.state, m.reason_code, m.version,
                       (SELECT count(*) FROM quest_reports r WHERE r.quest_id = m.quest_id AND r.resolution = 'OPEN') AS report_count
                """ + predicate + " ORDER BY m.submitted_at NULLS LAST, m.quest_id LIMIT ? OFFSET ?",
                (rs, row) -> new ReviewItem(
                        rs.getObject("quest_id", UUID.class), rs.getString("title"), rs.getString("description"),
                        rs.getString("status"), ModerationState.valueOf(rs.getString("state")),
                        rs.getString("reason_code"), rs.getLong("version"), rs.getLong("report_count")),
                size, page * size);
        int totalPages = (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        return new ReviewPage(content, page, size, total, totalPages,
                ((long) page + 1) < totalPages, page > 0);
    }

    @Transactional
    ReviewItem decide(UUID moderator, UUID questId, Action action, String reasonCode, long expectedVersion) {
        requireModerator(moderator);
        if (action == null || expectedVersion < 0) throw ModerationApiFailure.invalid();
        // Same quest lock order as publish/archive/Join to prevent approval/hide races.
        Quest quest = quests.findByIdForUpdate(questId).orElseThrow(ModerationApiFailure::notFound);
        QuestModeration review = reviews.findById(questId).orElseThrow(ModerationApiFailure::notFound);
        if (review.getVersion() != expectedVersion) throw ModerationApiFailure.conflict();
        ModerationState previous = review.getState();
        Instant now = clock.instant();
        if (action == Action.APPROVE) {
            if (quest.getStatus() != QuestStatus.DRAFT || !(previous == ModerationState.PENDING_REVIEW || previous == ModerationState.UNDER_REVIEW))
                throw ModerationApiFailure.conflict();
            review.approve(now);
        } else if (action == Action.REJECT) {
            if (quest.getStatus() != QuestStatus.DRAFT || !(previous == ModerationState.PENDING_REVIEW || previous == ModerationState.UNDER_REVIEW))
                throw ModerationApiFailure.conflict();
            review.reject(now, normalizedReason(reasonCode));
        } else if (action == Action.HIDE) {
            if (quest.getStatus() != QuestStatus.PUBLISHED || previous != ModerationState.APPROVED)
                throw ModerationApiFailure.conflict();
            review.hide(now, normalizedReason(reasonCode));
        } else {
            if (quest.getStatus() != QuestStatus.PUBLISHED || previous != ModerationState.APPROVED
                    || openReports(questId) == 0) throw ModerationApiFailure.conflict();
        }
        reviews.saveAndFlush(review);
        if (previous == ModerationState.UNDER_REVIEW) {
            int resolved = jdbc.update("""
                    UPDATE moderation_appeals SET status = ?, resolved_at = ?, resolved_by = ?
                    WHERE quest_id = ? AND status = 'PENDING'
                    """, action == Action.APPROVE ? "APPROVED" : "REJECTED",
                    Timestamp.from(now), moderator, questId);
            if (resolved != 1) throw ModerationApiFailure.conflict();
        }
        if (action == Action.HIDE || action == Action.DISMISS_REPORTS) {
            jdbc.update("""
                    UPDATE quest_reports SET resolution = ?, resolved_at = ?, resolved_by = ?
                    WHERE quest_id = ? AND resolution = 'OPEN'
                    """, action == Action.HIDE ? "ACTIONED" : "DISMISSED",
                    Timestamp.from(now), moderator, questId);
        }
        jdbc.update("""
                INSERT INTO moderation_audit (id, quest_id, moderator_id, previous_state, next_state, reason_code, decided_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), questId, moderator, previous.name(), review.getState().name(),
                action == Action.DISMISS_REPORTS ? "REPORTS_DISMISSED" : review.getReasonCode(),
                Timestamp.from(now));
        Long reports = jdbc.queryForObject("SELECT count(*) FROM quest_reports WHERE quest_id = ? AND resolution = 'OPEN'", Long.class, questId);
        return new ReviewItem(questId, quest.getTitle(), quest.getDescription(), quest.getStatus().name(),
                review.getState(), review.getReasonCode(), review.getVersion(), reports == null ? 0 : reports);
    }

    @Transactional
    AppealResult appeal(UUID creatorId, UUID questId, long expectedVersion, String reason) {
        String explanation = reason == null ? "" : reason.strip();
        if (expectedVersion < 0 || explanation.length() < 10 || explanation.length() > 1000)
            throw ModerationApiFailure.invalid();
        Quest quest = quests.findByIdAndCreatorForUpdate(questId, creatorId)
                .orElseThrow(ModerationApiFailure::notFound);
        if (quest.getStatus() != QuestStatus.DRAFT) throw ModerationApiFailure.conflict();
        QuestModeration review = reviews.findById(questId).orElseThrow(ModerationApiFailure::notFound);
        if (review.getVersion() != expectedVersion || review.getState() != ModerationState.REJECTED)
            throw ModerationApiFailure.conflict();
        Instant now = clock.instant();
        review.appeal(now);
        reviews.saveAndFlush(review);
        UUID appealId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO moderation_appeals (id, quest_id, creator_id, moderation_version, reason, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
                """, appealId, questId, creatorId, expectedVersion, explanation, Timestamp.from(now));
        return new AppealResult(appealId, "PENDING");
    }

    private long openReports(UUID questId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM quest_reports WHERE quest_id = ? AND resolution = 'OPEN'",
                Long.class, questId);
        return count == null ? 0 : count;
    }

    private static String normalizedReason(String value) {
        if (value == null) throw ModerationApiFailure.invalid();
        String normalized = value.strip();
        if (!normalized.matches("[A-Z_]{3,40}")) throw ModerationApiFailure.invalid();
        return normalized;
    }

    private void requireModerator(UUID userId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM moderation_staff WHERE user_id = ?)", Boolean.class, userId);
        if (!Boolean.TRUE.equals(exists)) throw ModerationApiFailure.forbidden();
    }
}
