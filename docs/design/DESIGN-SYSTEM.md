# SideQuest — Gaming Design System v1.0

These are **proposed tokens**, not yet visually approved. Test contrast and usability in real screens before locking.

## Color tokens (dark)
| Token | Hex | Role |
|---|---|---|
| background | `#0B1020` | Primary canvas |
| surface | `#151C30` | Cards and navigation |
| surface-raised | `#202A43` | Popovers and elevated controls |
| border | `#364460` | Dividers and fields |
| text-primary | `#F5F7FF` | Primary text |
| text-secondary | `#C0CBE2` | Supporting copy |
| accent-primary | `#A78BFA` | Interactive highlight |
| accent-secondary | `#67E8F9` | Secondary highlights |
| reward | `#F6C665` | XP/reward gold |
| success | `#74D9A0` | Success states |
| danger | `#FB8794` | Destructive/error states |
Colors are token candidates; verify contrast using a tool and adjust if needed.

## Type and spacing
- Font: Inter or system sans-serif for UI, optional expressive display font for screen headings (verify licensing).
- Type scale: 12/14/16/20/24/32/40px. Body 16px, line height >=1.5.
- Spacing based on 4px: 4, 8, 12, 16, 24, 32, 48, 64.
- Radius: 8px fields, 12px buttons, 16px cards, 24px modal/special containers.
- Container max width 1200px. Shadow and glow restrained; do not glow ordinary body text.

## Components
- **Button:** primary, secondary, ghost, destructive; default, hover, focus, disabled, loading.
- **QuestCard:** illustration or colored placeholder, title, short description, difficulty, category, XP, CTA/route.
- **DifficultyBadge:** EASY, MEDIUM, HARD with text label, not color alone.
- **XPBadge/Progress:** earnable reward clearly separate from achieved XP.
- **SearchField / FilterChips / Select:** selected state, accessible labels, keyboard support.
- **TextField / TextArea / FormError:** helper text, required indicators and validation.
- **AppShell:** top nav on desktop, bottom nav on narrow authenticated screens.
- **Dialog/Toast:** confirmation, error, success with accessible focus management.
- **EmptyState / Skeleton / ErrorPanel:** for discovery and profile asynchronous state.
- **AchievementTile:** awarded vs locked labels; no fabricated achievement claims.

## Iconography and imagery
- Consistent icon library; don't use emoji as the only signifier.
- Choose illustrations for quest categories (adventure, creativity, fitness, learning, food, social).
- Do not copy proprietary game artwork or trademarked assets.
- Prefer subtle background patterns over full-screen animated backgrounds.

## Components before code
Document component variants and states in Figma first. Codex can implement tokens as CSS variables after design sign-off.
