# Quest draft form (SQ-016 / #27)

SQ-016 adds create/edit screens for owned Quest drafts. Existing `/quests/new` and `/quests/:id/edit` routes are preserved, while `/my/quests/new` and `/my/quests/:id/edit` are compatibility aliases.

## Behavior

- Create loads a blank draft form and creates with `POST /api/v1/quests`.
- Successful create opens the edit route for the server-created ID.
- Edit loads `GET /api/v1/me/quests/:id` and rejects non-draft quest state in the UI.
- Edit submits `PATCH /api/v1/quests/:id` with the returned ETag in `If-Match`.
- Uses CSRF through the shared authentication security-token flow.
- Draft validation mirrors the contract for title, description, and reward.
- Server field errors stay attached to their inputs.
- Failed saves retain values and show stale/conflict or service errors.
- A pending save disables duplicate submissions and the screen tracks saved versus unsaved state.

## Interview questions

1. Why does the edit form send the ETag with `If-Match` instead of trusting stale client state?
2. Which draft fields can remain incomplete, and why can publication validation be stricter than draft validation?
3. Why should field errors and network failures preserve entered values rather than reset the form?
