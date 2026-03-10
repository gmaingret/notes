# Roadmap: Notes

## Milestones

- ✅ **v1.0 MVP** — Phases 1-4 (shipped 2026-03-10)
- 🚧 **v1.1 Mobile & UI Polish** — Phases 5-8 (in progress)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-4) — SHIPPED 2026-03-10</summary>

- [x] Phase 1: Foundation (6/6 plans) — completed 2026-03-09
- [x] Phase 2: Core Outliner (8/8 plans) — completed 2026-03-09
- [x] Phase 3: Rich Content (9/9 plans) — completed 2026-03-09
- [x] Phase 4: Attachments, Comments, and Mobile (9/9 plans) — completed 2026-03-10

Full details: [`.planning/milestones/v1.0-ROADMAP.md`](milestones/v1.0-ROADMAP.md)

</details>

### 🚧 v1.1 Mobile & UI Polish (In Progress)

**Milestone Goal:** Transform the app into a genuinely great mobile experience with a polished dark mode, responsive layout, hamburger navigation, improved visual design, PWA installation, and a quick-open palette.

- [ ] **Phase 5: Mobile Layout Foundation** - Responsive sidebar, hamburger menu, touch targets, and desktop toggle
- [ ] **Phase 6: Dark Mode** - System-preference dark theme with WCAG AA colors and FOUC prevention
- [ ] **Phase 7: Icons, Fonts, and PWA** - Lucide icons, self-hosted fonts, and home screen installation
- [ ] **Phase 8: Swipe Polish and Quick-Open Palette** - Swipe gesture animations and Ctrl+K navigation palette

## Phase Details

### Phase 5: Mobile Layout Foundation
**Goal**: Users can navigate the app comfortably on any mobile device — sidebar hidden by default, opened via hamburger, closed by tapping outside or the X button
**Depends on**: Phase 4 (v1.0 complete)
**Requirements**: MOBL-01, MOBL-02, MOBL-03, MOBL-04, MOBL-05, MOBL-06, MOBL-07
**Success Criteria** (what must be TRUE):
  1. On a mobile-width viewport, the sidebar is hidden and the content area fills the full screen width
  2. Tapping the hamburger button in the header slides the sidebar open with a smooth off-canvas animation
  3. Tapping outside the open sidebar or the X button closes the sidebar
  4. All buttons and interactive elements are tappable without precision — minimum 44×44px touch targets
  5. The app fills the visible browser viewport correctly on mobile (no content clipped by address bar or home indicator)
**Plans**: 4 plans
Plans:
- [ ] 05-00-PLAN.md — Test scaffold (mobileLayout.test.tsx, RED state)
- [ ] 05-01-PLAN.md — Viewport fix, sidebar off-canvas transform, X button, Ctrl+E
- [ ] 05-02-PLAN.md — Hamburger button, sticky header, dnd-kit TouchSensor
- [ ] 05-03-PLAN.md — Touch target audit + human verification checkpoint

### Phase 6: Dark Mode
**Goal**: Users with a dark OS preference see a fully themed dark interface with no white flash on load and no unthemed elements
**Depends on**: Phase 5
**Requirements**: DRKM-01, DRKM-02, DRKM-03, DRKM-04
**Success Criteria** (what must be TRUE):
  1. Switching the OS to dark mode causes the app to switch to a dark theme without any page action
  2. Every visible text and background combination passes WCAG AA contrast in both light and dark themes
  3. Hard-refreshing the page in dark OS preference shows a dark background immediately — no white flash
  4. Browser scrollbars and native form inputs (text fields, checkboxes) adopt the active theme
**Plans**: TBD

### Phase 7: Icons, Fonts, and PWA
**Goal**: The app looks polished and can be installed to the home screen — consistent SVG icons, modern typography, and a valid PWA manifest
**Depends on**: Phase 6
**Requirements**: VISL-01, VISL-02, VISL-03, PWA-01, PWA-02, PWA-03
**Success Criteria** (what must be TRUE):
  1. No Unicode characters or emoji are used as icons anywhere in the UI — all replaced with crisp SVG components
  2. The app displays Inter as the UI font and JetBrains Mono for code and tag chips, loaded from the app server (no Google Fonts)
  3. An "Add to Home Screen" prompt is available on both iOS Safari and Chrome Android
  4. After installation, the app opens in standalone mode without browser chrome
**Plans**: TBD

### Phase 8: Swipe Polish and Quick-Open Palette
**Goal**: Swipe gestures feel intentional and satisfying, and users can navigate to any document or bookmark from anywhere in the app with two keystrokes
**Depends on**: Phase 7
**Requirements**: GEST-01, GEST-02, GEST-03, GEST-04, GEST-05, QKOP-01, QKOP-02, QKOP-03, QKOP-04, QKOP-05, QKOP-06, QKOP-07
**Success Criteria** (what must be TRUE):
  1. Swiping a bullet reveals a color-coded backing (green for complete, red for delete) with the matching icon that scales with drag distance
  2. Releasing a swipe below the commit threshold snaps the row back to center with an ease-out animation
  3. Completing a swipe animates the row out before it disappears from the list
  4. Pressing Ctrl+K opens a palette showing recent documents, and typing instantly filters documents and searches bullet content
  5. The user can navigate palette results with arrow keys and open a result with Enter, or dismiss with Escape
**Plans**: TBD

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation | v1.0 | 6/6 | Complete | 2026-03-09 |
| 2. Core Outliner | v1.0 | 8/8 | Complete | 2026-03-09 |
| 3. Rich Content | v1.0 | 9/9 | Complete | 2026-03-09 |
| 4. Attachments, Comments, and Mobile | v1.0 | 9/9 | Complete | 2026-03-10 |
| 5. Mobile Layout Foundation | 2/4 | In Progress|  | - |
| 6. Dark Mode | v1.1 | 0/? | Not started | - |
| 7. Icons, Fonts, and PWA | v1.1 | 0/? | Not started | - |
| 8. Swipe Polish and Quick-Open Palette | v1.1 | 0/? | Not started | - |
