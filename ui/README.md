# SideQuest UI

React, TypeScript, and Vite starter for the SideQuest web client.

## Run locally

```sh
npm install
npm run dev
```

Start the backend in another terminal using `backend/README.md`. The dev server proxies `/api` to `http://localhost:8080`. To change the target, copy `.env.example` to `.env.local`, edit `API_ORIGIN`, and restart Vite. An exported `API_ORIGIN` also works and overrides the file. Never put database credentials in the UI configuration.

The home screen requests `/api/v1/health` and displays loading, online, or an understandable error with a retry button. Requests time out after eight seconds. Only the expected `UP` response from `sidequest-api` is treated as online. Quest discovery is not implemented yet.

## Check the integration

```sh
npm test
npm run build
curl --fail http://localhost:5173/api/v1/health
```

Run the curl command while both development servers are running. The expected JSON is `{"status":"UP","service":"sidequest-api"}` (key order may differ). Stop the API and refresh the page to see the error state; restart it and choose **Retry connection** to recover.

## Proxy and CORS

The browser calls the UI origin using a relative `/api` URL. Vite forwards that request to Spring Boot, so local development needs no cross-origin browser permissions or broad backend CORS policy. `npm run preview` uses the same proxy for local build previews. Production hosting must separately route `/api` to the backend; the static build does not include the Vite proxy. Deployment is outside this change.
