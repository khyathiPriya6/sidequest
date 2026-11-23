package com.sidequest.moderation;

import java.time.Clock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initial per-account abuse budget backed by committed PostgreSQL rows.
 * Must be called inside the caller's write transaction so the advisory lock
 * remains held until the inserted activity commits.
 *
 * Coarse protection only; does not detect Sybil accounts or shared-IP abuse.
 */
@Component
public class AccountAbuseQuota {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountAbuseQuota(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void requireQuestCreation(UUID userId) {
        enforce(userId, 21, """
                SELECT count(*) FROM quests WHERE creator_id = ? AND created_at >= ?
                """, 40, Duration.ofHours(24));
    }

    public void requireNewJoin(UUID userId) {
        enforce(userId, 22, """
                SELECT count(*) FROM participations WHERE user_id = ? AND joined_at >= ?
                """, 100, Duration.ofHours(1));
    }

    public void requireNewReport(UUID userId) {
        enforce(userId, 23, """
                SELECT count(*) FROM quest_reports WHERE reporter_id = ? AND created_at >= ?
                """, 15, Duration.ofHours(24));
    }

    private void enforce(UUID userId, int operation, String query, int limit, Duration period) {
        // Advisory locks are transaction-scoped: calls by the same user+operation
        // serialize the count + inserted row. Hash collisions only reduce throughput.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, ?)",
                (rs, row) -> 1, operation, userId.hashCode());
        Instant cutoff = clock.instant().minus(period);
        Long used = jdbc.queryForObject(query, Long.class, userId, Timestamp.from(cutoff));
        if (used != null && used >= limit) throw new QuotaExceededException();
    }

    public static final class QuotaExceededException extends RuntimeException {
        public QuotaExceededException() {
            super("Too many requests for this activity. Try again later.");
        }
    }
}
