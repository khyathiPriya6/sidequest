# M6 — Group Quests and Challenges Implementation

Related work: M6-01 #116, M6-02 #117, M6-03 #118, M6-04 #119, M6-05 #120, M6-06 #121. The user asked to deliver these together; the combined branch is an explicit exception to `AGENTS.md`'s usual one-issue-per-PR rule.

## Features
- **Teams:** authenticated player creates a private team; the creator becomes its non-removable owner; each team gets a stable UUID and a maximum of 50 active members.
- **Invitations:** owner invites by exact existing username only; recipient accepts/declines; owner can list/cancel outgoing pending invites; no automatic friendship or membership. Blocking between owner and invitee prevents acceptance. Owner may remove members; members can leave voluntarily; ownership transfer/deletion intentionally deferred.
- **Challenges:** owner attaches a published, human-approved Quest to a team with a bounded UTC time window (maximum 90 days); private, draft, hidden, unapproved, reported or archived Quest content is not eligible for creation or later member listing. Group challenges do not duplicate the Quest record.
- **Progress:** only accepted members' actual completed participations within the challenge window, with already-awarded ledger entries, contribute to group progress.
- **Ranking:** read-only owner/member leaderboard, members-only access and deterministic ties; awarded XP cannot be claimed a second time. Unsafe/moderated Quests excluded from ranking.
- **UI:** protected Teams list, create team, accept/decline invitations, team details, roster, owner invite/cancel/remove, member leave, create challenge, read progress and rankings. Uses existing dark-first SideQuest card/button/router/navigation design. No new images or dependencies.
- **Tests:** `backend/src/test/java/com/sidequest/team/TeamIT.java` covers consent, access boundaries, moderation, XP once-only, member removal, invitation cancellation, pagination, race on acceptance; `ui/e2e/teams-real.spec.ts` covers three separate real browser sessions and team authorization.

## API
All routes are `/api/v1` and require an authenticated session. Writes use existing Spring CSRF and response caching is disabled.
| Method | Path | Access |
|---|---|---|
| POST | /me/teams | signed-in member |
| GET | /me/teams?page=&size= | signed-in member |
| GET | /teams/{id}, /teams/{id}/members | current team member |
| POST | /teams/{id}/invites | owner |
| GET | /teams/{id}/invites | owner |
| POST | /teams/{id}/invites/{inviteId}/cancel | owner |
| GET | /me/team-invites | signed-in recipient |
| POST | /me/team-invites/{id}/accept or /decline | invitee |
| DELETE | /teams/{id}/members/{username} | owner for other users, member for self |
| POST | /teams/{id}/challenges | owner |
| GET | /teams/{id}/challenges | member |
| GET | /teams/{id}/leaderboard | member |

All lists are bounded, 20/page default and 100/page maximum with deterministic order, count and pagination metadata. Team IDs are opaque and must not appear in a global public team directory. Unknown and unauthorized team IDs return an indistinguishable 404.

## Persistence and concurrency
Liquibase changeset `015-teams.sql` adds `teams`, `team_members`, `team_invites`, `team_challenges`, and indexes/checks. No change to existing Quest, participation or XP schemas. Team row locks serialize capacity and invitation transitions; uniqueness prevents duplicate memberships and duplicate Quest challenges. Only the owner can add members by invitation, but the recipient alone decides to join.

**Migration gate:** do not run changeset 015 against an existing developer, staging or production database without an explicit backup/review. Test scripts provision disposable PostgreSQL databases.

## Execute verification
From a clean M6 checkout, with JDK 21, PostgreSQL binaries and Playwright Chrome installed:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
(cd ui && npm ci && npm test && npm run build)
bash backend/scripts/test-postgres.sh
bash backend/scripts/test-browser.sh
```

Focused tests:
```bash
bash backend/scripts/test-postgres.sh -Dit.test=TeamIT
bash backend/scripts/test-browser.sh teams-real.spec.ts
```

**Status:** Code and tests have been added through the connected GitHub editor. No Java, PostgreSQL, Vitest, TypeScript build, or Playwright tests have been executed in this environment. Do not merge or deploy merely because test files exist.

## Review and release gates
- [ ] Check that M6 implementation builds and all tests pass, including existing M1–M5 regression.
- [ ] Confirm member invitation and challenge concurrency with disposable database.
- [ ] Review copy, mobile/keyboard UI, 200% zoom, screen-reader labels, privacy edge cases.
- [ ] Define retention/abuse controls for sending many team invites and secure resource budget beyond simple capacity checks.
- [ ] If persistent deployment is planned, review migration, backup, and monitoring first.
- [ ] Resolve any E2E flakes, authorization gaps, idempotency and query performance warnings before owner merge.
- [ ] Owner approves and merges draft PR.

## Explicit non-goals
Realtime collaboration, chat, image uploads, proof verification, team ownership transfer, a public team directory, payments, new XP awards, automated group Quest generation and notification infrastructure.

## Teach-back
Group challenges are *views* over an already-approved Quest and its existing participation/XP records. They do not create second award transactions. Membership and invitation consent are separate authorization concerns. Database uniqueness and row locks are needed because browser UI buttons alone cannot prevent concurrent invitations/acceptances.

Questions:
1. Why does a team challenge reference the original Quest instead of duplicating its reward logic?
2. Why must only the recipient accept an invitation even if the owner created it?
3. How do PostgreSQL locks and unique keys stop two accepted invitations from bypassing team capacity?
