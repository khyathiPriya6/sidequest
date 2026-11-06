# SideQuest 🧭

A playful, cross-platform adventure platform for discovering, creating, joining and completing quests. This repository is a **learning-first software engineering project** built incrementally.

## Project status
**Sprint 01 — Foundation (in progress).** Work capacity: 8–10 hours/week.
- [Project requirements](docs/requirements/PRD.md)
- [Architecture](docs/architecture/ARCHITECTURE.md)
- [Sprint 01 plan](docs/planning/SPRINT-01.md)
- [Architecture decisions](docs/decisions/0001-modular-monolith.md)
- [Coding agent instructions](AGENTS.md)
- [GitHub issues / backlog](https://github.com/khyathiPriya6/sidequest/issues)

## Planned stack
- Web: React, TypeScript, Vite
- Backend: Java 21, Spring Boot, REST
- Data: PostgreSQL, Liquibase
- Mobile (later): React Native + Expo
- Quality: JUnit, Mockito, Testcontainers, Vitest, Playwright, GitHub Actions
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

Related integration issue: [SQ-004 / #15](https://github.com/khyathiPriya6/sidequest/issues/15).

> Never commit credentials or claim tests ran without evidence. See `AGENTS.md`.
