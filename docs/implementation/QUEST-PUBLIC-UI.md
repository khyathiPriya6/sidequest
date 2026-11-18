# Quest public UI (SQ-014 / #25)

SQ-014 adds the public Explore catalogue and Quest detail screen to the React/Vite frontend. Both views use the existing design tokens, application shell, and `QuestCard`; production requests go to the session-aware same-origin Quest API. No API, authentication, or database changes are part of this issue.

## What changed

- `/quests` requests published quests and renders loading, empty catalogue, filtered no-match, out-of-range page, retryable error, and result states.
- Search (`q`), category, difficulty, and page are kept in the URL. Search is submitted explicitly; changing a filter resets pagination, and browser history restores the prior filter state.
- `/quests/:id` loads a quest detail, distinguishes a missing/private resource from a service failure, and links back with the original Explore query string.
- Search length is checked in Unicode code points to match the API contract. API response shapes and enum values are checked before rendering.
- API requests use same-origin credentials and an abort signal. Superseded catalogue requests are ignored so a slower response cannot overwrite newer filter results.
- Responsive layouts use existing tokens. Controls have labels, result changes are announced, keyboard navigation is covered, and the Playwright axe scan checks WCAG AA rules.

## Engineering decisions

The page reads the URL as the source of truth for committed filters and pagination. The text field stays locally editable until Search is submitted. This preserves shareable deep links and browser back/forward behavior without issuing a request for every keystroke.

No separate quest requirements field exists in the approved API contract; the detail page renders the available description and does not invent a completion workflow. Joining and completion remain out of scope. Anonymous public reads do not need CSRF tokens, and same-origin session credentials allow the API to apply its existing owner/private-resource rules.

The repository's UI/UX specification, design system, user flows, and wireframe reference were used. The supplied Figma URL returned HTTP 403 during this work, so pixel-level comparison against that file could not be performed; this implementation does not claim exact Figma parity.

## Validation evidence

Executed from the repository root:

```sh
cd ui && npm test
```

Result: **passed**, 35 tests across 6 files.

```sh
cd ui && npm run build
```

Result: **passed**, TypeScript project build and Vite production bundle.

```sh
cd ui && npm run test:e2e
```

Result: **passed**, 9 Playwright browser tests. Coverage includes 320/390/768/1440-pixel viewports, keyboard navigation to details and return-filter preservation, route/history behavior, reduced motion, and axe WCAG AA checks. The browser suite uses deterministic API fixtures for UI behavior; production code does not use mock authentication or mock Quest data. The Quest backend's real-HTTP and isolated PostgreSQL coverage is recorded in [the implementation tracker](../planning/IMPLEMENTATION-TRACKER.md).

## Interview questions

1. Why should committed filters live in the URL while an unfinished search string can remain local component state?
2. How do `AbortController` and an effect cleanup prevent a stale network response from replacing newer results?
3. Why should a public UI distinguish a `404` resource from a temporary network or server error?
