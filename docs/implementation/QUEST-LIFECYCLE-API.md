# Quest lifecycle API (SQ-011 / #22)

SQ-011 adds the lifecycle operations specified by the Quest contract:

- `POST /api/v1/quests/{id}/publish`
- `POST /api/v1/quests/{id}/archive`

Both require the authenticated creator, a valid CSRF token, and a strong `If-Match` ETag. They return the shared Quest representation and the resulting ETag. There are no lifecycle migrations or external dependencies.

## Transaction and state decisions

Publication checks all required fields (description, category, difficulty, and reward) inside the same transaction that changes the state. Validation errors are field-specific and leave status, version, and timestamps unchanged. After validation, the entity transition records `publishedAt`; JPA updates `updatedAt` and increments `@Version` at flush.

Archiving is permitted from DRAFT or PUBLISHED. It retains `publishedAt` when present, records `archivedAt`, and preserves the row. No delete, reopen, or published-content editing operation is introduced.

Each action requires a syntactically valid `If-Match`. A repeated publish on PUBLISHED or archive on ARCHIVED returns the unchanged response and current ETag even if the supplied ETag is stale, so clients can safely retry after a lost response. Other state changes compare versions. A concurrent write that loses the optimistic-lock race returns `412` and the current ETag when the resource is still owned by the caller.

## Validation evidence

Executed from the repository root:

```sh
cd backend && JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash scripts/test-postgres.sh
```

Result: **BUILD SUCCESS**, Java 21.0.13, fresh isolated PostgreSQL cluster; 31 tests passed (9 unit and 22 PostgreSQL/integration tests), with zero failures, errors, or skips. Real HTTP tests cover invalid publication atomicity, ownership and anonymous access, lifecycle timestamps, idempotent stale retries, unsupported republishing/editing, draft archive, and a concurrent publish/archive race. No existing database was touched.

## Interview questions

1. Why should validation and a state transition share one transaction?
2. How can an API make a retry safe when the first response was lost after the server committed?
3. What role does optimistic locking play when a creator edits and publishes the same quest concurrently?
