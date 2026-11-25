# M5-02 — Social profile preferences API

**Status:** Implementation in draft PR; requires owner review. Parent design contract: [M5-01](../architecture/M5-SOCIAL-CONTRACT.md). This feature implements privacy settings and minimal social identity reads only, not friend requests or a user directory.

## API

All endpoints use the existing server-side authenticated session, are under `/api/v1`, and send `Cache-Control: no-store`. Mutations require the existing `X-CSRF-TOKEN` session token.

| Endpoint | Example response | Semantics |
|---|---|---|
| `GET /me/social/settings` | `{"profileId":"UUID","visibility":"PRIVATE","version":0}` | Owner-only; first read allocates stable random social profile ID, with private default |
| `PATCH /me/social/settings` | `{"profileId":"UUID","visibility":"PUBLIC","version":1}` | Body `{"visibility":"PRIVATE|FRIENDS|PUBLIC","expectedVersion":0}`; versioned, atomic CAS |
| `GET /social/profiles/{profileId}` | `{"profileId":"UUID","displayName":"username"}` | Authenticated; only self and explicit PUBLIC profiles currently visible |

**Visibility:** PRIVATE is the default. FRIENDS is accepted as a setting but denies access to other users until M5-03 establishes a server-verified accepted-friendship relation. Changing visibility does not change M4 leaderboard eligibility, username display or private progression history. There is no anonymous people directory, exact-user search, or raw XP/ledger disclosure in this slice.

**Errors:** Unknown, private, disabled and currently inaccessible FRIENDS profiles return the same 404 Problem Details (`code: not_found`) to nonowners. Invalid visibility or expectedVersion returns 400; stale version returns 409 (`version_conflict`). Missing login or invalid CSRF follow existing security boundary (401/403).

## Persistence

New Liquibase changeset **013** creates `social_profile_preferences` with user ID primary key, independently generated unique `public_id`, allowlisted visibility, nonnegative version, and server timestamp. The first authenticated owner GET or PATCH inserts a PRIVATE row using `ON CONFLICT DO NOTHING`; it does not enumerate, backfill or expose previously registered users. GET is idempotent but the first owner GET is a row-creation side effect. This tradeoff avoids coupling registration and identity code to social profile creation. Subsequent reads do not modify the record.

The PATCH uses compare-and-swap `UPDATE ... WHERE version = expectedVersion` and a transaction to prevent lost updates. The public profile read joins the current ACTIVE user and only returns the opaque profile ID and username; never the underlying user ID or sensitive columns.

Migration 013 has **not** been run on a developer or production database. Only run tests through the disposable PostgreSQL harness until owner authorizes a specific persistent migration target.

## Verification

New `SocialProfileIT` tests cover:
- Private-by-default preferences and stable non-user social ID.
- Identical private and nonexistent 404 behavior; unauthenticated denial.
- FRIENDS fail-closed, explicit PUBLIC opt-in and privacy downgrade.
- Allowlisted response fields, no-store, owner lookup, disabled-account concealment.
- CSRF, invalid requests, stale-version conflict and two competing PATCH requests.

Run locally with Java 21 and PostgreSQL test binaries:

```bash
JAVA_HOME=/path/to/java21 bash backend/scripts/test-postgres.sh -Dit.test=SocialProfileIT
JAVA_HOME=/path/to/java21 bash backend/scripts/test-postgres.sh
```

**Execution status:** Test code added and reviewed statically in the connected GitHub environment, but neither command has been executed here; there is no local Java/PostgreSQL runtime checkout. Do not treat the new tests as passing without actual output.

## Explicitly deferred

No UI, social friendships, profile search, friend request delivery, friend-only leaderboard or abuse rate-limiting in this PR. Those require M5-03+ issues and design approval.

## Teach-back

We separate a public social identifier from the account's internal ID. New users start private, and the database owns the visibility setting. Optimistic version checks ensure that two tabs cannot silently overwrite each other's privacy choices.

Questions:
1. Why must a known private profile ID return the same response as a nonexistent ID?
2. Why does a FRIENDS-only profile remain hidden until friendship authorization is implemented?
3. Why do we check `expectedVersion` in the SQL update rather than only in Java?
