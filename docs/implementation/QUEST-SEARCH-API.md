# Quest search API (SQ-013 / #24)

SQ-013 extends `GET /api/v1/quests` with case-insensitive literal substring search and exact category/difficulty filters. It adds no search service, cache, migration, or dependency.

## Agreed search behavior

The product owner selected:

- Trim surrounding whitespace from `q`; blank or whitespace-only queries mean no search filter.
- Limit the trimmed query to 120 Unicode code points. Longer queries receive `400`; the API does not truncate.
- Treat `%`, `_`, and backslash as literal characters, not SQL wildcard syntax.

Search checks title or description, case-insensitively. Category and difficulty use their exact enum values. Search and filters compose with the published-only predicate before both page content and total count are computed.

## Implementation decisions

`QuestService` normalizes and bounds the query. A custom Spring Data repository fragment uses JPA Criteria with a bound search-pattern parameter, an explicit `LIKE` escape character, and the same predicate builder for result and count queries. User text is never interpolated into SQL. The database still applies the status/category/difficulty predicates and deterministic `createdAt DESC, id DESC` ordering.

## Validation evidence

Executed from the repository root:

```sh
cd backend && JAVA_HOME=/private/tmp/jdk-21.0.13+9/Contents/Home PATH=/private/tmp/jdk-21.0.13+9/Contents/Home/bin:$PATH bash scripts/test-postgres.sh
```

Result: **BUILD SUCCESS**, Java 21.0.13, fresh isolated PostgreSQL cluster; 35 tests passed (9 unit and 26 PostgreSQL/integration tests), with zero failures, errors, or skips. Real HTTP tests cover case-insensitive title/description matching, combined filters and count consistency, literal wildcard/backslash behavior, empty queries, injection-shaped input, 120/121-code-point boundaries, and invalid enum filters. No existing database was touched.

## Interview questions

1. Why should a search pattern be a bound SQL parameter even when wildcard characters are escaped?
2. How can result and count queries drift if their filter predicates are implemented separately?
3. Why define a maximum query length in Unicode code points rather than UTF-16 code units?
