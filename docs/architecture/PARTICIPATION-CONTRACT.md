# E04 — Participation lifecycle and XP award contract

Issue: #57; parent epic #4. Status: proposed technical contract for review; five product policies approved by owner on 2026-11-19. Documentation only. Read PRD, QUEST-CONTRACT, AUTH-CONTRACT, API-CONVENTIONS and AGENTS.md. Existing Quest lifecycle remains unchanged. SQ-018 manual owner acceptance is recorded in #29. On 2026-11-19 the owner explicitly approved backend implementation without Figma handoff. Frontend implementation still requires design approval; #35 remains open until its actual acceptance criteria are satisfied. This backend-only authorization is not design sign-off.

## Rules and state
- No participation record means NOT_JOINED (not a persisted status). Join creates IN_PROGRESS; completion transitions to COMPLETED. No reverse transition, leave, deletion, repeat, deadlines or proof uploads in MVP.
- Only authenticated users may join published quests; creators may not join their own. Self-reported completion; no independent proof or approval.
- Archiving stops new enrollment and public discovery, but existing participants retain read access via their own participation and can complete for the original reward. No public visibility change or permission to edit the quest.
- A repeat join returns the existing participation (including completed/archived cases) and never creates a second one. This is retry semantics, not permission to replay a completed quest.
- Repeat completion returns the saved representation, with original completedAt and award, not a new award. No XP on joining.

## Persistence proposal
participations: UUID id PK (server generated), user_id UUID FK users, quest_id UUID FK quests, status IN_PROGRESS/COMPLETED, reward_xp INTEGER snapshot 1–1000, joined_at TIMESTAMPTZ not null, completed_at nullable TIMESTAMPTZ. All ownership and reward fields server-managed, immutable. UNIQUE(user_id, quest_id); check status/time consistency (IN_PROGRESS requires null completion; COMPLETED requires nonnull completion >= joined_at). Restrict FK deletes. Index (user_id, joined_at DESC, id DESC); add status-specific index only if justified.
xp_ledger: UUID id PK, participation_id UUID NOT NULL UNIQUE FK participations (delete restricted), amount INTEGER NOT NULL CHECK 1–1000, awarded_at TIMESTAMPTZ NOT NULL. Append-only in application; user derives from participation. No mutable total balance or separate user_id required. Ledger amount equals participation snapshot; completion+ledger written in one transaction. Cross-table consistency enforced in service and tested (ordinary CHECK cannot enforce cross-row relations). Totals later use SUM with bigint/long; no reward reversal in MVP. Use new immutable Liquibase changesets; do not alter existing ones. Run against isolated PostgreSQL only unless owner separately approves existing DB migration.

## API
All paths relative to /api/v1; authenticated session via existing principal, unsafe methods require X-CSRF-TOKEN; no client user ID, XP, timestamps or status input. Update explicit security allowlist for new routes, retain deny default. POST operations have no request body; do not use ETag/If-Match for these idempotent action endpoints. No JWT. GET endpoints are private; avoid shared caching (Cache-Control: no-store).

| Method/path | Success | Notes |
|---|---|---|
| POST /quests/{id}/join | 201 first; 200 existing | 201 Location: /api/v1/me/participations/{participationId} |
| GET /me/participations | 200 | page=0, size=20 max100, optional status=IN_PROGRESS or COMPLETED; joinedAt desc, id desc |
| GET /me/participations/{id} | 200 | Owner only |
| POST /participations/{id}/complete | 200 | First or repeat completion |

