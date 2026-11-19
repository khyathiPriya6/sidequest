# Participation persistence — E04-02 (#59)

Remediation on `feat/e04-02-participation-persistence`, 2026-11-19. Persistence primitives only; Join/Complete services, endpoints, UI and atomic completion/XP tests remain future work (E04-05 for completion).

## Domain and repository boundaries

`Participation.join` requires a published quest and rejects creator enrollment. Test fixtures invoke the actual package-local Quest publication transition through a test-only factory; production Quest editing and lifecycle visibility are unchanged. The future Join service must independently enforce eligibility in its transaction after locking the Quest and checking for an existing enrollment. Repeat enrollment must return the existing participation before applying new-enrollment rules, including after archival.

Participation completion locks are owner-scoped. Quest repositories expose both an ID lock for Join and a creator-scoped lock for archive. Tests verify ownership filtering and actual PostgreSQL row locks using a second connection with `FOR UPDATE NOWAIT` (55P03). Existing archive performs a versioned UPDATE, which takes a database row lock, but currently reads before that update. Future Join/archive integration must use the common lock before decisions and retain ETag/ownership rules; these primitives alone do not claim a complete concurrency protocol. Do not reuse stale managed Quest state after acquiring a lock. Completion needs only the participation lock.

The future completion service must write status and one ledger entry atomically, preserve original timestamps on retries, validate ledger amount against the enrollment snapshot, and reject completed-without-ledger corruption. No atomic award service behavior is claimed here.

## Database protections and testing approach

Changesets 005 and 006 are retained byte-for-byte. New corrective changeset 007 replaces trigger functions, adding stable SQLSTATE 23514 and constraint diagnostics, and prevents reversal of COMPLETED or changes to its original completion timestamp. Snapshot ID, owner, quest, reward and join time stay immutable. Ledger UPDATE/DELETE remains prohibited. Database checks reject invalid status/time combinations and rewards outside 1–1000; unique enrollment and ledger constraints return 23505, missing foreign-key references 23503, and restricted deletes 23001 on PostgreSQL 18.4. An unknown status also violates the completion-state check, which PostgreSQL checks first; the assertion records that exact constraint.

JDBC constraint tests use the Spring transaction's connection and savepoints. Fixtures are visible, and each expected PostgreSQL failure rolls back to its savepoint so later assertions remain valid. Separate-connection lock and rollback tests explicitly commit fixtures in the disposable cluster. Entity round trips flush, clear the persistence context, and reload. Migration reapplication checks unchanged IDs, checksums and execution times as well as the seven-change history count.

## Migration and rollback considerations

No existing developer database is migrated. Whether 005/006 were previously applied to any persistent environment is unverified, so a forward corrective migration is used conservatively. Fresh/repeated validation uses only the script-created temporary cluster. Applying migrations to a persistent environment still needs owner approval.

007 rollback restores the previous trigger definitions without dropping data, but removes completion immutability and stable diagnostics. Prefer a forward correction; coordinate application compatibility and backups before any approved persistent rollback. Rolling back 006/005 drops ledger/participation tables and destroys history: never use it as a routine remediation. Rollback SQL is documented, not executed against persistent data. Cross-table XP equality and completion atomicity remain service invariants.

## Acceptance prerequisites

[SQ-018 owner comment](https://github.com/khyathiPriya6/sidequest/issues/29#issuecomment-7001712986) explicitly records successful manual testing of authentication and Quest Management. This is manual acceptance, not new automated evidence; #29 stays open for formal evidence/closure. [UX-006 #35](https://github.com/khyathiPriya6/sidequest/issues/35) remains open with no comments/sign-off: clickable Explorer Join/Creator Publish flows, six wireframe pairs, tokens/states, accessibility risks, Figma evidence, and handoff sign-off (including UX-001–005 dependencies) remain outstanding. No merge badge substitutes for these approvals. Further product UI work remains gated.

## Teach-back

A test must prove the intended rule caused a failure: a duplicate enrollment test that fails because its user is invisible proves nothing about duplicates. Sharing the transaction makes fixtures visible, and savepoints recover from expected errors. Row locks serialize decisions, while unique constraints remain the final safeguard. Domain validation helps callers, but database triggers also protect against direct SQL changes.

1. Why can a separate JDBC connection fail to see fixtures saved and flushed by JPA?
2. Why do both a row lock and a unique constraint matter for concurrent enrollment?
3. Why must completion and the XP ledger insert commit together, and why is preserving the first completion time important?

## Verification

Environment: macOS 26.7.3 aarch64; Eclipse Adoptium Java 21.0.13.1 at `/private/tmp/sidequest-java21/Contents/Home`; Maven 3.9.16; PostgreSQL 18.4 (`/Library/PostgreSQL/18/bin`). All commands below run from repository root on 2026-11-19.

```sh
JAVA_HOME=/private/tmp/sidequest-java21/Contents/Home PATH=/private/tmp/sidequest-java21/Contents/Home/bin:$PATH mvn -f backend/pom.xml test-compile test
JAVA_HOME=/private/tmp/sidequest-java21/Contents/Home PATH=/private/tmp/sidequest-java21/Contents/Home/bin:$PATH bash backend/scripts/test-postgres.sh
git diff --check
```

- Maven test compilation succeeds, including all IT sources. Final standalone unit run: **17 passed, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Log: `/private/tmp/sidequest-59-unit-rerun.log`.
- Final isolated script: **17 unit + 35 integration passed, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Includes all 26 existing backend integration regressions and nine participation persistence tests. Log: `/private/tmp/sidequest-59-postgres-final.log`; Maven XML reports under `backend/target/{surefire,failsafe}-reports`.
- Fresh Liquibase startup: **7 run, 0 previously run**. Repeated startup: **0 run, 7 previously run**, including explicit unchanged-history assertions. No migration skipped; no persistent database accessed. Script stops its temporary server on exit.
- `git diff --check`: passes. Remediation self-review completed against P0/P1/P2 findings; owner PR review and merge approval remain pending. UI tests not rerun: this issue changes only backend persistence and documentation.

Actual failed attempts (retained rather than hidden):

1. The earlier documented JDK path `/private/tmp/jdk-21.0.13+9/Contents/Home` no longer existed, so the initial Maven command exited before tests. Downloaded a temporary Java 21 JDK after sandbox DNS denial and approved network retry.
2. Sandboxed Java 21 Maven run compiled all sources but returned **16 passed, 0 assertion failures, 1 error, 0 skipped**: `HealthControllerTest` could not initialize Mockito because Byte Buddy could not self-attach. The approved run outside the sandbox passed all 17.
3. Sandboxed PostgreSQL script stopped before tests on loopback socket allocation (`PermissionError: Operation not permitted`). Approved runs used the same isolated script outside the sandbox.
4. First full PostgreSQL run: **17 unit passed; 26 integration passed, 0 assertion failures, 9 errors, 0 skipped**. All nine participation fixtures violated `quests_timestamps_valid` because publication preceded initial persistence. Fixed to save the draft before publishing and flushing.
5. Second full PostgreSQL run: **17 unit passed; 34 integration passed, 1 assertion failure, 0 errors, 0 skipped**. Restricted user deletion correctly returned 23001 rather than the expected 23503. Corrected all three exact restricted-delete assertions. The final full run passed as recorded above.

Logs for the two failed full runs are `/private/tmp/sidequest-59-postgres.log` and `/private/tmp/sidequest-59-postgres-rerun.log`. No rollback of persistent data, service atomicity/concurrency claim, deployment or automatic merge is included.
