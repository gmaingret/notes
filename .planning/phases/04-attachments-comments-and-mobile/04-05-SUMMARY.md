---
phase: 04-attachments-comments-and-mobile
plan: "05"
subsystem: client-ui
tags: [focus-toolbar, mobile, attachments, ui-state]
dependency_graph:
  requires: [04-02, 04-03]
  provides: [FocusToolbar, focusedBulletId-state, context-menu-attach-note]
  affects: [BulletTree, BulletContent, ContextMenu, uiStore]
tech_stack:
  added: []
  patterns: [visualViewport-keyboard-offset, custom-event-dispatch, zustand-transient-state]
key_files:
  created:
    - client/src/components/DocumentView/FocusToolbar.tsx
  modified:
    - client/src/store/uiStore.ts
    - client/src/components/DocumentView/BulletContent.tsx
    - client/src/components/DocumentView/BulletTree.tsx
    - client/src/components/DocumentView/ContextMenu.tsx
decisions:
  - FocusToolbar placed in BulletTree (not DocumentView) since DocumentToolbar is rendered inside BulletTree alongside DndContext
  - FocusToolbar reads bullet data via useDocumentBullets to avoid prop drilling from BulletTree
  - Note button dispatches CustomEvent('focus-note') so BulletNode can handle focus independently
  - ContextMenu 'Attach file' dispatches CustomEvent('attach-file') for future BulletNode handler
  - focusedBulletId excluded from zustand partialize — resets to null on page reload
metrics:
  duration: 8min
  completed: 2026-03-09
  tasks: 2
  files: 5
---

# Phase 4 Plan 5: FocusToolbar Summary

**One-liner:** FocusToolbar with 11 actions + visualViewport keyboard positioning, wired via uiStore focusedBulletId to replace DocumentToolbar when a bullet has focus.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | uiStore focusedBulletId + BulletContent focus/blur wiring | 5066032 | uiStore.ts, BulletContent.tsx |
| 2 | FocusToolbar + BulletTree conditional render + ContextMenu items | 42bad10 | FocusToolbar.tsx, BulletTree.tsx, ContextMenu.tsx |

## What Was Built

### uiStore extension (Task 1)
- Added `focusedBulletId: string | null` field and `setFocusedBulletId` action to UiStore type
- Initial state: `focusedBulletId: null`
- **Not added to partialize** — transient UI state resets on page reload
- BulletContent calls `setFocusedBulletId(bullet.id)` in `handleFocus`
- BulletContent calls `setFocusedBulletId(null)` last in `handleBlur` (after undo-checkpoint flush)

### FocusToolbar component (Task 2)
- `client/src/components/DocumentView/FocusToolbar.tsx` — 11 action buttons:
  1. Indent (useIndentBullet)
  2. Outdent (useOutdentBullet)
  3. Up arrow (useMoveBullet — before previous sibling)
  4. Down arrow (useMoveBullet — after next sibling)
  5. Undo (POST /api/undo + invalidateQueries)
  6. Redo (POST /api/redo + invalidateQueries)
  7. Attach (hidden `<input type="file">` + useUploadAttachment)
  8. Note (dispatches CustomEvent 'focus-note' with bulletId; filled color when note exists)
  9. Bookmark (useAddBookmark/useRemoveBookmark toggle; gold color when bookmarked)
  10. Complete (useMarkComplete toggle; green color when complete)
  11. Delete (useSoftDeleteBullet)
- visualViewport `resize`/`scroll` listeners compute `keyboardOffset` — toolbar positions `bottom: keyboardOffset` (above soft keyboard)
- Fallback: keyboardOffset stays 0 when window.visualViewport is undefined

### BulletTree conditional render (Task 2)
- Imports `FocusToolbar` and `useUiStore`
- Reads `focusedBulletId` from uiStore
- `focusedBulletId ? <FocusToolbar> : <DocumentToolbar>` — DocumentToolbar receives same props as before

### ContextMenu additions (Task 2)
- "Attach file" button: dispatches CustomEvent('attach-file', { detail: { bulletId } })
- "Add note" button: dispatches CustomEvent('focus-note', { detail: { bulletId } })
- Both inserted after "Bookmark", before "Delete"

## Deviations from Plan

### Auto-adjusted scope

**1. [Rule 2 - Location] FocusToolbar rendered in BulletTree, not DocumentView**
- **Found during:** Task 2
- **Issue:** Plan specified adding conditional render to DocumentView.tsx, but DocumentToolbar is actually rendered inside BulletTree.tsx (inside DndContext, outside SortableContext per the locked decision). DocumentView only renders BulletTree — it has no direct access to the DocumentToolbar render point.
- **Fix:** Added the `focusedBulletId ? <FocusToolbar> : <DocumentToolbar>` conditional inside BulletTree.tsx where DocumentToolbar was already rendered.
- **Files modified:** BulletTree.tsx (not DocumentView.tsx)
- **Commits:** 42bad10

## Self-Check: PASSED

- FocusToolbar.tsx: EXISTS at client/src/components/DocumentView/FocusToolbar.tsx
- visualViewport effect: PRESENT (resize + scroll listeners)
- focusedBulletId NOT in partialize: CONFIRMED (only lastOpenedDocId + sidebarOpen persisted)
- DocumentView/BulletTree conditionally renders FocusToolbar: CONFIRMED
- TypeScript: CLEAN (npx tsc --noEmit — 0 errors)
- Commits: 5066032 (task 1), 42bad10 (task 2)
