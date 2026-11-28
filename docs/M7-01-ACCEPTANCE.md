# M7-01 — End-to-end journey and security verification

## Automated HTTP integration test

`backend/src/test/java/com/sidequest/participation/PlayerJourneyIT.java` exercises separate HTTP clients and session cookies for creator, provisioned moderator, participant and guest, against an **isolated disposable PostgreSQL cluster**.

| Step | Assertion |
| --- | --- |
| Registration and sessions | Three independent registered users; anonymous protected profile rejected |
| Draft | Creator can create; guest detail and other user's join are unavailable |
| CSRF | Submit-review and completion mutations reject a missing CSRF token |
| Moderation | Unprovisioned user cannot view queue/approve; provisioned staff can approve |
| Publish | Creator cannot publish before approval; published Quest is public |
| Join | Creator cannot join own Quest; another user can join |
| Complete | Completion needs owning session and CSRF; awards configured XP |
| Idempotency | Duplicate completion produces same response, one XP ledger row |
| Progress | Player profile and XP history update; creator earns no XP |
| Leaderboard | Player is represented in all-time ranking after eligible completion |
| Data isolation | User cannot read someone else's participation |

## Run locally

Prerequisites: Java 21, Maven, Python 3, PostgreSQL 18 binaries. `JAVA_HOME` must point to the Java 21 installation. The script needs `initdb`, `pg_ctl`, `createdb` and an isolated temp directory; it **does not connect to the developer database**.

From the repository root:

```bash
bash backend/scripts/test-postgres.sh -Dit.test=PlayerJourneyIT
```

For the full backend integration regression suite:

```bash
bash backend/scripts/test-postgres.sh
```

Run web and native static/test gates independently:

```bash
(cd ui && npm ci && npm test -- --run && npm run build)
(cd mobile && npm ci && npm run check && npx expo-doctor)
```

These commands are validation instructions, not evidence that this PR passed them. The GitHub file editor cannot run macOS/isolated-Postgres tests.

## Manual cross-client acceptance

Use test accounts and local/staging data only. Do not use an actual production database.

1. Start the backend in the `local` profile and confirm the API URL in the iOS simulator.
2. Register/sign in on **web**; open `/api/v1/auth/me` indirectly by navigating authenticated screens.
3. Sign in with that same account on **mobile**; refresh/reopen Expo Go and verify Profile, My Quests and Teams still load. Verify native session-cookie and CSRF continuity; record any 401 or 403 response.
4. Create a Quest in web; check its draft in mobile. Submit for moderation and approve through the staff-only screen. Confirm a non-staff account cannot load the queue.
5. Publish in one client; find the Quest through the other client. Join and complete with a separate account; confirm matching XP/profile/history and no duplicate award on a repeated action.
6. Check friend requests, privacy settings, private-team membership and challenge leaderboard on both clients.
7. Verify errors, empty lists, network disconnection, keyboard dismissal, tap targets and small-screen scroll/layout.
8. Never blindly replay a mutation after a connection failure; refresh saved server state before retry.

## Exit criteria

- `PlayerJourneyIT` and the full existing PostgreSQL integration suite pass.
- Web tests/build and mobile type checking pass.
- Web/mobile authenticated workflows are manually demonstrated (native session persistence in particular).
- No high/critical unresolved production dependencies; audit medium findings with actual dependency paths and compatible remediation.
- No unauthorized access to private or moderator endpoints.
- Do **not** merge based only on file changes. Verify and report failures first.

## Deferred (M7-02/M7-03)

CI/CD pipeline, Docker, staging deployment, HTTPS, monitoring and backup restore verification belong to subsequent M7 PRs.
