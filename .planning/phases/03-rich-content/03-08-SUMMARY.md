---
phase: 03-rich-content
plan: "08"
subsystem: client-bookmarks
tags: [react, react-query, bookmarks, context-menu, toolbar, canvas-view]

# Dependency graph
requires:
  - phase: 03-05
    provides: bookmark API endpoints (POST/DELETE/GET /api/bookmarks)
  - phase: 03-06
    provides: FilteredBulletList component, canvasView filtered branch pattern
  - phase: 03-07
    provides: DocumentToolbar with Search button
provides:
  - useBookmarks hooks (useBookmarks, useAddBookmark, useRemoveBookmark)
  - Bookmarks canvas branch in DocumentView (replaces main render when canvasView.type === 'bookmarks')
  - Bookmarks toolbar button in DocumentToolbar
  - Bookmark/Remove bookmark option in ContextMenu
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - useBookmarks follows the same React Query pattern as useTags/useSearch (queryKey, mutation, onSettled invalidate)
    - Bookmarks canvas uses same early-return pattern as filtered canvas (Plan 06)

key-files:
  created:
    - client/src/hooks/useBookmarks.ts
  modified:
    - client/src/components/DocumentView/DocumentView.tsx
    - client/src/components/DocumentView/DocumentToolbar.tsx
    - client/src/components/DocumentView/ContextMenu.tsx

key-decisions:
  - "BookmarkRow.id is the bullet ID (not the bookmark record ID) — getUserBookmarks returns bullets.id per service join"
  - "Bookmark lookup in ContextMenu uses bookmarks.some(b => b.id === bullet.id) — matches bullet IDs returned by API"
  - "ContextMenu imports and calls useBookmarks to determine isBookmarked state per bullet"

requirements-completed: [BM-01, BM-02, BM-03]

# Metrics
duration: 2min
completed: 2026-03-09
---

# Phase 3 Plan 08: Bookmarks UI Summary

React Query hooks for bookmarks, a full bookmarks canvas view replacing the main document render, a toolbar button, and a context menu toggle for per-bullet bookmark management.

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-09T13:35:35Z
- **Completed:** 2026-03-09T13:37:43Z
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- Created `useBookmarks.ts` with `BookmarkRow` type and three React Query hooks:
  - `useBookmarks()` — GET /api/bookmarks, queryKey ['bookmarks']
  - `useAddBookmark()` — POST /api/bookmarks {bulletId}, invalidates ['bookmarks'] on settle
  - `useRemoveBookmark()` — DELETE /api/bookmarks/:bulletId, invalidates ['bookmarks'] on settle
- Added bookmarks canvas branch to `DocumentView.tsx` (before the filtered branch):
  - Maps `BookmarkRow[]` to `FilteredBulletRow[]` with `isBookmarked: true`
  - Renders `FilteredBulletList` with title "Bookmarks", row click navigates + resets canvas, toggle removes bookmark
- Added Bookmarks button to `DocumentToolbar.tsx` after the Search button; calls `setCanvasView({ type: 'bookmarks' })`
- Added Bookmark/Remove bookmark option to `ContextMenu.tsx`:
  - Calls `useBookmarks()` to determine `isBookmarked` state per bullet
  - Adds handler that calls `addBookmark.mutate` or `removeBookmark.mutate` depending on current state

## Task Commits

Each task was committed atomically:

1. **Task 1: Create useBookmarks hooks + Bookmarks canvas in DocumentView** - `ceb2402` (feat)
2. **Task 2: Add Bookmarks toolbar button + context menu bookmark option** - `3ff2476` (feat)

## Files Created/Modified

- `client/src/hooks/useBookmarks.ts` — BookmarkRow type, useBookmarks, useAddBookmark, useRemoveBookmark
- `client/src/components/DocumentView/DocumentView.tsx` — Bookmarks canvas branch with FilteredBulletList + removeBookmark toggle
- `client/src/components/DocumentView/DocumentToolbar.tsx` — Bookmarks button after Search button
- `client/src/components/DocumentView/ContextMenu.tsx` — Bookmark/Remove bookmark menu item with toggle logic

## Verification

- TypeScript: clean (no errors)
- Client vitest: 32/32 tests passing
- Server vitest: 88/88 tests passing

## Decisions Made

- BookmarkRow.id is the bullet ID (not the bookmark record ID) — `getUserBookmarks` returns `bullets.id` per service JOIN
- Bookmark lookup in ContextMenu uses `bookmarks.some(b => b.id === bullet.id)` — correctly matches bullet IDs in the API response
- ContextMenu calls `useBookmarks()` directly (not passed as prop) — consistent with how mutations are called via hooks

## Deviations from Plan

None - plan executed exactly as written. The plan said to modify `BulletNode.tsx` for the context menu, but the actual context menu implementation lives in `ContextMenu.tsx` (a separate component imported by BulletNode). Modified ContextMenu.tsx instead — correct file for the behavior.

## Issues Encountered

None.

## Next Phase Readiness

- Bookmarks UI fully wired: toolbar button, canvas view, and context menu toggle
- All three bookmark requirements complete: BM-01 (add), BM-02 (remove), BM-03 (view list)
- Phase 03-rich-content all plans complete

---
*Phase: 03-rich-content*
*Completed: 2026-03-09*

## Self-Check: PASSED

- FOUND: client/src/hooks/useBookmarks.ts
- FOUND: client/src/components/DocumentView/DocumentView.tsx (modified)
- FOUND: client/src/components/DocumentView/DocumentToolbar.tsx (modified)
- FOUND: client/src/components/DocumentView/ContextMenu.tsx (modified)
- FOUND commit: ceb2402 (Task 1)
- FOUND commit: 3ff2476 (Task 2)
