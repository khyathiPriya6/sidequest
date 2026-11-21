# E04-08 — Participation and progression UI handoff

**Status:** Written implementation-ready proposal; owner design approval pending. The existing Figma file is [SideQuest — Gaming UI/UX Design](https://www.figma.com/design/0lh9h3dvhB3ndoLB1RvUNT). The Starter-plan Figma MCP limit blocked canvas edits during this task, so no new Figma frames or prototype links are claimed. This proposal does not satisfy or close UX-006 #35.

## Reuse and visual direction

Continue the existing dark gaming theme and app shell. Keep the system sans-serif font, semantic tokens and component styles in [`ui/src/styles/tokens.css`](../../ui/src/styles/tokens.css); use the existing Button, Card, Badge, Modal, QuestCard, XPProgressBar and navigation components. Do not introduce a second theme or hand-authored XP/achievement values. Existing Figma references include [Desktop Quest Details](https://www.figma.com/design/0lh9h3dvhB3ndoLB1RvUNT?node-id=1-65) and [Desktop My Quests](https://www.figma.com/design/0lh9h3dvhB3ndoLB1RvUNT?node-id=1-141). The full file has existing desktop/mobile prototypes for Join, completion and profile; update those states once Figma editing is available.

## Screens and states

| View | Required state and behavior |
|---|---|
| Quest Details | Guest Join routes through login and returns to this quest; it does not join automatically. Published quest for another user: Join → pending/disabled → Joined with Continue/Complete. Creator sees a disabled self-enrollment explanation. Completed shows Completed and the saved reward, with no repeat action. A public archived quest is unavailable; an existing participant reaches its private participation detail, sees an Archived label and may still complete while in progress. |
| My Quests | Three separately labelled views: In Progress, Completed, My Creations. Participant views use server pagination and filters; keep existing creator list/actions for My Creations. Preserve the archive status on an existing participant card. Empty tabs link to the relevant next step; error has retry. |
| Completion confirmation | Before the irreversible action, explain that completion is self-reported and permanent; show the Quest title and original reward snapshot. Confirm and Cancel are explicit. Pending prevents repeat clicks. Only a successful server response may show the XP success state. Repeat completion renders the saved result without replaying the reward animation. |
| Player Profile | Show API username/created date, total XP, level, current-level progress, remaining XP, joined/active/completed/created counts and unlocked count. List all nine achievements with progress and locked/unlocked labels. XP history is paginated and displays ledger amounts and award times. Archived completions count. |
| Shared loading | Use existing skeleton/loading patterns with stable layout; label loading for assistive technology. Do not substitute sample XP, quest rows or achievements. |
| Shared empty | My Quests and XP history explain that no records exist and provide a navigation action. Zero-data profile shows Level 1, zero XP and the actual locked achievement progress from the APIs. |
| Shared error | Explain the failed operation and offer a safe GET reload. Never show raw server/SQL details. On uncertain Join or completion outcome, fetch the owner-scoped history/detail before presenting a result; do not automatically replay the mutation. |
| Session expired | Preserve a safe return route for reads/navigation and send the user to login. For a timed-out mutation, authenticate, then reload participation state before offering an explicit action. Never assume the mutation failed or replay it automatically. |

## API and state mapping

- Quest Join: `POST /api/v1/quests/{id}/join`; guest redirect preserves the quest path. Do not send a user ID or XP.
- My Quests participant views: `GET /api/v1/me/participations?page=0&size=20`, with optional `status` and `questId`. Detail/recovery: `GET /api/v1/me/participations/{id}`. Use response pagination metadata and server ordering; archived summaries come from these private endpoints.
- Completion: `POST /api/v1/participations/{id}/complete`, bodyless with the current CSRF token. Recovery uses owner detail/history GET. Display the server's saved `completedAt` and `awardedXp`.
- Profile: `GET /api/v1/me/profile`; achievements: `GET /api/v1/me/achievements`; ledger history: `GET /api/v1/me/xp-transactions?page=0&size=20`. Keep all private responses out of shared caches.
- Use the backend integration PR #71 and its OpenAPI files as the authoritative response shapes. No frontend calculations for total XP, levels, reward awards or unlock times.

## Responsive and accessibility requirements

- Match the existing 1440px desktop shell and 390px mobile navigation; verify tablet at 768px and narrow viewport reflow. One-column cards on mobile; no horizontal overflow. Preserve 44px minimum interactive targets and safe-area spacing.
- Use semantic links for navigation, buttons for mutations, labelled filters, and tab semantics with keyboard arrow/Home/End behavior if implemented as an ARIA tablist. Keep focus visible and logical after route changes.
- Confirmation uses an accessible modal/dialog: labelled title, focus placed inside, contained tab sequence, Escape/Cancel behavior, and focus restored to the triggering control. Announce pending and successful status without moving focus unexpectedly.
- Convey status with text and icon/shape as well as color. Verify WCAG AA contrast for actual foreground/background pairs (4.5:1 normal text, 3:1 large text and essential UI boundaries); current token values have not been fully contrast-audited in these states.
- Respect `prefers-reduced-motion`; remove or shorten nonessential XP transitions and never make animation carry reward information.
- Manually review keyboard-only operation, screen-reader names/status announcements, 200% zoom/reflow, and desktop/mobile prototype click targets before sign-off. None is claimed complete by this written proposal.

## Handoff and acceptance

Design review should update the existing desktop/mobile Joined/Completed and profile screens, add Archived participant, completion confirmation, loading/empty/error/session-expired states, and correct the existing Mobile Joined card to “Photo Safari” / CREATIVITY. Then verify the desktop Joined card's full click target and run the journeys in Present mode.

UX-006 #35 remains open. Required owner actions remain: review the six wireframe pairs; review tokens and component states; review keyboard, contrast and reduced-motion behavior; inspect the linked Figma states and desktop/mobile Join/Creator journeys; record explicit handoff/design sign-off in #35. Backend implementation authorization does not satisfy these frontend design approvals.

## Teach-back

The API owns participation and reward truth. A frontend timeout is uncertain, so it first reloads the owner's saved state. The design must show an existing archived participant a private route to finish while keeping archived quests unavailable for new joins.

1. Why should an uncertain completion be recovered with GET before any retry?
2. Which API fields make the profile progress display accurate without client-side reward logic?
3. How can a status remain understandable when a user cannot distinguish the UI colors?
