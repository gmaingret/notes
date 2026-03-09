---
phase: 03-rich-content
plan: "07"
subsystem: client-search
tags: [search, modal, keyboard, react-query]
dependency_graph:
  requires: [03-03, 03-04, 03-05]
  provides: [SRCH-01, SRCH-02, SRCH-03, SRCH-04]
  affects: [useUndo, DocumentToolbar, uiStore]
tech_stack:
  added: []
  patterns: [react-query-useQuery, debounce-useEffect, zustand-uiStore]
key_files:
  created:
    - client/src/hooks/useSearch.ts
    - client/src/components/DocumentView/SearchModal.tsx
  modified:
    - client/src/hooks/useUndo.ts
    - client/src/components/DocumentView/DocumentToolbar.tsx
decisions:
  - "SearchModal uses position:fixed so mounting inside DocumentToolbar is safe — renders in viewport regardless of DOM tree position"
  - "Ctrl+* bookmarks stub replaced with real setCanvasView call during this plan (was Phase-3 no-op)"
metrics:
  duration: 3min
  completed_date: "2026-03-09"
  tasks: 2
  files: 4
---

# Phase 3 Plan 07: Ctrl+F Search Modal Summary

Spotlight-style search modal with debounced React Query hook, Ctrl+F keyboard shortcut, and search icon in the document toolbar.

## What Was Built

### useSearch hook (`client/src/hooks/useSearch.ts`)
- Wraps `useQuery` from React Query; queries `/api/search?q=...`
- Only fires when `query.length >= 2` (enabled gate)
- 10-second staleTime to avoid hammering the endpoint while typing
- Exports `SearchResult` type: `{ id, content, documentId, documentTitle }`

### SearchModal component (`client/src/components/DocumentView/SearchModal.tsx`)
- Spotlight-style centered overlay (`position: fixed`, `top: 20%`, `translateX(-50%)`)
- Backdrop click closes modal; Escape key closes modal
- 300ms debounce on input before firing `useSearch`
- Results rendered via `FilteredBulletList` with `highlightText` for match highlighting
- Clicking a result navigates to `/doc/:id#bullet/:bulletId` and closes modal
- Auto-focuses the search input on mount

### useUndo.ts — Ctrl+F + Ctrl+* upgrades
- Added Ctrl+F handler: calls `setSearchOpen(true)` and `e.preventDefault()` (blocks browser find)
- Replaced Ctrl+* no-op stub with real `setCanvasView({ type: 'bookmarks' })` call

### DocumentToolbar.tsx — Search button + modal mount
- "Search" button added as first item in toolbar; title tooltip shows `Ctrl+F`
- `{searchOpen && <SearchModal onClose={() => setSearchOpen(false)} />}` mounted after toolbar div
- Component return now uses a fragment to accommodate the modal portal sibling

## Verification

- TypeScript: clean (no errors)
- Vitest: 32/32 tests passing

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing functionality] Ctrl+* bookmarks stub upgraded**
- **Found during:** Task 2 — reading useUndo.ts revealed `console.log('[Phase 3] Bookmarks not yet implemented')`
- **Issue:** Bookmarks canvas view (`setCanvasView`) was already wired in uiStore from Plan 05 but the keyboard shortcut was still a stub
- **Fix:** Replaced stub with real `setCanvasView({ type: 'bookmarks' })` call
- **Files modified:** `client/src/hooks/useUndo.ts`
- **Commit:** b3c3ed0

## Self-Check: PASSED

Files confirmed on disk:
- client/src/hooks/useSearch.ts: FOUND
- client/src/components/DocumentView/SearchModal.tsx: FOUND

Commits confirmed:
- 0fb6d22: feat(03-07): add useSearch hook and SearchModal component
- b3c3ed0: feat(03-07): wire Ctrl+F handler and search icon in DocumentToolbar
