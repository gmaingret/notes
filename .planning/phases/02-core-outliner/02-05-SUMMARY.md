---
phase: 02-core-outliner
plan: "05"
subsystem: ui
tags: [react, dnd-kit, drag-and-drop, zoom, url-routing, keyboard-shortcuts]

# Dependency graph
requires:
  - phase: 02-core-outliner
    provides: "02-04 BulletTree, BulletNode, BulletContent components"
provides:
  - "BulletTree.tsx: DndContext + SortableContext with flatten/projected-depth DnD, DropIndicator, DragOverlay"
  - "BulletNode.tsx: useSortable wiring, dot as drag handle, click-to-zoom with 5px disambiguation"
  - "BulletContent.tsx: Ctrl+] zoom in, Ctrl+[ zoom out keyboard shortcuts"
affects: [02-06, 02-07, 02-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DnD flatten/projected-depth: single flat SortableContext, dragOffsetX / INDENTATION_WIDTH determines projected nesting level"
    - "Click vs drag on dot: onPointerDown records position; onPointerUp checks distance < 5px -> navigate zoom"
    - "Drop indicator: dedicated DropIndicator component rendered at overIndex position with depth-adjusted marginLeft"
    - "isDragOverlay prop: BulletNode and BulletContent skip interactive elements, render text-only at 0.5 opacity"
    - "Ctrl+]/[ implemented in BulletContent handleKeyDown — navigate('#bullet/{id}') for zoom in, navigate('', {relative:'path'}) to clear hash"

key-files:
  modified:
    - client/src/components/DocumentView/BulletTree.tsx
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/components/DocumentView/BulletContent.tsx
    - client/src/test/bulletTree.test.tsx

key-decisions:
  - "Single flat SortableContext over entire tree — nested SortableContexts would block cross-level drag"
  - "afterId derivation: item at overIndex-1 (if not the active bullet itself) — simple and correct for append-after semantics"
  - "DropIndicator uses marginLeft = depth*24 + 32 to align with content (32 = chevron 16 + dot 16)"
  - "isDragOverlay skips BulletContent interactivity entirely — renders plain text div instead of contenteditable"

patterns-established:
  - "Pattern: pointerDownPos ref + distance check for click-vs-drag on drag handles"
  - "Pattern: dragOffsetX / INDENTATION_WIDTH = projected depth delta (from RESEARCH.md Pattern 5)"

requirements-completed: [BULL-05, BULL-07, KB-04]

# Metrics
duration: 2min
completed: 2026-03-09
---

# Phase 2 Plan 05: Drag-and-Drop + URL Zoom Summary

**dnd-kit flatten/projected-depth drag-and-drop with cross-level reparenting, dot-click zoom, and Ctrl+]/[ keyboard shortcuts wired into BulletTree and BulletNode**

## Performance

- **Duration:** ~2 min
- **Started:** 2026-03-09T11:08:19Z
- **Completed:** 2026-03-09T11:10:41Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Updated `BulletTree.tsx` with full dnd-kit DnD: `DndContext` (PointerSensor distance:5), `SortableContext` (flat list, verticalListSortingStrategy), `DragOverlay`, `getProjectedDepth` from RESEARCH.md Pattern 5, `handleDragEnd` deriving `newParentId` and `afterId` from projected depth and calling `useMoveBullet`
- Added `DropIndicator` component (2px blue line, depth-adjusted marginLeft) rendered at the `overId` position during drag
- Updated `BulletNode.tsx` with `useSortable` wiring (setNodeRef, transform, transition, opacity 0.3 when dragging), dot spreading dnd-kit `listeners`/`attributes`, `onPointerDown`/`onPointerUp` with distance check for click-vs-drag disambiguation, `isDragOverlay` prop to skip chevron and interactivity in overlay
- Updated `BulletContent.tsx` with `useNavigate`, `isDragOverlay` prop (renders plain text div in overlay), `Ctrl+]` (zoom in to `#bullet/{id}`) and `Ctrl+[` (zoom out to parent or clear hash) keyboard shortcuts
- Implemented Ctrl+] and Ctrl+[ logic-assertion tests — 15 tests now pass (up from 13); 6 remaining stubs are for future plans (zoom URL component tests, Ctrl+Z/Y, Ctrl+E)

## Task Commits

1. **Task 1: DnD wiring in BulletTree** - `3ecec56` (feat)
2. **Task 2: BulletNode useSortable + dot-click zoom + global keyboard zoom** - `5a97120` (feat)

## Files Created/Modified

- `client/src/components/DocumentView/BulletTree.tsx` - DndContext, SortableContext, DragOverlay, getProjectedDepth, handleDragEnd, DropIndicator
- `client/src/components/DocumentView/BulletNode.tsx` - useSortable wiring, dot drag handle with click-zoom disambiguation, isDragOverlay prop
- `client/src/components/DocumentView/BulletContent.tsx` - useNavigate, isDragOverlay render branch, Ctrl+] and Ctrl+[ handlers
- `client/src/test/bulletTree.test.tsx` - Ctrl+] and Ctrl+[ logic-assertion tests implemented (was stubs)

## Decisions Made

- Single flat `SortableContext` over the entire tree — nested SortableContexts block cross-level drag (established pattern from RESEARCH.md)
- `afterId` derived as `flatItems[overIndex - 1]` if it's not the active bullet — simple append-after semantics, server handles position computation
- `DropIndicator` marginLeft = `depth * 24 + 32` where 32 = chevron (16px) + dot (16px) — aligns indicator with content start at the projected depth
- `isDragOverlay` skips chevron and `BulletContent` interactivity entirely, renders plain text `<div>` — avoids hover effects and contenteditable quirks in overlay

## Deviations from Plan

### Auto-fixed Issues

None - plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

- Drag-and-drop reordering (including cross-level reparenting) is wired end-to-end: dot drag handle -> projected depth -> useMoveBullet API call
- Click on dot navigates to `#bullet/{id}` for zoom (5px threshold prevents accidental zoom during drag)
- Ctrl+] and Ctrl+[ zoom shortcuts implemented in BulletContent keyboard handler
- Ready for Plan 06 (Breadcrumb/zoom navigation) and remaining plans
- Blocker remains: iOS Safari keyboard focus edge case requires real device testing before Phase 2 closes

---
*Phase: 02-core-outliner*
*Completed: 2026-03-09*
