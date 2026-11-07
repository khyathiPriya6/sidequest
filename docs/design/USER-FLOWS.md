# SideQuest — MVP User Flows

## Information architecture
```text
App
├── Explore (public)
│   ├── Search + Filters
│   └── Quest Detail
│       └── Join → Login (if guest) → Return → Join
├── Create (authenticated)
│   ├── New Draft
│   ├── Edit Draft
│   └── Publish
├── My Quests (authenticated)
│   ├── In Progress
│   ├── Completed
│   └── My Creations (Draft / Published / Archived)
├── Profile (authenticated)
│   └── XP / Badges / Edit
└── Login / Register
```

## Flow A — Explorer joins and completes
Explore → search/filter → open detail → Join → My Quests / In Progress → Complete → XP confirmation → My Quests / Completed → Profile.
- Guest Join redirects to Login and returns to detail.
- Archived/unavailable quest: show meaningful status, disable Join.
- Double completion: no duplicate XP (server-enforced).

## Flow B — Creator publishes quest
Login → Create → fill required fields → Save Draft → Preview/Edit → Publish → published detail → Explore.
- Missing fields: inline errors; submitted values preserved.
- Failed API request: non-destructive retry.
- Unsaved edits: warning before leaving.
- A quest with participants cannot silently change XP/reward rules.

## Flow C — Profile and re-engagement
Login → My Quests → choose in-progress quest → open details → complete → profile XP and completed count update.
- Empty state: suggest Explore.
- API unavailable: error plus retry.
- Progress updates should be consistent across web and future mobile.

## Required screen state matrix
| Screen | Loading | Empty | Error | Unauthenticated | Success |
|---|---|---|---|---|---|
| Explore | skeleton | no results | retry | browse | cards |
| Quest Details | skeleton | not found | retry | Join triggers Login | stateful CTA |
| Create | submit pending | new form | inline + retry | redirect | saved draft |
| My Quests | skeleton | discover CTA | retry | redirect | tabs |
| Profile | skeleton | zero XP | retry | redirect | totals/badges |
| Auth | submitting | blank fields | credential/form error | form | redirect |
