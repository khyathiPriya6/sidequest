# Identity persistence (#41)

The `identity` module owns User, AccountStatus and UserRepository. A UUID remains stable when login details change and will be the Quest creator foreign key. Controllers must use explicit safe DTOs; never serialize the entity or put it in a session, because it contains the password hash.

Email is stripped and lowercased with Locale.ROOT, bounded to 254 characters. No provider-specific dot/plus rewriting occurs. Username is lowercased but not silently stripped; it must contain 3–30 ASCII letters/digits/underscores. Database unique indexes enforce canonical uniqueness even for concurrent registrations. Registration API syntax validation comes in #43. The database and factory require the approved `{argon2}$argon2id$` envelope; this rejects plaintext but is not a cryptographic verifier. Only the password encoder in #42 may produce real stored hashes; the test fixture is synthetic.

JPA callbacks set UTC Instants on insert/update; creation time is immutable through JPA. Database checks enforce required values, supported account state and timestamp ordering. Direct operational SQL must supply timestamps explicitly. Only ACTIVE and DISABLED exist; session revocation on disable is implemented in #42, not by the entity alone.

## Migration operations

`002-identity.sql` adds users and the Spring Session JDBC tables without modifying the previously applied placeholder. The session columns/indexes follow [Spring Session 3.4.2 PostgreSQL schema](https://raw.githubusercontent.com/spring-projects/spring-session/3.4.2/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql), the version managed by Spring Boot 3.4.4. Expiry is epoch milliseconds and indexed for cleanup; deleting a session cascades to attributes. Runtime scheduled cleanup, idle and absolute limits belong to #42. Unique email/username constraints provide lookup indexes, avoiding redundant indexes. SQ-009 adds a restrictive foreign key from `quests.creator_id` to `users.id`; remove the Quest table before rolling back the users changeset.

Do not start the application against an existing database until the owner approves migration of that exact target. Liquibase currently runs on application startup. Review the SQL, back up the database and confirm the JDBC target first. Session auto-initialization must be disabled when Spring Session is introduced, leaving Liquibase as schema owner.

Rollback statements are explicit, destructive table drops. Roll back Quest persistence before users so the creator foreign key is removed first. Rolling back identity deletes all accounts and invalidates sessions; it requires separate approval and a recovery backup. Prefer forward fixes after real data exists. No rollback was executed against a developer database.

## Isolated tests

From `backend`, set JAVA_HOME to Java 21 and run `bash scripts/test-postgres.sh`. Set PG_BIN if PostgreSQL binaries are elsewhere (default is the installed macOS PostgreSQL 18 directory). The script initializes a new temporary UTF-8 cluster, binds only loopback on an unused port, creates `sidequest_isolated_test`, passes an explicit test JDBC URL and stops that cluster on exit. It ignores the developer connection configuration. The fixture cluster uses local trust authentication and synthetic test data only; do not reuse it for development. Logs/data remain in the printed temporary directory for diagnosis.

`mvn test` runs ordinary tests; `mvn -Ppostgres-it verify` requires the isolated URL supplied by the script. No H2 fallback hides PostgreSQL behavior. Tests cover persistence, normalization, uniqueness, invalid values, account status/timestamps, repeat migrations, and expiry deletion/cascade. Actual result: 1 health + 3 PostgreSQL tests passed on Java 21.0.13/PostgreSQL 18.4. Runtime JDBC session round trips are a #42 requirement.

## Concepts and interview questions

Database constraints are the final protection against races: two requests can both pass an application-level existence check, but only one may commit a duplicate identity. Migrations track immutable changeset identities/checksums, so repeated startup does not recreate tables. Session expiry and deletion are separate: expired sessions must be rejected immediately even before a cleanup job deletes rows.

1. Why does checking whether an email exists before insertion not guarantee uniqueness?
2. How do Liquibase checksums and repeat execution protect a deployed schema?
3. Why must session expiration enforcement work independently of scheduled row cleanup?
