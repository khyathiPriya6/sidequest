# SAFETY-02 — moderation persistence and conservative screening

**Issue:** [#83](https://github.com/khyathiPriya6/sidequest/issues/83)  
**Contract:** Draft [#82](https://github.com/khyathiPriya6/sidequest/pull/82)  
**Status:** Implemented in draft PR, tests not run; not production ready.

## Included

- New append-only Liquibase changeset `008-quest-moderation.sql` with Quest foreign key, review state, policy version, timestamps, version and constraints. Existing Quest table, publication lifecycle and XP ledger remain unchanged.
- Internal `QuestModeration` JPA record and repository (no public endpoints).
- Deterministic `QuestSafetyScreen` returns `NO_SIGNAL`, `REVIEW`, or `HIGH_PRIORITY_REVIEW` and human-readable categories for moderation triage. **No result means content is safe, and no keyword alone produces irreversible rejection.** Particularly, `NO_SIGNAL` does NOT mean `APPROVED`.
- Focused unit tests for safe, unsafe, ambiguous, and educational false-positive examples.
- Isolated PostgreSQL test for `UNREVIEWED` persistence and no implicit legacy approval. Existing Liquibase repeat-count test adjusted from 7 to 8.

## Deliberate limitations

- **Not connected to the Quest publish, public detail/search, or Join services.** Those current routes remain unaffected until SAFETY-03.
- No reporting API, moderator role, review queue, appeals, policy editing, rate limits, AI, or mobile integration.
- No existing Quest backfill is performed by this migration. Legacy quests have no moderation row. SAFETY-03 must choose a deliberate, fail-closed rollout with the owner before enabling public visibility gating.
- Keyword-only triage is easily evaded and can flag harmless material; it is not a moderation solution by itself.

## Test commands to run in a Java 21 + PostgreSQL environment

```bash
mvn -f backend/pom.xml test
JAVA_HOME=/path/to/jdk21 PG_BIN=/path/to/postgres/bin bash backend/scripts/test-postgres.sh
```

**Actual execution:** Not run in ChatGPT GitHub connector environment. Do not merge based on a claimed green run. Run the existing backend regression and resolve any schema/test differences. The PostgreSQL test helper targets freshly created, isolated `sidequest_isolated_test` databases and does not use developer DB credentials.

## Security and migration decisions

Keep review records internal. Future moderation endpoints require server-assigned moderator authorization, CSRF, strict public 404 behavior and audit logging. Do not expose review evidence or reporter identity. The current migration adds a new table but does not modify existing data.

## Teach-back

Moderation is distinct from Quest publication. An `UNREVIEWED` record says no trustworthy review has happened; it does not permit public discovery. Later, the Quest `publish` operation and all read/Join predicates must require a durable approval decision while preserving historical XP records.

1. Why must `NO_SIGNAL` from the rule-based screen not count as approval?
2. Why does an existing published Quest without a moderation row need an explicit migration policy?
3. Why do moderation and Quest lifecycle states belong to different domain concepts?
