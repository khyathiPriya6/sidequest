# M3 / SAFETY-01 — Trust & Safety contract (proposal for owner review)

**Issue:** [#78](https://github.com/khyathiPriya6/sidequest/issues/78)  
**Status:** Proposed, not yet approved or implemented  
**Architecture:** Spring Boot modular monolith, PostgreSQL + Liquibase, existing session/CSRF and Quest API rules.

## Safety objectives and limits

- Prevent clearly unsafe public quests; make questionable content reviewable before discovery, search, joining or mobile display.
- Allow legitimate adventurous, creative, health and educational content. Classifiers and keyword screens can miss harmful context or flag harmless context: do not claim perfect screening.
- Provide user reporting, proportionate moderator intervention, appeal and auditable decisions, without disclosing reporter identities.
- Protect against abuse of rewards, reports and leaderboards. Existing one-participation-per-user and immutable exactly-once XP ledger remain authoritative.
- No external AI/moderation API, paid service, new queue broker or production migration is authorized by this contract.

## Policy taxonomy

| Code | Disallowed public quest content | Review considerations |
|---|---|---|
| `DANGEROUS_ACT` | Reckless driving, unsafe stunts, deliberate injuries, extreme hazardous activity | Ordinary exercise/outdoor adventure is not inherently disallowed |
| `SELF_HARM` | Encouragement or instructions for self-harm or suicide | Support and awareness challenges can be benign |
| `ILLEGAL` | Trespassing, theft, vandalism, evading law enforcement or other unlawful behavior | Local context matters; don't assume illegality from keywords alone |
| `VIOLENCE_HARASSMENT` | Threats, bullying, hazing, coercion, targeted humiliation or incitement | Personal safety takes priority over game engagement |
| `HATE` | Targeted hateful or discriminatory challenges | Evaluate actual target/context, not isolated words |
| `EXPLOITATION` | Sexual exploitation, grooming or coercive sexual tasks, especially concerning minors | Escalate urgent child safety concerns using appropriate platform process |
| `FRAUD_SPAM` | Phishing, scams, gambling manipulation, referral farming or fake reward schemes | Legitimate financial education is not automatically fraud |
| `PRIVACY` | Doxxing, recording/sharing another person's private data, stalking or location tracking without consent | User-owned information and consent need careful context review |
| `OTHER` | Other reasonably documented credible harm or circumvention | Written rationale required for moderation decision |

Policy applies to titles and descriptions initially; if images, comments, groups or AI generation are added, apply compatible enforcement to those inputs before release. Show clear creator-facing community rules and policy version.

## Keep Quest lifecycle unchanged

Existing `Quest.status` remains `DRAFT | PUBLISHED | ARCHIVED`, with the current ETag/ownership semantics. Introduce a separate moderation state:

```text
UNREVIEWED -> PENDING_REVIEW -> APPROVED
                         \-> REJECTED
APPROVED -> UNDER_REVIEW -> APPROVED
                        \-> HIDDEN
HIDDEN -> UNDER_REVIEW -> APPROVED (restored)
REJECTED -> UNDER_REVIEW (appeal / corrected draft if allowed)
```

Rules:

1. **Effective public visibility** requires both `quest.status = PUBLISHED` and `moderation.status = APPROVED`. This condition MUST be applied consistently in public list/search/detail and join eligibility, including cached responses.
2. To preserve the existing creator workflow, `POST /quests/{id}/publish` remains the entry point. A new draft may be **submitted for screening** rather than immediately publicly visible. If moderation is pending, return an explicit state without implying public publication. Before implementation, decide whether `Quest.status` stays DRAFT until approval (preferred: preserves existing invariant) and whether to introduce a dedicated `submit` endpoint or an asynchronous response variant; do **not** silently change the existing publish response contract.
3. Moderation screening must execute on the authoritative backend boundary, not only in React or Expo.
4. Never allow a new Quest to become publicly visible while screening is unavailable. Return `PENDING_REVIEW` or explicit temporary failure; don't silently allow it.
5. No editing an approved/published Quest in the MVP. If editing after approval is enabled later, any material edit invalidates prior safety approval until re-review.
6. Hiding an already published Quest immediately removes it from discovery and blocks new joins. Existing participants retain private access and may complete under existing E04 rules **unless** a separate explicit safety-hold policy and owner-approved change decides otherwise. For severe dangerous content, prevent further encouragement/completion through UI and consider additional backend safeguards; do not silently rewrite earned ledger events.
7. Public access to hidden/rejected/unreviewed content uses the same concealed 404 semantics as private/draft Quest resources. Creators and moderators can see only appropriately authorized private review information.
8. Moderation decisions do not alter Quest's existing optimistic concurrency checks or immutable reward snapshots.

### Architecture choice to confirm before runtime code

Prefer `DRAFT` + `PENDING_REVIEW` until approved; only then atomically set `PUBLISHED` and `APPROVED`. This avoids exposing a published-but-not-approved Quest to older clients. Once an already-published Quest is hidden, visibility/join predicates must also check moderation state. The existing `POST publish` API currently returns a published `Quest` synchronously, so a contract/UX revision and explicit compatibility tests are required. No runtime changes until that revision is reviewed.

## Detection, triage and human moderation

- **Layer 1: deterministic validation.** Enforce existing bounds and reject obvious prohibited patterns/unsafe links with rule identifiers. Keyword matching alone is insufficient to make irreversible punitive decisions.
- **Layer 2: risk assessment.** Initially use conservative rules and manual review of uncertain material. A later classifier is optional, contingent on provider/privacy/cost agreement and measured error rates.
- **Layer 3: human decision.** A moderator can approve, reject, temporarily hide, restore or escalate. Store policy version, reason code, timestamps and moderator actor; provide a written rationale. Emergency safety escalations may immediately hide pending review under narrowly defined authority.
- **Appeals:** creator can request review of rejection/hiding. Add rate limits and a queue; reviewer action requires an audit event. Appeal should not automatically restore public visibility.
- **User reports:** authenticated users may report published content with one bounded reason and optional text. Duplicate reports by the same reporter should not amplify ranking; coordinated mass reporting should not automatically cause permanent punitive action.
- **Policy explanations:** creator receives actionable category-level feedback, but do not expose bypassable detection details or reporter identities.
- **Operational limit:** without human moderation capacity, hold ambiguous submissions from public discovery instead of claiming auto-approval is safe.

## Suggested data model (future Liquibase migration, not yet authorized)

`quest_moderation`: `quest_id` unique FK, `state`, `policy_version`, `reason_code`, `version`, `submitted_at`, `decided_at`, `last_reviewed_at`. For approvals/rejections, store actor ID when appropriate; protect sensitive evidence.

`quest_reports`: UUID ID, Quest FK, reporter FK, reason enum, bounded optional notes, createdAt, triageStatus; unique active report per reporter/quest (or unique reporter/quest for MVP), with indexed Quest and triage state. Do not expose reporter identity publicly or to Quest owner.

`moderation_events`: append-only UUID event, target Quest, moderator actor/system source, previous/next state, policy version/reason, action timestamp, optional restricted internal note. Log decisions without duplicating sensitive content unnecessarily.

`moderation_appeals`: UUID ID, Quest FK, creator FK, reason, state, timestamps; at most one pending appeal per moderation decision.

Constraints and access controls must live in PostgreSQL/Spring Security, not frontend-only. Design data retention/deletion and access restrictions before storing raw private evidence. Add migrations only on a new, isolated test database after explicit approval.

### Legacy-content rollout

Existing Quest rows have no moderation state. **Never** default legacy content to approved in a new public query without an explicit policy. Choose and document a controlled rollout: backfill to `PENDING_REVIEW` and pause publication pending review, or batch-review existing published quests before switching to enforced visibility. Test migration repeatability, rollback strategy and readiness on isolated PostgreSQL; no existing developer/production DB modifications without approval.

## Proposed API contracts (new versioned endpoints; exact DTOs to be approved)

| Method | Route | Permission | Meaning |
|---|---|---|---|
| `POST` | `/api/v1/quests/{id}/reports` | Authenticated | Create a deduplicated report for a visible Quest; CSRF, bounded text, rate limit |
| `GET` | `/api/v1/me/quest-moderation` | Creator | See own submitted Quest moderation status/reason, paginated |
| `POST` | `/api/v1/me/quests/{id}/appeals` | Creator | Request appeal without making the Quest visible |
| `GET` | `/api/v1/moderation/queue` | Moderator role only | Paginated, prioritized queue |
| `POST` | `/api/v1/moderation/quests/{id}/decisions` | Moderator role only | Approve/reject/hide/restore with concurrency guard and audit |
| `GET` | `/api/v1/moderation/quests/{id}/events` | Moderator role only | Restricted audit history |

Use the existing Problem Details format, owner-only 404 behavior, session cookies, CSRF and stable pagination. Moderator permissions must be server-assigned roles/authorities, **never** derived from request fields, username patterns or client toggles. Add a bootstrap strategy for the first moderator without open self-registration escalation. No public moderation notes/reporter details. Consider request-scoped idempotency for decisions and safe retry behavior.

## Abuse and XP fraud

- Rate limit Quest create/submit, Join/Complete, report and appeal by server-verified account and coarse IP signal; support shared networks and avoid exposing account identity to other users.
- Preserve E04's unique participation and unique immutable XP ledger award per participation; suspicious behavior should result in review/eligibility hold, not deleting ledger events.
- Leaderboards must explicitly filter disqualified/under-review rewards or actors and make only aggregated, opted-in rankings public. Avoid rewards for speed, hazardous acts or expensive proof.
- Monitor high-frequency quest creation, repeated self-like accounts, unusual completion bursts and coordinated reporting; signals alone are insufficient evidence for permanent bans.
- No silent fingerprinting/precise location tracking, new high-risk device identifiers or permanent blacklisting without a separate privacy/security review.

## Threat model and tests required before public release

| Threat or failure | Expected control / test |
|---|---|
| Unsafe Quest published directly via API | Backend screening prevents public visibility; bypassing UI cannot bypass review |
| Screening service times out | Fail closed: no new public availability |
| Hidden Quest remains in search or join | All public predicates enforce moderation AND published; join/archive/hide race tests |
| Reporter spams or mass-reports a competitor | Deduplication/rate limit; reports require review, no automatic permanent penalty |
| Forged moderator request or horizontal access | 401/403/404, CSRF, server-assigned role, no leak of reports |
| Concurrent moderator decisions | Row lock/optimistic version; one deterministic decision/audit chain |
| Old approved content materially edited | Re-review mandatory before rediscovery |
| Duplicate Quest completion | Existing ledger unique event, exactly-once XP; moderation hold does not mutate earned history |
| Malformed/malicious text in reports | Bounded input, output escaping, no SQL injection/XSS, safe logging |
| Legacy Quests missing moderation state | Explicit backfill and fail-closed behavior; no implicit approval |
| Reviewer abuse / accidental hide | Append-only audit, scoped roles, appeals and restoration review |

## Implementation PR sequence (after owner contract approval)

1. **SAFETY-02:** Liquibase schema, model, policy validator and isolated PostgreSQL tests. Add safe legacy handling and explicit review states.
2. **SAFETY-03:** Publish screening + visibility/join enforcement; API and UI contract update; concurrency and fail-closed tests.
3. **SAFETY-04:** Reports, dedupe/rate limits, creator status and user-facing report action.
4. **SAFETY-05:** Moderator queue/actions/audit, review permissions and appeals.
5. **SAFETY-06:** Abuse monitoring/ranking eligibility controls and end-to-end/moderation security acceptance.

No deployment, paid provider, migrations of existing databases or PR auto-merge is authorized. E04-09 and E04-10 remain draft/unverified.

## Teach-back

**Why separate moderation from Quest status?** Quest status describes the creator's publication lifecycle. Moderation describes safety clearance. Keeping them separate lets a moderator hide a dangerous quest without deleting participation and XP history.

**Questions**
1. How do public listing and Join predicates prevent a hidden quest from being discoverable or joinable even if its Quest status remains `PUBLISHED`?
2. Why can a mass-report count never automatically prove that a Quest violates policy?
3. How can a moderation decision be audited without exposing reporter identities or rewriting the immutable XP ledger?
