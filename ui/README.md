# SideQuest UI

React, TypeScript, and Vite web client with the SQ-019 gaming UI foundation. See [architecture, components, and handoff](../docs/design/FRONTEND-FOUNDATION.md).

## Run locally

```sh
cd ui
npm ci
npm run dev
```

Start the backend in another terminal using `backend/README.md`. The dev server proxies `/api` to `http://localhost:8080`. To change the target, copy `.env.example` to `.env.local`, edit `API_ORIGIN`, and restart Vite. An exported `API_ORIGIN` also works and overrides the file. Never put database credentials in the UI configuration.

The root redirects to `/quests`, a public catalogue with URL-backed search, filters and pagination. `/quests/:id` displays API details; the backend conceals private quests from non-owners. `/login` and `/register` establish server sessions. Protected `/my-quests`, `/quests/new`, and `/quests/:id/edit` provide creator management. `/my/quests` redirects to the canonical dashboard.

The shared request boundary sends same-origin cookies, handles Problem Details and notifies authentication state about expired sessions. CSRF rejection verifies session status without replaying writes. Draft editing requires a real strong ETag; uncertain mutations require explicit recovery. Unsaved changes block publication and prompt before navigation or sign-out. Title is required even for drafts; validation counts Unicode code points.

The footer retains the retryable health check. `/dev/components` is a development-only presentation gallery. Profile, joining, completion, XP awards and achievements are outside this implementation.

## Check the integration

```sh
npm test
npm run build
curl --fail http://localhost:5173/api/v1/health
```

Run the curl command while both development servers are running. The expected JSON is `{"status":"UP","service":"sidequest-api"}` (key order may differ). Stop the API and refresh the page to see the error state; restart it and choose **Retry connection** to recover.

## Browser checks

From `ui/`, with port 5173 free:

```sh
npx playwright install chromium
npm run test:e2e
```

Alternatively use installed Google Chrome without downloading Chromium:

```sh
PLAYWRIGHT_CHANNEL=chrome npm run test:e2e
```

Playwright starts and stops its own Vite server. Foundation browser checks use deterministic API fixtures. The authentication and review regression suites require `AUTH_E2E=1` and a real API; they are skipped otherwise. Run `JAVA_HOME=/path/to/jdk21 bash backend/scripts/test-browser.sh` from the repository root for all 11 checks against a freshly created PostgreSQL database and real API. The harness sets `API_ORIGIN` and `AUTH_E2E`, then stops its own services. Screenshots and failure traces go to ignored `test-results/`. Component tests use Vitest, React Testing Library, and jsdom; browser tests cover native dialog focus, navigation, responsive layout, reduced motion, and automated accessibility checks.

## Proxy and CORS

The browser calls the UI origin using a relative `/api` URL. Vite forwards that request to Spring Boot, so local development needs no cross-origin browser permissions or broad backend CORS policy. `npm run preview` uses the same proxy for local build previews. Production hosting must separately route `/api` to the backend and serve `index.html` for client-side route refreshes, while preserving real asset/API errors. The static build does not include the Vite proxy. Deployment is outside this change.
