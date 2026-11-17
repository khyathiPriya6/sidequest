# SideQuest UI

React, TypeScript, and Vite web client with the SQ-019 gaming UI foundation. See [architecture, components, and handoff](../docs/design/FRONTEND-FOUNDATION.md).

## Run locally

```sh
cd ui
npm ci
npm run dev
```

Start the backend in another terminal using `backend/README.md`. The dev server proxies `/api` to `http://localhost:8080`. To change the target, copy `.env.example` to `.env.local`, edit `API_ORIGIN`, and restart Vite. An exported `API_ORIGIN` also works and overrides the file. Never put database credentials in the UI configuration.

The root redirects to `/quests`, which shows the themed application shell and existing empty quest-board state. The footer's **Service status** section requests `/api/v1/health` and displays loading, online, or an error with retry. Requests time out after eight seconds. Only the expected `UP` response from `sidequest-api` is treated as online.

In development, open `/dev/components` to inspect the seven reusable components. Its **Preview member navigation** button shows desktop/mobile member navigation using a local presentation fixture, not an authenticated session. The gallery, link, and sample quest data are excluded from production builds. Other product routes are explicit coming-soon placeholders; Quest CRUD, authentication, and XP awards are not implemented.

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

Playwright starts and stops its own Vite server. It mocks only the health endpoint for deterministic component/layout checks; a real-backend proxy check is separate. Screenshots and failure traces go to ignored `test-results/`. Component tests use Vitest, React Testing Library, and jsdom; browser tests cover native dialog focus, navigation, responsive layout, reduced motion, and automated accessibility checks.

## Proxy and CORS

The browser calls the UI origin using a relative `/api` URL. Vite forwards that request to Spring Boot, so local development needs no cross-origin browser permissions or broad backend CORS policy. `npm run preview` uses the same proxy for local build previews. Production hosting must separately route `/api` to the backend and serve `index.html` for client-side route refreshes, while preserving real asset/API errors. The static build does not include the Vite proxy. Deployment is outside this change.
