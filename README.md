# SideQuest 🧭

A playful, cross-platform adventure platform for discovering, creating, joining and completing quests. This repository is a **learning-first software engineering project** built incrementally.

## Project status
**Authentication and Quest integration candidate — owner review pending.** The candidate includes session authentication, public quest browsing, and creator draft/publish/archive flows. See the [implementation tracker](docs/planning/IMPLEMENTATION-TRACKER.md) and [pre-testing review](docs/reviews/PRETESTING-REVIEW.md) for branch history, actual test results, and remaining acceptance gates.
- [Project requirements](docs/requirements/PRD.md)
- [Architecture](docs/architecture/ARCHITECTURE.md)
- [Sprint 01 plan](docs/planning/SPRINT-01.md)
- [Frontend foundation and design handoff](docs/design/FRONTEND-FOUNDATION.md)
- [Architecture decisions](docs/decisions/0001-modular-monolith.md)
- [Coding agent instructions](AGENTS.md)
- [GitHub issues / backlog](https://github.com/khyathiPriya6/sidequest/issues)

## Stack
- Web: React, TypeScript, Vite
- Backend: Java 21, Spring Boot, REST
- Data: PostgreSQL, Liquibase
- Mobile (later): React Native + Expo
- Quality: JUnit, Mockito, isolated PostgreSQL integration tests, Vitest, Playwright
- Cloud (later): Docker, AWS, observability; optional infrastructure only when warranted

## Development workflow
1. Choose a scoped issue from the current sprint.
2. Discuss requirements and learning goals.
3. Implement in a feature branch (personally or with a coding agent).
4. Add meaningful tests, verify results, and open a PR.
5. Read the teach-back explanation, review changes and merge after approval.
6. Update the issue and weekly learning notes.

## Roadmap
**Web MVP:** ~8 weeks. **Mobile beta:** ~16 weeks. **Production-style portfolio release:** ~24 weeks at 8–10 hours/week, subject to learning pace and scope.

## Getting started
The UI and API are in `ui/` and `backend/`. Follow [backend setup](backend/README.md) first, then [UI setup](ui/README.md). The UI calls `/api/v1/health` through Vite's configurable proxy and displays loading, online, and retryable error states. Local backend startup and PostgreSQL authentication have been verified with the ignored local configuration; no credentials are committed.

The UI uses real session and Quest APIs. The development-only `/dev/components` gallery retains presentation fixtures. Joining, completing quests, XP awards, and achievements remain outside this implementation.

Run `npm test --prefix ui` and `npm run build --prefix ui`. With Java 21, Maven, PostgreSQL binaries and Chrome installed, run `bash backend/scripts/test-postgres.sh` and `bash backend/scripts/test-browser.sh` with `JAVA_HOME` set. Both scripts create isolated test databases; the browser harness starts its own API and Vite server (port 5173 must be free). It explicitly disables importing local database credentials. Never start migrations against an existing database without owner approval.

The real browser journey passes on this candidate, but SQ-018 remains open for owner acceptance. Figma prototype review and outstanding handoff/accessibility checklists remain visible in the tracker; no pixel-perfect design sign-off is claimed.
