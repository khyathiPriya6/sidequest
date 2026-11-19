# Quest public catalogue (SQ-012 / #23)

SQ-012 implements the anonymous published-quest reads:

- `GET /api/v1/quests`
- `GET /api/v1/quests/{id}`

## Query and visibility decisions

The list query filters on `status = PUBLISHED` in the database, and Spring Data applies the same predicate to both content and count queries. Results use `createdAt DESC, id DESC`, with zero-based page numbering, default size 20, and a hard maximum size of 100. Invalid page/size values are rejected rather than clamped.

Public detail first looks up a published Quest. If it is not public, only the authenticated creator can resolve it; an absent ID, another user's draft, and an archived Quest requested by an anonymous or non-owner caller all return the same generic `404`. The same representation and ETag are returned for public and owner detail reads.

The repository query and explicit DTO keep privacy filtering server-side. No migration, search engine, cache, or dependency was added. Search and category filters are intentionally deferred to SQ-013.

## Validation evidence

Executed from the repository root:

```sh
cd backend && JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash scripts/test-postgres.sh
```

Result: **BUILD SUCCESS**, Java 21.0.13, fresh isolated PostgreSQL cluster; 33 tests passed (9 unit and 24 PostgreSQL/integration tests), with zero failures, errors, or skips. Real HTTP tests cover an empty catalogue, published-only detail and counts, anonymous and authenticated owner visibility, deterministic tie-breaking, default/max/invalid pagination, out-of-range empty pages, and malformed/nonexistent IDs. No existing database was touched.

## Interview questions

1. Why should the published-only predicate be applied in the database query rather than filtering a page after retrieval?
2. How does a deterministic identifier tie-breaker improve paginated results?
3. Why should the response for someone else's private Quest match the response for a nonexistent Quest?
