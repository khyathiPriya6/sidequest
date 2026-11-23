# M3 consolidated review — SAFETY-01 through SAFETY-05

**Canonical draft PR:** #88 against `main` (single review target; no code merged into main).  
**Covered:** Issue #78, SAFETY-02 #83, SAFETY-03 #85, SAFETY-04 #87, SAFETY-05 #89.

## Why consolidate?

This branch descends from the SAFETY-02 and SAFETY-03 branches, contains the SAFETY-04 implementation and copies in the SAFETY-01 architecture document. One PR to `main` therefore shows the integrated M3 changes instead of asking reviewers to reconcile several stacked PRs. **One larger PR is not inherently less risky**; thorough review and full regression remain mandatory. The superseded PRs are closed unmerged, not merged/deployed.

## SAFETY-05 additional changes

- `AccountAbuseQuota` performs transaction-scoped PostgreSQL advisory locking for each account and activity followed by a committed-row rolling-window count. The lock remains until the request's existing `@Transactional` commit, serializing same-account writers.
- Account limits: up to **40 new Quest drafts per rolling 24 hours**, **100 new Quest joins per rolling hour**, and **15 distinct reports per rolling 24 hours**. These are **initial policy defaults**, not proven ideal by usage data.
- Existing Join idempotency is checked before consuming a quota; duplicate existing reports are similarly exempt, including after acquiring the report's activity lock. Multiple reports from one account do not inflate the same Quest's report count.
- Exceeding quotas produces `429 rate_limited` with `Retry-After: 3600`. This conservative header is a retry hint, **not** a precise calculation of the rolling-window expiry.
- `quest_reports_reporter_created_idx` supports efficient report activity counting.
- Isolated PostgreSQL integration-test source covers boundary conditions and account separation. **Not executed**.

## SAFETY-06 — creator review workflow (Issue #90)

- The web editor now loads the owner-only moderation state alongside the owned Quest, shows `UNREVIEWED`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `UNDER_REVIEW`, and `HIDDEN` status to the creator, and explains the private pending period.
- Only a saved draft can be submitted using `POST /api/v1/quests/{id}/submit-review` with the existing strong Quest ETag and session-bound CSRF token. A pending review does not automatically publish or make the Quest public.
- The Publish button is unavailable unless the server reports `APPROVED`; publishing still uses the authenticated server endpoint and its own authorization checks. Editor changes reset review and require re-submission.
- My Quests now links to the review-aware editor instead of offering an immediate Publish button.
- Existing frontend unit test source updated to exercise review submission, stale ETag, and approved publishing paths. **Tests not executed**.
- UI cannot recover from a rejected Quest merely by auto-publishing; the creator must edit and re-submit. Full appeals, moderator comment explanations, and report resolution remain separate work.
- The E04 frontend PR #76 also changes routes and Quest details; resolve and retest any overlapping modules before release.

## SAFETY-07 — appeals and report disposition (Issue #91)

- New append-only Liquibase changeset `010-moderation-appeals.sql` adds creator appeals and lifecycle fields for report resolutions. Previously recorded reports become `OPEN` without losing evidence.
- Rejected DRAFT creators can submit one reasoned appeal per rejection version with session, CSRF, ownership and optimistic-version checks. The Quest becomes `UNDER_REVIEW`, remains private and cannot be edited until the appeal is resolved.
- The staff queue displays the appeal explanation; authorized moderators can APPROVE or REJECT an appeal, updating the appeal record and adding a transactional moderation audit entry.
- Staff can dismiss unresolved reports without hiding the Quest, providing a coded decision rationale in the audit. Hiding a Quest marks its open reports `ACTIONED`. Report counts and queues consider only `OPEN` reports.
- Quest row locks serialize new reports against moderator hide/dismiss transitions. Repeated reports retain the same receipt but cannot create a second report for the same reporter/Quest after disposition.
- Frontend creator appeal form and moderator dismissal action implemented with authorization-safe API calls and deliberate no-auto-retry. Backend HTTP/PostgreSQL and UI test source added; **tests have not been executed**.
- **Excluded:** appeals of already published hidden Quests, independent appeal panels, notifications, text/media AI screening, and repeated report submission by the same account after dismissal.
- Legacy published Quest approval needs separate documented rollout in `docs/implementation/M3-LEGACY-ROLLOUT.md`; current staff APPROVE endpoint is draft-only.

## Limitations and release blockers

1. **No automated builds or regression executed.** Tests are code only. Run frontend Vitest/TypeScript build, Java units, isolated PostgreSQL, Playwright full-stack/security tests. Fix failures before review/merge.
2. **No production migration or deployment performed.** Liquibase 008/009 are new proposed schema changes. Legacy published Quests have no safety record and will disappear when the SAFETY-03 gate is activated. Owner-approved backfill, review inventory, staging, and rollback runbook are required.
3. **Creator workflow code is drafted** for submission, pending/approved state, and approve-only publishing. It is untested, needs accessibility review and must be reconciled with E04 #76. Full appeals and detailed reasons remain outstanding.
4. **E04 frontend is still a separate draft PR (#76)**; its Quest detail and router edits overlap with M3. Resolve conflicts rather than quietly overwriting E04 Join/Complete.
5. **Moderator provisioning** is out-of-band via restricted `moderation_staff` table. Need operational secure bootstrap/revoke, a staffed review process and scoped access to private evidence.
6. **Appeals and report resolution are now drafted**, including rejection appeals, staff decisions and report dismissal. They remain untested; hidden published appeals, reporter notifications, independent escalation and reviewer history UI remain outstanding.
7. **Abuse quotas are per-account only**, not per-IP or device; fresh accounts and coordinated activity can evade them. Account limits may disadvantage legitimate heavy users. Improve observability, policy tuning, and risk scoring without excessive fingerprinting.
8. No mature AI classifier or image moderation. The current deterministic screen only flags some risky text. `NO_SIGNAL` **never grants automatic approval**.
9. **Serious-harm participant completion policy remains undecided**; current already-joined player can still complete a hidden Quest and earn ledger XP. Evaluate a separate safety hold with clear criteria and historical XP preservation.

## Operational validation checklist

- [ ] Review API/SQL static correctness, migration consistency and auth/CSRF
- [ ] Run full backend isolated PostgreSQL suite and compile all Java
- [ ] Run frontend tests and production build
- [ ] Reconcile E04 Quest detail and router work
- [ ] Run browser Join/Complete + moderation/report E2E
- [ ] Verify report and moderation concurrency, quotas, idempotency and authorization
- [ ] Choose legacy-data transition and run it on disposable staging first
- [ ] Document moderator account provisioning/revocation and policy escalation
- [ ] Approve M3 contract, complete pending creator UI and review-appeal handling
- [ ] Review/merge **only after passing evidence**, never automatically

## Commands (not executed here)

```bash
mvn -f backend/pom.xml test
JAVA_HOME=/path/to/jdk21 PG_BIN=/path/to/postgres/bin bash backend/scripts/test-postgres.sh
cd ui && npm ci && npm test && npm run build
```

Testing has not been run in this chat session. Do not turn draft PR #88 into ready-to-merge based on documentation alone.
