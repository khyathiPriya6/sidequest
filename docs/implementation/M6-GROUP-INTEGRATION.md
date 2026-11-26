# M6 — Teams and Group Quest Challenges: Integration Review

**Status:** Complete coordinated implementation candidate; no merge, deployment or persistent migration yet. Issues: [M6-01 #116](https://github.com/khyathiPriya6/sidequest/issues/116), [M6-02 #117](https://github.com/khyathiPriya6/sidequest/issues/117), [M6-03 #118](https://github.com/khyathiPriya6/sidequest/issues/118), [M6-04 #119](https://github.com/khyathiPriya6/sidequest/issues/119), [M6-05 #120](https://github.com/khyathiPriya6/sidequest/issues/120), [M6-06 #121](https://github.com/khyathiPriya6/sidequest/issues/121). Owner explicitly requested one coordinated M6 branch; this is an exception to one-issue/one-PR normally mandated by AGENTS.md. See [M6 contract](../architecture/M6-GROUP-CONTRACT.md).

## Files and behavior

- Migration `015-group-teams.sql`: teams, member roles/state, versioned invitations, team/Quest challenges, integrity constraints and indexes. All migrations additive. Only safe to apply to freshly created disposable test DB without separate owner approval.
- `group/GroupService.java`: atomic team create; friend-only invitations and acceptance; member role and consent checks; one invite per team/user; 20-member capacity; membership/archiving lifecycle; per-owner active team limit; 10 invites/day; immutable challenge targets; safe Quest moderation eligibility; existing XP only; bounded private progress/ranking lists.
- `group/GroupController.java`, `GroupErrors.java`, `GroupFailure.java`: private session/CSRF REST APIs, no-store, Problem Details, 404 for unauthorized IDs.
- `ui/src/features/groups/groupsApi.ts`, `GroupPages.tsx`: typed client, private teams listing, creation, invitation acceptance/rejection, owner sent-invite cancellation, roster, member removal/leave, team archiving, published Quest challenge creation, progress and ranking. Routes: `/teams` and `/teams/:id`.
- `GroupJourneyIT.java`: real PostgreSQL+HTTP permission, abuse, membership, XP, moderation, duplicate and concurrency tests, including two simultaneous accepts for last seat.
- `groupsApi.test.ts`: API-response validation, CSRF and no automated replay.
- `groups-real.spec.ts`: real distinct browser sessions, friend-gated team invitations, outsider and guest access denial, member acceptance and leaving.

## Rules and clarifications

1. **No new team XP ledger exists.** Team scores derive from already awarded personal XP, after challenge creation and before deadline. A repeat completion never creates a new ledger entry.
2. Only **PUBLISHED + APPROVED + no open reports** Quests may start a challenge. Once started, approved archived Quests may still contribute for enrolled participants, consistent with M4; a subsequently hidden/open-reported Quest contributes zero and has a redacted team challenge title. Personal historic XP stays intact.
3. Team leadership is fixed. The owner can archive but not leave; any nonowner may leave. Archived teams stop invitations/challenges/removals, with history read-only. Team access remains member-private.
4. Invite acceptance requires a currently accepted, unblocked friendship with the owner. A previously issued invitation cannot bypass a later block. Team title is visible only to invited recipients or active members.
5. Creation of separate group challenges is a **tracking layer**. It does not auto-enroll group members in Quests, mint group bonuses or create an alternative moderation/publishing process.
6. Team challenge creation UI currently offers the first page of published Quests; advanced Quest search/selection, owner-to-member moderation policies, team block interaction after membership, and social audit retention need explicit product review before public deployment.

## REST surface

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/teams` | Any authenticated active account, 10 active-team limit |
| GET | `/api/v1/me/teams` | Own active memberships |
| GET | `/api/v1/teams/{id}` | Active member |
| GET | `/api/v1/teams/{id}/members` | Active member |
| POST / GET | `/api/v1/teams/{id}/invitations` | Owner, active friend recipients only |
| GET | `/api/v1/me/team-invitations` | Recipient-only pending invitations |
| POST | `/api/v1/me/team-invitations/{id}/accept|reject|cancel` | Recipient decisions; cancel by owner; CAS version |
| POST | `/api/v1/teams/{id}/leave` | Active member other than owner |
| POST | `/api/v1/teams/{id}/members/{userId}/remove` | Active team owner |
| POST | `/api/v1/teams/{id}/archive` | Active team owner |
| POST / GET | `/api/v1/teams/{id}/challenges` | Owner creates; member reads |
| GET | `/api/v1/teams/{id}/leaderboard` | Active member |

Page/size default 0/20, maximum size 100. All social/team resources remain authenticated.

## Verification

Connected GitHub tool editing does **not** execute code. **No M6 test or build success is claimed** here. Owner/CI must run from the M6 branch with Java 21 and disposable PostgreSQL:

```bash
git fetch origin
git worktree add ../sidequest-m6 origin/feat/m6-group-quests-complete
cd ../sidequest-m6
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
"$JAVA_HOME/bin/java" -version

(cd ui && npm ci && npm test && npm run build)
bash backend/scripts/test-postgres.sh
bash backend/scripts/test-browser.sh groups-real.spec.ts
bash backend/scripts/test-browser.sh
```

The browser test harness uses a disposable `sidequest_browser_test` DB; PostgreSQL integration harness uses `sidequest_isolated_test`. Do not run Liquibase against a standing dev/prod DB. Verify no Vite process already owns port 5173.

## Review/acceptance gates

- [x] Implement M6-01 contract and data/privacy architecture
- [x] Implement M6-02 private teams + friend-gated invitations with concurrency locks
- [x] Implement M6-03 approved group Quest challenges; no extra XP
- [x] Implement M6-04 derived progress without duplicate rewards
- [x] Implement M6-05 private team ranking, member-facing UI and outgoing invite cancel
- [x] Add M6-06 PostgreSQL/unit/browser test coverage
- [ ] Run Java/PostgreSQL suite and record exact passes/failures
- [ ] Run frontend unit tests/build and record exact passes/failures
- [ ] Run Playwright full suite and record exact passes/failures
- [ ] Review UX against approved wireframes, mobile 390px, keyboard, focus, AA contrast, large-text, and error/empty states
- [ ] Review privacy semantics of blocking *after* team join, moderation reporting, invitation event retention/abuse controls; address release blockers
- [ ] Owner review/approval and manual merge

## Risks and design tradeoffs

This is a single large PR because the owner asked for the entire milestone together; a reviewer can still review it in architecture → persistence → backend → frontend → tests order. The immutable unique team/Quest pair simplifies ranking and prevents duplicate scoring of the same participation across reissued challenges; another run of the identical Quest requires a later product decision. The team capacity limit relies on serializing invitation acceptances with a team-row lock and rechecking active membership before insert. Invitation rate limiting uses the invitation table and is baseline, not edge/WAF-level spam protection. Do not launch publicly without privacy/abuse review.

## Teach-back

The team is a private graph of consented memberships. Inviting a friend is a pending state, not permission to access the roster. The challenge is a time-bounded query over completed, already awarded personal Quest entries. Ranking is recalculated from eligible records; moderation safety can hide group contributions without deleting earned personal XP.

1. Why do both invitations for the last team seat need to lock the same team row before checking capacity?
2. Why does creating a group challenge *not* automatically join every team member to the underlying Quest?
3. Why must a hidden Quest's group contribution disappear while its old personal XP ledger remains unchanged?
