# M5-03–05 — Social integration

Status: combined implementation candidate for owner review. Issues [#110](https://github.com/khyathiPriya6/sidequest/issues/110), [#111](https://github.com/khyathiPriya6/sidequest/issues/111), [#112](https://github.com/khyathiPriya6/sidequest/issues/112). User requested delivery together; this integration branch is an intentional exception to the usual one-issue-per-PR practice. M5-06 remains separate for full browser acceptance.

## M5-03 Friendship backend
- Canonical pair relationship, one pending request per unordered user pair; recipient-only acceptance/rejection, sender-only cancellation, either-party unfriend.
- Server session identity and existing CSRF protection. No implicit acceptance on an opposite-direction invitation.
- Optimistic version on decisions and row locking for transitions. Database constraints prevent duplicate pairs.
- A user can block another user, removing any existing relationship. Profile reads also reject blocked relationships. Unblocking does not automatically restore a friendship.
- Request send throttling: at most 10 successful sends per actor per UTC rolling 24 hours, 1-hour cooldown after a closed request to the same peer. These are baseline per-account safeguards; deployment must add abuse monitoring and IP/network-level throttling.
- An immutable social transition event log is retained with no declared retention job in this version; define and implement a retention/privacy policy before deploying to real users.
- No directory or autocomplete; exact username only. Unknown/inaccessible profiles never leak private fields.
- The initial implementation lists at most 100 recent requests and 100 friends; **pagination and full-list traversal remain required before production use**.

## M5-04 Frontend
- `/friends` has owner privacy setting, username invitation, incoming/outgoing requests, friend list, removal and blocking.
- `/players/:id` uses the existing authorized social profile read, with private/unavailable states.
- Uses shared SideQuest components and API authentication/CSRF; stale mutations reload authoritative state.
- UI design and keyboard/screen-reader acceptance require owner review.

## M5-05 Friends board and sharing
- `GET /api/v1/me/friends/leaderboard?period=all|weekly|monthly` includes the signed-in user and accepted, unblocked friends.
- Reuses M4 period cutoffs (UTC), ledger scoring, moderation eligibility, open-report exclusion, active accounts, competition ranks and username tie ordering. Returns a bounded first 100 (pagination remains a follow-up before production).
- Quest share action copies the existing same-origin public Quest detail URL. It never grants a user permission to view an unpublished, hidden, archived or private Quest.

## APIs
| Verb | Endpoint |
|---|---|
| POST | `/api/v1/me/friend-requests` body `{"username":"exact_username"}` |
| GET | `/api/v1/me/friend-requests?direction=incoming|outgoing` |
| POST | `/api/v1/me/friend-requests/{id}/accept|reject|cancel|remove` body `{"expectedVersion":0}` |
| GET | `/api/v1/me/friends` |
| POST | `/api/v1/me/social/blocks` and `/api/v1/me/social/unblocks` body `{"username":"exact_username"}` |
| GET | `/api/v1/me/friends/leaderboard?period=all|weekly|monthly` |

Authenticated sessions are required; sensitive responses use `Cache-Control: no-store`. Failure responses use RFC9457. Operations are not automatically retried after uncertain network outcomes.

## Database
New additive Liquibase changeset 014: `friend_connections`, `social_blocks`, `friend_request_events`. No existing migration is edited. It must be applied only to a fresh disposable PostgreSQL instance until the owner authorizes migration of a specific database with a backup.

## Test plan
```bash
JAVA_HOME=/private/tmp/sidequest-jdk21-e2e/Contents/Home bash backend/scripts/test-postgres.sh -Dit.test=FriendshipIT,SocialProfileIT
JAVA_HOME=/private/tmp/sidequest-jdk21-e2e/Contents/Home bash backend/scripts/test-postgres.sh
(cd ui && npm test && npm run build)
JAVA_HOME=/private/tmp/sidequest-jdk21-e2e/Contents/Home bash backend/scripts/test-browser.sh
```
These commands have **not been executed** by this connected GitHub editor. The owner should record exact pass/fail counts and logs before reviewing or merging. Existing M4 test results do not constitute M5 evidence.

## Outstanding production-readiness gates
1. Implement bounded page/size listing and leaderboard pagination (initial implementation returns first 100).
2. Add full, multi-account Playwright coverage in M5-06 and validate real keyboard/mobile journeys.
3. Define IP-level abuse controls and retention/redaction of social transition events.
4. Review UI design/accessibility and verify blocking/private profile semantics after races.
5. Never deploy, merge, or migrate persistent databases without owner review.

## Teach-back
Canonical sorted user IDs make a relationship unique independently of invite direction. Acceptance is a server-side state transition allowed only to the original recipient; an existing session is insufficient unless it belongs to one of the two participants. Friends-only ranking restricts M4 ledger entries to accepted, unblocked peers and still excludes unsafe Quest rewards.

Questions: Why is cross-request acceptance never automatic? Why does blocking invalidate even previously accepted friendships? Why must a friends leaderboard reuse M4 moderation filtering instead of simply summing every user's personal lifetime XP?
