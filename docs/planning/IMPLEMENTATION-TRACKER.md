# Authentication and Quest runtime implementation

Updated 2026-11-18 against `origin/main` at `d0e57806417d91819b431bf7cc1984a3a2501753`. No existing developer database was migrated. The pre-existing untracked `ui/test-results/` directory was preserved.

| Task / issue | Actual repository state | Remaining acceptance work |
|---|---|---|
| Auth architecture #39 / PR #40 | Merged into `main` | None for the approved architecture. |
| Frontend foundation #36 / PR #37 | Merged into `main` | The foundation is present; this does not complete UX-001–006 or auth UI #44. |
| User/session persistence #41 / PR #45 | Merged into `main` at `d0e5780` | Schema migration runs only after a target has been explicitly approved. |
| Session security #42 / PR #46 | GitHub marks merged, but PR base was `feat/e03-user-persistence`; its runtime code is not in current `main`. | Integrate the security changes into `main` before relying on them there. |
| Authentication APIs #43 / PR #47 | GitHub marks merged, but PR base was `feat/e03-session-security`; its runtime code is not in current `main`. | Integrate the API changes into `main` before frontend integration. |
| Authentication UI #44 | Not started | Depends on #43 runtime being available on the target branch and the registration design/UX handoff. No production auth UI exists. |
| SQ-009 #20 | PR #48 open for review on `feat/sq-009-quest-persistence` | Migration/entity/repository implemented and validated against isolated PostgreSQL. No Quest APIs or UI are part of this issue. |
| SQ-010–013 #21–24 | Not started | Depend on Quest persistence; protected API work also needs auth runtime available on `main`. The existing UX handoff gate applies to Quest CRUD/product UI. |
| SQ-014–017 #25–28 | Not started | Depend on their APIs and, for authenticated views, auth UI. UX-001–006 sign-off remains outstanding in GitHub. |
| SQ-018 #29 | Not started | Depends on #20–28 and the real browser auth flow; no Quest E2E journey has run. |

## Approval and handoff evidence

- The owner authorized migrations only on a newly created isolated PostgreSQL test database in [issue #41's approval comment](https://github.com/khyathiPriya6/sidequest/issues/41#issuecomment-6968296428). No existing database may be migrated under that approval.
- PRs #40, #37, and #45 are merged into `main`. PRs #46 and #47 are marked merged by GitHub but target the preceding feature branches, not `main`; their runtime code is absent from `d0e5780`.
- UX issues #30–#35 remain open. The username requirement was added to #31, but the repository does not contain owner sign-off of the updated registration design. UX-006 #35 has no sign-off comment, and `docs/design/PROTOTYPE-REVIEW.md` still lists owner testing and acceptance as incomplete.
- The Quest database-only SQ-009 work does not add API endpoints or product UI. The existing UX-001–006 gate remains in force before Quest CRUD/product UI work; no additional design approval is imposed for this storage-only issue.
- The current task calls the linked Figma file approved, but repository handoff status is still pending. Record any new owner sign-off in the design issues before starting gated UI work.

## Validation evidence

Commands executed from the repository root on Java 21.0.13:

- `cd ui && npm test` — **passed**, 31 tests across 5 files.
- `cd ui && npm run build` — **passed**, TypeScript and Vite production build.
- `cd backend && JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash scripts/test-postgres.sh` — **passed** on SQ-009 branch; 13 tests, zero failures/errors/skips. It initialized and stopped a fresh isolated UTF-8 PostgreSQL 18.4 cluster.
- Liquibase first run applied four changesets; the repeat run applied zero. The test URL was constrained to `127.0.0.1` and `sidequest_isolated_test`.
- `git diff --check` — passed for the SQ-009 patch at the time checked.
- No auth UI, Quest HTTP API, or full Quest browser journey was implemented or tested. The currently fetched `main` does not contain PR #46/#47 runtime code.

Learning notes: [identity persistence](../implementation/IDENTITY-PERSISTENCE.md), [Quest persistence](../implementation/QUEST-PERSISTENCE.md).
