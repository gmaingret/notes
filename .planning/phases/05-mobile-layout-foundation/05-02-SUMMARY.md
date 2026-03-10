---
phase: 05-mobile-layout-foundation
plan: "02"
subsystem: client/layout
tags: [mobile, dnd-kit, touch-sensor, sticky-header, hamburger, react]

requires:
  - phase: "05-01"
    provides: "Off-canvas sidebar with class toggle, backdrop, X close button, 100dvh"
provides:
  - "MOBL-01: hamburger button in DocumentView sticky header and AppPage floating button"
  - "MOBL-05: .sticky-header CSS class with position:sticky"
  - "MOBL-04: dnd-kit multi-sensor — TouchSensor with 250ms delay on mobile"
affects: ["05-03", "08-swipe-gestures"]

tech-stack:
  added: []
  patterns:
    - "TouchSensor with delay=250ms + tolerance=5 alongside PointerSensor for hybrid drag-and-swipe"
    - "position:sticky header in document view (z-index 5, below sidebar z-index 10)"
    - "mobile-only floating hamburger in AppPage (fixed position, z-index 8) for when sidebar is closed"

key-files:
  created: []
  modified:
    - client/src/components/DocumentView/DocumentView.tsx
    - client/src/components/DocumentView/BulletTree.tsx
    - client/src/pages/AppPage.tsx
    - client/src/index.css

key-decisions:
  - "Hamburger added to BOTH AppPage (floating, fixed pos) AND DocumentView (sticky header) — AppPage one needed for MOBL-01 test which mocks DocumentView; DocumentView one provides sticky UX while scrolling"
  - "TouchSensor uses delay:250ms + tolerance:5 (not 0) per RESEARCH.md — prevents accidental drag activation on tap/swipe, avoids cancel on minor finger tremor"
  - "isMobile = window.matchMedia at render time — valid in CSR-only Vite app; spreads into useSensors without violating Rules of Hooks"
  - ".sticky-header z-index:5 is intentional — below sidebar (z-index 10) and backdrop (z-index 9)"

requirements-completed: [MOBL-01, MOBL-05]

duration: 8min
completed: "2026-03-10"
---

# Phase 5 Plan 02: Mobile Layout Foundation — Hamburger Button and Touch Drag Summary

**Sticky DocumentView header with hamburger open button (MOBL-01) and dnd-kit TouchSensor with 250ms delay activation for mobile swipe-safe drag (MOBL-04 prerequisite)**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-10T13:24:00Z
- **Completed:** 2026-03-10T13:31:52Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- DocumentView header wrapped in `.sticky-header` div — title/breadcrumb stays visible while scrolling bullets
- Hamburger button (&#9776;) in DocumentView header with `mobile-only` class calls `setSidebarOpen(true)` on click
- Floating hamburger added to AppPage for the case when no document is active (satisfies MOBL-01 test which mocks DocumentView)
- BulletTree `useSensors` updated with conditional `TouchSensor` (250ms delay, tolerance 5) alongside `PointerSensor` for hybrid pointer/touch drag
- All 62 tests GREEN including all 13 mobileLayout.test.tsx tests

## Task Commits

Each task was committed atomically:

1. **Task 1 (prereq): AppPage hamburger button** - `64641e6` (feat)
2. **Task 1: DocumentView sticky header with hamburger** - `87de5ba` (feat)
3. **Task 2: BulletTree multi-sensor TouchSensor** - `1bfec44` (feat)

## Files Created/Modified
- `client/src/components/DocumentView/DocumentView.tsx` - Added sticky header div with hamburger button, added `setSidebarOpen` to useUiStore destructure
- `client/src/components/DocumentView/BulletTree.tsx` - Added TouchSensor import, multi-sensor useSensors pattern with mobile detection
- `client/src/pages/AppPage.tsx` - Added floating mobile-only hamburger button when sidebar is closed
- `client/src/index.css` - Added `.sticky-header` CSS class rule (position:sticky, z-index:5)

## Decisions Made
- Hamburger added to both AppPage AND DocumentView: the MOBL-01 test renders `<AppPage />` with DocumentView mocked out, so the hamburger must exist outside DocumentView for the test to find it. Both buttons serve different UX purposes (AppPage: always-accessible when sidebar closed; DocumentView: visible while scrolling a document).
- TouchSensor `tolerance: 5` (not 0) per RESEARCH.md guidance — zero tolerance causes drag cancel on minor finger tremor during the 250ms delay window.
- `isMobile` computed via `window.matchMedia('(max-width: 768px)').matches` at render time. This is correct for a CSR-only Vite app. The spread into `useSensors` is not a conditional hook call — useSensor and useSensors are called unconditionally.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added hamburger button to AppPage in addition to DocumentView**
- **Found during:** Task 1 verification
- **Issue:** The MOBL-01 test renders `<AppPage />` with DocumentView mocked via `vi.mock()`. A hamburger added only to DocumentView would be invisible to the test, keeping MOBL-01 RED.
- **Fix:** Added a `mobile-only` floating hamburger button to AppPage's main area, visible only when `!sidebarOpen`. Also added the one to DocumentView as specified in the plan.
- **Files modified:** `client/src/pages/AppPage.tsx`
- **Verification:** `npx vitest run src/test/mobileLayout.test.tsx` — all 13 tests GREEN including MOBL-01
- **Committed in:** `64641e6` (feat(05-01): AppPage — hamburger button opens sidebar on mobile)

---

**Total deviations:** 1 auto-fixed (blocking — test mock prevented DocumentView-only hamburger from satisfying MOBL-01)
**Impact on plan:** Fix was necessary for test to pass. Adds a second hamburger entry point which improves UX (accessible even on the "Select a document" empty state screen).

## Issues Encountered
- Plan 05-01 was already executed in a prior session (commits `c233c40`, `294ff83`). The dependency was satisfied — implemented only 05-02 changes plus the AppPage hamburger deviation.

## Next Phase Readiness
- MOBL-01 complete: sidebar open path works (hamburger → setSidebarOpen(true)) on mobile
- TouchSensor in place: Phase 8 swipe gesture work can proceed without conflict with drag activation
- `.sticky-header` CSS class available for use by any future sticky UI elements

---
*Phase: 05-mobile-layout-foundation*
*Completed: 2026-03-10*
