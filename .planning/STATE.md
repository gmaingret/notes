---
gsd_state_version: 1.0
milestone: v2.0
milestone_name: Native Android Client
status: defining_requirements
stopped_at: null
last_updated: "2026-03-12T00:00:00Z"
last_activity: 2026-03-12 — Milestone v2.0 started
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
---

---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Mobile & UI Polish
status: ready_to_plan
stopped_at: Completed 08-03-PLAN.md
last_updated: "2026-03-11T06:15:18.643Z"
last_activity: 2026-03-10 — v1.1 roadmap created, phases 5-8 defined
progress:
  total_phases: 5
  completed_phases: 4
  total_plans: 23
  completed_plans: 22
---

---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Mobile & UI Polish
status: ready_to_plan
stopped_at: Completed 07.1-03-PLAN.md — Phase 7.1 approved by user
last_updated: "2026-03-10T20:19:38.250Z"
last_activity: 2026-03-10 — v1.1 roadmap created, phases 5-8 defined
progress:
  total_phases: 5
  completed_phases: 4
  total_plans: 18
  completed_plans: 18
  percent: 94
---

---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Mobile & UI Polish
status: ready_to_plan
stopped_at: Completed 07-05-PLAN.md — checkpoint approved
last_updated: "2026-03-10T17:45:23.353Z"
last_activity: 2026-03-10 — v1.1 roadmap created, phases 5-8 defined
progress:
  [█████████░] 94%
  completed_phases: 3
  total_plans: 14
  completed_plans: 14
---

---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Mobile & UI Polish
status: ready_to_plan
stopped_at: Completed 06-dark-mode-04-PLAN.md
last_updated: "2026-03-10T15:48:38.846Z"
last_activity: 2026-03-10 — v1.1 roadmap created, phases 5-8 defined
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 9
  completed_plans: 9
---

---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Mobile & UI Polish
status: ready_to_plan
stopped_at: "Roadmap created for v1.1 — ready to plan Phase 5"
last_updated: "2026-03-10T00:00:00Z"
last_activity: 2026-03-10 — v1.1 roadmap created, phases 5-8 defined
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-12)

**Core value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.
**Current focus:** Milestone v2.0 — Native Android Client

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-03-12 — Milestone v2.0 started

## Performance Metrics

**Velocity:**
- Total plans completed: 32 (v1.0)
- Average duration: ~45 min (v1.0 estimate)
- Total execution time: ~24 hours (v1.0)

**By Phase:**

| Phase | Plans | Avg/Plan |
|-------|-------|----------|
| 1-4. v1.0 | 32 total | - |
| 5-8. v1.1 | TBD | - |

