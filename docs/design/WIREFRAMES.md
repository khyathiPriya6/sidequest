# SideQuest — Wireframe Plan (Web First)

**Design tool:** Figma or equivalent. This file is a screen-by-screen blueprint, not an existing Figma design.
**Breakpoints:** Desktop 1440px, tablet 768px, mobile 390px. Include alternate loading, empty and error states.

## WF-01 Explore
Desktop layout:
```text
[Logo] [Explore] [My Quests] [Create]            [Profile/Login]
[Hero: Your next adventure awaits]           [Featured Quest]
[Search quests........................] [Difficulty] [Category]
[Quest Card]   [Quest Card]   [Quest Card]
[Quest Card]   [Quest Card]   [Quest Card]
[Previous] Page 1 of N [Next]
```
Mobile: logo + auth, hero short, search full-width, horizontal filter chips, single-column cards, bottom nav if authenticated.

## WF-02 Quest Details
```text
[Back to Explore] [Category] [Difficulty]
[Quest title]                                  [XP Reward]
[Illustration / thematic art]
[Description + requirements]                   [Join / Continue / Complete]
[Created by ...] [Status feedback]
```
Mobile: stacked content; primary CTA after details, sticky only if it does not obscure content.

## WF-03 Create/Edit
```text
[Create a Quest]
[Title                               ]
[Description                         ]
[Category]       [Difficulty]
[XP Reward]
[Preview] [Save Draft] [Publish]
[Validation errors and unsaved change prompts]
```
Mobile: single-column controls and accessible action grouping.

## WF-04 My Quests
```text
[My Quest Log]
[In Progress] [Completed] [My Creations]
[Quest card with status and next action] ...
[Empty state: Browse quests]
```

## WF-05 Profile
```text
[Avatar] [Display name] [Edit Profile]
[Total XP] [Completed quests] [Joined quests]
[Achievement collection (earned/locked)]
[Recent quest completions]
```

## WF-06 Auth
```text
[Branding / short value proposition]
[Login | Register]
[Email]
[Password + visibility toggle]
[Submit] [Validation / API feedback]
[Return to intended quest when applicable]
```

## Required Figma deliverables
1. One sitemap and three flow diagrams.
2. Six desktop low-fidelity screen frames and six mobile counterparts.
3. Type/color/spacing token page, component variants/state page.
4. High-fidelity desktop designs for Explore, Quest Details, and Create; mobile versions of those three.
5. Screen states: Explore empty/error, Join guest state, Create invalid/loading, Quest completed.
6. Interactive click-through prototype for Explorer Join and Creator Publish.
7. Handoff: component list, design links, responsive rules, acceptance checklists and accessibility review.

## Review checklist
- Can a guest find a quest in <=3 meaningful steps?
- Is the primary CTA obvious without relying on color?
- Can a creator publish without confusion?
- Are error, empty, loading and completed states designed?
- Is XP reward distinguished from XP already earned?
- Does narrow/mobile layout remain usable?
- Are accessibility considerations recorded?
- Have design decisions been reviewed before coding Quest CRUD?
