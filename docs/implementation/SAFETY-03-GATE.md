# SAFETY-03 — approval-gated publishing and discovery (draft)

**Issue:** [#85](https://github.com/khyathiPriya6/sidequest/issues/85)  
**Depends on:** SAFETY-02 [#83](https://github.com/khyathiPriya6/sidequest/issues/83) / draft PR #84 and contract draft PR #82.

## Implemented backend changes

1. New Quest drafts automatically get internal `UNREVIEWED` moderation records. Existing Quests without a moderation record are **not** implicitly approved.
2. Creators can submit complete drafts to review via `POST /api/v1/quests/{id}/submit-review`, with session, CSRF and strong `If-Match`. The response is `202` plus a coarse `PENDING_REVIEW` status; repeat pending requests return the same state.
3. Deterministic `QuestSafetyScreen` flags potential problems for human triage, but **never automatically approves** or permanently rejects solely from keywords.
4. Creators can inspect their own status via `GET /api/v1/me/quests/{id}/moderation`, with anonymous and non-owner access denied/concealed.
5. `POST /api/v1/quests/{id}/publish` now requires `APPROVED` moderation status and otherwise returns `409 moderation_not_approved`. Drafts stay private until an approved publish.
6. Public search (including its count query), public detail and new Join require explicit `APPROVED`, not merely Quest `PUBLISHED`. Existing participant records remain accessible and already enrolled players may complete under current E04 rules.
7. Editing an owned draft invalidates its previous review. Publication/submit/patch serialize on the existing Quest row lock; Join uses that same lock.
8. The Quest OpenAPI contract includes the new API endpoints and moderation gate. Existing Quest and participation API fixture tests have been adapted to explicitly submit and approve their safe test Quests via the repository; **this is test-only approval, not a public admin bypass**.
9. A real HTTP+PostgreSQL integration test covers authorization, CSRF, pending/approved publishing, public visibility and counting, joining, hiding, and archived/existing participant behavior.

## Known limitations — blocking merge / release

- There is **no moderator-authenticated review endpoint, queue, audit trail, or creator-facing review UI** yet. Review approval is only possible through internal application logic/test fixtures. Therefore normal users cannot get a new Quest approved. This PR must remain **draft/unmerged** until SAFETY-04 provides a protected, auditable approval workflow and the web UX is updated.
- This intentionally changes the old synchronous publish contract. The current React creator UI assumes publication is immediate. Before merge, implement the pending-review state, rejection/hide messaging, and a safe creator-side approval completion workflow.
- Legacy published Quests with no moderation record disappear from public search/detail and cannot gain new participants if the gate is enabled. This is **fail closed** and needs a reviewed migration/backfill rollout plan, metrics, and operator procedure before release.
- Policy keyword patterns are limited and can miss harms or flag benign educational content. Human review and user reports are necessary. No AI, provider or paid service is included.
- For a Quest hidden for serious harm, E04 presently permits an existing participant to complete and receive XP. Whether urgent safety holds must block further completion/XP requires explicit policy and implementation, preserving ledger immutability.
- No moderation action audit trail or moderator role exists in this slice. A production moderator action must never be offered without backend role checks and append-only audit.
- Repository feature PRs #76/#77 are also unverified and unmerged. Do not deploy a staged moderation branch to public traffic.

## Verification

Test code has been written but **no build, Java tests, PostgreSQL integration tests, Playwright, or deployments were executed** in this chat session because there is no project checkout or dependencies available. Run on Java 21, Maven 3.9+, and isolated PostgreSQL using:

```sh
mvn -f backend/pom.xml test
JAVA_HOME=/path/to/jdk21 PG_BIN=/path/to/postgresql/bin bash backend/scripts/test-postgres.sh
```

The project test script provisions its own newly created PostgreSQL test cluster. Do not point it at a developer or production DB. Verify all the existing Quest/participation/profile tests, migration repeatability, search/filter ordering, concurrent publish/archive/join and CSRF/ownership boundaries. Fix test regressions before review; do not report pass counts without execution.

## Review/merge order

1. Review contract PR #82 (document-only).
2. Verify and merge SAFETY-02 PR #84.
3. Implement SAFETY-04 (moderator queue/role/audit), creator pending-review UX, and explicit legacy content migration/runbook.
4. Execute full backend/frontend/Playwright regression and review SAFETY-03 PR.
5. Retarget SAFETY-03 to `main` after its SAFETY-02 base merges; merge only after owner approval.

## Teach-back

The existing Quest status controls the creator lifecycle, while moderation status controls safety clearance. A Quest is visible to strangers only when both `PUBLISHED` and `APPROVED`. The new submit endpoint records a review request; it does **not** grant publication rights.

1. Why must public catalogue counts and private details use the same approval predicate as public search?
2. Why is it safer to return `PENDING_REVIEW` for weak keyword matching than to approve automatically?
3. Why must a Join transaction check moderation status after taking the same Quest lock as archive/publish?