*Updated after each plan completion*
| Phase 05-mobile-layout-foundation P03 | 1 | 1 tasks | 5 files |
| Phase 05-mobile-layout-foundation P00 | 12 | 1 tasks | 1 files |
| Phase 05 P01 | ~4 min | 2 tasks | 4 files |
| Phase 05-mobile-layout-foundation P02 | 8 | 2 tasks | 4 files |
| Phase 06-dark-mode P00 | 5 | 1 tasks | 1 files |
| Phase 06-dark-mode P01 | 2 | 2 tasks | 2 files |
| Phase 06-dark-mode P02 | 6 | 2 tasks | 14 files |
| Phase 06-dark-mode P03 | 7 | 2 tasks | 8 files |
| Phase 06-dark-mode P04 | 5 | 1 tasks | 1 files |
| Phase 07-icons-fonts-and-pwa P01 | 5 | 1 tasks | 1 files |
| Phase 07-icons-fonts-and-pwa P02 | 8 | 2 tasks | 5 files |
| Phase 07-icons-fonts-and-pwa P03 | 8 | 2 tasks | 4 files |
| Phase 07-icons-fonts-and-pwa P04 | 8 | 2 tasks | 3 files |
| Phase 07 P05 | 8 | 1 tasks | 6 files |
| Phase 07.1-ui-polish-tweaks P00 | 4 | 1 tasks | 1 files |
| Phase 07.1-ui-polish-tweaks P01 | 4 | 2 tasks | 2 files |
| Phase 07.1-ui-polish-tweaks P02 | 12 | 2 tasks | 4 files |
| Phase 07.1-ui-polish-tweaks P03 | 5 | 2 tasks | 0 files |
| Phase 08-swipe-polish-and-quick-open-palette P00 | 7 | 2 tasks | 2 files |
| Phase 08-swipe-polish-and-quick-open-palette P02 | 2 | 2 tasks | 3 files |
| Phase 08-swipe-polish-and-quick-open-palette P01 | 5 | 2 tasks | 1 files |
| Phase 08-swipe-polish-and-quick-open-palette P03 | 8 | 2 tasks | 3 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- v1.1 scope: System-preference dark mode only — manual toggle deferred to v1.2
- v1.1 scope: Static PWA manifest without service worker — avoids API cache-stale risk on deploys
- v1.1 scope: CSS transitions for swipe snap-back — defer Framer Motion until proven insufficient on physical device
- v1.1 arch: Sidebar always-mounted — conditional unmount evicts React Query caches (DocumentList, TagBrowser flicker)
- v1.1 arch: dnd-kit sensor must switch to delay-based activation (250ms) in Phase 5 before swipe polish in Phase 8
- [Phase 05-mobile-layout-foundation]: CSS ::after pseudo-element (inset:-14px) for bullet dot tap expansion — zero layout impact, visual dot size unchanged
- [Phase 05-mobile-layout-foundation]: Targeted per-element touch target fixes (no global rule) to avoid breaking toolbar and tab bar layouts
- [Phase 05-mobile-layout-foundation]: MOBL-02 backdrop tests expect always-mounted .sidebar-backdrop class (not conditional .mobile-overlay) to support CSS fade transitions
- [Phase 05-mobile-layout-foundation]: TDD Wave 0 scaffold written before any production code — all tests RED at time of writing
- [Phase 05-01]: sidebar-backdrop always rendered (not conditional) for CSS fade-out transition
- [Phase 05-01]: !important on position/height in mobile CSS overrides inline styles on aside element
- [Phase 05-02]: Hamburger added to both AppPage (floating) and DocumentView (sticky header) — MOBL-01 test mocks DocumentView so AppPage hamburger needed
- [Phase 05-02]: TouchSensor delay=250ms tolerance=5 in BulletTree — pre-positions dnd-kit for Phase 8 swipe without intercepting taps or horizontal swipes
- [Phase 06-dark-mode]: process.cwd() path resolution in Vitest jsdom: import.meta.url is not file:// URL, use process.cwd() + path.resolve() instead
- [Phase 06-dark-mode]: Wave 0 TDD: 6 RED tests written before implementation — DRKM-01, DRKM-03, DRKM-04 covered by string-contains assertions on index.html and index.css
- [Phase 06-dark-mode]: light mode --color-chip-date-text #b45309 (not #d97706) for WCAG AA 4.7:1 on #fef3c7
- [Phase 06-dark-mode]: --color-row-active-bg token added with rgba light/dark variants for DocumentRow active state
- [Phase 06-dark-mode]: UndoToast uses var(--color-bg-raised) for background; white text intentionally kept for contrast on dark raised backgrounds
- [Phase 06-dark-mode]: ContextMenu onMouseEnter/Leave JS color assignments removed in favor of CSS .context-menu-item:hover rule
- [Phase 06-dark-mode]: DocumentRow active state via .document-row--active class (not inline) so var(--color-row-active-bg) resolves correctly in both themes
- [Phase 06-dark-mode]: LoginPage const styles object deleted entirely — all styles now in login-* CSS classes in index.css
- [Phase 06-dark-mode]: Synchronous FOUC script in index.html <head> — applied before first paint so dark OS users never see white flash
- [Phase 06-dark-mode]: color-scheme meta tag delegates scrollbar and form input dark theming to the browser
- [Phase 07-icons-fonts-and-pwa]: TDD Wave 0: all 20 tests RED before any production code — establishes verification contract for phase 7
- [Phase 07-icons-fonts-and-pwa]: PWA-01 manifest tests use readFileSync inside test body (not top-level) to avoid test file crash when manifest.webmanifest doesn't exist yet
- [Phase 07-icons-fonts-and-pwa]: Sidebar tabs icon-only: tabIcon const map inside IIFE; tab title attributes for accessibility
- [Phase 07-icons-fonts-and-pwa]: Lucide icon standard: size=20 strokeWidth=1.5 — no color, no wrapping span
- [Phase 07-icons-fonts-and-pwa]: Star fill state via .star-filled/.star-outline CSS classes (fill + color tokens) — consistent with design token approach
- [Phase 07-icons-fonts-and-pwa]: Fontsource imports placed at top of main.tsx (before React) per @fontsource docs and plan spec
- [Phase 07-icons-fonts-and-pwa]: JetBrains Mono applied to SearchModal input via .search-modal-input CSS class (not inline style) to keep font declarations in index.css
- [Phase 07-05]: favicon.svg uses <text> with Arial/Helvetica — librsvg renders system fonts so sharp can generate PNGs without path data
- [Phase 07-05]: Generated PNG icons committed to git — not regenerated on server — avoids sharp binary in Docker image
- [Phase 07-05]: No apple-touch-icon or apple-mobile-web-app-capable meta tags — deprecated per MDN/RESEARCH.md anti-patterns
- [Phase 07.1-ui-polish-tweaks]: Phase 7.1 TDD Wave 0: 21 RED test assertions in uiPolish.test.ts cover all 4 Phase 7.1 behaviors before any implementation
- [Phase 07.1-ui-polish-tweaks]: FocusToolbar Lucide icons placed between React and @tanstack imports; BulletNode paddingTop/paddingBottom on outer dnd-kit ref div only
- [Phase 07.1-ui-polish-tweaks]: Footer buttons use sidebar-footer-btn CSS class with :hover token rule for dark mode correctness
- [Phase 07.1-ui-polish-tweaks]: pendingRenameId in Sidebar local state threaded as prop to DocumentList/DocumentRow for inline rename on creation
- [Phase 07.1-ui-polish-tweaks]: User approved all four Phase 7.1 behaviors on https://notes.gregorymaingret.fr — FocusToolbar icons, bullet spacing, sidebar footer, and inline rename verified working
- [Phase 08-swipe-polish-and-quick-open-palette]: Wave 0 TDD: all Phase 8 test assertions written before any production code — establishes verification contract
- [Phase 08-swipe-polish-and-quick-open-palette]: existsSync fallback pattern for files not yet created: falls back to empty string so assertions fail cleanly rather than throwing
- [Phase 08-swipe-polish-and-quick-open-palette]: QuickOpenPalette uses flat PaletteResult union type for keyboard navigation across grouped result sections
- [Phase 08-swipe-polish-and-quick-open-palette]: quickOpenOpen intentionally excluded from uiStore partialize — resets to false on page reload
- [Phase 08-swipe-polish-and-quick-open-palette]: exitDirection + onTransitionEnd pattern: mutations fire after CSS slide-off animation, not at pointer-up
- [Phase 08-swipe-polish-and-quick-open-palette]: pendingActionRef captures bullet.id/documentId/isComplete before setExitDirection() to avoid stale React closure in onTransitionEnd
- [Phase 08-swipe-polish-and-quick-open-palette]: iconScale computed in render from live swipeX ratio (0.5x→1.0x charging, 1.2x pulse at threshold)
- [Phase 08-swipe-polish-and-quick-open-palette]: Ctrl+K branch added inside existing handleKeyDown useEffect alongside Ctrl+E — single event listener handles both shortcuts
- [Phase 08-swipe-polish-and-quick-open-palette]: header-search-btn visible on all screen sizes (not mobile-only) — search is universally useful

### Roadmap Evolution

- Phase 07.1 inserted after Phase 7: UI Polish Tweaks (URGENT)

### Pending Todos

None.

### Blockers/Concerns

- iOS 26 visualViewport regression (WebKit bug #237851): defensive FocusToolbar clamp needed in Phase 5; full validation requires physical device on iOS 26 stable
- Physical iPhone required to validate: dnd-kit swipe conflict, 100dvh clip, contenteditable auto-zoom — DevTools emulation does not reproduce these issues

## Session Continuity

Last session: 2026-03-11T06:15:18.638Z
Stopped at: Completed 08-03-PLAN.md
Resume file: None
