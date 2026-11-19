# Quest creator dashboard (SQ-015 / #26)

SQ-015 adds the signed-in creator dashboard at `/my-quests`. `/my/quests` remains a compatibility alias and redirects to the canonical route without losing query parameters.

## What changed

- Requests `GET /api/v1/me/quests` through the same-origin, session-aware API client.
- Keeps status and page in the URL.
- Provides All, Draft, Published, and Archived status views.
- Uses owner-only API responses for the creator's private drafts and archives.
- Links drafts to the edit route and published/archived quests to public/owner detail routes.
- Shows loading, empty, retryable error, and pagination states.
- Reuses the authentication provider and `RequireAuth`; expired or anonymous sessions use the shared sign-in redirect.

## Engineering decisions

The UI does not infer creator ownership or filter out non-owner quests. Ownership and data isolation remain the server's responsibility at `/api/v1/me/quests`. The status filter is an exact enum filter and pagination mirrors the API contract. Editing is exposed only for `DRAFT`; published and archived cards do not contain an edit action.

The compatibility route is public and canonicalizes to `/my-quests` before the protected child route redirects anonymous users to sign-in. This preserves the issue-approved route alternative without changing existing navigation.

## Validation evidence

```sh
cd ui && npm test
```

Result: 57 tests passed across 9 files.

```sh
cd ui && npm run build
```

Result: TypeScript and Vite production build passed.

```sh
cd ui && npm run test:e2e
```

Result: 9 browser tests passed and 1 real-backend authentication test was intentionally skipped because it requires the opt-in environment. Existing deterministic browser coverage includes protected-route redirects and mobile/desktop layouts.

## Interview questions

1. Why should a private dashboard rely on server ownership filtering instead of filtering a broader public response in the browser?
2. Why should draft edit actions be state-dependent while published and archived actions are read-only?
3. How does a compatibility route redirect preserve authentication-safe return behavior?
