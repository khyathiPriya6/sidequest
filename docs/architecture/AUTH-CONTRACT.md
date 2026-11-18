# E03 — Authentication API and security boundaries

**Status:** Core session and registration policies approved; registration design handoff remains open
**Issue:** [#39 — E03-ARCH](https://github.com/khyathiPriya6/sidequest/issues/39)
**Scope:** Contract proposal only; no endpoints or security configuration are implemented.

This contract assumes the owner-approved server-side session design in ADR 0002. Cookie, CSRF, expiration, and logout details are approved; update this document if the architecture changes.

## Route access matrix

“Public” means the endpoint is usable without an authenticated principal; it does not mean every request body or response is public. API authentication failures return JSON Problem Details, never browser redirects.

| Route | Access | Contract |
|---|---|---|
| `GET /api/v1/health` | Public | Existing health response |
| `GET /api/v1/auth/csrf` | Public | Establishes/returns a synchronizer CSRF token bound to the current anonymous or authenticated browser session |
| `POST /api/v1/auth/register` | Public + CSRF token | Validate account input, create an active user, establish a session, return `201 Created` |
| `POST /api/v1/auth/login` | Public + CSRF token | Verify credentials, rotate/create a session, return current-user summary |
| `POST /api/v1/auth/logout` | Authenticated + CSRF token | Invalidate current session; return `204 No Content` |
| `GET /api/v1/auth/me` | Authenticated | Return the current user's safe account/profile summary |
| `GET /api/v1/quests` | Public | Published quests only; SQ-008 contract |
| `GET /api/v1/quests/{id}` | Public for published; owner-only otherwise | Private detail uses concealed `404`; SQ-008 contract |
| `GET /api/v1/me/**` | Authenticated | Resolve caller identity exclusively from the principal |
| `POST`, `PATCH`, publish, archive on `/api/v1/quests/**` | Authenticated + CSRF token + owner check where applicable | Derive creator from principal; apply SQ-008 rules |
| Any other business route | Deny by default | Add only through an explicit access decision |

Safe methods (`GET`, `HEAD`, `OPTIONS`) must not mutate domain state. The CSRF bootstrap may create anonymous session state. CSRF protection applies to all cookie-authenticated unsafe methods, including login and account creation.

## Proposed payloads

The exact user/profile fields depend on the owner-approved users schema. Do not include password hashes, account status internals, session identifiers, roles, or CSRF tokens in the current-user representation.

Registration (required username; canonical lowercase, 3–30 ASCII letters, digits, or underscores):

```http
POST /api/v1/auth/register
Content-Type: application/json
X-CSRF-TOKEN: <csrf-token>

{
  "email": "explorer@example.test",
  "username": "Quest_Maker",
  "password": "<user-supplied password>"
}
```

Successful registration returns `201 Created`, a session cookie, and a safe account summary. It rotates the pre-authentication session identifier and CSRF token. Store the username canonically as `quest_maker`; uniqueness is case-insensitive. Update the registration UI design to include the approved username field before UI implementation.

Login:

```http
POST /api/v1/auth/login
Content-Type: application/json
X-CSRF-TOKEN: <csrf-token>

{
  "email": "explorer@example.test",
  "password": "<user-supplied password>"
}
```

Current user:

```json
{
  "id": "018f47d1-7c61-7e00-9a7c-000000000002",
  "email": "explorer@example.test",
  "username": "quest_maker"
}
```

The server returns the session identifier only as a cookie for the browser. The CSRF endpoint returns a token separately for use in the request header; clients do not treat it as an authentication credential. Native-client authentication transport remains out of web-MVP scope pending a mobile security decision.
Successful login rotates the session identifier and CSRF token; the browser fetches a fresh CSRF token before its next unsafe request.

CSRF bootstrap example:

```http
GET /api/v1/auth/csrf
HTTP/1.1 200 OK
Content-Type: application/json

{"csrfToken":"<session-bound-csrf-token>"}
```

The browser sends that value as `X-CSRF-TOKEN` on unsafe requests. The server validates it against the current session; do not put the authentication session identifier in the response body.

Proposed browser session cookie (the value is an opaque secret and must never be logged):

```http
Set-Cookie: __Host-id=<opaque-session-id>; Path=/; HttpOnly; Secure; SameSite=Lax
```

`__Host-` requires `Secure`, `Path=/`, and no `Domain`; local-only HTTP development must use a separate profile-specific cookie name without the prefix and must not leak that configuration into deployment.

## Error behavior

Errors use the shared RFC 9457 Problem Details contract from [API conventions](./API-CONVENTIONS.md).

- Missing, expired, malformed, or invalid session on a protected operation: `401` with a generic problem; no redirect.
- Wrong email/password or disabled account at login: same generic `401` response so credential failures do not identify which value was wrong.
- Authenticated user lacking permission: `403`, except private Quest resources that use the SQ-008 concealed `404`.
- Invalid registration fields: `422` with field-level errors that do not echo the submitted password.
- Password policy: 12–128 Unicode code points, no composition rules, no trimming/normalization, no truncation; hash using approved Argon2id parameters.
- Duplicate email or username: generic `409` `account_conflict`; do not identify which field is already registered.
- Apply configurable application-level throttling by source IP and normalized account for login and registration; do not permanently lock accounts. Select numeric thresholds and test them in the implementation issue. Per-instance limits do not coordinate across replicas.
- Missing/invalid CSRF token on an unsafe cookie-authenticated request: `403` Problem Details with a stable error code and no session/token values.

Never log passwords, password hashes, session cookies/IDs, CSRF tokens, authorization headers, or full credential-bearing request bodies.

## Browser and API boundary

- Production browser deployment serves the SPA and `/api` from the same origin. Local development uses the Vite proxy; do not enable permissive credentialed CORS.
- Browser session ID is HttpOnly, Secure in HTTPS deployments, SameSite=Lax, path-scoped, and absent from JSON or web storage.
- Browser sends the CSRF token in a dedicated header on unsafe methods; the server validates it in addition to the session.
- The frontend may remember a post-login destination only as a same-origin relative path. Reject absolute URLs and protocol-relative paths to prevent open redirects.
- Native React Native is not governed by browser CORS, but it must use OS-protected credential storage and a separately reviewed transport; it is not implemented by this contract.
- `Authentication` principal identifies a stable user ID. Controllers pass that identity into services; services do not trust request-body ownership or client-provided role fields.

## Users schema dependency (not a migration)

The implementation must define and migrate a `users` table before protected identity and Quest writes ship. The owner approved a required username, but the PRD/auth wireframe still need a design update before UI implementation. Resolve username format/canonicalization before freezing the DTO.

At minimum, the schema design must specify:

- Stable primary key type compatible with the `quests.creator_id` foreign key.
- Normalized email and a database uniqueness constraint/index.
- Required unique username, 3–30 ASCII letters/digits/underscores, stored in lowercase; enforce uniqueness on the canonical value.
- Password hash storage only (no reversible password field).
- Active/disabled account state, creation/update timestamps, and behavior on disable.
- A session repository schema and cleanup/expiry policy if ADR 0002 is approved.

The current user-schema proposal uses a UUID ID, normalized unique email, required canonical unique username, active/disabled status, password hash, and UTC created/updated timestamps. The approved password-hash direction is Argon2id via Spring Security's `DelegatingPasswordEncoder` and Bouncy Castle provider.

Do not run schema changes against an existing database without explicit approval.

## Dependencies and blockers

- [ADR 0002](../decisions/0002-authentication-and-sessions.md): mechanism, cookie/CSRF policy, expiration and revocation need owner approval.
- [SQ-008 Quest contract](./QUEST-CONTRACT.md): defines public Quest reads, private visibility, and principal-based ownership.
- [Issue #37](https://github.com/khyathiPriya6/sidequest/pull/37): frontend foundation PR is still open; do not claim its UI foundation is merged into `main` or integrate auth UI before review/merge.
- [UI/UX specification](../design/UI-UX-SPEC.md): login/register are designed around email/password. No authentication screens or UI behavior are implemented here.

## Approval checklist

- [x] Approve authentication/session mechanism, cookie/CSRF assumptions, and session behavior.
- [ ] Review API route list and status codes.
- [x] Approve required username and its format/canonicalization; update registration design before frontend integration.
- [x] Approve immediate account activation, generic duplicate-account response, and configurable per-IP/account throttling (numeric thresholds remain for the implementation issue).
- [x] Approve session duration and revocation policy in ADR 0002.
