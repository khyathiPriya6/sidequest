# Authentication UI (#44)

The React UI connects to the real session-backed endpoints under `/api/v1/auth`; production contains no mock identity or sample user. The Vite `/api` proxy keeps local browser requests same-origin with the Spring API.

## Flow and security

- `AuthProvider` bootstraps a synchronizer CSRF token, then calls `/auth/me` to restore the current user after page load. A `401` means anonymous; network and server errors stay visible and can be retried.
- Registration and login include the in-memory CSRF token in `X-CSRF-TOKEN`. After successful authentication the session rotates, so the UI fetches a fresh token before another unsafe request.
- The HttpOnly session cookie is managed by the browser. The UI never reads, stores, or copies credentials/session IDs into web storage.
- Logout invalidates the current backend session and clears UI identity. If the session has expired, the `401` is treated as an already-ended session; CSRF failures refresh the token without automatically retrying the mutation.
- Protected routes redirect to login and preserve their requested path in router state. Only validated same-origin relative paths can be used after login; absolute, protocol-relative, backslash, and auth-loop destinations fall back to `/quests`.
- Navigation and the account label use the `/auth/me` response. Guest browsing remains public; the existing quest/profile route bodies remain placeholders and are not represented as implemented features.

## Form behavior

Registration validates an email up to 254 characters, a username matching `[A-Za-z0-9_]{3,30}`, and a password of 12–128 Unicode code points. Email is trimmed to match server canonicalization; password bytes are preserved exactly. The username help text explains that it is public. Inputs keep their values on RFC 9457 validation, conflict, CSRF, network, and generic login failures.

Forms use the shared `Input`, `Button`, and `Card` components, with associated labels, inline field errors, an alert summary, pending/disabled submit state, and mobile sizing. The username/help/error addition follows the user-approved registration update. The linked Figma page was not readable to the local tools, so this implementation reuses the repository design tokens/components and does not claim pixel-level Figma comparison.

## Validation evidence

- `cd ui && npm test` — 46 tests passed across 7 files, including auth state, field errors, duplicate submission, redirects, CSRF, logout, and safe-path validation.
- `cd ui && npm run build` — TypeScript check and Vite production build passed.
- `cd ui && OUTPUT=$(mktemp -d /private/tmp/sidequest-auth-playwright.XXXXXX) && AUTH_E2E=1 PLAYWRIGHT_CHANNEL=chrome npm run test:e2e -- --workers=1 --output="$OUTPUT"` — all 9 browser tests passed. This includes the actual Spring API and isolated PostgreSQL journey (registration, session restore, invalid CSRF rejection, logout, login, and expired-session redirect), 320/390/768/1440px responsive checks, keyboard focus, and automated WCAG AA checks. Auth browser requests were not mocked.

No existing database was changed. The isolated browser-test PostgreSQL cluster was loopback-only and stopped after validation.

## How it works

The API owns authentication state and the browser automatically attaches its HttpOnly cookie. React keeps only the safe user summary and CSRF token in memory. A protected-route guard redirects guests without guessing identity, and a validated return path brings a user back to their intended destination after successful login or registration.

## Teach-back

The session cookie proves the browser's session to the server; it is not exposed to JavaScript. The CSRF token is a separate value used to prove an unsafe request came from the same session. A same-origin relative return path is safer than accepting arbitrary URLs because the app cannot redirect a successful login to an attacker-controlled site.

1. Why does an HttpOnly session cookie still need CSRF protection?
2. Why should a stale CSRF failure not automatically replay registration or logout?
3. How does checking both the path shape and URL origin prevent an open redirect?
