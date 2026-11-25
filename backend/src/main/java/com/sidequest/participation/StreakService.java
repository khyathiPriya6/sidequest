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
    public record History(Streak streak, Instant firstThreeDays, Instant firstSevenDays) { }

    @Transactional(readOnly = true)
    public Streak streak(UUID user) { return history(user).streak(); }

    @Transactional(readOnly = true)
    public History history(UUID user) {
        var now = clock.instant();
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        return jdbc.queryForObject("""
            WITH days AS (
              SELECT (p.completed_at AT TIME ZONE 'UTC')::date AS activity_day, MIN(p.completed_at) first_at
              FROM participations p JOIN xp_ledger x ON x.participation_id=p.id
              WHERE p.user_id=? AND p.status='COMPLETED' AND p.completed_at<=?
              GROUP BY (p.completed_at AT TIME ZONE 'UTC')::date
            ), grouped AS (
              SELECT *, activity_day - (ROW_NUMBER() OVER (ORDER BY activity_day))::int island FROM days
            ), numbered AS (
              SELECT *, ROW_NUMBER() OVER (PARTITION BY island ORDER BY activity_day) run_day FROM grouped
            ), runs AS (
              SELECT MAX(activity_day) last_day, COUNT(*) length FROM grouped GROUP BY island
            )
            SELECT MAX(last_day) last_day, COALESCE(MAX(length),0) longest,
              COALESCE((SELECT length FROM runs ORDER BY last_day DESC LIMIT 1),0) latest,
              (SELECT MIN(first_at) FROM numbered WHERE run_day=3) first_three,
              (SELECT MIN(first_at) FROM numbered WHERE run_day=7) first_seven
            FROM runs
            """, (rs, n) -> {
                LocalDate last = rs.getObject("last_day", LocalDate.class);
                long current = last != null && !last.isBefore(today.minusDays(1)) ? rs.getLong("latest") : 0;
                var three = rs.getObject("first_three", OffsetDateTime.class);
                var seven = rs.getObject("first_seven", OffsetDateTime.class);
                return new History(new Streak(current, rs.getLong("longest"), last, "UTC"),
                        three == null ? null : three.toInstant(), seven == null ? null : seven.toInstant());
            }, user, Timestamp.from(now));
    }
}
