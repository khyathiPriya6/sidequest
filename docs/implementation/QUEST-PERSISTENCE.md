# Quest persistence (SQ-009 / #20)

SQ-009 adds the PostgreSQL/JPA persistence foundation for the agreed Quest contract. It does not expose Quest APIs or change the frontend.

## What is persisted

- `Quest` uses a server-generated UUID and a required, immutable `creator` association to `User`.
- Draft title is required and trimmed; description, category, difficulty, and XP reward may be absent until publication. Supplied values follow the contract's Unicode code-point limits and numeric/enumerated bounds.
- Category, difficulty, and lifecycle status use explicit enums stored as strings.
- Creation/update instants are UTC `Instant` values; publication/archive instants are nullable and reserved for the lifecycle API work.
- JPA's `@Version` maps to a nonnegative `BIGINT` version for stale-write detection.
- PostgreSQL checks enforce title/description bounds, supported enum values, XP range, lifecycle consistency, and timestamp ordering. The creator foreign key uses `ON DELETE RESTRICT`; quests are not cascaded away with a user.
- `quests_creator_created_idx` supports owner listings. `quests_public_created_idx` is a partial index for published-only discovery ordered by creation time and UUID.

The entity is persistence/domain state only and must not be serialized as an API response. Quest controllers use explicit DTOs. SQ-010 adds authenticated draft CRUD and owner reads with server-derived creator identity, ownership-scoped queries, and version-derived ETags. SQ-011 adds atomic publish/archive transitions; public-query behavior remains in its respective issue.

## Migration and rollback

`004-quests.sql` is included after `002-identity.sql`, because `creator_id` references the UUID primary key established by E03. Liquibase applies the immutable changeset once and records its checksum; an unchanged rerun applies zero changesets. The rollback is `DROP TABLE quests`, which removes quest history and must not be run after dependent participation tables exist without an approved rollback plan.

The application automatically applies Liquibase changes at startup. No existing database was migrated for SQ-009. The owner authorized migration execution only on a newly created isolated PostgreSQL test database. Applying this migration to any existing local, shared, staging, or production database still requires explicit target approval and a reviewed backup/recovery plan. No rollback was executed.

## Validation evidence

Executed from the repository root:

```sh
cd backend && JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash scripts/test-postgres.sh
```

Result: **BUILD SUCCESS** on Java 21.0.13 and PostgreSQL 18.4. The runner created a fresh temporary cluster and stopped it at exit. Thirteen tests passed (5 unit tests, 8 PostgreSQL persistence/integration tests); zero failures, errors, or skips. Initial Liquibase application ran all four changesets; the test's repeat run applied zero changesets.

The isolated cluster explicitly uses UTF-8 so PostgreSQL `char_length` checks count Unicode code points consistently with the Quest contract. The tests cover draft save/load and timestamps, JPA optimistic-lock rejection, database field/enum/foreign-key constraints, non-cascading creator deletion, and repeatable migrations. They do not claim Quest HTTP/API or authorization coverage.

## Learning notes

`@ManyToOne` models the creator relationship while the database foreign key is the final guard against an absent user. `ON DELETE RESTRICT` preserves quests instead of silently deleting them. `@Version` implements optimistic locking: a stale ORM update fails after another transaction increments the row version. Liquibase changesets are ordered and immutable so deployments can reliably determine which schema changes have already run.

1. Why should a database foreign key still be present when the service already verifies that a creator exists?
2. How does JPA's `@Version` detect a lost update without locking a row for the full duration of a user's edit?
3. Why is rolling back a table-drop migration more dangerous after other tables reference Quest?
