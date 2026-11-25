# E04-10 full-stack verification (pending execution)

Branch: `test/e04-10-chatgpt-e2e` (stacked on E04-09). Do not merge before the frontend base passes review.

## Real browser coverage added

`ui/e2e/participation-real.spec.ts` runs only with `AUTH_E2E=1` and verifies:
- Creator registration, Quest creation and publishing, and self-join restriction
- Guest visibility and sign-in routing
- Distinct participant enrollment and In Progress list
- Cross-user read/complete authorization
- Creator archive and absence from public details
- Private archived participation completion and permanent status
- Single XP reward, Completed list, profile and persistence after reload
- Idempotent repeat completion and no duplicate total XP

The existing PostgreSQL integration suites are responsible for transactional isolation, enrollment/archive serialization, concurrent completion, ledger uniqueness and rollback. Browser assertions do not prove database concurrency.

## How to run

From the repository root, on a machine with Java 21, Maven, npm dependencies, Chromium or Chrome and PostgreSQL binaries:

```sh
cd ui && npm ci && npm test && npm run build && cd ..
JAVA_HOME=/path/to/java21 PG_BIN=/path/to/postgres/bin \
  bash backend/scripts/test-postgres.sh
JAVA_HOME=/path/to/java21 PG_BIN=/path/to/postgres/bin \
  PLAYWRIGHT_CHANNEL=chrome bash backend/scripts/test-browser.sh
```

The repository's browser script creates a new isolated PostgreSQL database cluster and starts the real backend. It currently uses macOS `/private/tmp` and defaults to PostgreSQL 18's local macOS location; adjust portability only in a separate reviewed change.

## Actual results and status

- Code written and committed through the GitHub connector.
- No commands above executed in this ChatGPT environment: the container has no network access to clone the repository/install dependencies and no executable project checkout.
- Tests **unverified**; no pass/fail counts are asserted.
- No existing developer database touched; no deployment.
- Accessibility and network-disconnect flows still require full verification.
- UX-006 formal sign-off still outstanding. Provisional implementation authorization recorded in issue #35.
- Do not close #74, #35 or epic #4 until the required suite is run and accepted.

## Follow-up validation

Review the full-stack test selectors against the runtime UI, run the suite, inspect its actual results, fix failures on the same branch, then record counts, failure traces, browser/runtime versions and any skipped tests. The E04-09 PR must be verified and merged before retargeting this E04-10 PR to `main`.
