# SQ-004 — Frontend/API integration

Issue: [#15](https://github.com/khyathiPriya6/sidequest/issues/15).

The web client fetches the real Spring Boot health endpoint through Vite's `/api` proxy. It shows loading while waiting, online only for the expected API response, and a retryable error for failed, invalid, or timed-out responses. `API_ORIGIN` configures the target without source changes.

## Changes and decisions

- `ui/`: React/Vite scaffold, health status component, seven behavior tests, configurable dev/preview proxy, and dependency lockfile.
- `backend/`: Spring Boot scaffold, versioned health endpoint and contract test, PostgreSQL configuration, local credentials import, and initial Liquibase changelog.
- Root ignore rules exclude credentials, dependencies, and build output. READMEs explain local startup and the proxy/CORS tradeoff.
- The API remains a modular monolith. No permissive backend CORS policy is needed for the same-origin proxy. Production requires a hosting route for `/api`.

The owner explicitly requested pushing all prepared changes directly to `main`, including the earlier scaffold work.

## Verification — November 6, 2026

- In `backend/`: `mvn test` — passed, 1 test, 0 failures/errors. The first sandbox attempt could not write the Maven cache; the rerun with cache access succeeded.
- In `ui/`: `npm test` — passed, 7 tests. Covers loading, success, network/HTTP/JSON/contract failures, retry, and timeout.
- In `ui/`: `npm run build` — passed after fixing a syntax error found by the initial build.
- `npm install -D vitest@5.1.0 --fetch-retries=0 --fetch-timeout=20000` — completed; installation audit reported 0 vulnerabilities after updating the test dependency.
- Backend: `mvn -B spring-boot:run -Dspring-boot.run.arguments=--spring.liquibase.enabled=false` — started on 8080 and authenticated to local PostgreSQL 18.4.
- UI: `npm run dev -- --host 127.0.0.1 --strictPort` — started on 5173.
- `curl --fail --silent --show-error http://127.0.0.1:5173/api/v1/health` — returned `{"service":"sidequest-api","status":"UP"}` through the frontend proxy.
- `git diff --check` — passed; `git check-ignore backend/.env.properties` confirmed the credentials file is ignored.

Checks used installed JDK 26.0.1 with compiler release 21. No browser automation was run. Liquibase migrations were disabled for the live check; migration execution was not verified. The health endpoint reports API availability, not continuous database health.

## Security and edge cases

Database passwords stay in the ignored backend configuration and never enter the UI bundle. No migrations or database schema changes were executed. Requests are cancelled when the component unmounts, and stalled requests fail after eight seconds. Invalid or unrelated JSON cannot produce a false online state.

## Teach-back

The browser asks its own Vite server for `/api/v1/health`. Vite forwards that request to Spring Boot and returns the JSON response. React changes the displayed state when that response arrives. This keeps local browser requests on one origin and makes the backend address configurable in one place.

1. Why does the browser request port 5173 while the API listens on 8080?
2. Why should an HTTP 200 response with unexpected JSON still display an error?
3. Why must database credentials stay in the backend configuration rather than a Vite environment variable?
