# SideQuest — UI/UX Specification v1.0 (Gaming)

**Status:** Proposed design baseline for Sprint 0; requires human review before implementation.
**Platforms:** Responsive web first, mobile-specific design now, React Native implementation later.
**Product goal:** Motivating, accessible quest discovery and personal progress—not a gambling or pay-to-win experience.

## Personality and direction
- Mood: adventurous, immersive, mysterious but welcoming.
- Dark-first visual language with high contrast, luminous violet/cyan accent, achievement-gold used sparingly.
- Game language: quest board, XP, challenges, quest log, achievements, difficulty levels.
- Real-world user value before decoration: readable titles and descriptions, visible progress, obvious actions.
- No dark patterns, streak punishment, confusing fake currencies, or excessive animation.

## Navigation architecture (web)
- Global topbar: SideQuest logo → Explore, My Quests, Create, Profile; auth actions.
- Mobile browser: compact header + bottom navigation Explore, My Quests, Create, Profile (when authenticated).
- Guests may Explore and view Quest Details. Join/Create/My Quests/Profile routes ask users to sign in.
- Do not expose links to teams, maps, rankings or photo uploads in MVP navigation.

## Primary MVP screens
1. **Explore** (`/quests`): hero/featured challenge; search; category chips; difficulty filter; quest card grid; pagination; loading/empty/error states.
2. **Quest Detail** (`/quests/:id`): title, category, difficulty, XP, description, creator, participation state, primary CTA (Join / Continue / Complete / Already Completed); disabled state for archived/unauthorized actions.
3. **Create/Edit Quest** (`/quests/new`, `/quests/:id/edit`): title, description, category, difficulty, XP reward, client/server validation, Save Draft / Publish flows, unsaved changes warning.
4. **My Quests** (`/my-quests`): joined/in-progress/completed tabs; creator-owned drafts accessible via separate My Creations section; cards and empty states.
5. **Profile** (`/profile`): avatar fallback, display name, earned XP, completion count, basic achievement badge; edit profile.
6. **Auth** (`/login`, `/register`): email/password, helpful validation, loading/errors; no social login until specified.

## User experience principles
- One dominant CTA per view.
- Publish and completion actions use clear confirmation where irreversible.
- XP is a reward for a valid first completion; do not imply proof was independently verified.
- Use readable text labels along with icons; ensure focus, keyboard and screen reader support.
- All interactive controls have loading, disabled, hover/focus and error styles.
- Error messages tell users what went wrong and how to recover.
- No business rules enforced only in the frontend.

## Interaction contracts
- Explore → Detail → Join (guest redirected to Login then returned to quest) → My Quests → Complete → XP confirmation → Profile.
- Creator: Auth → Create → Save Draft → Preview/Edit → Publish → Discoverable in Explore.
- Editing or archiving applies only to owned quests; past participation preserved according to PRD.
- Form submissions indicate pending/success/failure; duplicate submissions are prevented on client while server must be idempotent where required.

## Responsive standards
- Baseline widths to design: 390px mobile, 768px tablet, 1440px desktop.
- Content max width: 1200px; desktop cards 3 columns as space permits, tablet 2, mobile 1.
- On mobile: >=44px touch targets, bottom navigation clear of safe area, no hover-only functionality.
- Web keyboard navigation, visible focus outline, semantic controls, labels, readable scaling to 200%.

## Motion and accessibility
- 120–220ms subtle transitions for cards/modal/CTA, never essential for understanding.
- Respect `prefers-reduced-motion`; XP success animation optional and dismissible.
- Target WCAG 2.2 AA for standard text contrast (4.5:1) and larger text (3:1); validate actual token pairings.
- Avoid communicating status exclusively through color.
- Define screen-reader announcement for async search results and XP confirmations.

## Not part of Sprint 0
Do not build or deploy any new feature. Figma or equivalent designs, component spec and wireframes are design artifacts, not working implementations.
