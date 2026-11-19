# Quest lifecycle UI (SQ-017 / #28)

SQ-017 connects publish and archive actions to the existing draft editor and creator dashboard.

## Behavior

- Draft editor shows Publish for owned drafts.
- Publish requires the shared CSRF token and the draft ETag.
- Publication validation stays in the server and is mapped to a useful UI error.
- Creator dashboard shows Publish for drafts and Archive for drafts/published quests.
- Archive uses a native accessible confirmation dialog; Cancel performs no mutation.
- Successful actions refresh the creator dashboard and remove quests from applicable views.
- Repeat clicks are disabled while mutation is pending.
- ETag, lifecycle, and server failures are shown instead of false success.

## Validation

- `cd ui && npm test` — 61 tests passed across 10 files.
- `cd ui && npm run build` — passed.
- `cd ui && npm run test:e2e` — 9 passed; 1 opt-in real-backend auth test skipped.

## Interview questions

1. Why must lifecycle mutations use `If-Match` instead of only the Quest ID?
2. Why should the archive confirmation explain public discovery impact?
3. What state does the client need to refresh after a successful publish or archive?
