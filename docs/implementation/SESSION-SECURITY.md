# Session security (#42)

The filter chain allows only approved public reads and authentication submissions. Other routes require authentication or deny access entirely. JSON RFC 9457 errors replace browser redirects. CSRF remains required on unsafe methods, including anonymous registration/login. Same-origin hosting and the existing Vite proxy avoid credentialed cross-origin access.

Argon2id uses a 16-byte salt, 32-byte hash, 19 MiB memory, two iterations and parallelism one, wrapped by DelegatingPasswordEncoder. These parameters follow the [OWASP minimum Argon2id guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). No production latency target is claimed. User lookup normalizes email and performs a dummy hash verification for unknown accounts. Successful authentication returns a UUID-only principal and ROLE_USER, with null credentials; account entities and hashes never enter sessions.

`SessionAuthentication.establish` invokes session-ID and CSRF rotation, records the authentication time, then explicitly saves the security context. It is the shared primitive for #43 registration/login. Spring Session stores these minimal attributes in PostgreSQL, enforcing 30-minute idle expiry and running cleanup every minute. A filter checks 12-hour absolute age and current account state before CSRF/authorization. Disabled/deleted accounts have their sessions revoked; absolute expiry invalidates the current session. Sessions are read from the real JDBC repository in tests.

The default cookie is `__Host-id`, Secure/HttpOnly/SameSite=Lax/Path=/, no Domain. Only the explicit `local` profile uses `sidequest-local-id` without Secure; mixing local with another profile fails startup. Deployment must never activate `local`. Configure same-origin HTTPS termination and do not enable request/header/body logging. PostgreSQL constraint diagnostics can contain rejected hash values, so Hibernate's SQL exception logger is disabled. Application errors must not log credential-bearing exception details.

## Serialization and operational boundary

Spring Session 3.4.2 uses Java serialization for JDBC attributes. Only server-generated Spring Security context, UUID principal, CSRF token and primitive timestamp are persisted by this implementation. No serialized bytes are accepted from HTTP. Database writes/backups must remain restricted to trusted application/operators: a malicious database writer could modify authentication state or serialized objects. Do not expose session tables or introduce arbitrary session attributes. Password hashes, request bodies and cookies must never be logged. Further serializer changes require compatibility/security review; existing sessions would need invalidation.

## Verification and scope

Java 21 isolated PostgreSQL suite exercises public health, denied routes, RFC 9457, anonymous CSRF failures, typed principal, exact password matching, disabled-login rejection, cookie attributes, session/CSRF rotation, JDBC restoration, 30-minute idle expiry/cleanup, 12-hour absolute expiry and disable revocation. The #42 PR used test-only probe controllers to exercise the session primitive. The #43 stack replaces them with real runtime auth controllers and adds an actual HTTP-server journey. Private Quest 404 concealment is a Quest service rule; it remains unverified until that gated implementation exists. No claim is made that Quest authorization is complete.

## Teach-back

Authentication identifies the caller; authorization decides what that caller can do. CSRF validates that an unsafe browser request has a session-bound token. The session cookie cannot be read by JavaScript, while the CSRF token may be returned to the same-origin UI. Idle expiry slides as a session is used; absolute expiry limits even a continuously active session.

1. Why must a custom login controller explicitly save the SecurityContext in Spring Security 6?
2. Why does rotating a session ID at login prevent fixation but not replace CSRF validation?
3. How can an account be revoked immediately while session cleanup runs only once per minute?
