# M4 Gamification contract

Owner request: M4-01 through M4-05, issues #95–99. Extends the M2 progression and M3 safety contracts; no duplicate balance or reward source.

## Leaderboards

Both GET `/api/v1/leaderboard` and GET `/api/v1/me/leaderboard-rank` require a session and return `Cache-Control: no-store`. There is no anonymous publication of account rankings. Only usernames, ranks and aggregate earned XP are exposed, never email or account IDs.

`period=all|weekly|monthly` defaults to `all`. Weeks start Monday 00:00 UTC; months start on their first day 00:00 UTC. The inclusive lower bound and server-clock as-of upper bound select ledger `awarded_at`; future events do not count. All-time starts at the Unix epoch. The response period is echoed. Ties share competition rank (1,1,3); display order is username under C collation then UUID. Pagination uses existing page/size bounds (0/20, size 1–100, offset at most Integer.MAX_VALUE). Out-of-range pages are empty. Invalid values return 400 Problem Details.

Only active accounts with positive qualifying XP appear. Zero qualifying XP means an absent row and `player: null` for own rank, not a fabricated rank. Scores come solely from immutable XP ledger entries linked to currently APPROVED Quests without OPEN reports. Archived approved Quests still count. Hiding, reporting, or disabling removes affected ranking eligibility without deleting historical personal XP. Resolving reports can restore eligibility. Scores may therefore differ from private lifetime XP. No opt-in public leaderboard is introduced.

SQL aggregates and window ranks avoid N+1 queries. Count and page share a repeatable-read snapshot. Separate browser requests can observe different commits; offset pages are not a frozen cross-request snapshot. Index migration 012 supports award-time scans and owner completion reads. No production performance guarantee is asserted; large-scale ranking still requires an aggregate/sort over eligible users.

## Delivery checklist

| Workstream | Issue | Status |
|---|---|---|
| M4-01 leaderboard | #95 | In progress |
| M4-02 streaks | #96 | Pending |
| M4-03 UI | #97 | Pending |
| M4-04 achievements | #98 | Pending |
| M4-05 verification | #99 | Pending |

Migrations run only on disposable test clusters during this work. Owner review and approval are required before merge and any persistent database migration.

## Daily streaks

GET `/api/v1/me/streak` is session-only, no-store, and returns `currentStreak`, `longestStreak`, nullable `lastCompletionDate`, and `timezone: UTC`. It accepts no client identity, timestamp or mutation. SQL groups distinct UTC completion dates into consecutive runs, joining the immutable ledger to count committed awards only. No new event table or write path is required. Dates later than the injected server clock are excluded. Yesterday preserves the latest run; earlier last activity yields current zero while retaining longest. Month, leap-day and year boundaries are ordinary consecutive dates.

Streaks describe private historical activity, including archived/hidden history under existing M2/M3 retention rules. They award no XP or rank bonus, and do not change completion or moderation eligibility. No punitive streak loss animation or automatic reminder is added.
