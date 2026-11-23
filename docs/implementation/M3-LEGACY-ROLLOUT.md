# M3 — Legacy Quest safety-gate rollout (operator review required)

**Status:** Draft runbook only; no database migration execution is authorized.  
**Canonical review:** [M3 PR #88](https://github.com/khyathiPriya6/sidequest/pull/88).

## Failure mode

Before M3, `quests.status = PUBLISHED` was enough for public detail, search and Join.
After M3, public detail/search/Join require `PUBLISHED` **and** `quest_moderation.state = APPROVED`.
Legacy published quests with no moderation record are fail-closed and disappear from public
discovery. Their immutable participation/XP history must not be deleted or re-awarded.

## Required preparation (safe read-only inventory)

On a disposable staging copy, after applying migration 008 and later:

```sql
SELECT q.status, count(*) AS quest_count,
       count(m.quest_id) AS with_moderation_record
FROM quests q
LEFT JOIN quest_moderation m ON m.quest_id = q.id
GROUP BY q.status ORDER BY q.status;

SELECT q.id, q.title, q.created_at
FROM quests q LEFT JOIN quest_moderation m ON m.quest_id = q.id
WHERE q.status = 'PUBLISHED' AND m.quest_id IS NULL
ORDER BY q.created_at DESC;
```

Treat any public Quest missing a safety review as unapproved, not implicitly safe.
Do not expose full content or creator identifiers in public logs.

## Proposed safe rollout

1. Owner reviews sample size, published Quest inventory, available moderator capacity and acceptable temporary visibility gap.
2. Apply versioned Liquibase migrations to **disposable staging only**. Verify tables, constraints and query plans. Do not run against developer or production databases without explicit authorization.
3. The proposed SAFETY-08 workflow now exposes a staff-only, paginated **read-only inventory** at `GET /api/v1/moderation/legacy`. An authenticated, provisioned moderator can manually select an individual Quest through `POST /api/v1/moderation/legacy/{id}/intake`. This records `PENDING_REVIEW` and immutable intake provenance/audit. The Quest remains publicly hidden. **There is no bulk backfill and no automatic approval.** Snapshot current public visibility metrics first.
4. The moderator decision API now permits `APPROVE` and `REJECT` for an already `PUBLISHED` Quest **only** when it has a `PENDING_REVIEW` record and immutable `legacy_moderation_intake` provenance. Rejection sets moderation to `HIDDEN` but leaves Quest lifecycle and XP untouched. The operation logs a separate decision audit record; approval restores public discovery. The code and isolated-PostgreSQL tests are **written but not run**. Do not deploy without actual verification.
5. Review legacy Quests against the policy. Approved Quests may regain public visibility after a documented, audited decision. Rejected/harmful Quests remain invisible, with a creator explanation and appropriate appeal path.
6. Roll out behind a reversible deployment flag or controlled release stage only after the new review UI, moderation staffing, creator notification and compatibility tests are ready. A feature flag alone does not make older already-published content safe.
7. Monitor public Quest counts, report volume, moderation backlog, 404/409 rates and Join failures. Document rollback boundaries, schema forward compatibility and how to restore previous functionality without exposing unsafe public Quests.

## Additional blocking decisions

- Whether and how to suspend completion for already-enrolled participants in a severely harmful hidden Quest, without deleting immutable XP history.
- Whether old archived Quests need a review only if restored; current Quest lifecycle forbids restoration.
- Appeal handling for *hidden PUBLISHED* Quests is not implemented. SAFETY-07 currently supports only rejected DRAFT appeals.
- How to distinguish normal moderator rejection from urgent safety response, and how to communicate user-visible reasons safely.
- Whether to cap creator resubmissions after rejection. Report/Quest creation budgets do not yet cap repeated `submit-review` operations.

## Release rule

**Do not merge or deploy M3 as a public-safety guarantee** until owner-approved rollout, actual backend/PostgreSQL/frontend/browser tests, review of moderator authorization, and full M2 integration. None of these deployment steps has been executed here.
