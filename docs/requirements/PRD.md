# SideQuest — Product Requirements Document v1.0
**Product:** A playful cross-platform platform for discovering, creating, joining and completing personal and social quests.
**Working pace:** 8–10 hours/week. **Roadmap:** ~8 weeks web MVP, ~16 mobile beta, ~24 portfolio-ready release; estimates subject to scope/learning.

## Personas
- **Explorer:** discovers and completes fun challenges.
- **Creator:** authors, publishes, edits, and archives challenges.
- **Organizer (later):** coordinates teams and friend groups.

## Web MVP (P0)
- Email/password account creation, login, logout, profile.
- Creator can create drafts, edit own drafts, publish and archive quests.
- Visitors can view published quests, filter and search with pagination.
- Authenticated users can join published quests and complete them.
- Self-reported completion awards XP once and shows profile progress.
- Responsive accessible UI, API validation, auth and authorization, DB migrations, core tests, CI and deployment.

## Core business rules
1. Draft quests are creator-only; only published quests are publicly discoverable or joinable.
2. Only a quest's creator may edit/publish/archive it.
3. A user may enroll in a quest at most once, enforced in the database.
4. Completing a quest is self-reported in MVP; proof is not independently verified.
5. XP may be awarded at most once per participation even under concurrent requests; preserve an XP ledger with a unique participation reference.
6. Quests with participations must be archived instead of destructively deleted.
7. Once participants exist, do not change XP award or material completion requirements in the MVP.

## Not in web MVP
Native mobile, teams, messaging, uploads, realtime, maps, AI generation, payments, microservices.

## Non-functional targets
- Secure password storage, protected API operations, server authorization, no secrets in Git.
- Paginated queries; consistent error responses and versioned REST API.
- Use Postgres constraints, transactions and migrations.
- Keyboard-friendly UI and useful error/empty states.
- Measure performance in a reproducible environment; do not claim unmeasured latency.
- Tests for validation, authorization, persistence and XP idempotency.

## MVP acceptance journey
User A registers, creates/publishes a quest. User B discovers, joins and completes it; B earns XP once; repeat completion does not award XP; unauthorized editing is rejected. Automated tests pass, and web/API are deployed.

## Planned future releases
- **M3 Mobile:** React Native + Expo with authentication, quest feed, join/complete, progress.
- **M4 Social:** friends/teams, proof images, realtime updates, notifications.
- **M5 Operations:** cloud, CI/CD, monitoring, backups, secure configuration.
- **M6 Release:** profiling, load tests, accessibility, release docs.
- **Advanced:** geo-quests, offline sync, AI, event streaming experiments.
