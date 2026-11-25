package com.sidequest.participation;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class LeaderboardService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public LeaderboardService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public record Entry(String username, long rank, long earnedXp) { }
    public record Ranking(String period, Entry player) { }
    public record Board(String period, List<Entry> content, int page, int size, long totalElements,
                        int totalPages, boolean hasNext, boolean hasPrevious) { }
    record Period(Instant start, Instant end) { }

    static Period period(String period, Instant now) {
        var today = now.atZone(ZoneOffset.UTC).toLocalDate();
        return switch (period) {
            case "all" -> new Period(Instant.EPOCH, now);
            case "weekly" -> new Period(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(ZoneOffset.UTC).toInstant(), now);
            case "monthly" -> new Period(today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant(), now);
            default -> throw ParticipationFailure.badRequest("invalid_period");
        };
    }

    // Only aggregate earned ledger XP; moderation visibility never rewrites private XP history.
    private static final String RANKED = """
        WITH scores AS (
          SELECT p.user_id, SUM(x.amount::bigint)::bigint earned_xp
          FROM xp_ledger x JOIN participations p ON p.id=x.participation_id
          JOIN quest_moderation m ON m.quest_id=p.quest_id AND m.state='APPROVED'
          WHERE x.awarded_at >= ? AND x.awarded_at <= ?
            AND NOT EXISTS (SELECT 1 FROM quest_reports r WHERE r.quest_id=p.quest_id AND r.resolution='OPEN')
          GROUP BY p.user_id
        ), ranked AS (
          SELECT u.id, u.username, s.earned_xp, RANK() OVER (ORDER BY s.earned_xp DESC) position
          FROM scores s JOIN users u ON u.id=s.user_id WHERE u.status='ACTIVE'
        )
        """;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Board board(String period, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw ParticipationFailure.badRequest("invalid_pagination");
        var bounds = period(period, clock.instant());
        Object[] args = {Timestamp.from(bounds.start()), Timestamp.from(bounds.end())};
        long total = jdbc.queryForObject(RANKED + "SELECT count(*) FROM ranked", Long.class, args);
        var rows = jdbc.query(RANKED + "SELECT * FROM ranked ORDER BY earned_xp DESC, username COLLATE \"C\", id LIMIT ? OFFSET ?",
                (rs, n) -> new Entry(rs.getString("username"), rs.getLong("position"), rs.getLong("earned_xp")),
                args[0], args[1], size, (long) page * size);
        int pages = (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        return new Board(period, rows, page, size, total, pages, (long) page + 1 < pages, page > 0);
    }

    @Transactional(readOnly = true)
    public Ranking rank(UUID user, String period) {
        var bounds = period(period, clock.instant());
        var rows = jdbc.query(RANKED + "SELECT * FROM ranked WHERE id=?",
                (rs, n) -> new Entry(rs.getString("username"), rs.getLong("position"), rs.getLong("earned_xp")),
                Timestamp.from(bounds.start()), Timestamp.from(bounds.end()), user);
        return new Ranking(period, rows.isEmpty() ? null : rows.getFirst());
    }
}
