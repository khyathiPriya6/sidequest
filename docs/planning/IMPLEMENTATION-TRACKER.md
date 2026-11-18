# Authentication and Quest runtime implementation

Updated 2026-11-18. Owner has authorized stacked backend PRs; no agent merges.
No existing developer database has been migrated. The untracked `ui/test-results/` belongs to earlier work.

| Task / issue | Status / branch / PR | Dependencies and acceptance criteria | Tests / next action |
|---|---|---|---|
| User and session persistence #41 | Implemented locally; `feat/e03-user-persistence`; PR pending | PR #40 merged. UUID users, canonical unique identities, encoded hashes, status/timestamps, JDBC session schema, isolated PostgreSQL verification | Java 21 `bash scripts/test-postgres.sh`: 1 unit + 3 integration tests passed. Review diff and open PR. |
| Security #42 | Pending | #41; deny-default filter chain, Argon2id, JDBC sessions, cookies, CSRF, timeouts, account revocation, JSON errors | Next backend stack branch after #41 PR. |
| Authentication APIs #43 | Pending | #42; registration/login/logout/me/CSRF, validation, generic conflicts/failures, bounded throttling | Stack after #42; full PostgreSQL HTTP/security verification required. |
| Authentication UI #44 | Blocked | #43; **PR #37 merge and owner-approved username/error registration design** | Session restoration, safe redirects, actual backend integration and browser tests remain. |
| SQ-009 #20 | Blocked | Identity foundation and **UX-001–006 signoff** | Quest persistence, constraints, optimistic locking, PostgreSQL tests remain. |
| SQ-010–018 #21–29 | Blocked | #20 and issue-specific dependencies; **UX-006 #35 signoff** | Quest APIs/UI, ownership, lifecycle, ETags, search/pagination and complete E2E journey remain. |

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
- Security runtime, authentication flows and Quest E2E have not yet been implemented or tested.

Learning notes: [identity persistence](../implementation/IDENTITY-PERSISTENCE.md).
