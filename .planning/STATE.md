---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Mobile & UI Polish
status: ready_to_plan
stopped_at: Completed 07-icons-fonts-and-pwa-05-PLAN.md
last_updated: "2026-03-10T17:39:38.338Z"
last_activity: 2026-03-10 — v1.1 roadmap created, phases 5-8 defined
progress:
  total_phases: 4
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

See: .planning/PROJECT.md (updated 2026-03-10)

**Core value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.
**Current focus:** Phase 5 — Mobile Layout Foundation

## Current Position

Phase: 5 of 8 (Mobile Layout Foundation)
Plan: 0 of ? in current phase
Status: Ready to plan
Last activity: 2026-03-10 — v1.1 roadmap created, phases 5-8 defined

Progress: [██░░░░░░░░] 50% (v1.0 complete, v1.1 not started)

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

### Pending Todos

None.

### Blockers/Concerns

- iOS 26 visualViewport regression (WebKit bug #237851): defensive FocusToolbar clamp needed in Phase 5; full validation requires physical device on iOS 26 stable
- Physical iPhone required to validate: dnd-kit swipe conflict, 100dvh clip, contenteditable auto-zoom — DevTools emulation does not reproduce these issues

## Session Continuity

Last session: 2026-03-10T17:39:38.336Z
Stopped at: Completed 07-icons-fonts-and-pwa-05-PLAN.md
Resume file: None
