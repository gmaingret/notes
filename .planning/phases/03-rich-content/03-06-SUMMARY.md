---
phase: 03-rich-content
plan: 06
subsystem: ui
tags: [react, zustand, react-query, sidebar, tag-browser, filtered-view]

# Dependency graph
requires:
  - phase: 03-05
    provides: canvasView state in uiStore, sidebarTab state, FilteredBulletList component
  - phase: 03-03
    provides: tags API endpoints (GET /api/tags, GET /api/tags/:type/:value/bullets)
  - phase: 03-04
    provides: chip rendering and TagCount/TagBulletRow data shapes
provides:
  - Sidebar [Docs][Tags] tab bar with conditional DocumentList/TagBrowser render
  - TagBrowser component with grouped tag/mention/date list and filter input
  - useTags hooks: useTagCounts and useTagBullets (React Query)
  - DocumentView filtered branch rendering FilteredBulletList from canvasView state
  - DocumentRow resets canvasView to 'document' on doc click
affects:
  - 03-07
  - 03-08

# Tech tracking
tech-stack:
  added: []
  patterns:
    - useTagCounts/useTagBullets follow the same React Query pattern as useBullets
    - canvasView.type === 'filtered' early-return in DocumentView before main render

key-files:
  created:
    - client/src/hooks/useTags.ts
    - client/src/components/Sidebar/TagBrowser.tsx
  modified:
    - client/src/components/Sidebar/Sidebar.tsx
    - client/src/components/DocumentView/DocumentView.tsx
    - client/src/components/Sidebar/DocumentRow.tsx

key-decisions:
  - "canvasView consumed in DocumentView with early return before main BulletTree render — keeps filtered vs document branches fully separate"
  - "DocumentRow (not DocumentList) holds the setCanvasView reset — navigation happens in DocumentRow onClick, DocumentList is a pure DnD wrapper"
  - "useTagBullets enabled flag uses isFiltered local bool — avoids calling hook conditionally (React rules of hooks)"

patterns-established:
  - "Filtered canvas views use early-return pattern: check canvasView.type at component top, return alternate JSX before main render"

requirements-completed: [TAG-04, TAG-05]

# Metrics
duration: 3min
completed: 2026-03-09
---

# Phase 3 Plan 06: Tag Browser and Sidebar Tab System Summary

**[Docs][Tags] tab bar in Sidebar with TagBrowser component, React Query useTags hooks, and DocumentView filtered canvas via canvasView state**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-09T13:30:51Z
- **Completed:** 2026-03-09T13:33:30Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Created useTags.ts with useTagCounts and useTagBullets React Query hooks
- Created TagBrowser component with grouped tags/mentions/dates list, filter input, and click-to-filter handler
- Added [Docs][Tags] tab bar to Sidebar with conditional rendering
- Extended DocumentView with filtered canvas branch that renders FilteredBulletList when canvasView.type === 'filtered'
- Modified DocumentRow to call setCanvasView({ type: 'document' }) on navigation, resetting the canvas

## Task Commits

Each task was committed atomically:

1. **Task 1: Create useTags hook + TagBrowser component** - `9d034ed` (feat)
2. **Task 2: Add tab bar to Sidebar; handle filtered view in DocumentView and DocumentList** - `2cb02ee` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `client/src/hooks/useTags.ts` - useTagCounts and useTagBullets React Query hooks
- `client/src/components/Sidebar/TagBrowser.tsx` - Grouped tag/mention/date browser with filter input
- `client/src/components/Sidebar/Sidebar.tsx` - Added [Docs][Tags] tab bar + TagBrowser import
- `client/src/components/DocumentView/DocumentView.tsx` - Filtered canvas branch for canvasView.type === 'filtered'
- `client/src/components/Sidebar/DocumentRow.tsx` - setCanvasView reset on doc navigation

## Decisions Made
- canvasView consumed in DocumentView with early return before main BulletTree render — keeps filtered vs document branches fully separate
- DocumentRow (not DocumentList) holds the setCanvasView reset — navigation happens in DocumentRow onClick; DocumentList is a pure DnD wrapper
- useTagBullets enabled flag uses a local `isFiltered` bool computed before hook call — avoids calling hooks conditionally (React rules of hooks)

## Deviations from Plan

None - plan executed exactly as written. (The plan says to modify DocumentList.tsx, but the actual nav/click logic lives in DocumentRow.tsx. Modified DocumentRow instead — correct file for the behavior.)

## Issues Encountered

None.

## Next Phase Readiness
- TagBrowser and filtered canvas are fully wired; ready for Plan 07 (search integration) and Plan 08 (bookmarks canvas)
- No blockers.

---
*Phase: 03-rich-content*
*Completed: 2026-03-09*

## Self-Check: PASSED

- FOUND: client/src/hooks/useTags.ts
- FOUND: client/src/components/Sidebar/TagBrowser.tsx
- FOUND: client/src/components/Sidebar/Sidebar.tsx
- FOUND: client/src/components/DocumentView/DocumentView.tsx
- FOUND: client/src/components/Sidebar/DocumentRow.tsx
- FOUND commit: 9d034ed (Task 1)
- FOUND commit: 2cb02ee (Task 2)
