---
phase: 03-rich-content
plan: "03"
subsystem: ui
tags: [zustand, react, typescript, state-management]

# Dependency graph
requires:
  - phase: 03-01
    provides: Phase 03 branch and initial setup (marked, dompurify, chip test scaffolds)
provides:
  - useUiStore with sidebarTab, canvasView, searchOpen state (non-persisted)
  - CanvasView discriminated union type exported from uiStore
  - FilteredBulletList shared component for tag-filtered, bookmarks, and search canvas views
  - FilteredBulletRow type for typed row data passed to the list
affects:
  - 03-06 (tag-filtered view)
  - 03-07 (bookmarks view)
  - 03-08 (search view)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "zustand partialize: persist only lastOpenedDocId and sidebarOpen; transient UI state resets on reload"
    - "CanvasView discriminated union: document | filtered | bookmarks — exhaustive at type level"
    - "FilteredBulletList: shared list renderer with internal highlight helper; dangerouslySetInnerHTML used only for internally-generated highlight HTML"

key-files:
  created:
    - client/src/components/DocumentView/FilteredBulletList.tsx
  modified:
    - client/src/store/uiStore.ts

key-decisions:
  - "canvasView and sidebarTab are NOT persisted (partialize excludes them) — they reset to defaults on page reload per plan requirement"
  - "CanvasView exported from uiStore.ts so all consumers (plans 06/07/08) import the type from a single canonical location"
  - "highlight() helper uses dangerouslySetInnerHTML with internally-generated HTML only — safe because query comes from app state, not user-provided HTML"

patterns-established:
  - "Non-persisted transient UI state lives in uiStore but excluded via partialize"
  - "Shared list component pattern: consumers pass typed rows, component handles render + interactions"

requirements-completed: [TAG-04, TAG-05, BM-02, BM-03, SRCH-03]

# Metrics
duration: 1min
completed: 2026-03-09
---

# Phase 3 Plan 03: UI State Contracts and Shared List Component Summary

**Zustand uiStore extended with non-persisted canvasView/sidebarTab/searchOpen state, plus FilteredBulletList shared renderer used by tag-filtered, bookmarks, and search canvas views**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-03-09T13:21:40Z
- **Completed:** 2026-03-09T13:22:36Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Expanded uiStore with three new non-persisted state fields: `sidebarTab`, `canvasView`, `searchOpen`
- Exported `CanvasView` discriminated union type as canonical type source for plans 06/07/08
- Created `FilteredBulletList` with loading, empty, row, highlight, and bookmark toggle rendering
- TypeScript passes clean with zero errors across full client build

## Task Commits

Each task was committed atomically:

1. **Task 1: Expand uiStore with sidebarTab, canvasView, searchOpen** - `772a47c` (feat)
2. **Task 2: Create FilteredBulletList shared component** - `1661187` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `client/src/store/uiStore.ts` - Added CanvasView type, sidebarTab/canvasView/searchOpen fields and setters; partialize restricts persistence to lastOpenedDocId and sidebarOpen
- `client/src/components/DocumentView/FilteredBulletList.tsx` - Shared list component exporting FilteredBulletRow type and FilteredBulletList component; handles loading, empty, rows, text highlighting, bookmark toggle

## Decisions Made

- `canvasView` and `sidebarTab` use `partialize` to be excluded from localStorage persistence — they always reset to defaults on page reload
- `CanvasView` type exported from `uiStore.ts` as the single canonical import location
- `highlight()` helper uses `dangerouslySetInnerHTML` only for internally-generated HTML (safe — no user-supplied HTML)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plans 06, 07, 08 can now import `useUiStore` (for `setCanvasView`) and `FilteredBulletList` / `FilteredBulletRow` as concrete contracts
- No blockers for downstream implementation plans in Wave 2+

---
*Phase: 03-rich-content*
*Completed: 2026-03-09*
