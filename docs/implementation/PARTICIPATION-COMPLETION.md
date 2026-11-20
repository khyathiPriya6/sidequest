# E04-05 — Completion and atomic XP (#62)

POST `/api/v1/participations/{id}/complete` is session-authenticated, CSRF-protected and bodyless. An owner-scoped pessimistic lock precedes reading status/ledger. Missing or nonowner records return the same 404. The existing transaction either saves COMPLETED and its one immutable reward entry together or rolls back both. No REQUIRES_NEW, mutable balance, background award or new infrastructure is used.

First completion uses server Clock time at PostgreSQL microsecond precision for both completion and award. A clock before joinedAt fails safely. Repeats return the persisted representation without changing either timestamp or amount. Existing archived participants may complete. Reward comes only from the original snapshot. Completed-without-ledger, wrong amount, award-before-completion and in-progress-with-ledger fail as integrity errors; no repair awards are minted. Unique ledger participation remains the final database safeguard.

The controller receives a service result only after the transaction interceptor commits. Database/transaction errors become generic RFC9457 responses; private SQL and identifiers are not leaked. Status flush deliberately precedes ledger insertion; tests prove database rejection rolls the status back. Publication/edit restrictions are unchanged.

No schema migration or frontend changes. Backend approval is recorded, frontend #35 remains open. Rollback disables the completion route; it must not reverse completed records, remove awards or roll back history tables. Branch `feat/e04-05-completion-rewards` targets `feat/e04-04-participation-history` (#67), which targets Join #66. Merge predecessors into main in order and retarget each dependent PR before merge. Owner merges only.

## Verification

Java 21.0.13.1, Maven 3.9.16, PostgreSQL 18.4, macOS aarch64. From repository root, prefix with `JAVA_HOME=/private/tmp/sidequest-java21/Contents/Home PATH=/private/tmp/sidequest-java21/Contents/Home/bin:$PATH`:

- `mvn -f backend/pom.xml test-compile`: passed.
- `bash backend/scripts/test-postgres.sh -Dit.test=ParticipationCompletionIT`: 17 unit + 5 focused IT passed, zero failures/errors/skips.
- `bash backend/scripts/test-postgres.sh`: 17 unit + 47 IT passed, zero failures/errors/skips; fresh seven migrations and repeat zero applied. Logs: `/private/tmp/e04-05-compile.log`, `/private/tmp/e04-05-focused.log`, `/private/tmp/e04-05-full.log`. No failed test runs.
- `git diff --check`: passed.

Concurrency tests use real HTTP requests/independent database transactions and confirm two PostgreSQL lock waiters before release; latches, futures, HTTP and transaction waits are bounded. An isolated test trigger rejects one specific award insert, then is removed in finally. Tests assert status rollback, null completedAt, zero ledger rows and successful retry. Other tests assert ownership/CSRF/body errors, archived completion, unchanged retry timestamps, completed Join retry, exact counts and corrupt-ledger refusal. No existing database touched.

## Teach-back

The row lock makes competing completions take turns. The second caller sees the first committed result. A shared transaction is what prevents a completed Quest without its reward when insertion fails.

1. Why does a lock alone not guarantee atomic status and XP writes?
2. Why must a completed participation with no ledger fail instead of minting XP?
3. How does a database-injected failure prove rollback more strongly than a mocked response?
