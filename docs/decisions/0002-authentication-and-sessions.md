# ADR 0002 — Authentication and session architecture

**Status:** Core architecture approved by product owner on 2026-11-17; PR #40 review/merge pending
**Issue:** [#39 — E03-ARCH](https://github.com/khyathiPriya6/sidequest/issues/39)
**Scope:** Architecture proposal only. No security dependency, endpoint, user schema, session table, or UI is implemented by this ADR.

## Context

SideQuest is a web-first product with one Spring Boot modular-monolith API, a React SPA, PostgreSQL, and a future React Native client. The MVP requires email/password registration, login/logout, profiles, and protected writes. Public quest discovery must remain anonymous. The backend currently has only the health endpoint; identity persistence and Spring Security are absent.

The Quest contract requires the server to assign the creator from an authenticated principal and enforce owner-only operations. It does not choose how a principal is authenticated.

## Decision

Use **server-side opaque sessions** rather than JWT access/refresh tokens for the MVP.

- Use Spring Security's servlet session model and, after approval, a PostgreSQL-backed Spring Session JDBC repository. This fits the existing database and single deployable service without introducing Redis or an external identity provider.
- For the browser SPA, carry only the opaque session identifier in an `HttpOnly` cookie. Never return it in JSON or put it in `localStorage` or `sessionStorage`.
- For a future React Native client, defer the exact credential transport to the mobile issue. Preferred direction is the same opaque, revocable server-side session using a platform-protected credential store and a non-cookie request header; do not make the API depend on JWT claims. Mobile-specific CSRF and session handling must be reviewed before that client ships.
- Return CSRF tokens separately from session credentials and require them for cookie-authenticated unsafe methods.
- Enforce authentication and account status on the server. Resolve a stable user ID from the Spring Security principal and pass it to domain services; never accept an owner ID from request data.

The product owner approved the session mechanism and session policies listed below on 2026-11-17. This documentation change does not implement authentication. The remaining registration-error/throttling and username-format details must be settled before their implementation; PR #40 still requires repository review/merge.

## Alternatives considered

### JWT access and refresh tokens

**Advantages:** Native clients can send bearer access tokens without relying on cookies; access-token verification can be stateless across API instances.

**Costs for this MVP:** Refresh tokens still require server-side storage, rotation, replay detection, and revocation. A signed access token remains usable until expiry after logout or account disable unless every request performs a revocation lookup. Safe browser storage then adds a separate design choice: JavaScript-readable storage increases token theft impact under XSS, while cookie storage reintroduces CSRF controls. Key rotation, signing-key configuration, token claims/audience, expiry, refresh reuse, and revocation add operational and test burden before SideQuest has multiple APIs or services.

### In-memory servlet sessions

**Advantages:** Minimal setup and native Spring Security integration.

**Costs:** Restart loses all sessions; multiple API instances cannot share sessions without sticky routing or another session store. Since PostgreSQL is already part of the architecture, JDBC-backed sessions give the MVP durable, centrally revocable sessions without adding infrastructure.

### External identity provider

**Advantages:** Outsources credential lifecycle and identity operations.

**Costs:** Adds an external service, account configuration, and dependency outside the MVP requirement and current self-hosted email/password scope. Revisit only with a product requirement and approved issue.

## Security policy proposed for approval

### Browser credential and CSRF controls

- In deployed HTTPS environments use a host-only `__Host-` session cookie with `Secure`, `HttpOnly`, `SameSite=Lax`, and `Path=/`; do not set `Domain`. Local HTTP development must use a separate profile-specific cookie name because browsers reject `__Host-` cookies without `Secure`.
- Local HTTP development may disable `Secure` only in the local development profile. It must not be possible to carry this setting into production accidentally.
- Rotate the session identifier and CSRF token after successful login, registration, and privilege changes to prevent session fixation and keep the CSRF token bound to the active session.
- Use Spring Security synchronizer-token CSRF protection for unsafe methods, including login, registration, logout, and protected mutations. Provide a same-origin CSRF bootstrap endpoint that returns a token bound to the current server-side session; the SPA keeps it in memory and sends it in a request header. A CSRF token is not an authentication credential.
- Configure the production browser deployment as same-origin for the SPA and `/api`; use the existing Vite `/api` proxy locally. Do not enable wildcard credentialed CORS. Native clients are not governed by browser CORS.
- CSRF protects against cross-site request forgery, not same-origin XSS. Continue output encoding, input handling, and CSP/security-header work as separate security controls.

### Expiration, logout, and revocation

Proposed initial limits are **30 minutes idle** and **12 hours absolute**. The idle timeout may slide with activity, but the absolute lifetime does not. No “remember me” flow or refresh-token endpoint is proposed for the MVP.

- Logout invalidates the current server-side session, clears the cookie with matching path/security attributes, and rotates/clears the CSRF token.
- Multiple devices may have separate sessions. This endpoint logs out the current session; global “log out all devices” is outside the initial MVP unless the owner adds it.
- A disabled account cannot authenticate. Disabling an account must invalidate its active sessions before protected requests are accepted.
- Expired, absent, malformed, or invalid session credentials produce `401` Problem Details on API requests; APIs do not redirect to an HTML login page.
- Session cleanup and expired-row retention must be configured and tested with the session repository. Do not log cookie values, session IDs, passwords, CSRF tokens, or authorization headers.
- Spring Session JDBC stores serialized session attributes. Keep session contents minimal (stable user ID and required authorities only); never store credentials or password hashes. Review the serializer/deserialization configuration and database access controls before enabling persisted sessions.
- An absolute lifetime is distinct from Spring Session's idle timeout and requires explicit enforcement; test both expiration conditions and verify expired session records are cleaned up.

### Passwords and account behavior

- Store only an adaptive password hash, never plaintext or reversible encrypted passwords. Proposed implementation is Spring Security's `DelegatingPasswordEncoder` with Argon2id for new hashes and an upgrade path for future algorithms. Spring's Argon2 encoder requires the Bouncy Castle provider; add it only after this ADR is approved. Configure memory/time parameters using current OWASP guidance and benchmark without claiming an unmeasured performance target.
- Approved password validation is 12–128 Unicode code points, with no arbitrary composition rules and no silent truncation. Enforce the limit before hashing and reject oversized JSON bodies.
- Treat passwords as exact input: do not trim, case-fold, or normalize them; enforce the proposed length limit before hashing.
- Login failure responses use the same generic `401` shape for unknown email, wrong password, and disabled account.
- Registration activates the account and creates a session on success; email verification and password recovery are not in the MVP requirements. Duplicate-account responses use a generic `409` message rather than returning an account record. This reduces detail in the response but cannot eliminate all account-enumeration risk; rate-limit registration/login at the application or deployment boundary without introducing unapproved infrastructure.
- Normalize email consistently before comparison and enforce uniqueness in the database. Do not apply provider-specific transformations such as removing dots or plus tags.
- The product owner approved a required unique username on 2026-11-17. The PRD and auth wireframe still need to be updated before registration UI implementation; username validation and canonicalization details remain to be confirmed.
- The proposed user schema uses a UUID ID, normalized unique email, unique username, password hash, `ACTIVE`/`DISABLED` status, and UTC created/updated timestamps.

## Principal and authorization boundary

- Spring Security is the only source of authenticated identity. The principal exposes the stable user ID (proposed UUID, subject to the users-schema decision) and any minimum roles/authorities needed by the API.
- Controllers handle HTTP and bind validated DTOs; services receive the authenticated user ID explicitly and enforce business ownership. Never accept `userId`, `creatorId`, role, or account status from a client as authority.
- Anonymous endpoints are explicitly allow-listed. All other business routes deny by default.
- Private-resource concealment remains endpoint-specific. In particular, draft/archived Quest detail returns the same `404` to anonymous users and non-owners, as specified by SQ-008. Missing authentication on protected writes and `/me` routes returns `401`.

## Consequences

- Browser logout and account disable can revoke a session immediately because server-side state is consulted.
- Session state becomes operationally important data: secure PostgreSQL access, migration/versioning, expiry cleanup, and tests are required. No Redis or distributed service is needed for the current modular monolith.
- Browser cookie authentication requires CSRF defenses. Same-origin hosting avoids broad CORS but does not remove CSRF or XSS considerations.
- The SPA must treat a `401` as an authentication state change, preserve only validated local return paths, and must not store session credentials in browser-readable storage.
- A future mobile client requires a separately reviewed transport and secure-storage integration. The API should keep principal semantics transport-independent.
- Introducing stateless JWT later would be a deliberate architecture change with a documented revocation, key-rotation, and refresh-token policy.

## Dependencies and rollout order

1. Owner approves this ADR's mechanism and security policy, or requests changes.
2. E03 defines the users fields, uniqueness/canonicalization rules, account-state semantics, and migration. Update the registration design to include the owner-approved username field and finalize its format.
3. Add Spring Security and the approved session repository in its own implementation issue; protect the API deny-by-default while retaining public health and public published Quest reads.
4. Implement registration/login/logout/current-user contracts and security tests.
5. Integrate the browser UI only after the frontend-foundation PR #37 is reviewed/merged and the approved login/register design is available.
6. Release Quest write endpoints only after identity and authorization are verified.

No schema, session persistence, or protected business API is implemented in this proposal.

## Approval checklist

- [x] Approve server-side opaque sessions instead of JWT for the MVP.
- [x] Approve PostgreSQL-backed session storage without Redis.
- [x] Approve browser cookie + CSRF approach and same-origin deployment expectation.
- [x] Approve proposed 30-minute idle / 12-hour absolute session limits.
- [x] Approve session behavior: login rotation, current-session logout, multiple sessions, and disable-account revocation.
- [x] Approve registration activation without email verification/password recovery in MVP.
- [x] Approve required unique username; registration design still needs a username field.
- [x] Approve Argon2id provider/dependency and the proposed 12–128 code-point password bounds.
- [ ] Approve username format/canonicalization and duplicate-registration/throttling policy.

## Learning notes

- A server-side session keeps the authentication state on the API side; the browser holds only an unguessable session identifier.
- Cookies are automatically attached by browsers, so cookie-authenticated writes need CSRF protection. `HttpOnly` reduces JavaScript access to the credential but does not prevent XSS from acting as the user.
- JWTs are signed claims, not encrypted secrets. Stateless verification can simplify multi-instance reads, but logout/revocation and refresh-token rotation still need state or short expiry.

### Technical interview questions

1. Why does cookie-based authentication need CSRF protection even when the API validates authorization?
2. What security and operational problems remain when an API uses short-lived JWT access tokens with refresh tokens?
3. How would a server-side session ID rotation after login mitigate session fixation?

## References

- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Spring Session JDBC reference](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
