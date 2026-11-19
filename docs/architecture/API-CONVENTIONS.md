# SideQuest API conventions

**Status:** Proposed baseline from SQ-006 (#17), reconciled for SQ-008 (#19); review before CRUD implementation.

These conventions apply to the versioned REST API in the modular monolith. They describe contracts, not implementations. The backend currently exposes only the health endpoint.

## Routes, JSON, and timestamps

- Prefix REST routes with `/api/v1`. Introduce a new version only for an incompatible public contract change; do not version individual resources independently.
- Use JSON request and response bodies with `camelCase` property names. Use explicit request/response DTOs rather than serializing persistence entities.
- Represent instants as ISO 8601 / RFC 3339 UTC timestamps ending in `Z`. The server owns creation and update timestamps.
- Return `201 Created` and a `Location` header when a request creates a resource. Use `200 OK` with the resource for reads, updates, and successful state transitions; use `204 No Content` only when there is intentionally no response body.
- Treat PATCH as a partial update: omitted fields remain unchanged. Reject fields that are server-managed or not part of the relevant request DTO.

Success example, matching the existing health endpoint:

```http
GET /api/v1/health
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "status": "UP",
  "service": "sidequest-api"
}
```

## Errors and validation

Use RFC 9457 Problem Details with `Content-Type: application/problem+json`. Every error includes `type`, `title`, `status`, and `detail`; include `instance` when a request path is available. Validation errors may add an `errors` array whose entries contain `field`, stable machine-readable `code`, and user-actionable `message`.

Example:

```json
{
  "type": "about:blank",
  "title": "Request validation failed",
  "status": 422,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/example",
  "errors": [
    {
      "field": "name",
      "code": "required",
      "message": "Name is required."
    }
  ]
}
```

Use status codes consistently:

| Status | Meaning |
|---|---|
| `400 Bad Request` | Malformed JSON, invalid path/query/header syntax, or invalid pagination parameters |
| `401 Unauthorized` | Authentication is missing or invalid for a protected operation |
| `403 Forbidden` | Authenticated caller is denied an operation when resource-existence privacy does not require concealment |
| `404 Not Found` | Resource is absent or intentionally concealed from this caller |
| `409 Conflict` | Request conflicts with the resource's current state |
| `412 Precondition Failed` | Conditional request refers to a stale representation |
| `422 Unprocessable Content` | Syntactically valid input fails domain validation |
| `428 Precondition Required` | A required conditional request header is missing |

Never return stack traces, credentials, tokens, or private resource details in an error response. Do not silently coerce invalid input or return a success-shaped fallback.

## Pagination

Collection endpoints use zero-based `page`, with a default of `0`, and `size`, with a default of `20` and a maximum of `100`. Reject negative page numbers, non-positive sizes, and sizes above the maximum with `400`; do not silently clamp. Use deterministic ordering with an identifier tie-breaker.

Return the collection and its pagination metadata in a stable envelope:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false,
  "hasPrevious": false
}
```

## Conditional updates

When a resource supports stale-write detection, return a strong ETag with its representation and require `If-Match` on mutations. Return `412` and make no change if the supplied ETag is stale; return `428` if the required header is absent. A resource's domain contract must specify whether safe retries of an already-completed transition are idempotent.

## OpenAPI

Keep request/response examples and status behavior in versioned OpenAPI 3.1 documents. The project uses hand-maintained specifications as the API contract; [authentication](../api/auth.openapi.yaml) and [Quest](../api/quest.openapi.yaml) are the current source files. Integration tests verify runtime behavior against the documented status codes, shapes, and access rules. Do not add an OpenAPI generation dependency unless a later issue selects and validates that approach.

## Scope and compatibility

- Authentication, authorization, and CORS policy are defined by the identity/security ADR and endpoint-specific access rules; this document does not select token or session behavior.
- Endpoint-specific contracts may narrow these general rules, but must not contradict their wire formats or status meanings.
- The [SQ-008 Quest contract](./QUEST-CONTRACT.md) applies these conventions to Quest fields, visibility, transitions, validation, and ETag handling.
