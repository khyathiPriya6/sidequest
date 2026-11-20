# E04-04 — Participation history (#61)

GET `/api/v1/me/participations` and `/{id}` return owner-scoped DTOs and no-store. Status and questId filters combine independently or together. Pagination is 0/20 by default, size 1–100, nonnegative page, offset <= Integer.MAX_VALUE; ordering joinedAt DESC/id DESC. Missing/nonowner details are concealed as 404. Strict UUID/status parsing rejects invalid inputs. Existing public Quest visibility remains unchanged, while participant summaries include archived Quests.

A JPQL scalar projection joins Quest and optional ledger in one query; full pages add a count query. It never initializes lazy associations per item. Award amount comes from the ledger, rewardXp from the enrollment snapshot. Corrupt completed/missing/mismatched awards fail with a generic integrity error. Explicit GET security routes replace the previous broad `/me/**` matcher, preserving known routes and denying unknown ones.

No migration, mutable XP balance or frontend changes. Backend approval is recorded; frontend handoff remains pending in #35. Rollback disables these reads without changing persistent history. Branch `feat/e04-04-participation-history` targets `feat/e04-03-join-quest` (PR #66); retarget to main after #66 merges. No automatic merge.

## Verification

Java 21.0.13.1, Maven 3.9.16, PostgreSQL 18.4, macOS aarch64. Prefix commands from repository root with `JAVA_HOME=/private/tmp/sidequest-java21/Contents/Home PATH=/private/tmp/sidequest-java21/Contents/Home/bin:$PATH`:

- `mvn -f backend/pom.xml test-compile`: passed.
- `bash backend/scripts/test-postgres.sh -Dit.test=ParticipationHistoryIT`: 17 unit + 3 focused IT passed, zero failures/errors/skips.
- `bash backend/scripts/test-postgres.sh`: 17 unit + 42 IT passed, zero failures/errors/skips; fresh seven migrations and repeated zero applied. No failed test runs. Logs: `/private/tmp/e04-04-compile.log`, `/private/tmp/e04-04-focused.log`, `/private/tmp/e04-04-full.log`.
- `git diff --check`: passed.

Tests cover filters, empty/out-of-range pages, response snapshots and ledger awards, private/archive access, invalid parameters and exact bounded query count plus UUID tie-breaking. No frontend tests needed.

## Teach-back

Filtering ownership in SQL prevents another player's data from entering a response. A scalar projection fetches every response field together; pagination limits the returned rows rather than loading all history.

1. Why must owner filtering happen before pagination?
2. How does a joined scalar projection prevent N+1 queries?
3. Why does a stable page order need an ID tie-breaker?
