# SideQuest — Clickable Prototype Review

**Status:** Clickable draft completed in Figma; user acceptance and final QA pending.
**Figma file:** https://www.figma.com/design/0lh9h3dvhB3ndoLB1RvUNT

## How to test
1. Open the file in Figma.
2. Select **Desktop / Explore** or **Mobile / Explore** on the canvas, then use **Present (Play)**.
3. Click the featured challenge, then **Join Quest**, then **Continue Adventure** on login.
4. Join the quest, view My Quests, open the in-progress detail, **Complete Quest**, and inspect the XP reward screen.
5. Return to the initial screen and use the **Create Quest** navigation. Try **Save Draft → Preview Draft → Publish Quest → Explore**.
6. Repeat in mobile screens starting from **Mobile / Explore**. Check readable text, button placement, viewport clipping and keyboard/touch accessibility.

## Implemented clickable states
- Original design: 6 desktop + 6 mobile core screens.
- Added 7 desktop prototype states: Signed-in Quest Detail, Joined/My Quests, In-progress Detail, XP Reward Success, Draft Saved, Draft Preview, Published Quest Success.
- Added 7 mobile states covering the same journey.
- Clickable Figma navigation using on-click prototype reactions with 0.2-second dissolve transitions.
- Prototype navigation via sidebar for desktop and bottom-navigation labels for mobile.

## Core prototype journeys
**Explorer:** Explore → Quest Detail → Login → Signed-in Detail → Join → My Quests → In-progress Detail → Complete → XP Reward → Profile.

**Creator:** Create → Save Draft → Preview → Publish → Published Detail → Explore. Direct Create → Preview also exists.

## Known gaps for review
- Prototype frames use illustrative static data, not a live backend; joining, login, XP and publishing do **not** persist state.
- Mobile Joined/My Quests currently shows a **Book Quest** card linking to the Photo Safari in-progress screen; rename it to Photo Safari and update its category in Figma.
- Desktop Joined/My Quests link may be attached to text within the Photo Safari card rather than its complete card surface; verify full click area.
- Some form states are illustrative only; input fields are not interactive data-entry widgets.
- Full validation/errors, keyboard/focus behavior, voiceover, and accessible color contrast are not yet tested in presentation.
- Current design uses no actual proof-image verification; completion is self-reported in MVP.
- Figma Starter plan MCP tool limit prevented completing the last refinement and final screenshot check; do not claim those validations are complete.
- Figma prototype should be tested via Present mode from the named entry frame. No verified public prototype-player URL has been created.

## Acceptance checklist (UX-006)
- [x] Explorer and Creator sequences connected with Figma on-click navigation
- [x] Desktop and mobile prototype states added
- [x] Figma frame navigation references checked programmatically
- [ ] Repair inconsistent Mobile Joined card label/category
- [ ] Expand Desktop Joined card click target if needed
- [ ] Owner tests both journeys in Present mode
- [ ] Accessibility and responsive-state review
- [ ] Owner accepts handoff and marks UX-006 complete

## Handoff to implementation
Codex should read docs/design/ and build UI only after owner sign-off. Figma interactive prototype is a **UX demo**, not working application code, authentication, database, or XP ledger.

## E04-08 participation/progression handoff (2026-11-21)

Implementation-ready written states and API mappings are in [E04-08 participation handoff](E04-08-PARTICIPATION-HANDOFF.md). The Figma Starter-plan MCP limit blocked this session's canvas edits; no new Figma frames, screenshots, or approval are claimed. The handoff specifies Join/Joined/Completed/Archived states, My Quests tabs, irreversible completion confirmation/recovery, profile and XP history, loading/empty/error/session-expired behavior, responsive rules and accessibility review. Owner review and actual design sign-off are still required; #35 remains open.
