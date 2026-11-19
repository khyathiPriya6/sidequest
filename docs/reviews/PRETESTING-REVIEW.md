# Pre-testing integration review — issue #56

Reviewed 2026-11-19. Branch: `fix/pretesting-stack-integration`. Owner approval and mainline merge remain pending. SQ-018 (#29) is open for acceptance, not marked complete.

## Integration evidence

Latest remote main was fetched again before publication: `4f6b0867db27644e0217a486e79c64d1d2debe56`. Reviewed lifecycle commit `861148d6ebcc7b0fe71e7cdea5aeff5107b80208` was 20 commits ahead and one behind main. A new branch from main merged it without conflicts in `e9a1d34cbafec3599e87299c544bf94711e585c2`. No force push, reset, or owner merge was performed. The merge brings 78 files of runtime, tests and documentation missing from main.

| Merged PR | Actual target | Consequence |
|---|---|---|
| #49 | main | Security integration present on main |
| #50 | feat/e03-security-main-integration | Authentication runtime absent from main |
| #51 | feat/e03-authentication-main-integration | Authentication UI remained on feature stack |
| #52 | feat/sq-013-search-filter | Public Quest UI remained on feature stack |
| #53 | feat/e03-auth-ui | Creator dashboard remained on feature stack |
| #54 | feat/sq-015-creator-dashboard | Draft editor remained on feature stack |
| #55 | feat/sq-016-draft-form | Lifecycle UI remained on feature stack |

Earlier contract, foundation and persistence integrations (#38, #40, #45, #48) are retained. A GitHub merged badge alone did not establish mainline integration. The candidate contains AuthController, QuestController, AuthContext, Explore/details/dashboard/draft pages and their tests. It preserves the public catalogue and backend ownership boundary.

## Resolved findings

Paths and lines refer to this candidate. Regression evidence follows below.

| Severity | Location | Impact and correction |
|---|---|---|
| P0 | Git history above | Feature-stack merges left runtime absent from main. Dedicated integration branch preserves both parents and adds remediation for owner review. |
| P1 | `ui/src/app/router.tsx:29` | Details rendered a placeholder. Route now renders API-backed QuestDetailPage; public published and owner archived records are tested with actual content. Legacy creator route redirects safely. |
| P1 | `ui/src/features/quests/questApi.ts:44` | Fabricated/reused ETags could authorize unintended updates. All versioned owner reads/writes require a canonical quoted nonnegative Java-long version, including successful mutation responses. Missing/malformed tags invalidate state; explicit recovery replaces retries. |
| P1 | `ui/src/lib/api.ts:12`, `ui/src/features/auth/AuthContext.tsx:61` | Quest failures left stale authentication state. Shared transport handles authoritative 401 independently of JSON parsing; CSRF rejection checks `/auth/me` without replaying writes. Protected destination is retained. |
| P1 | `ui/src/pages/QuestDraftPage.tsx:185` | Publish could silently omit unsaved input. Publication requires clean saved state and latest returned ETag; busy guards prevent duplicate submissions. Failed saves cannot publish. |
| P1 | `backend/src/main/java/com/sidequest/quest/QuestService.java:29` | New UUID entities use JPA merge; mapping the original instance produced null persistence timestamps in real 201 responses. Map the managed `saveAndFlush` return value. API timestamp assertions and the real browser create flow cover this. |
| P2 | `ui/src/pages/QuestDraftPage.tsx:97` | Missing title validation, UTF-16 limits, omitted field messages and unsafe navigation could lose data. Required whitespace-aware code-point validation, per-control server messages, label associations, navigation/reload/sign-out confirmation and pending-write disabling now apply. |
| P2 | `ui/src/features/auth/authApi.ts:1` | Unvalidated current-user data could establish misleading authenticated state. Register/login/me validate UUID, email and canonical username before accepting the response. |
| P2 | `backend/src/main/java/com/sidequest/quest/QuestService.java:115` | Huge owner page offsets could overflow downstream pagination. Shared bound check returns 400; owner/public tests cover boundaries. |
| P2 | `ui/src/app/router.test.tsx:1` | Integrated tests referenced a missing render helper and obsolete placeholder behavior. Restored helper and assertions against routed API data; strict TypeScript compilation passes. |

## Review scope and remaining findings

Inspected the integrated production source beyond modified files: identity entities/repositories/DTOs/service/controllers, password rules/encoder, SecurityFilterChain, JDBC sessions, absolute expiration/account checks, CSRF/CORS/cookies, throttling/body limits and Problem Details; Liquibase users/session/Quest changesets and indexes; Quest entity/repositories/validation/service/controllers/concurrency; OpenAPI; React bootstrap/layout/navigation, auth context/routes/forms/API clients, all Quest pages, shared controls, health/gallery and responsive styles. Inspected test coverage and ran the suites below. This is a static review plus scoped automated verification, not a penetration test or a complete manual accessibility audit.

| Severity / state | Location | Impact and recommended action |
|---|---|---|
| P2 — open hardening | `backend/src/main/java/com/sidequest/quest/QuestController.java:41` and `:53` | Quest JSON is deserialized before field-length validation and has no application-level byte cap comparable to AuthBodyLimit. Large bodies can consume resources. Add a bounded streaming body limit and tests, or enforce a reviewed ingress limit before external deployment. Current local functional acceptance is unaffected. |
| P2 — verification gap | `backend/pom.xml:1` | Java dependency vulnerability scanning was not configured/run. Add a reviewed scanner and assess findings before deployment. Successful compilation is not security certification. |
| P2 — design acceptance pending | `docs/design/PROTOTYPE-REVIEW.md:1` | Approved visual direction does not complete every recorded prototype/handoff/accessibility checklist. Owner review and manual assistive-technology/Figma parity checks remain; no new design introduced. |

Deployment constraints retained from the approved architecture: session deserialization assumes a trusted database; restrict database access and backups. Authentication throttling is in-process and proxy/IP handling must be reviewed for the eventual deployment topology. HTTPS and secure cookies are mandatory outside the explicitly local profile. No deployment is part of this PR.

No additional blocking ownership/lifecycle/concurrency defect was found in the reviewed implementation. This does not assert absence of undiscovered defects. Quest joining, completion, awards, achievements and profile product work remain outside scope.

## Actual validation

All commands below were executed on the candidate on 2026-11-19. Java 21.0.13, local PostgreSQL 18 binaries, installed Chrome; no existing database was used or migrated.

```sh
npm test --prefix ui
npm run build --prefix ui
cd backend
JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash scripts/test-postgres.sh
# From repository root:
JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash backend/scripts/test-browser.sh
cd ui
npm audit --json
```

| Check | Actual result |
|---|---|
| Vitest | 100 tests passed, 11 files |
| TypeScript / Vite production build | Passed, no compiler failures |
| Maven unit tests | 9 passed, zero failures/errors/skips |
| Isolated PostgreSQL integration tests | 26 passed, zero failures/errors/skips; persistence, repeated migrations, constraints, auth/security and Quest APIs |
| Playwright | All 11 passed (9.3 seconds), no skips; includes two real API suites |
| npm audit | 0 reported vulnerabilities in the registry snapshot; not a Java dependency audit |
| Lint | No standalone lint command configured; not run. Strict TypeScript checks passed. |
| Manual design/accessibility, penetration testing, Java vulnerability scanner | Not run; no claim of approval or exhaustive coverage |

Logs in this execution environment: `/private/tmp/sq-review-ui.log`, `/private/tmp/sq-review-build.log`, `/private/tmp/sq-review-backend.log`, `/private/tmp/sq-review-browser.log`, `/private/tmp/sq-review-npm-audit.json`. Final browser API/PG artifacts: `/private/tmp/sidequest-browser.y0nVJC`. Temporary logs are not committed and may be removed by the operating system.

Initial runs exposed the obsolete route/helper, outdated auth fixtures, missing select-label binding, null draft timestamps, and a logout/navigation ordering race. These failures were corrected and the final suites above rerun successfully; earlier failures are not counted as passes.

The real browser tests register/login/restore/logout and expire a session; create/edit/save/preview/publish actual content; discover/search/filter; reject another user's private access and modification; reject stale versions; archive with confirmation; preserve owner archive access; conceal archives from anonymous users. Backend integration tests additionally cover invalid transitions and idempotent retries. Foundation browser tests cover keyboard navigation, dialog focus, responsive widths, reduced motion and automated accessibility. They use fixtures; real suites use the actual API and isolated PostgreSQL.

## Migration and security considerations

No schema changes or edits to existing Liquibase changesets were needed. Existing users/session/Quest migrations are included through integration and exercised only in disposable clusters. Rollback considerations remain in the identity and Quest persistence learning notes. Both isolated scripts stop only their own services. The browser harness disables ignored local configuration imports. No secrets or test credentials are committed. Shared API code never automatically resubmits uncertain writes; missing ETags require explicit recovery, avoiding accidental duplicates.

## Teach-back

A merged PR only lands in its target branch: inspect ancestry before claiming a feature is on main. ETags represent the saved server version, so a successful HTTP response with a missing version is unsafe for the next write. JPA merge returns the managed instance; lifecycle callbacks and timestamps belong to that instance. A CSRF rejection indicates a token/session mismatch, not necessarily an expired login; an independent read can determine session status without replaying the user's mutation.

1. Why can a PR be merged while none of its functionality is available on main?
2. Why must a client reload after an uncertain mutation rather than reuse the old ETag?
3. How do JPA persist and merge differ, and why does mapping the return value matter for generated timestamps?

## SQ-018 readiness

The integrated candidate is ready for owner SQ-018 acceptance review with a passing actual full-stack browser journey. #29 remains open; owner approval, mainline merge and remaining design/security follow-ups are explicitly pending. Do not describe this as production deployment readiness.
