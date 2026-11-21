# Authentication and Quest implementation tracker

Updated 2026-11-21. Java 21 retained. No existing database migrated. Current remote main: `59727c489d0e6a9e245b13689ae44997845dcbcd`. E04-04 through E04-07 integration candidate: `feat/e04-backend-main`, merging feature stack `f4a340e88bf88e6cc8ef98767175ef3d235b5267` into main. [Integration PR #71](https://github.com/khyathiPriya6/sidequest/pull/71) is open and unmerged. No owner merge has been performed.

## Current implementation status

| Task / issues | Branch / PR evidence | Dependencies / acceptance status / next action |
|---|---|---|
| Auth architecture #39, foundation #36 | PRs #40/#37 merged to main | Approved architecture and existing components retained. |
| User/session persistence #41 | PR #45 on main | Isolated PostgreSQL constraints, timestamps and repeated migrations verified. |
| Session security #42 | Original #46; integration #49 on main | Secure session/CSRF/error behavior covered by integration tests. |
| Registration/login APIs #43 | #47/#50 merged into feature stack; now in #56 candidate | Depends on persistence/security. Register/login/me/logout and real cookie journey pass; owner mainline review pending. |
| Authentication UI #44 | #51 merged into feature stack; now in #56 candidate | Depends on auth APIs. Real session restoration/expiration/CSRF/logout verified; design handoff remains visible below. |
| Quest persistence SQ-009 #20 | #48 merged to main | User FK, indexes, lifecycle and optimistic version model retained/tested. |
| Quest draft/lifecycle/public/search APIs SQ-010–013 #21–24 | Original feat/sq-010-draft-api through feat/sq-013-search-filter included in reviewed #55 stack; now #56 candidate | Depends on identity/security/persistence. Ownership, validation, lifecycle, pagination, search and concurrency suites pass. |
| Public and creator UI SQ-014–017 #25–28 | #52–55 feature-stack merges; now #56 candidate | Depends on API/auth UI. Details routing, saved draft publication, strict ETags, expiration and navigation recovery corrected and tested. |
| E04-08 UI/UX handoff / UX-006 #35 | [PR #72](https://github.com/khyathiPriya6/sidequest/pull/72); [E04-08 participation handoff](../design/E04-08-PARTICIPATION-HANDOFF.md) | Figma MCP Starter-plan limit prevented canvas edits. Handoff is proposed only; owner review/design sign-off and all existing #35 acceptance criteria remain outstanding. Frontend stays gated. |
| Pre-testing remediation #56 | fix/pretesting-stack-integration; PR pending creation | Requested findings resolved; 9 backend unit + 26 PG integration + 100 UI + 11 browser tests pass. Review open hardening findings and approve integration PR. |
| SQ-018 #29 | Remains open; owner manual acceptance comment and test evidence in #56 | Owner confirmed manual Auth/Quest Management acceptance; this is not formal issue closure. Owner still needs to review evidence against the issue's two-user/anonymous draft and publish/archive scenarios, validation/stale/network regressions, exact environment/results/failures/gaps and documentation/teach-back; then approve the integration merge. Issue criteria remain unchecked. |

## Acceptance and approval evidence

- The owner authorized stacked PRs, not owner merges. Earlier PRs marked merged did not necessarily reach main; [review report](../reviews/PRETESTING-REVIEW.md) records exact targets.
- Migrations were authorized only for newly created isolated PostgreSQL test databases ([approval](https://github.com/khyathiPriya6/sidequest/issues/41#issuecomment-6968296428)). This scope was preserved.
- The owner approved the gaming visual direction and minimal authentication field/error additions. UX-001–006 handoff/prototype checklists still require documented sign-off; Figma pixel parity and manual assistive-technology acceptance are not claimed.
- Remaining risks: Quest request-body byte limit, Java dependency scanning, deployment-specific HTTPS/proxy/throttling/database controls. No cloud deployment or new product feature introduced.

## Validation and learning notes

Exact commands, counts, initial failures subsequently fixed, file/line findings and remaining acceptance gates are in [PRETESTING-REVIEW.md](../reviews/PRETESTING-REVIEW.md). Current results supersede earlier branch-only evidence: 100 UI tests; TypeScript/Vite build; 9 unit and 26 isolated PostgreSQL tests; 11 Playwright tests including the real API journey, all passing. npm audit reported zero vulnerabilities; no standalone lint task or Java vulnerability scanner was run.

Learning notes: [identity persistence](../implementation/IDENTITY-PERSISTENCE.md), [session security](../implementation/SESSION-SECURITY.md), [authentication API](../implementation/AUTHENTICATION-API.md), [Quest persistence](../implementation/QUEST-PERSISTENCE.md), [draft API](../implementation/QUEST-DRAFT-API.md), [lifecycle API](../implementation/QUEST-LIFECYCLE-API.md), [public catalogue](../implementation/QUEST-PUBLIC-CATALOGUE.md), [search API](../implementation/QUEST-SEARCH-API.md), [public UI](../implementation/QUEST-PUBLIC-UI.md), and [review teach-back and three interview questions](../reviews/PRETESTING-REVIEW.md#teach-back).

## E04-02 participation persistence remediation — #59 (2026-11-19)

Existing branch: `feat/e04-02-participation-persistence`; [PR #60](https://github.com/khyathiPriya6/sidequest/pull/60) targets main. Removed duplicate test helpers; corrected transaction visibility and exact PostgreSQL diagnostics; enforced published/noncreator enrollment; added owner-scoped participation and Quest locking primitives; added corrective migration 007 for immutable completion and trigger diagnostics. Existing applied changesets and Quest editing restrictions remain unchanged. No Join/Complete services or subsequent features implemented.

Java 21.0.13.1 / Maven 3.9.16 / PostgreSQL 18.4: test compilation passed; **17 unit + 35 integration tests passed, zero failures/errors/skips**. All existing backend regressions included. Fresh migrations apply seven changesets; repeat applies zero. Exact commands, initial failures, limitations, rollback considerations and three interview questions: [participation persistence notes](../implementation/PARTICIPATION-PERSISTENCE.md).

Prerequisite correction to the earlier tracker: [SQ-018 owner manual acceptance is recorded](https://github.com/khyathiPriya6/sidequest/issues/29#issuecomment-7001712986); formal evidence/closure remains outstanding. UX-006 #35 remains open without recorded sign-off, including UX-001–005 handoff dependencies. Do not infer completed design handoff from existing implementation or merge badges. Remediation self-review complete; independent PR review and owner merge approval pending. No existing database migrated; no merge performed.

## E04 backend approval and Join — #65

Owner authorization (2026-11-19): backend may proceed without Figma handoff; frontend remains design-gated, #35 stays open. E04-01/02 are on main at `92af701`; E04-03 was missing and is now implemented on `feat/e04-03-join-quest` targeting main. POST Join uses shared Quest locking with archive and idempotent enrollment. Java 21: 17 unit + 39 isolated integration tests passed, no failures/errors/skips on final run; compilation and diff check passed. Initial pool-exhaustion errors and exact commands are in [Join notes](../implementation/PARTICIPATION-JOIN.md). No existing DB migrated.

Join #65 and the persistence base are on main. History #61, completion/rewards #62, the approved progression contract #63 and profile #64 are implemented in their focused PR stack; mainline integration is tracked above. The backend-only authorization is separate from frontend design approval. SQ-018 manual acceptance is recorded; formal closure and frontend design sign-off remain outstanding.

## E04-04 through E04-07 mainline integration — #61–64

The verified current main is `59727c4` and contains E04-03 Join (#66). PRs #67–70 merged into predecessor feature branches, not main. Integration branch `feat/e04-backend-main` merges latest main with `feat/e04-05-completion-rewards` at `f4a340e`; there were no file conflicts. This preserves the existing Join/archive shared Quest-row locking, owner-scoped history/completion queries, atomic completion+ledger transaction, repeat idempotency, reward snapshot, archived participant access, CSRF and deny-by-default routes, bigint profile aggregation and derived achievement event ordering. The progression contract approval is recorded in PR #69 and the contract. No subsequent service or frontend feature is implemented here.

macOS 26.7.3 aarch64 / Adoptium Java 21.0.13.1+2-LTS / Maven 3.9.16 / PostgreSQL 18.4: test compilation passed; final run had 20 unit tests and 50 isolated PostgreSQL integration tests passed, zero failures/errors/skips. All seven changesets applied to a fresh isolated database; repeated context initialization applied zero. Exact commands: `JAVA_HOME=/private/tmp/sidequest-java21/Contents/Home PATH=/private/tmp/sidequest-java21/Contents/Home/bin:$PATH mvn -f backend/pom.xml test-compile`, same environment with `mvn -f backend/pom.xml test`, and `JAVA_HOME=/private/tmp/sidequest-java21/Contents/Home PATH=/private/tmp/sidequest-java21/Contents/Home/bin:$PATH bash backend/scripts/test-postgres.sh`. The first sandboxed unit attempt ran 20 tests: 19 passed, 0 failed, 1 errored, 0 skipped because sandbox policy blocked Mockito/Byte Buddy agent attachment in `HealthControllerTest`; rerunning the same Maven unit command with process permission passed 20/20, zero failures/errors/skips. This was an execution restriction, not a code/test assertion failure. Existing developer database was not accessed. `git diff --check` passed. Join, history, completion, and profile implementation/rollback notes remain in the linked story documents above.

SQ-018 #29: manual acceptance is recorded, but formal evidence and closure remain outstanding. UX-006 #35 remains open until its actual handoff acceptance criteria and design approval are satisfied. Backend implementation authorization does not approve frontend design. E04 backend integration PR #71 is not merged.

## E04-05 completion and rewards — #62

Implemented owner-scoped POST complete with CSRF/bodyless checks, pessimistic locking, status+ledger atomic transaction, original reward/timestamps, repeat semantics, archived eligibility, and fail-closed ledger consistency. PR #68 is in the E04 stack. Exact tests and rollback/concurrency evidence are in [completion notes](../implementation/PARTICIPATION-COMPLETION.md). No mutable balance or migration was introduced.

## E04-06 progression and achievement proposal — #63

The proposed ledger-derived XP levels and nine deterministic achievements were approved in PR #69. See [PROGRESSION-CONTRACT.md](../architecture/PROGRESSION-CONTRACT.md). Runtime/profile implementation is in #64; backend implementation authorization does not satisfy frontend design approval or UX-006 #35.

## E04-07 Player Profile — #64

Owner approval of E04-06 is evidenced by merged PR #69 (2026-11-20). E04-07 profile, XP history and derived achievement APIs are implemented in [PR #70](https://github.com/khyathiPriya6/sidequest/pull/70); no progression state or migrations are stored. Mainline integration of #61–64 is tracked above. Exact validation and learning notes are in [PLAYER-PROFILE.md](../implementation/PLAYER-PROFILE.md).
