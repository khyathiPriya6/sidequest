# M7-02 — CI checks and local Docker stack

This is a development-focused setup. **It does not deploy a production backend**. No production PostgreSQL connection or release secrets are configured.

## Continuous integration

Workflow: `.github/workflows/ci.yml`

On pull requests and main pushes, three independent jobs run:
- Backend: JDK 21, PostgreSQL binaries, existing isolated PostgreSQL suite via `backend/scripts/test-postgres.sh`.
- Web: Node 22, locked dependency install, Vitest and Vite/TypeScript build.
- Mobile: Node 22, npm install, TypeScript and Expo compatibility checks.

All jobs are expected to pass before a PR is merged. GitHub Actions must be enabled on the repository. The native simulator/device E2E scenarios are **not** run by this workflow. The local test runner does not touch a developer database.

The mobile folder currently has no committed package-lock.json, so the CI install is not fully reproducible. A follow-up should add that lockfile and use `npm ci`.

## Local Docker usage

Prerequisites: Docker Desktop / Docker Engine with Compose, ports 8080 and 5173 available on loopback.

From the repository root:

```bash
cp .env.docker.example .env.docker
# Edit .env.docker and replace the example with a strong *local-only* password.
docker compose --env-file .env.docker config
docker compose --env-file .env.docker up --build -d
docker compose --env-file .env.docker ps
curl http://127.0.0.1:8080/api/v1/health
```

Open `http://127.0.0.1:5173` in your browser. Vite proxies `/api` requests internally to the API container; the browser communicates with the same origin. Mobile iOS simulator can use `EXPO_PUBLIC_API_URL=http://127.0.0.1:8080` when the API is running on the Mac.

The Compose database is a **new isolated Docker volume**, separate from the developer PostgreSQL server. Database port 5432 is **not** published. Web and API ports bind to `127.0.0.1` only. The backend uses the `local` profile with non-secure development session cookies; NEVER use it on an internet-exposed server.

View logs:

```bash
docker compose --env-file .env.docker logs -f api
```

Stop without erasing data:

```bash
docker compose --env-file .env.docker down
```

**Do not run `docker compose down -v`** unless deliberately deleting the disposable Docker DB and after confirming nothing important was stored in it.

## Security and production gaps

- `.env.docker` must stay ignored; Compose interpolation supplies a local-only password.
- Local HTTP cookies are permitted only for loopback development. A future production release needs HTTPS, the default secure cookie policy, trusted reverse proxy configuration, session/CSRF/cross-origin review, managed DB backups and restores, rate limiting, logging and vulnerability review.
- The backend runs as a non-root user inside its runtime container.
- Existing database migrations are applied automatically on API startup. Only point this Compose configuration at its own database; NEVER reuse a real database or production URL.
- Dockerfiles and CI changes have not been executed by the GitHub editing connection; run the above commands and inspect CI results before marking the PR ready.

## Verification checklist

- [ ] Backend isolated PostgreSQL CI job passes
- [ ] Web Vitest and build jobs pass
- [ ] Mobile TypeScript and Expo jobs pass
- [ ] `docker compose config` validates with a real local password
- [ ] API health responds on loopback
- [ ] Web loads and authenticates through Vite proxy
- [ ] Separate Docker DB does not interfere with host PostgreSQL
- [ ] Stop/restart preserves Docker volume data
- [ ] Local mobile simulator authentication still works

## Teach-back

Why is the database not bound to a host port? Why should GitHub CI create its own isolated database rather than use a developer database? How does same-origin Vite proxying help with cookies and CSRF on web? Why shouldn't the `local` profile be deployed publicly?
