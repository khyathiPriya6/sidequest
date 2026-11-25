package com.sidequest.participation;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StreakService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public StreakService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public record Streak(long currentStreak, long longestStreak, LocalDate lastCompletionDate, String timezone) { }

    @Transactional(readOnly = true)
    public Streak streak(UUID user) {
        var now = clock.instant();
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        return jdbc.queryForObject("""
            WITH days AS (
              SELECT DISTINCT (p.completed_at AT TIME ZONE 'UTC')::date AS activity_day
              FROM participations p JOIN xp_ledger x ON x.participation_id=p.id
              WHERE p.user_id=? AND p.status='COMPLETED' AND p.completed_at<=?
            ), grouped AS (
              SELECT activity_day, activity_day - (ROW_NUMBER() OVER (ORDER BY activity_day))::int island FROM days
            ), runs AS (
              SELECT MAX(activity_day) last_day, COUNT(*) length FROM grouped GROUP BY island
            )
            SELECT MAX(last_day) last_day, COALESCE(MAX(length),0) longest,
              COALESCE((SELECT length FROM runs ORDER BY last_day DESC LIMIT 1),0) latest
            FROM runs
            """, (rs, n) -> {
                LocalDate last = rs.getObject("last_day", LocalDate.class);
                long current = last != null && !last.isBefore(today.minusDays(1)) ? rs.getLong("latest") : 0;
                return new Streak(current, rs.getLong("longest"), last, "UTC");
            }, user, Timestamp.from(now));
    }
}
