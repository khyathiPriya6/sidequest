# M5-01 — Social profiles and friendship contract

**Status:** Proposed for owner review. Issue [#105](https://github.com/khyathiPriya6/sidequest/issues/105), parent social epic #6. This is a design-only change; it does **not** authorize publication of user data, database migration, or new endpoints. M5 follows merged M4 gamification.

## Goals and boundaries

Provide a safe foundation for optional friend connections and limited public-facing player identity. The server remains authoritative for identities, friendship state, permissions, and privacy decisions. Preserve M1–M4 session/CSRF, Quest moderation, participation privacy, XP ledger and achievements.

**Not in M5-01:** implementation, public XP transactions, exact Quest participation history, private creator drafts, email or account metadata disclosure, uploads, location tracking, messaging, teams, real-time infrastructure, social recommendations, notifications or a feed. These need separate issues and privacy reviews.

## Privacy and profile visibility

An account is **private by default**. New users are not discoverable by username and are not listed in a global people directory. Friendships are voluntary; there is no automatic follow or implicit friendship through joining a Quest.

Proposed user setting: `profileVisibility = PRIVATE | FRIENDS | PUBLIC`, default `PRIVATE`. Adding a visibility control is a separate implementation issue and needs approved UI design. Changing visibility must never automatically accept requests.

- **Self:** authenticated user can see existing complete owner profile, XP transaction history, completion history, achievements, and account settings under established owner-only endpoints.
- **PRIVATE:** other users receive no public profile data. Account existence should not be revealed through search/detail endpoints; use indistinguishable not-found semantics.
- **FRIENDS:** accepted friends can see an allowlisted social profile, while nonfriends see the same no-access result as private accounts.
- **PUBLIC:** other signed-in users may view an allowlisted social profile, but this must be an explicit opt-in.
- **Allowlisted social fields only:** stable opaque profile URL identifier, chosen display name (initially the existing username if approved), and optional derived *summary* achievements/level (separate product opt-in before exposure). **Default social response should contain only identifier and display name.** Never disclose email, session information, date of birth, account creation timestamp, raw ledger or XP transactions, private Quest participation, moderation status, or hidden/draft content.
- Leaderboard visibility currently follows the approved M4 contract, which already exposes usernames and qualifying XP **to authenticated users**. A future profile privacy policy must explicitly reconcile this existing exposure; do not silently alter leaderboard rules or imply that `PRIVATE` hides the username there. Opening a social profile from the leaderboard must still pass its own visibility check.

User lookup for invitations should use **exact normalized username** (case normalization matching existing registration rules), require authentication, and return only the minimum information needed for a request. No bulk user directory or autocomplete in this slice. Exact lookup can still facilitate enumeration; impose per-account and per-IP controls, uniform errors where feasible, and audit/monitoring before enabling the feature.

## Friendship state machine

Prefer a canonical single request/relationship record per **unordered pair** of distinct user IDs (unique database constraint on `least(user_a,user_b), greatest(user_a,user_b)` or normalized pair columns). Persist `requester_id` separately to identify who can accept/reject. An accepted relationship is undirected, but the pending request has a sender and recipient.

| Existing pair | Action | Allowed actor | Result |
|---|---|---|---|
| None | Send request | Either member | PENDING (requester=actor) |
| PENDING | Accept | Recipient only | ACCEPTED |
| PENDING | Reject | Recipient only | Closed/rejected; no friendship |
| PENDING | Cancel | Sender only | Closed/cancelled; no friendship |
| ACCEPTED | Remove friend | Either member | Closed/removed; no friendship |
| PENDING / ACCEPTED | Repeat same request | Sender | Idempotent read of current state; never duplicate |
| PENDING | Opposite request | Recipient | Conflict; explicitly accept existing request instead |
| Closed | Send later | Either member | New request subject to cooldown and block checks |

Recommend preserving a limited, auditable transition history (including timestamps and actor) to enforce cooldown/rate limits. Never expose this history to other users; apply retention policy before deployment. For simplicity an implementation may store a canonical pair row plus audit events or a state/timestamp history, provided unique pair constraints and monotonic versions enforce concurrent safety.

Security rules:
- No self requests, requests to disabled accounts, or involuntary connections.
- An explicit block must prevent new invitations and revoke/disable existing friendship. Blocking and unblocking UX and persistence must be separately designed before friend requests can be launched widely; initial M5 delivery should **not** ship without an abuse prevention policy.
- Authenticated identity is derived from the session, never from supplied `requesterId`. State-changing calls require existing CSRF protection and request-size limits.
- On concurrent opposite-direction requests, exactly one pending row can commit; the loser receives a stable conflict, not an implicit acceptance.
- Concurrent accept/reject/remove requests use row locking or optimistic version checks so one transition wins; stale changes return conflict and never resurrect a friendship.
- Never automatically send or retry a non-idempotent request following ambiguous network failure; refresh authoritative state.
- Rejected, cancelled, removed and blocked connections must not leak one party's private profile.
- Avoid returning another user's identity or pending request count to unrelated users.

## Proposed API (pending review)

Versioned paths under `/api/v1`, JSON responses, established RFC 9457 Problem Details, `Cache-Control: no-store`, bounded pagination (default 20, max 100) and deterministic sorting. These are contracts, not implemented routes.

| Method/path | Meaning |
|---|---|
| `GET /social/profiles/{opaqueId}` | Resolve allowlisted profile only when visibility authorizes |
| `GET /me/social/settings` | Read own privacy preferences |
| `PATCH /me/social/settings` | Explicitly update visibility (CSRF, validated enum, concurrency safety) |
| `POST /me/friend-requests` | Invite an exact, validated username; never accept requester ID |
| `GET /me/friend-requests?direction=incoming|outgoing&page=0&size=20` | Own pending invitations |
| `POST /me/friend-requests/{id}/accept` | Recipient accepts |
| `POST /me/friend-requests/{id}/reject` | Recipient rejects |
| `POST /me/friend-requests/{id}/cancel` | Sender cancels |
| `GET /me/friends?page=0&size=20` | Own accepted connections, limited profile fields |
| `DELETE /me/friends/{opaqueId}` | Unfriend an accepted peer |

Only relevant parties may see a request or friendship. Unauthorized object IDs should return the same 404 as unknown IDs. Pending duplicate requests should return a stable current status; cross-direction requests should report a consistent 409. Response shape should carry an opaque request ID, state, last-updated timestamp and a concurrency version/ETag where needed. Private response fields must remain explicitly allowlisted. All paginated results need deterministic ordering and safe maximum offsets.

A friends-only leaderboard is a **later workstream**, not part of this contract's initial API. It must reuse M4 eligibility/score/tie semantics and use accepted friend IDs only; it must not expose a private friendship graph.

## Persistence and concurrency proposal

Add new Liquibase changesets **only after approval**. Possible schema: `social_profile_preferences(user_id PK, visibility, updated_at, version)` with missing row interpreted PRIVATE; `friend_connections(user_low_id, user_high_id, requester_id, status, version, created_at, updated_at)` with user foreign keys, unique pair and check `user_low_id <> user_high_id`; plus audited transition metadata with approved retention. Index the recipient/requester pending views and accepted connections in both directions. Normalize pair IDs on the server. Avoid adding duplicate user display names or XP balances.

Select locking and idempotency together so a retry, network timeout, cross request and concurrent decision cannot manufacture extra relationships. Hard deletion of friendship records can defeat cooldown and audit needs; prefer a bounded state history with explicit retention policies.

## Threat model and acceptance tests

Before a release, verify with isolated PostgreSQL and browser sessions:
1. Private-by-default account is not discoverable or openable; allowlisted PUBLIC/Friends views reveal only approved fields.
2. No self-request; disabled or blocked accounts cannot receive new requests.
3. Only recipient accepts/rejects and only sender cancels; unrelated account IDs reveal no request data.
4. Double-click, repeated POST and opposite-direction races never create two active pair records.
5. Two concurrent decisions have one winner; stale version returns conflict.
6. Unfriend and visibility downgrade immediately remove social profile access without modifying personal XP/ledger.
7. CSRF failures, expired sessions, invalid UUID/usernames, rate limits and oversized requests fail safely.
8. Pagination, empty states, stable sort, mobile navigation, keyboard and accessible dialogs.
9. Account existence probing, username lookup throttling, request spam and moderation reporting are assessed before launch.
10. M1–M4 backend, UI and E2E regression remains green without weakening Quest safety.

## Proposed M5 delivery order

1. **M5-01:** approve this privacy/friendship contract (docs only).
2. **M5-02:** privacy preferences and minimal social profile API, schema, authorization and PostgreSQL tests.
3. **M5-03:** friendship lifecycle and abuse controls, atomic constraints and concurrency tests.
4. **M5-04:** responsive profile/friends/request management UI with design review.
5. **M5-05:** friends leaderboard and shareable approved Quest links (no email/contact import).
6. **M5-06:** integrated multi-user E2E plus privacy/accessibility/security checks.

Each workstream gets its own issue, branch and PR under `AGENTS.md`; stacked PRs must record dependencies. Owner approval is required before merging, deploying, or migrating any persistent database.

## Teach-back

A unique canonical pair prevents two friendship rows for A→B and B→A, while an explicit requester preserves who can accept. A profile's visibility is checked at every read, including link-based reads; a user's own XP/history does not automatically become social data.

Questions:
1. Why is a single canonical pair safer than independent outgoing and incoming rows?
2. Why does `PRIVATE` need to return the same not-found response as an unknown profile?
3. How do optimistic versions or row locks stop a stale decision from reactivating a removed friend?
