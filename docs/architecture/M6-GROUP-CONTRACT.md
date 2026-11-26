# M6 — Teams, invitations and group Quest challenges

**Status:** Implementation proposal in combined M6 PR, requested as one coordinated delivery by the owner. Follow-up approval required before merge or persistent migrations. Parent issues #116–#121. M6 numbering is the expanded SideQuest roadmap, distinct from PRD v1 milestone labels.

## Boundary
M6 introduces private teams (2–20 members), consent-based invitations, approved Quest challenges, progress and team ranking. It does NOT introduce new XP grants, public team directories, upload proof, messaging, realtime, payments, team-created bypasses of Quest moderation, or automatic enrollment in a Quest. Personal participation and XP remain governed by M1–M5.

A team has an active creator/owner and members. Membership must be accepted explicitly. Team titles are private; listing/reading a team requires active membership. Invitations can go only to an existing accepted friend of the owner, without an intervening block; an invite contains minimum team name and owner name for recipient. No email or full account discovery. Team ownership never transfers in M6: owner cannot leave or be removed; the team can be archived, which stops new membership/challenges without deleting history. Owners can remove any member. Members can leave; removed members are no longer allowed to read team data. No silent rejoin; a fresh invitation is required.

## Team lifecycle and concurrency

- `POST /api/v1/teams` creates an ACTIVE team with creator OWNER. Name 3–80 chars; owner creates at most 10 active teams. Canonical UUID ID.
- `GET /api/v1/me/teams?page=0&size=20`, `GET /api/v1/teams/{id}` list only active memberships.
- `POST /api/v1/teams/{id}/invitations` owner sends an invitation with exact username. Active accepted friendship and no block required at send; both conditions rechecked on acceptance. One open invitation per team/user. Owner sends at most 10 invites/day. Invite acceptance locks the team and membership rows; max 20 active members, one active membership per (team, user), no duplicate rows.
- `GET /api/v1/me/team-invitations`; `POST /api/v1/me/team-invitations/{id}/accept|reject`; owner may cancel with team-scoped endpoint. Other actors receive identical 404 for unknown/unauthorized IDs.
- `POST /api/v1/teams/{id}/leave` members only; `POST /api/v1/teams/{id}/members/{userId}/remove` owner only; `POST /api/v1/teams/{id}/archive` owner only.
- No automatic XP removal or reassignment on leave, remove, archive, or block. Active membership is always required to view team data. All writes require authenticated session and existing CSRF protection.

## Group Quest challenges

- Team owner may create a challenge referencing an **existing PUBLISHED, APPROVED, not currently open-reported** Quest. Creating a challenge **does not auto-join any participant**.
- Challenge has creation time and immutable `ends_at` (1–30 days after creation), target completions 1–20. At most 20 active challenges per team and unique team/Quest pair while active. Team scope only; owners cannot alter challenge rules or score after creation.
- Challenge progress counts unique current team members with existing `COMPLETED` participations and eligible `xp_ledger` entry at or after challenge creation, before the deadline; no extra XP issued. Unapproved, hidden or open-reported Quests contribute **zero**, without rewriting a participant's own XP. This means prior completion does not count retrospectively.
- Progress and ranking are read-only derived projections, not mutable reward tables. 100% of target is capped; total eligible unique completions are also returned. Team challenge results remain member-private. `GET /api/v1/teams/{id}/challenges?page=0&size=20`; `GET /api/v1/teams/{id}/leaderboard` ranks members by eligible challenge XP, with deterministic username tie order. No cross-team public leaderboard in this first slice.
- Challenge expiry is computed from server clock; it cannot be extended via UI. Archived teams preserve history read-only.
- Quest sharing uses existing approved Quest URLs. Any member can independently join or complete through normal participation endpoints.

## Storage and privacy
Add Liquibase changeset 015: `teams` owner/state/version, `team_members` unique (team,user) ACTIVE/LEFT/REMOVED, `team_invitations` unique (team,recipient) PENDING/ACCEPTED/REJECTED/CANCELLED with version, `team_challenges` immutable Quest/target/deadline. Foreign keys RESTRICT, indexes for owner/membership/invitation and challenge queries. Lock team before any membership or invitation transition to maintain capacity and avoid cross-request races.

Use established Problem Details: unauthorized resources 404, invalid input 400, conflict 409, rate limit 429. No-store for private team responses. All pageable APIs must enforce size 1–100 and offset bound.

## Threat scenarios and verification
- Intruder cannot inspect team, roster, challenges or rankings by guessing UUID. Blocked/non-friends cannot be invited or accept stale invites.
- Two accept requests cannot exceed capacity, duplicate memberships or create extra XP. Stale invite version is 409.
- Non-owner cannot invite, remove or archive; owner cannot leave or remove self. Disabling an account removes effective access and eligibility.
- Private/moderation-blocked Quest cannot be added to a challenge; a subsequent hide/report removes its scoreboard contribution.
- Re-requests after rejected invitation require cooldown/owner decision; invitation spam and replay prevention require integration tests.
- No M1–M5 regression; track actual counts rather than assuming.

## Implementation breakdown
- M6-01 contract and privacy model (#116)
- M6-02 team/membership/invitation backend (#117)
- M6-03 team challenge backend (#118)
- M6-04 derived group progress (#119)
- M6-05 member-facing UI and ranking (#120)
- M6-06 PostgreSQL concurrency and browser acceptance (#121)

## Teach-back
Teams are private membership graphs; challenge progress is a projection of already awarded XP rather than a new wallet. Membership lock + constraints protect capacity and uniqueness.

Questions: Why do we recheck friendship at invitation acceptance? Why should a moderation-hidden Quest stop counting toward group ranking without deleting personal XP? Why use one team lock when two people accept the last available seat?
