# M6 — Teams and Group Quests Contract (proposed review baseline)

**Scope:** Opt-in teams, team membership invitations, time-limited challenges referencing existing approved published Quests, aggregated team progress, friends-compatible team leaderboard, and responsive UI. M6 is an optional post-MVP expansion, not the original PRD's similarly named release phase.

## Invariants
- A team has one owner (creator); membership is opt-in; no automatic joining or friend-data disclosure.
- Each account is limited to one membership per team; invites are addressed to the invitee's exact username. Only team owner may invite; only recipient can accept/decline; inviter can cancel. Existing blocked relationships cannot be used to force an invite.
- Team owner may remove members. Owner cannot abandon an active team without transferring ownership; explicit ownership transfer is later scope. Limit member count (50) and outstanding invitations (50) to protect abuse.
- Teams have an opaque UUID and non-public roster: only current members can read roster and challenges. No global directory; team names must not reveal other users' identities. Owner and users always derive from authenticated session.
- Group challenges reference *existing* approved and PUBLISHED Quest IDs; never duplicate/redefine completion requirements, rewards, or participation. A challenge has a UTC startsAt/endsAt interval; publishing is not implicitly granted by challenge creation.
- Group progress is a **read-only projection** derived from users' existing completed participations and awarded XP ledgers, filtered to approved safe quests. Never award XP again for joining/completing a group challenge.
- A challenge counts only completions during its active interval by accepted team members. A removed member's contribution is hidden from current team rollups by default; no disclosure of private personal history.
- One owner can create a team; all member-facing writes require existing HTTP session and CSRF. Restrict access with indistinguishable 404 for not-member and unknown team IDs.
- No realtime, chat, proof uploads, arbitrary location tracking, external invites, notifications, or new gamification currency.
- Read endpoints use bounded pagination with deterministic ordering; API errors use Problem Details with no-store response.
- Concurrency: unique constraints on (team,user), (team,invitee), and (team,quest) prevent duplicates; row locks/versioned state transitions prevent double acceptance and exceeding team capacity. Maintain strict lock order.
- Rate limit invitation creation and audit membership transitions; production rollout requires data retention review and IP-level abuse controls.

## API proposal (version v1)
- POST /api/v1/me/teams: {name} => owner-owned team
- GET /api/v1/me/teams?page=&size=: teams joined as member
- GET /api/v1/teams/{id}: member-only metadata and roster
- POST /api/v1/teams/{id}/invites: {username}; owner-only
- GET /api/v1/me/team-invites?page=&size=: incoming invitations
- POST /api/v1/me/team-invites/{id}/{accept|decline}: recipient-only
- POST /api/v1/teams/{id}/invites/{inviteId}/cancel: owner-only
- DELETE /api/v1/teams/{id}/members/{profileId}: owner removes member, member may leave if non-owner
- POST /api/v1/teams/{id}/challenges: {questId, startsAt, endsAt}; owner-only and approved/public quest validation
- GET /api/v1/teams/{id}/challenges?page=&size=: member-only
- GET /api/v1/teams/{id}/leaderboard: member-only ledger-derived totals; no raw transaction history

## Delivery
M6-01 contract; M6-02 membership/invitation persistence + APIs; M6-03 safe Quest challenge creation; M6-04 projection queries; M6-05 UI and leaderboards; M6-06 PostgreSQL and browser acceptance. Owner reviews the draft as a single integrated milestone by explicit request. UI must follow existing design tokens; review for accessibility and mobile state. No persistent database migrations until approved.

**Teach-back:** Membership controls authorization. Challenges do not mint XP—they reuse the already-once-only awarded ledger. A team's leaderboard never implies that users' private history is public.

Questions: Why does a group challenge reference a Quest rather than create a second XP ledger? Why should a blocked member not be auto-invited? Why must member count enforcement be transactional?
