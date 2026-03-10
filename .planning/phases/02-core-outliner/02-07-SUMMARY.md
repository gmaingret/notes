---
phase: 02-core-outliner
plan: "07"
subsystem: client-ui
tags: [context-menu, bullet-actions, complete, toolbar, hide-completed]
dependency_graph:
  requires: ["02-05", "02-06"]
  provides: ["BULL-11", "BULL-12", "BULL-13", "BULL-14", "BULL-15"]
  affects: ["client/src/components/DocumentView"]
tech_stack:
  added: []
  patterns: ["right-click context menu with useEffect close handlers", "client-side filter with useMemo for hide-completed"]
key_files:
  created:
    - client/src/components/DocumentView/ContextMenu.tsx
    - client/src/components/DocumentView/DocumentToolbar.tsx
  modified:
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/components/DocumentView/BulletTree.tsx
decisions:
  - "ContextMenu hover highlight uses inline onMouseEnter/onMouseLeave (no CSS module or Tailwind dependency)"
  - "BulletTree visibleItems derived from flatItems via useMemo; DnD logic migrated to use visibleItems so drag-and-drop works correctly when hide-completed is active"
  - "DocumentToolbar rendered outside SortableContext to avoid dnd-kit capturing toolbar pointer events"
metrics:
  duration: "~3min"
  completed_date: "2026-03-09"
  tasks: 2
  files: 4
---

# Phase 2 Plan 07: Right-click Context Menu, Complete/Delete, Toolbar Summary

**One-liner:** Right-click context menu with full bullet actions (indent/outdent/move/complete/delete) plus toolbar with hide-completed toggle and bulk-delete-completed.

## Tasks Completed

| # | Name | Commit | Files |
|---|------|--------|-------|
| 1 | ContextMenu component + BulletNode right-click | 8d996fc | ContextMenu.tsx (new), BulletNode.tsx |
| 2 | DocumentToolbar (hide completed / delete completed) | ca9c783 | DocumentToolbar.tsx (new), BulletTree.tsx |

## What Was Built

### Task 1: ContextMenu + BulletNode right-click

Created `ContextMenu.tsx` with props `{ bullet, bulletMap, position, onClose }`. Renders a fixed-position div at cursor coordinates (z-index 1000) with these menu items:

- **Indent** — `useIndentBullet`; disabled when bullet has no previous sibling
- **Outdent** — `useOutdentBullet`; disabled when bullet is at root level (parentId === null)
- **Move Up** — `useMoveBullet` with `afterId` of the sibling before the previous sibling
- **Move Down** — `useMoveBullet` with `afterId` of the next sibling
- Separator line
- **Mark complete / Unmark complete** — label toggles on `bullet.isComplete`; calls `useMarkComplete`
- **Delete** — `useSoftDeleteBullet`; red text

Closes on outside `mousedown` and `Escape` keydown via two `useEffect` listeners cleaned up on unmount.

Updated `BulletNode.tsx`:
- Added `useState<{ x: number; y: number } | null>` for context menu position
- Added `onContextMenu` to outer div: `e.preventDefault(); setContextMenuPos({ x: e.clientX, y: e.clientY })`
- Renders `<ContextMenu>` at bottom of return when `contextMenuPos !== null`
- Complete styling (opacity 0.5 + line-through) was already present from prior plan — no change needed

### Task 2: DocumentToolbar + BulletTree hideCompleted

Created `DocumentToolbar.tsx` with props `{ documentId, hideCompleted, onToggleHideCompleted }`. Renders slim toolbar row with:
- **Hide completed / Show completed** toggle button (blue when active, gray when inactive)
- **Delete completed** button (red): calls `window.confirm`, then `apiClient.delete('/api/bullets/documents/:docId/completed')`, then `queryClient.invalidateQueries`

Updated `BulletTree.tsx`:
- Added `hideCompleted` state (useState false)
- Added `visibleItems` memo: `hideCompleted ? flatItems.filter(b => !b.isComplete) : flatItems`
- Renders `<DocumentToolbar>` above `SortableContext`
- Migrated `dropIndicatorIndex`, `projectedDropDepth`, and `handleDragEnd` to use `visibleItems` so DnD works correctly when completed bullets are hidden

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Consistency] Migrated DnD logic to visibleItems**
- **Found during:** Task 2
- **Issue:** Plan said to only filter display but left DnD on `flatItems`. If completed bullets are hidden, `flatItems` contains IDs not present in the SortableContext items list, causing stale index lookups in handleDragEnd and drop indicator computation.
- **Fix:** All DnD index lookups (handleDragEnd, dropIndicatorIndex, projectedDropDepth) switched to `visibleItems`.
- **Files modified:** BulletTree.tsx
- **Commit:** ca9c783

## Verification

- `npx tsc --noEmit` — clean, no errors
- Client tests: 15 pass, 6 pre-existing RED scaffolds (zoom URL, Ctrl+Z/Y, Ctrl+E from Plan 02-01)

## Self-Check: PASSED

- ContextMenu.tsx: FOUND at client/src/components/DocumentView/ContextMenu.tsx
- DocumentToolbar.tsx: FOUND at client/src/components/DocumentView/DocumentToolbar.tsx
- BulletNode.tsx: modified, FOUND
- BulletTree.tsx: modified, FOUND
- Commit 8d996fc: FOUND
- Commit ca9c783: FOUND
