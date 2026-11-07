# SideQuest — Coding Agent Instructions

Read `docs/requirements/PRD.md`, `docs/architecture/ARCHITECTURE.md`, the assigned GitHub issue, and any relevant ADR before changing code.

## Workflow
1. Work on **one issue per branch and pull request**. Propose a concise plan before implementation.
2. Do not invent features or change unrelated files. Explain tradeoffs and choose the simplest viable implementation.
3. Use React + TypeScript + Vite for web, Java 21 + Spring Boot for API, PostgreSQL and Liquibase for persistence; mobile later with React Native + Expo.
4. Start with a modular monolith. Do not add Kafka, Redis, Kubernetes, microservices, or external AI dependencies without an issue and ADR.
5. Implement required validation and error handling. Add meaningful tests for new behavior.
6. Run available build/test commands and report exact results; never claim tests ran if they did not.
7. Never commit secrets or use paid/cloud resources, destructive operations, deployment or migrations without approval.
8. Do not merge on behalf of the owner.

## Every PR must include
- Linked issue and acceptance criteria addressed
- Summary of changed files and architecture decisions
- Exact test commands and results, or explicit reason not run
- Security, migration, and edge-case considerations
- **Teach-back:** plain-language explanation and three questions for the learner

## Definition of Done
Acceptance criteria satisfied, tests added or reason noted, documentation updated where needed, review completed, owner approves merge.

## Design-first gate
- Read `docs/design/UI-UX-SPEC.md`, `docs/design/DESIGN-SYSTEM.md`, `docs/design/USER-FLOWS.md`, and the relevant approved wireframe before building user-facing screens.
- Sprint 0 design issues UX-001 through UX-006 must be reviewed and signed off before Quest CRUD/product UI implementation. Existing environment setup and health checks may continue.
- Do not claim that design reference documents are completed Figma mockups; actual wireframes and prototype links require creation and approval.
- Implement UI according to approved design states, accessibility and responsiveness; do not invent a new visual style without approval.
