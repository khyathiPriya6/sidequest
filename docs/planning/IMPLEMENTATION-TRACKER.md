# Authentication and Quest runtime implementation

Updated 2026-11-18. Owner has authorized stacked backend PRs; no agent merges.
No existing developer database has been migrated. The untracked `ui/test-results/` belongs to earlier work.

| Task / issue | Status / branch / PR | Dependencies and acceptance criteria | Tests / next action |
|---|---|---|---|
| User and session persistence #41 | Review pending; `feat/e03-user-persistence`; [PR #45](https://github.com/khyathiPriya6/sidequest/pull/45) | PR #40 merged. UUID users, canonical unique identities, encoded hashes, status/timestamps, JDBC session schema, isolated PostgreSQL verification | Java 21 `bash scripts/test-postgres.sh`: 1 unit + 3 integration tests passed. Owner review/merge pending. |
| Security #42 | Review pending; `feat/e03-session-security`; [PR #46](https://github.com/khyathiPriya6/sidequest/pull/46), base #45 | #41; deny-default filter chain, Argon2id, JDBC sessions, cookies, CSRF, timeouts, account revocation, JSON errors | Java 21 isolated suite: 2 unit + 7 integration tests passed. Quest private-404 service verification deferred to Quest implementation. |
| Authentication APIs #43 | Implemented; `feat/e03-authentication-api`; stacked PR pending | #42; registration/login/logout/me/CSRF, validation, generic conflicts/failures, bounded throttling | 5 unit + 12 integration tests passed, including actual HTTP registration/login/logout. Owner review pending. |
| Authentication UI #44 | Blocked | #43; **PR #37 merge and owner-approved username/error registration design** | Session restoration, safe redirects, actual backend integration and browser tests remain. |
| SQ-009 #20 | Blocked | Identity foundation and **UX-001–006 signoff** | Quest persistence, constraints, optimistic locking, PostgreSQL tests remain. |
| #21 Draft APIs | Blocked; no branch/PR | #19, #20, E03; **UX-006 #35 signoff** | Create/edit, owner listings/details, draft validation and ETags. Tests not run; begin only after dependencies/gate resolve. |
| #22 Lifecycle APIs | Blocked; no branch/PR | #21, E03; **UX-006 #35 signoff** | Publish/archive ownership, validation, idempotency and concurrency. Tests not run; begin only after dependencies/gate resolve. |
| #23 Public reads | Blocked; no branch/PR | #19, #20; **UX-006 #35 signoff** | Published-only list/detail, visibility, bounded stable pagination. Tests not run; begin only after dependencies/gate resolve. |
| #24 Search/filter | Blocked; no branch/PR | #23; **UX-006 #35 signoff** | Parameterized literal search, category/difficulty, consistent counts. Tests not run; begin only after dependencies/gate resolve. |
| #25 Explore/details UI | Blocked; no branch/PR | #23, #24, #37; **UX-006 #35 signoff** | Real data, URL filters/page, stale-request handling, responsive accessible states. Tests not run; begin only after dependencies/gate resolve. |
| #26 Creator dashboard | Blocked; no branch/PR | #21, #25, #44; **UX-006 #35 signoff** | Owned quests, status filters, expired sessions. Tests not run; begin only after dependencies/gate resolve. |
| #27 Draft forms | Blocked; no branch/PR | #21, #26, #44; **UX-006 #35 signoff** | Create/edit, preserved input, field errors, stale updates. Tests not run; begin only after dependencies/gate resolve. |
| #28 Lifecycle UI | Blocked; no branch/PR | #22, #26, #27; **UX-006 #35 signoff** | Preview/publish, archive confirmation, refresh state. Tests not run; begin only after dependencies/gate resolve. |
| #29 Quest E2E | Blocked; no branch/PR | #19–28, #44; **UX-006 #35 signoff** | Two users plus anonymous lifecycle journey, isolation, stale updates, accessibility and evidence. Tests not run; begin only after dependencies/gate resolve. |

## Approval and handoff gates

- #41–43 may be prepared as one-issue stacked PRs. Parent review/merge is required before merging its child; only the owner merges.
- #37 is open/draft. Foundation availability does not mean it is merged or that prototype review is complete.
- #35 prototype review/handoff remains open. Do not treat written references as approved Figma frames.
- AUTH-CONTRACT requires username/error registration design approval before #44.
- Existing database migrations need explicit approval, backup and reviewed target. Test runner provisions a new cluster only.

## Actual verification

- Initial sandboxed `JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home mvn -q test` failed because Mockito could not attach to the JVM; no passing result claimed.
- Approved isolated run: `JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home bash scripts/test-postgres.sh`, backend directory: **BUILD SUCCESS**, 1 health test and 3 persistence integration tests, zero failures/errors/skips. PostgreSQL 18.4; Java 21.0.13. Temporary cluster stopped by EXIT trap.
- Schema application: three changesets (including existing placeholder); second Liquibase execution applied zero changesets.
- Security runtime suite passed (2 unit + 7 integration tests); authentication endpoints now verified below; Quest E2E remains gated. Initial #42 test compile/generic injection failures were fixed before the passing run.

Learning notes: [identity persistence](../implementation/IDENTITY-PERSISTENCE.md).

Security learning notes: [session security](../implementation/SESSION-SECURITY.md).

- Final #43 run of the same isolated command: **BUILD SUCCESS, 5 unit + 12 integration tests**, zero failures/errors/skips; includes an actual random-port HTTP server and PostgreSQL-backed cookie session journey.
- OpenAPI YAML parsed with Ruby Psych: 3.1.0, five paths. This is syntax verification, not a full OpenAPI conformance validator. Python YAML parsing was unavailable (PyYAML is not installed); no dependency was installed solely for documentation.
- Current Argon2id local measurement: five encode+verify pairs averaged 129.8 ms; not a production/hash-only latency claim.

Authentication learning notes and usage: [authentication API](../implementation/AUTHENTICATION-API.md); [OpenAPI](../api/auth.openapi.yaml).

## Remaining issues

No tasks are marked owner-complete: PRs require owner review/merge. #42 private Quest concealment verification is deferred to Quest service tests. No new issues were necessary: #41–44 and #20–29 already have acceptance criteria/dependencies. Some older Quest issue prose differs from the merged contract (owner-detail route and description/requirements); follow QUEST-CONTRACT.md as requested when those stories are unblocked. Frontend source has not been changed and no frontend tests or Quest journey have run in this implementation.
