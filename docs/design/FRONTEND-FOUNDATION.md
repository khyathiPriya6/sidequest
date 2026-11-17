# SQ-019 frontend foundation

Issue: [#36](https://github.com/khyathiPriya6/sidequest/issues/36). Owner approved implementation in the November 17 conversation. Scope: reusable presentation, routing, and navigation; no Quest CRUD or backend/database changes.

## Architecture and reuse

```text
ui/src/
  app/                 Router, route metadata, navigation, responsive layouts
  pages/               Explore shell, feature placeholders, not-found page
  components/ui/       Button, Input, Card, Badge, Modal and scoped styles
  features/health/     Existing health request and all seven original tests
  features/quests/     Presentational QuestCard and its tests
  features/progress/   Presentational XPProgressBar and its tests
  styles/              Semantic tokens and global element defaults
  dev/                 Development-only component/navigation fixtures
  test/                Shared test setup
```

Generic controls do not depend on app routes or business features. Feature components compose those controls and take typed display props. They neither fetch nor mutate quests or earned progress. Tests stay close to the behavior they verify. The original health request, timeout, cancellation, retry, and JSON validation are preserved; only its placement/styling changed. Vite's configurable API proxy remains intact.

CSS custom properties centralize the theme; CSS Modules prevent component rules leaking into other features. Global CSS handles element defaults, readable text, focus, selection, and reduced motion. No styling framework, global state library, or data fetching abstraction was needed. React Router manages routes; Lucide provides consistent decorative icons with text labels. System fonts avoid an unverified font download or license assumption.

## Token mapping

Source: [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md), approved as the implementation baseline in this conversation. Figma styles/variables could not be inspected because the Starter-plan MCP limit blocked design-context and screenshot tools. These mappings are to the written specification, **not verified Figma variable bindings**.

| Specification | CSS variable | Value |
|---|---|---|
| background | `--color-bg` | `#0B1020` |
| surface / raised | `--color-surface` / `--color-raised` | `#151C30` / `#202A43` |
| border | `--color-border` | `#364460` |
| primary / secondary text | `--color-text` / `--color-muted` | `#F5F7FF` / `#C0CBE2` |
| primary / secondary accent | `--color-accent` / `--color-secondary` | `#A78BFA` / `#67E8F9` |
| reward / success / danger | `--color-reward` / `--color-success` / `--color-danger` | `#F6C665` / `#74D9A0` / `#FB8794` |
| UI font | `--font-ui` | System sans-serif fallback permitted by the specification |
| type scale | `--text-xs` through `--text-3xl` | 12/14/16/20/24/32/40px at default root size |
| spacing | `--space-1` through `--space-16` | 4/8/12/16/24/32/48/64px |
| field / button / card / dialog radii | `--radius-*` | 8/12/16/24px |
| content / motion | `--content-width` / `--duration` | 1200px / 160ms |

Control outlines use `--color-control-border: #8493B0` rather than the faint decorative divider color, to distinguish input/control boundaries on dark surfaces. Accent buttons use dark foreground text. This accessibility adjustment and the decorative hero treatment still need visual handoff review. The hero reuses the existing headline/empty-state copy; no unverified Figma imagery is substituted. Reduced motion disables transitions/spinners. All seven components use visible labels, not color alone.

## Component contracts and states

| Component | Props and behavior | Verification |
|---|---|---|
| Button | Native button props; primary/secondary/ghost/destructive; small/medium; loading disables repeated clicks, keeps action name and sets aria-busy; defaults to type=button | Keyboard activation, no unintended form submit, loading clicks blocked |
| Input | Native input props plus label, hint, error; generated unique ID; links helper/error descriptions; required/invalid/disabled/read-only | Controlled typing, error removal, labels/descriptions, unique IDs |
| Card | Native div props and composable children; no forced interactive role | Interactive children work without extra controls |
| Badge | Text plus neutral/accent/reward/success/danger tone | Status remains readable text |
| Modal | Controlled open/onOpenChange; title, optional description, children; native dialog top layer; initial close-button focus; explicit Tab loop; Escape/close; focus restoration and scroll lock | Real Chrome test verifies open, wrap, Escape, close button, return focus, scroll restoration |
| XPProgressBar | earned, target, optional label; clamps bar to target but reports actual supplied earned value; invalid/negative/nonfinite values show unavailable | Zero, exceeded target, invalid and nonfinite bounds; earned label differs from reward |
| QuestCard | title, description, category, Easy/Medium/Hard difficulty, rewardXp, internal href, optional artwork src/alt; one semantic title link; content fallbacks | Named article/link, missing content/artwork, earnable reward label, no fetch |

Native dialog provides background inertness. The wrapper cycles focus because relying on the browser alone allowed backward tabbing into browser chrome. Dialog actions are callbacks; no publish/archive semantics are implemented. Data/asset props must come from trusted application adapters when real features are added. Reward figures and difficulty are display inputs only and do not define the future API contract.

## Routing and responsive decisions

React Router uses a nested AppShell/Outlet, NavLink active states, and named route metadata. Each completed route transition sets the title and focuses main after browser history restoration. There is a visible-on-focus skip link. Production hosts must support SPA fallback for document routes and preserve API/static asset behavior.

| Route | Current content |
|---|---|
| `/` | Redirect to `/quests` |
| `/quests` | Themed Explore shell and existing empty state |
| `/quests/:id` | Quest-detail coming-soon placeholder |
| `/quests/new`, `/quests/:id/edit` | Create/edit coming-soon placeholders |
| `/my-quests`, `/profile` | Coming-soon placeholders |
| `/login`, `/register` | Explicitly unavailable auth placeholders; no credential inputs |
| unmatched routes | Not-found page |
| `/dev/components` | Development only; absent from the production route table/bundle |

This follows Sprint 0 routes rather than the older E02 `/my/quests` examples. Reconcile those story contracts before feature implementation; do not rename existing API endpoints based on these presentation routes.

At 1024px and above, a 240px desktop sidebar accompanies the header. Below that, the sidebar gives way to a compact header so content remains readable at tablet width. Guest mode is the production default with Explore and a sign-in entry. The development-only member fixture exposes the four-label mobile bottom navigation; it cannot grant a session or feature access. Member layout reserves scalable bottom space and the device safe area. A single navigation list drives both forms. Example card grids use 3/2/1 columns as space permits.

The sidebar follows the user's explicit request and the newer prototype notes; the older written topbar requirement is superseded for this foundation. Button/navigation hit areas are at least 44px. Labels are never hover-only. Product-route placeholders communicate unavailability rather than showing sample accounts, fake progress, or working-looking quest actions.

## Verification — November 17, 2026

From `ui/`:
- `npm test` — **31 passed**, including the seven original health tests.
- `npm run build` — **passed** (TypeScript and Vite).
- `PLAYWRIGHT_CHANNEL=chrome npm run test:e2e` — **8 passed**. Checks 320/390/768/1440px widths, navigation hit areas/content clearance, route refresh/history, actual native modal focus, doubled text, reduced motion, and axe WCAG A/AA scans of Explore/gallery/login/modal. No automated accessibility violations in those checked states.
- Initial browser runs exposed backward modal focus escape and a history-focus timing/race. Both were corrected; the final full run passed. An initial npm command from the repository root failed because package.json lives in ui/; rerun in ui/ succeeded.
- Dependency installation reported **0 vulnerabilities** at install time.

From `backend/`:
- `mvn -o test` — **1 passed**, outside the sandbox. Installed JDK 26.0.1, compiler target 21; not a JDK 21 runtime verification.
- `mvn -o spring-boot:run -Dspring-boot.run.arguments=--spring.liquibase.enabled=false` — started and authenticated to PostgreSQL 18.4, migrations disabled.

Live production-preview check:
- `npm run preview -- --host 127.0.0.1 --port 4173 --strictPort`.
- `curl --fail --silent --show-error http://127.0.0.1:8080/api/v1/health` and the same request on port **4173** both returned `{"service":"sidequest-api","status":"UP"}`.
- A Chrome smoke check without mocked health responses opened Service status and observed **API online**, found no Component preview link, and confirmed `/dev/components` shows the not-found page in the build preview.
- The built JS contains neither the `/dev/components` route nor sample quest copy. Browser regression tests mock health for determinism; only this separate smoke check proves live integration.

Recorded screenshots (implementation evidence, not Figma exports):
- [Desktop Explore](evidence/sq-019/explore-1440.png)
- [Mobile Explore](evidence/sq-019/explore-390.png)
- [Mobile component/member preview](evidence/sq-019/components-390.png)

Automated checks do not prove full WCAG conformance. Screen-reader/manual assistive-technology review, native browser zoom beyond the doubled-text/reflow check, and direct screenshot comparison to Figma remain handoff work.

## Open handoff and Sprint 1 items

- [UX-006 #35](https://github.com/khyathiPriya6/sidequest/issues/35) stays open: owner must play Explorer/Creator desktop/mobile flows and approve handoff. See [PROTOTYPE-REVIEW.md](PROTOTYPE-REVIEW.md) for the Mobile Joined label/category inconsistency, Desktop Joined card click area, and remaining prototype accessibility/visual QA.
- Figma quota still blocks direct canvas inspection. Exact layout, font, assets, states, and token matching are **not claimed verified**. Follow the recorded frame links in Sprint 0 when access/exports are available.
- The owner approved the documented foundation scope; that does not authorize Quest CRUD, authentication, joins/completion, XP awards, or closing Sprint 0 design issues.
- SQ-007 remains open: Docker Compose (#16), actual Java 21 checks, migration verification, and the owner's retrospective remain outstanding. API conventions (#17) are a separate follow-up.
- No backend code/config, database data/schema, or credentials changed. Test screenshots/reports/dependencies are ignored except the three deliberate evidence screenshots above. No deployment or migration was performed.

## Teach-back

Tokens are shared visual values; CSS Modules contain each component's rules. The router gives pages consistent navigation and focus, while the shell provides the reusable desktop/mobile frame. QuestCard and XPProgressBar render supplied data without owning backend rules. The development gallery exposes states for review and is removed from the production build.

1. Why should a QuestCard receive props instead of fetching or mutating a quest itself?
2. How do semantic CSS tokens and CSS Modules solve different maintenance problems?
3. Why did passing jsdom tests not prove that the native dialog's keyboard behavior was correct?
