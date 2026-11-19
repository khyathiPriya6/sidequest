# E04-03 — Join Quest (#65)

POST `/api/v1/quests/{id}/join` uses the session principal, CSRF and no request body. First enrollment returns 201 with Location; repeat returns 200. Both return the participation DTO with nested Quest summary and no-store. Existing enrollment is checked before new-enrollment eligibility, including archived/completed cases. The ledger is read, never written by Join. Inconsistent saved awards fail safely.

Join locks the Quest row before looking for enrollment and checking current eligibility. Archive now acquires that same row through its creator-scoped query before lifecycle/ETag checks. The transaction retains the lock through flush and commit; a unique enrollment constraint remains the final safeguard. Application joins cannot race past this lock. We never catch uniqueness errors and continue an aborted transaction; unexpected persistence failures return generic integrity errors. Published edits remain forbidden. Join timestamps use PostgreSQL microsecond precision so the first response and a reload agree exactly.

Owner authorization on 2026-11-19 explicitly permits backend implementation without Figma handoff. Frontend still requires design approval; #35 remains open. Authorization is recorded in #35, #61–65 and the architecture documents. SQ-018 manual acceptance exists; formal closure remains outstanding. Progression logic still needs #63 approval.

No migrations or dependencies added. Existing databases are untouched. Reverting this API requires removing its security route and restoring archive behavior together; retain persisted history and never roll back data tables just to disable an endpoint.

## Verification

Environment: macOS aarch64, Adoptium Java 21.0.13.1, Maven 3.9.16, PostgreSQL 18.4. From repository root, prefix Maven/script commands with:
`JAVA_HOME=/private/tmp/sidequest-java21/Contents/Home PATH=/private/tmp/sidequest-java21/Contents/Home/bin:$PATH`.

- `mvn -f backend/pom.xml test-compile`: all sources compile.
- `bash backend/scripts/test-postgres.sh -Dit.test=ParticipationJoinIT`: 17 unit + 4 focused integration tests passed, zero failures/errors/skips.
- `bash backend/scripts/test-postgres.sh`: 17 unit + 39 integration passed, zero failures/errors/skips; fresh seven migrations and repeat zero applied.
- `git diff --check`: passed.

Tests use real HTTP sessions and independent transactions with bounded latches/futures; database lock waits prove both join/archive orderings. No frontend changed or tested. The first full run passed 17 unit and 30 integration tests but had nine context-start errors (zero assertion failures/skips): cached Spring pools exhausted PostgreSQL connections. The isolated script now bounds each test pool to four connections/minimum one idle; production pool settings are unchanged. Logs: `/private/tmp/e04-03-focused.log`, `/private/tmp/e04-03-full.log`, `/private/tmp/e04-03-full-final.log`.

## Teach-back

Locking the Quest makes enrollment and archival take turns. Looking for an existing enrollment before checking whether a new one is allowed preserves retries after archive. A server snapshot protects the original reward.

1. Why must Join and archive lock the same row before decisions?
2. Why does a repeat Join after archive still return its existing record?
3. Why retain the unique constraint even when the service locks a row?
