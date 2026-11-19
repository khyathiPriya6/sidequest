# Authentication API (#43)

The runtime endpoints now implement the approved cookie/session contract. `AuthController` binds explicit credential DTOs, obtains the source address, and delegates account creation to the transactional `AuthService`. AuthenticationManager verifies credentials and returns a UUID-only principal. SessionAuthentication rotates the existing anonymous session ID and CSRF token, records the authentication time, and saves the context. Responses contain only id/email/username. The registration transaction commits before session establishment.

Registration hashes exact password input with Argon2id; validation counts Unicode code points (12–128), preserving whitespace and normalization differences. The authentication JSON body is limited to 8 KiB before deserialization/hashing. Unknown JSON fields are rejected. Validation errors never echo input values; DTO string representations are redacted. Duplicate email and username both become the same 409 account_conflict response. Unknown email, wrong password and disabled-account authentication all use the same generic 401 response.

## API source of truth and usage

[auth.openapi.yaml](../api/auth.openapi.yaml) is the hand-maintained OpenAPI 3.1 runtime description. This avoids a documentation-generation runtime dependency for five endpoints. The reviewed AUTH-CONTRACT remains the policy authority; runtime integration tests verify the described routes, status codes and response shape. The specification is not hosted as a new public endpoint.

The same-origin browser client follows this request sequence:

```ts
const bootstrap = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
let { csrfToken } = await bootstrap.json()
const result = await fetch('/api/v1/auth/register', {
  method: 'POST', credentials: 'same-origin',
  headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
  body: JSON.stringify({ email, username, password }), // actual form variables; never log
})
// Handle non-2xx Problem Details; do not retry mutations automatically.
if (result.ok) {
  const user = await result.json() // id, email, username only
  ;({ csrfToken } = await (await fetch('/api/v1/auth/csrf')).json())
}
```

Login uses POST `/api/v1/auth/login` with email/password and the current CSRF header. Fetch CSRF again after success. GET `/api/v1/auth/me` restores authenticated state after refresh. Logout uses POST `/api/v1/auth/logout` with the current token and returns 204; discard local user/token state. Missing/invalid CSRF produces 403 before unsafe-request authentication checks. Missing/expired authentication on GET me produces 401. The server clears the matching session cookie; there is no separate CSRF cookie to clear.

## Throttling and limitations

Login and registration share fixed 15-minute windows: 60 attempts per source IP, 10 per normalized email, maximum 10000 map entries. Configure `sidequest.auth.throttle.ip-limit`, `account-limit`, `capacity`, `window` (Duration, e.g. 15m). All attempts count, including success. Synchronized checks bound concurrent updates. Expired entries are removed on the next attempt; capacity exhaustion fails closed with 429 instead of evicting active limits. This is a per-process safeguard, not a distributed limiter; do not scale replicas until a shared design is approved. The server uses the actual remote address, not untrusted X-Forwarded-For. Reverse-proxy deployments must review trusted forwarding and throttling before release.

## Tests and measurement

`JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home bash scripts/test-postgres.sh` runs all tests against a fresh PostgreSQL cluster. The latest run on the #43 integration branch included **9 unit and 17 integration tests**, including a real random-port HTTP server with a browser-style cookie jar, PostgreSQL persistence, registration, login, session restoration, CSRF rotation, logout and invalidated-session rejection. Security/persistence regressions, safe errors, duplicate identities, throttle capacity/reset, and Unicode boundaries also passed. Frontend coverage is recorded in [the authentication UI notes](./AUTHENTICATION-UI.md).

On this local Java 21.0.13 run, five synthetic Argon2id encode-plus-verify pairs averaged **129.8 ms** per pair. This includes warmup and is not a production latency benchmark or a hash-only timing; measure on deployment hardware before tuning. No credentials/hashes were printed.

## Teach-back

Password hashing is intentionally costly, so validation, bounded bodies and throttling happen before hashing. Database uniqueness handles registration races, while a generic response limits information disclosure. A successful login changes both session identity and CSRF token; the UI must refresh the token before its next write.

1. Why must password validation count Unicode code points instead of UTF-16 code units?
2. Why should duplicate-account errors avoid naming the duplicate field, even though a 409 still leaks some information?
3. What changes when a per-instance authentication limiter is deployed behind multiple replicas or a reverse proxy?
