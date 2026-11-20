# E04-07 — Player profile and progression APIs (#64)

The owner-approved contract is [PROGRESSION-CONTRACT.md](../architecture/PROGRESSION-CONTRACT.md), approved by merge of PR #69 on 2026-11-20. E04-07 is backend-only; frontend design remains subject to approval and UX-006 #35 stays open.

## Implementation

- GET /api/v1/me/profile: username and createdAt, ledger-derived total XP, level and progress, participation counts, created quest count and unlocked achievement count.
- GET /api/v1/me/xp-transactions?page=0&size=20: owner-only paginated ledger rows ordered awardedAt DESC/id DESC, with safe current Quest summary including archive status.
- GET /api/v1/me/achievements: all nine approved entries, locked and unlocked, capped progress, and deterministic event-derived unlock timestamps.

The profile summary is one PostgreSQL aggregate query; XP is cast to bigint before SUM. Achievement progress and threshold timestamps use bounded SQL window/aggregate queries, not in-memory histories. Profile, achievement and XP page reads use one read-only REPEATABLE READ transaction. Missing or mismatched completion awards fail closed. No mutable balance, level, unlock table, migration or new infrastructure was added. Responses are explicit DTOs and no-store; security explicitly allows the three GET paths while retaining deny-by-default behavior.

## Verification

Environment: macOS 26.7.3 aarch64, Adoptium Java 21.0.13.1, Maven 3.9.16, PostgreSQL 18.4. Commands from repository root, with the configured JAVA_HOME and PATH for the temporary Java 21 installation.

Commands run:

- mvn -f backend/pom.xml test-compile — passed.
- bash backend/scripts/test-postgres.sh -Dit.test=ProgressionTest,PlayerProfileIT — focused profile/progression tests passed.
- bash backend/scripts/test-postgres.sh — 20 unit + 50 integration passed; zero failures, errors or skips; BUILD SUCCESS.
- git diff --check — passed.

Fresh cluster startup applied seven changesets; repeated startups applied zero, seven already present. No migration skipped and no developer database accessed.

Final regression log: `/private/tmp/e04-07-full-final.log`; final compilation log: `/private/tmp/e04-07-compile-final.log`. Fresh Liquibase startup reported seven run and zero previously run; repeated startup reported zero run and seven previously run. Initial failures, corrected before the final run: malformed Java text-block delimiter in a test; one inconsistent unit fixture (four completions without the required unlock event); test JDBC did not infer Instant SQL types; archived synthetic Quest fixtures omitted archived_at. Logs are under /private/tmp/e04-07-*.log. Each was fixed and the focused or full suite rerun; no failures remain.

## Teach-back

The XP ledger is the history from which balance and level are calculated. SQL can total and rank that history without loading every row. An achievement timestamp is the saved time of the event that first met its rule, not the time someone opened the profile.

1. Why cast each XP amount before summing?
2. Why use one repeatable-read snapshot across profile aggregates?
3. How do ordered SQL events give stable achievement unlock timestamps?
