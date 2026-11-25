# SAFETY-04 — Reporting and moderator workflow

**Issue:** [#87](https://github.com/khyathiPriya6/sidequest/issues/87)  
**Depends on:** SAFETY-02 #83, SAFETY-03 #85 and contract #78.

## Added

- **Private reports**: authenticated users can send a reason-coded report for a currently approved published Quest. `(quest_id, reporter_id)` is unique, so repeat reports return the same receipt and do not increase report counts. Creators cannot report their own Quest. Reports are not exposed to Quest owners or public APIs.
- **Review queue**: `GET /api/v1/moderation/queue` returns pending quests and approved published quests with reports, with title/description, reason, version and aggregate report count.
- **Moderator decisions**: `POST /api/v1/moderation/quests/{id}/decisions` accepts APPROVE/REJECT/HIDE; only a staff member pre-provisioned in the `moderation_staff` table can see the queue or decide. Requests require session and CSRF, and reject stale `expectedVersion`.
- **Transactional audit**: each successful decision changes the Quest moderation state and inserts a `moderation_audit` entry in the same transaction. A Quest row lock serializes decisions against publish/archive/new Join.
- **New Liquibase migration 009** creates staff allowlist, reports, audit. Does not modify existing Quest/XP data. Never seed staff by signup or from a public request.
- **UI**: report action on Quest Detail and `/moderation` dashboard with full Quest text, review choices, reason selector, confirmation and retry/reload. The route requires login, while server access additionally requires explicit staff membership.
- **Tests written**: isolated PostgreSQL HTTP flow exercises staff/nonstaff access, approval, duplicate reporting, hiding, public concealment and audit. Existing Liquibase repeatability test updated to 9 changesets.

## Moderator provisioning

No moderator is automatically assigned. A trusted operator with secured database admin access must verify the intended account and grant membership **out of band**, e.g. after review through a controlled data-change process:

```sql
-- Administrative-only operation: never expose this as a public API.
INSERT INTO moderation_staff (user_id) VALUES (:verified_moderator_uuid);
```

Never run against a developer/production database without a reviewed migration/authorization. For production, require a revoke runbook, restricted access to staff list, periodic review, and a separation-of-duties process. Staff checks are authoritative database lookups at every moderation operation, not frontend roles.

## Verification commands (NOT EXECUTED)

```bash
cd ui && npm ci && npm test && npm run build
cd ../
mvn -f backend/pom.xml test
JAVA_HOME=/path/to/java21 PG_BIN=/path/to/postgres/bin bash backend/scripts/test-postgres.sh
```

These commands have **not** run in this chat environment; no passing counts can be claimed. The existing PostgreSQL scripts create a fresh isolated database. No source branch was merged or deployed, and no existing database was modified.

## Release blockers / deliberate limitations

1. **SAFETY-02 and SAFETY-03 are draft, unmerged**. This PR is stacked on SAFETY-03, and its gate still requires creator-facing pending/review UX, plus a legacy-Quest policy. The current frontend publish flow expects immediate publishing.
2. **No rate-limits or abuse detection** beyond report deduplication. Prevent account/quest report flooding, bot submissions and coordinated mass-reporting before public use.
3. Human moderation needs a staffed operational policy and **actual decisions**; screening keywords alone do not approve any Quest or prove safety.
4. Reviewer dashboard currently lacks a secure audit-history browser, escalation workflows, appeals, notification integration and report adjudication status. Hiding a reported approved Quest is supported; resolving a baseless report without hiding is not yet supported.
5. For dangerous content hidden after enrollment, E04 still permits existing participants to complete. Review severe-harm safety holds before public deployment.
6. Verify report reason schema, CSRF errors, ETag/expectedVersion races, accessibility and full test suite in a live isolated environment.
7. Test and reconcile with the unmerged E04-09 frontend PR #76, which also edits Quest details and routes. Do not merge stacked UI changes without conflict resolution.

## Teach-back

The browser cannot decide who is a moderator. Users log in normally, but only accounts explicitly authorized in `moderation_staff` can see the review queue or write moderation decisions. Reports are private signals, not automatic proof of wrongdoing.

**Questions:** Why does report deduplication not replace rate-limiting? Why must an APPROVE decision and its audit event commit atomically? Why is the moderator allowlist kept separate from normal account registration?
