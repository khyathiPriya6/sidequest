# E04-06 — Progression and achievement contract (proposed for owner approval)

**Status: proposed; no progression or profile runtime code is authorized by this document.** Issue #63, parent #4. Depends on E04-04 history and E04-05 completion being reviewed and merged. Owner approval is required before E04-07 (#64) implementation. Backend implementation has a separate owner-authorized exception to the Figma handoff gate; frontend implementation remains design-gated and UX-006 #35 remains open. No new product formula should be inferred until the owner approves this contract.

## XP and levels

The immutable `xp_ledger` is the only source of earned XP. `totalXp` is `SUM(amount)` cast to PostgreSQL `bigint` / Java `long`; empty is zero. There is no persisted XP balance, level or progress cache.

For nonnegative total XP:

- `level = floor(totalXp / 500) + 1`
- `xpIntoLevel = totalXp % 500`
- `xpRemaining = 500 - xpIntoLevel`
- `progressPercent = xpIntoLevel / 500.0 * 100`

Thus 0 XP gives Level 1, 0/500 and 0%; exactly 500 gives Level 2, 0/500 and 0%; 999 gives Level 2, 499/500 and 99.8%. Use integer division/modulus first and a decimal numeric type for percentage. 5000 XP gives Level 11. Totals outside Java `long` are integrity/operational errors, never wraparound. Since each award is 1–1000, `SUM(bigint)` is appropriate; the database must cast before sum, not after an int sum.

## Achievement catalogue and progress

Stable IDs are lowercase snake case. Progress is a monotonic integer numerator, `target` is the unlock threshold, and displayed progress is clamped at target. Unlock does not grant XP. Catalogue order is the following fixed order; adding an achievement is an additive contract change with a new unique ID.

| ID | Name | Rule | Target | Unlock event time |
|---|---|---|---:|---|
| `first_adventure` | First Adventure | Complete any quest | 1 | completedAt of the first completion |
| `quest_explorer` | Quest Explorer | Complete quests | 5 | completedAt of the fifth completion |
| `quest_veteran` | Quest Veteran | Complete quests | 10 | completedAt of the tenth completion |
| `dedicated_adventurer` | Dedicated Adventurer | Complete quests | 25 | completedAt of the 25th completion |
| `xp_rookie` | XP Rookie | Earn XP | 500 | awardedAt of the ledger event that first reaches 500 cumulative XP |
| `rising_hero` | Rising Hero | Earn XP | 1000 | awardedAt of the event that first reaches 1000 cumulative XP |
| `quest_master` | Quest Master | Earn XP | 5000 | awardedAt of the event that first reaches 5000 cumulative XP |
| `all_rounder` | All-Rounder | Complete quests in every category | 6 | completedAt of the last first-achieved distinct category |
| `challenge_seeker` | Challenge Seeker | Complete HARD quests | 5 | completedAt of the fifth HARD completion |

“First Adventure” is a legacy product name; it means the first completed Quest of any category/difficulty, not only ADVENTURE. Category coverage is based on the six enum values `ADVENTURE`, `CREATIVITY`, `FITNESS`, `LEARNING`, `FOOD`, `SOCIAL`. Difficulty comes from the Quest at enrollment/completion; published material requirements are immutable while participants exist. Archived completions count. Only COMPLETED participations count. The category and difficulty predicates use Quest attributes joined to the completed participation; they never use public visibility, so archive does not remove history.

For event selection, order relevant completion events by `(completed_at ASC, participation_id ASC)` and XP events by `(awarded_at ASC, ledger_id ASC)`. The event at which an accumulated count/XP first crosses the threshold determines the unlock timestamp. All-Rounder uses the maximum of the first completion timestamp for each of the six categories. This deterministic tie-break makes concurrent events reproducible; UUID comparison must be identical in PostgreSQL and Java, preferably perform selection in SQL. Completion and ledger timestamps are server-generated UTC instants persisted at PostgreSQL microsecond precision.

A catalogue entry includes `id`, `name`, `description`, `progress`, `target`, `unlocked` and nullable `unlockedAt`. For a locked item, `unlockedAt` is null; do not fabricate it from the current time. For an unlocked derived item, compute it from the qualifying immutable event history above. Catalog order is stable. `progress` returns `min(actual qualifying progress, target)`. Counts and sums use bigint; category distinct count is bounded by six.

## Derived achievements versus persisted unlock records

**Recommendation: derive the nine MVP achievements from immutable participation/ledger/Quest history.** This requires no extra table, migration, dual-write or repair job. It naturally includes archived Quest completions and remains consistent when completion and ledger commit atomically. The response cost is small for a nine-entry catalogue; implement as bounded SQL aggregates/event-threshold queries rather than loading complete histories into memory. A small fixed number of queries is acceptable initially and must be measured before claiming performance. Prefer a single aggregate query for counts/distinct categories/XP total, plus threshold-event queries only for timestamps. XP sums and completion counts are bigint.

