# Quest draft API (SQ-010 / #21)

SQ-010 implements authenticated draft creation/editing and creator-only reads. It adds no migration or new dependency; Quest persistence and its `users.id` foreign key already exist from SQ-009.

## What changed

- `POST /api/v1/quests` creates a draft and returns `201`, `Location`, a strong ETag, and the Quest response.
- `PATCH /api/v1/quests/{id}` updates only an owned draft and requires the exact current `If-Match` value.
- `GET /api/v1/me/quests` returns the authenticated creator's paginated quests with an optional lifecycle status filter.
- `GET /api/v1/me/quests/{id}` returns an owned quest and its ETag.
- The authenticated `UserPrincipal.userId` supplies the creator identity. Request-supplied IDs, state, and audit fields are rejected as unknown input.
- Validation uses Unicode code-point lengths, exact enum values, and integer-only XP. Draft patching distinguishes omitted fields from explicit `null`; only optional fields can be cleared.
- Owner-scoped database queries return the same `404` for missing and non-owned private quests. Pagination rejects out-of-range values and orders by creation time and UUID.
- RFC 9457 errors include stable codes and field-level errors. Optimistic locking maps concurrent stale writes to `412`; each successful response carries the current strong ETag.
- `docs/api/quest.openapi.yaml` is the hand-maintained OpenAPI 3.1 contract. Runtime behavior is verified through real HTTP integration tests.

## Architecture and security decisions

The API is implemented in the existing Quest module: the controller handles HTTP mapping, the service owns transactions and the authenticated user boundary, the validator parses request nodes without coercing types, and the repository scopes lookups by creator. Explicit DTOs prevent JPA entities from becoming the wire format. No schema changes were needed.

The session cookie remains browser-managed and HTTP-only; mutation calls require the existing synchronizer CSRF token. User IDs are read only from the verified principal, not the request. Owner queries apply ownership in the database lookup itself, preventing accidental exposure of private Quest data.

JPA's initial version is `0`, so the initial ETag is `"0"`; content mutations increment it. Assigning the persistence version manually would undermine `@Version` semantics. A no-op PATCH does not dirty the entity and therefore does not advance its version or `updatedAt`.

## Validation evidence

Executed from the repository root:

```sh
cd backend && JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash scripts/test-postgres.sh
```

Result: **BUILD SUCCESS**, Java 21.0.13, fresh isolated PostgreSQL cluster; 29 tests passed (9 unit and 20 PostgreSQL/integration tests), with zero failures, errors, or skips. The three Quest HTTP integration tests cover authenticated create/read/update, CSRF, owner privacy, ETag preconditions/no-op updates, optional-field clearing, validation, and pagination. The runner shut down the temporary database; no existing database was touched.

The initial test attempt exposed that a controller-scoped exception advice was eagerly requiring a service bean in the health-only MVC slice. The advice now uses an `ObjectProvider` for its optional optimistic-lock lookup; the complete suite passes after that fix.

## Interview questions

1. Why should ownership be included in the repository query instead of loading by quest ID and checking the creator afterward?
2. How do a strong ETag and `If-Match` prevent one browser tab from silently overwriting changes made by another?
3. Why does a PATCH request need to distinguish a missing property from an explicit JSON `null`?