Representation used for all single responses and list content entries:
```json
{
  "id": "018f47d1-7c61-7e00-9a7c-000000000010",
  "questId": "018f47d1-7c61-7e00-9a7c-000000000001",
  "status": "IN_PROGRESS",
  "rewardXp": 100,
  "joinedAt": "2026-11-19T06:00:00Z",
  "completedAt": null,
  "awardedXp": 0,
  "quest": {
    "title": "Explore a new walking route",
    "description": "Discover a new route and enjoy a thirty-minute walk.",
    "category": "ADVENTURE",
    "difficulty": "EASY",
    "status": "PUBLISHED"
  }
}
```
On completion status COMPLETED, completedAt UTC ISO instant, awardedXp 100. The nested quest summary is needed for archived access; no creator email or private participant data. Quest status reflects current Quest state, while rewardXp is frozen at enrollment. A repeat response retains participation status, timestamps and reward; nested Quest status can change independently. List envelope follows API-CONVENTIONS (content,page,size,totalElements,totalPages,hasNext,hasPrevious). Reject invalid enum, UUID, pagination, overflow as 400. For UI lookup on public details, proposed optional questId UUID filter on the owner list, combined with status and pagination; returns empty list for not joined (no disclosure about another user's records). No public participation lists.

Errors: RFC 9457 with stable code. Missing session on protected GET or valid-CSRF write 401 unauthenticated; invalid/missing CSRF on unsafe request 403 csrf_invalid may precede auth. Join: first check caller's existing participation (return 200 even if archived); otherwise nonexistent or nonpublic inaccessible Quest 404 not_found (including archived for nonparticipant), creator's own published Quest 403 self_enrollment_forbidden. Creator's own nonpublished Quest may return 409 quest_not_joinable as owner already knows existence; use 404 for nonowner private resources. Completion/read by another user or missing participation 404 not_found, even if Quest is public. No exposure of private user identifiers in errors. No automatic retry of uncertain frontend mutations; recover using owner list/detail.
Example: {"type":"about:blank","title":"Forbidden","status":403,"detail":"You cannot join a quest you created.","instance":"/api/v1/quests/018f47d1-7c61-7e00-9a7c-000000000001/join","code":"self_enrollment_forbidden"}.

## Transaction and concurrency strategy
Join transaction: acquire pessimistic write lock on Quest row (same row as archive writes); after acquisition read current persisted state and look for existing (user,quest); return existing if present; enforce creator/public eligibility, snapshot reward, insert and flush. All archive mutations must serialize with join through the same database row lock (verify existing optimistic-lock update behavior and explicitly align lock protocol if necessary). If archive commits first, no new join; if join commits first, participation remains eligible to complete. Unique constraint is final safeguard. If insert races and violates unique constraint, recover in a fresh transaction after rollback; don't continue an aborted PostgreSQL transaction. No changes to existing Quest editing restrictions.
Complete transaction: select owner-scoped participation FOR UPDATE; if absent return 404; if completed, return saved award; otherwise verify IN_PROGRESS, use snapshot amount, set completedAt/status and insert ledger record, flush and commit together. No Quest row lock required for completion because archival does not revoke eligibility. If award fails, rollback participation update. Repeated/concurrent requests produce a single ledger entry and original completion time. Treat an inconsistent completed-without-ledger record as server integrity failure; do not silently mint another reward. No asynchronous broker, distributed lock or cache required. Handle transaction/lock failures with safe generic errors; never claim success before commit.

## UI handoff
Existing Explore/detail and My Quests design: guest Join -> login -> return -> explicit Join; creator cannot join own; eligible published quest CTA Join; joined CTA Complete; completed CTA Completed with reward history (do not show replay as fresh award). In Progress, Completed, My Creations tabs; archived participant view supplied by private participation route with archive label and completion still enabled. Confirmation explains self-reported completion is permanent. Pending, disabled, error, retry/reload, empty and session expiration states; keyboard/focus and responsive checks. Use original Figma visual direction, review updated states and record owner sign-off before product UI implementation. Reward progress, levels, XP history and achievements are separate contract/story; no new formulas adopted here.

## Acceptance and regression matrix
- First join 201, repeat 200 same id, concurrent join exactly one row; completed repeat does not reset state.
- Guest + CSRF behavior; self-join denied; nonowner draft/archived concealed; published available; owner-private visibility as above.
- Join/archive race both orderings; existing archived participant read/complete allowed, anonymous archive detail still hidden; no reward changes.
- Owner-only list/detail/complete, status and questId filters, pagination and oversized offsets, no cross-user access.
- First and repeated completion, concurrent requests in separate real transactions/connections, ledger uniqueness, exact snapshot reward, unchanged timestamps; intentional award insert failure rolls back status.
- Invalid body/parameters, no client-supplied authority or reward, safe errors; unknown/new routes denied unless explicitly approved.
- PostgreSQL constraint/migration repeat tests; API integration tests with independent sessions; later real UI journey with creator, participant and anonymous contexts. Report actual results; no performance claims without measurement.

## Teach-back
Database uniqueness guarantees no duplicate records even if two requests arrive together; row locking serializes conflicting decisions; a transaction commits completion and XP as a unit. Idempotency means repeated action has no additional effect, not identical HTTP status or identical live Quest metadata.
1. Why does a pre-insert existence check alone fail under concurrency?
2. Why must the ledger insert share the completion transaction?
3. How does locking the Quest row prevent enrollment after archive?