Persisted unlock rows make unlocks cheap to read and can record an explicit notification/seen state, but create a second state requiring the completion transaction to update it. If unlock persistence is asynchronous, it can lag; if synchronous, it couples the ledger transaction to derived rules and requires backfill/reconciliation when a rule changes. Persist only if product later requires durable notification/claim state or measured query cost warrants it. Such a change requires a new issue, a versioned ruleset/achievement ID policy, immutable unlock event semantics, transactional consistency or an explicit outbox/rebuild strategy, migration and reconciliation plan. Do not store a mutable copy of current XP or level.

Because derived unlock times depend on source timestamps, changing historical ledger/participation/Quest category data would change them. Database immutability protects core participation and ledger rows; future Quest persistence must continue preventing material category/difficulty edits after participation. Administrative correction should be an audited new policy, not silent mutation. The contract intentionally derives current status, not a separate “first displayed” or acknowledgment time.

## Proposed owner-only API shape for E04-07

These are proposals and must be finalized with owner approval before implementation. All routes are under `/api/v1`, use session principal identity, explicit DTOs, `Cache-Control: no-store`, owner privacy and deny-by-default security. No client user ID or calculated totals are accepted.

- `GET /me/profile`: username, account `createdAt`, `totalXp`, level, XP into/remaining/percentage, counts `joinedQuests`, `activeQuests`, `completedQuests`, `createdQuests`, and `unlockedAchievementCount`. Joined count includes every participation once; active means IN_PROGRESS; completed means COMPLETED, including archived Quest. Created count includes all Quest lifecycle statuses.
- `GET /me/xp-transactions?page=0&size=20`: page newest `awardedAt DESC, ledger id DESC`, with ledger ID, participation ID, Quest ID, amount, awardedAt and a safe Quest title snapshot/current summary policy to be decided. Standard page envelope; bounds 0/20 and size 1–100; bigint total count. No XP balance is materialized.
- `GET /me/achievements`: all nine stable catalogue entries including locked and unlocked, progress and nullable derived `unlockedAt`.

Missing auth is 401; all data is the caller's only. Use aggregate queries to calculate totals/counts; never load complete participation/Quest/ledger histories into application memory. Profile endpoint query design and its consistency snapshot should use one read-only transaction so aggregates represent one database snapshot; under PostgreSQL READ COMMITTED separate queries can see different committed states, so use a single SQL statement or REPEATABLE READ if the API promises snapshot consistency. Returned XP ledger history uses ordinary offset pagination and deterministic key tie-break; a cursor may be a later additive improvement, not required now. Invalid pagination and enum/UUIDs return RFC9457 400.

## Consistency and security

Completion commits participation state and its one ledger row atomically. All profile/progression reads sum ledger entries, not participation reward snapshots, so they count awarded XP only. Cross-check completion count against COMPLETED participations and XP amount against each completed participation's snapshot as integrity monitoring; do not “repair” by re-awarding. A completed participation without exactly one matching ledger row is an integrity error for completion and should not be silently counted as earned XP. An unmatched ledger award should likewise fail integrity checks. These cross-row conditions cannot be enforced with ordinary SQL CHECK constraints and are guaranteed by E04-05 transaction plus unique/restricted FK and service checks.

No additional XP is awarded for achievements. There is no client-controlled unlock, category, completion, XP, timestamp or user identity. Backend progression does not require Figma handoff by owner authorization; any UI that presents these values still requires design approval and #35 acceptance.

## Approval checklist

- [ ] Approve 500 XP per level and exact level boundary formulas.
- [ ] Approve all nine achievement names, IDs, category/difficulty/count rules and no bonus XP.
- [ ] Approve deterministic unlock timestamp semantics, especially XP-award event ordering and All-Rounder.
- [ ] Approve deriving achievements from history for MVP, with no persisted unlock table.
- [ ] Approve proposed profile, XP history and achievement endpoints/fields and read consistency policy.

E04-07 (#64) must remain blocked until these are explicitly approved. PR #63 is documentation only and targets completion PR #68; E04 branch merge order is Join #66 → History #67 → Completion #68 → this contract PR. Each dependent PR must be retargeted after predecessor merges; no merge is automatic.

## Teach-back

XP balance and level can be recalculated from the ledger, so copying them into mutable columns creates extra state that can drift. Derived achievements avoid syncing a second table; their unlock timestamps need precise rules so a retry or tie produces the same result.

1. Why cast `amount` to bigint before summing XP?
2. What consistency cost does a persisted achievement unlock table add?
3. How can two awards at the same timestamp still produce a deterministic unlock time?
