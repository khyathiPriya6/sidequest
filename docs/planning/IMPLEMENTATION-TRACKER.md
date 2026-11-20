# Authentication and Quest implementation tracker

Updated 2026-11-19. Java 21 retained. No existing database migrated. Current main: `4f6b0867db27644e0217a486e79c64d1d2debe56`. Candidate: `fix/pretesting-stack-integration`, issue [#56](https://github.com/khyathiPriya6/sidequest/issues/56). Integration commit: `e9a1d34cbafec3599e87299c544bf94711e585c2`. Integration PR: pending creation. No owner merge has been performed.

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
| Pre-testing remediation #56 | fix/pretesting-stack-integration; PR pending creation | Requested findings resolved; 9 backend unit + 26 PG integration + 100 UI + 11 browser tests pass. Review open hardening findings and approve integration PR. |
| SQ-018 #29 | Remains open; evidence in #56 | Actual full-stack journey passes. Next: owner acceptance against integration candidate, resolve/accept documented follow-ups, approve merge. Not marked complete. |

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

Integration order: Join → history #61 → completion #62 → progression contract #63 → profile #64 after explicit contract approval. Each story gets its own PR; dependent PRs target the predecessor branch, then must be retargeted to main after predecessor merge. Owner merges only. SQ-018 manual acceptance is recorded; formal closure and frontend design sign-off remain outstanding.

## E04-04 history — #61

Implemented owner-scoped list/detail, status+questId filters, deterministic pagination, archived summaries, ledger-derived awards and bounded projection queries. Branch `feat/e04-04-participation-history` targets Join branch `feat/e04-03-join-quest` ([#66](https://github.com/khyathiPriya6/sidequest/pull/66)); retarget to main after #66 merges. Java 21 compilation passed; 17 unit + 42 integration tests passed, zero failures/errors/skips. [Exact evidence and learning notes](../implementation/PARTICIPATION-HISTORY.md). No migration or frontend changes; self-review complete, owner review pending.

## E04-05 completion and rewards — #62

Implemented owner-scoped POST complete with CSRF/bodyless checks, pessimistic locking, status+ledger atomic transaction, original reward/timestamps, repeat semantics, archived eligibility, and fail-closed ledger consistency. Branch `feat/e04-05-completion-rewards` targets history branch `feat/e04-04-participation-history` (#67), itself dependent on Join #66; retarget after merges in that order. Java 21: 17 unit + 47 integration tests passed, no failures/errors/skips; exact tests and rollback/concurrency evidence in [completion notes](../implementation/PARTICIPATION-COMPLETION.md). No migration or mutable balance. Owner review pending.

## E04-06 progression and achievement proposal — #63

Prepared owner-approval contract on `docs/e04-06-progression-contract`, stacked after completion PR #68. Proposes ledger-derived long XP totals and levels; nine deterministic achievements with stable IDs/progress/unlock timestamps; recommends deriving, not persisting, achievement unlocks for MVP; specifies proposed private profile/history/catalogue APIs for #64. **No progression runtime or profile code implemented.** Owner approval is required before #64. Review and acceptance checklist: [PROGRESSION-CONTRACT.md](../architecture/PROGRESSION-CONTRACT.md). PR targets `feat/e04-05-completion-rewards`; merge order #66 → #67 → #68 → contract PR, retargeting each dependent PR after its predecessor is merged. Backend-only implementation authorization is recorded; frontend design approval and #35 criteria remain separate.

## E04-07 Player Profile — #64

Owner approval of E04-06 is evidenced by merged PR #69 (2026-11-20); mainline currently has only Join #66 because #67–69 were merged into predecessor branches. E04-07 branch `feat/e04-07-player-profile` is based on `feat/e04-05-completion-rewards` to include the approved contract and all dependencies. Implemented profile, XP history and derived achievement APIs; no progression state/migrations stored. Java 21 full isolated suite: 20 unit + 50 integration pass, zero failure/error/skip; seven migrations fresh, repeat applies zero. Exact validation and corrected initial failures are in [PLAYER-PROFILE.md](../implementation/PLAYER-PROFILE.md). PR target must preserve the dependency chain, with an explicit remaining path into main. Owner review/merge pending.
