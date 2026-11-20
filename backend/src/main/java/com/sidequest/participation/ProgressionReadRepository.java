package com.sidequest.participation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProgressionReadRepository {
    private final JdbcTemplate jdbc;
    public ProgressionReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Summary summary(UUID userId) {
        return jdbc.queryForObject("""
                SELECT u.username, u.created_at,
                  COALESCE((SELECT SUM(x.amount::bigint) FROM xp_ledger x JOIN participations px ON px.id=x.participation_id WHERE px.user_id=u.id),0)::bigint total_xp,
                  COUNT(p.id)::bigint joined_count,
                  COUNT(p.id) FILTER (WHERE p.status='IN_PROGRESS')::bigint active_count,
                  COUNT(p.id) FILTER (WHERE p.status='COMPLETED')::bigint completed_count,
                  (SELECT COUNT(*)::bigint FROM quests cq WHERE cq.creator_id=u.id) created_count,
                  COUNT(DISTINCT q.category) FILTER (WHERE p.status='COMPLETED')::bigint categories_count,
                  COUNT(p.id) FILTER (WHERE p.status='COMPLETED' AND q.difficulty='HARD')::bigint hard_count,
                  COUNT(p.id) FILTER (WHERE (p.status='COMPLETED' AND
                    (x.id IS NULL OR x.amount<>p.reward_xp OR x.awarded_at<p.completed_at)) OR
                    (p.status='IN_PROGRESS' AND x.id IS NOT NULL))::bigint integrity_errors
                FROM users u
                LEFT JOIN participations p ON p.user_id=u.id
                LEFT JOIN quests q ON q.id=p.quest_id
                LEFT JOIN xp_ledger x ON x.participation_id=p.id
                WHERE u.id=?
                GROUP BY u.id
                """, (rs, row) -> new Summary(rs.getString("username"), instant(rs,"created_at"),
                rs.getLong("total_xp"), rs.getLong("joined_count"), rs.getLong("active_count"),
                rs.getLong("completed_count"), rs.getLong("created_count"), rs.getLong("categories_count"),
                rs.getLong("hard_count"), rs.getLong("integrity_errors")), userId);
    }

    public Events events(UUID userId) {
        return jdbc.queryForObject("""
                WITH completed AS (
                  SELECT p.completed_at,p.id,q.category,q.difficulty,
                    ROW_NUMBER() OVER (ORDER BY p.completed_at,p.id) completion_n
                  FROM participations p JOIN quests q ON q.id=p.quest_id
                  WHERE p.user_id=? AND p.status='COMPLETED'
                ), category_first AS (
                  SELECT category, MIN(completed_at) first_at FROM completed GROUP BY category
                ), completion_thresholds AS (
                  SELECT MAX(completed_at) FILTER (WHERE completion_n=1) first_quest,
                    MAX(completed_at) FILTER (WHERE completion_n=5) fifth_quest,
                    MAX(completed_at) FILTER (WHERE completion_n=10) tenth_quest,
                    MAX(completed_at) FILTER (WHERE completion_n=25) twenty_fifth_quest,
                    MAX(completed_at) FILTER (WHERE difficulty='HARD' AND hard_n=5) fifth_hard
                  FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY difficulty='HARD' ORDER BY completed_at,id) hard_n FROM completed) ranked
                ), xp_ranked AS (
                  SELECT x.awarded_at,
                    SUM(x.amount::bigint) OVER (ORDER BY x.awarded_at,x.id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) earned
                  FROM xp_ledger x JOIN participations p ON p.id=x.participation_id
                  WHERE p.user_id=?
                ), xp_thresholds AS (
                  SELECT MIN(awarded_at) FILTER (WHERE earned>=500) xp_500,
                    MIN(awarded_at) FILTER (WHERE earned>=1000) xp_1000,
                    MIN(awarded_at) FILTER (WHERE earned>=5000) xp_5000 FROM xp_ranked
                )
                SELECT c.first_quest,c.fifth_quest,c.tenth_quest,c.twenty_fifth_quest,c.fifth_hard,
                  CASE WHEN (SELECT COUNT(*) FROM category_first)=6 THEN (SELECT MAX(first_at) FROM category_first) END all_categories,
                  x.xp_500,x.xp_1000,x.xp_5000
                FROM completion_thresholds c CROSS JOIN xp_thresholds x
                """, (rs, row) -> new Events(instant(rs,"first_quest"), instant(rs,"fifth_quest"),
                instant(rs,"tenth_quest"), instant(rs,"twenty_fifth_quest"), instant(rs,"xp_500"),
                instant(rs,"xp_1000"), instant(rs,"xp_5000"), instant(rs,"all_categories"),
                instant(rs,"fifth_hard")), userId, userId);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public record Summary(String username, Instant createdAt, long totalXp, long joined,
            long active, long completed, long created, long categories, long hard, long integrityErrors) { }
    public record Events(Instant firstQuest, Instant fifthQuest, Instant tenthQuest, Instant twentyFifthQuest,
            Instant xp500, Instant xp1000, Instant xp5000, Instant allCategories, Instant fifthHard) { }
}
