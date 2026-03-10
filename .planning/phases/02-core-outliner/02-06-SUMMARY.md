---
phase: 02-core-outliner
plan: "06"
subsystem: client-ui
tags: [breadcrumb, undo, keyboard-shortcuts, zoom-navigation]
dependency_graph:
  requires: [02-04]
  provides: [breadcrumb-component, useGlobalKeyboard-hook, global-undo-redo]
  affects: [App.tsx, BulletContent.tsx, DocumentView.tsx]
tech_stack:
  added: []
  patterns:
    - GlobalKeyboard component pattern (renders null, mounts side-effect hook)
    - Breadcrumb ancestor traversal via parentId chain from BulletMap
    - Dual undo handler (contenteditable BulletContent + global window listener)
key_files:
  created:
    - client/src/components/DocumentView/Breadcrumb.tsx
    - client/src/hooks/useUndo.ts
  modified:
    - client/src/components/DocumentView/DocumentView.tsx
    - client/src/App.tsx
    - client/src/components/DocumentView/BulletContent.tsx
decisions:
  - "GlobalKeyboard skips Ctrl+Z/Y when contenteditable has focus — lets BulletContent handle it to prevent double-API-call"
  - "invalidateQueries uses queryKey ['bullets'] prefix (no docId) for global per-user undo scope (UNDO-02)"
  - "Store path is store/uiStore (not stores/uiStore as referenced in plan) — corrected during implementation"
metrics:
  duration: 4min
  completed_date: "2026-03-09"
  tasks: 2
  files: 5
requirements: [BULL-07, BULL-08, UNDO-01, UNDO-02, UNDO-03, UNDO-04, KB-05, KB-07]
---

# Phase 2 Plan 06: Breadcrumb + Global Keyboard Shortcuts Summary

**One-liner:** Breadcrumb component traversing BulletMap ancestor chain + useGlobalKeyboard hook wiring Ctrl+Z/Y/E/P with contenteditable-aware guard.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Breadcrumb component + DocumentView zoom integration | c084fbd | Breadcrumb.tsx (created), DocumentView.tsx (modified) |
| 2 | useUndo hook + global keyboard shortcuts mounted in App | de81c7d | useUndo.ts (created), App.tsx, BulletContent.tsx |
| 2b | Fix missing imports in BulletContent | 6c8a563 | BulletContent.tsx |

## What Was Built

### Breadcrumb.tsx
- `getAncestorChain(bulletMap, bulletId)` traverses `parentId` links to build root-to-current chain
- Truncation: chains longer than 3 ancestors show `[first] › … › [last before current]`
- All ancestor segments clickable via `navigate('#bullet/' + ancestor.id)`; document title navigates to `''` (root)
- Current bullet rendered non-clickable at rightmost position
- Max 20-char segments with `...` truncation

### DocumentView.tsx
- Added `useDocumentBullets` query to fetch `flatBullets` and build `bulletMap`
- When `zoomedBulletId` is set: renders `<Breadcrumb>` instead of `<h1>`
- Passes `bulletMap` built from same query as `BulletTree`

### useUndo.ts (useGlobalKeyboard hook)
- `Ctrl+Z`: calls `POST /api/undo`, invalidates all `['bullets']` queries (global scope)
- `Ctrl+Y`: calls `POST /api/redo`, invalidates all `['bullets']` queries
- `Ctrl+E`: toggles sidebar via `useUiStore.setSidebarOpen`
- `Ctrl+P`, `Ctrl+*`: no-op stubs with `console.log` for Phase 3
- Guard: skips Ctrl+Z/Y if `document.activeElement.contentEditable === 'true'` (BulletContent handles it)

### App.tsx
- `GlobalKeyboard` function component (renders null) calls `useGlobalKeyboard()` and is mounted inside `RequireAuth` on both `/` and `/doc/:docId` routes

### BulletContent.tsx
- Added `Ctrl+Z`/`Ctrl+Y` handlers in `handleKeyDown` with `preventDefault` to block browser native undo
- Same API calls as global handler; fires when contenteditable has focus

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wrong store import path**
- **Found during:** Task 2
- **Issue:** Plan referenced `'../stores/uiStore'` but actual path is `'../store/uiStore'` (no trailing 's')
- **Fix:** Used correct path `'../store/uiStore'`
- **Files modified:** useUndo.ts

**2. [Rule 3 - Blocking] Missing imports in BulletContent after linter reformat**
- **Found during:** Task 2 verification
- **Issue:** Linter reformatted BulletContent.tsx and removed `useCallback`, `useQueryClient`, `apiClient` imports that I had added (the linter reformatted imports to match a pre-edit version)
- **Fix:** Re-applied missing imports in separate fix commit
- **Files modified:** BulletContent.tsx
- **Commit:** 6c8a563

## Pre-existing Issues (Deferred — not introduced by this plan)

Via `npx tsc -p tsconfig.app.json --noEmit` (project reference mode):
- `BulletContent.tsx:34` — `text` unused (Plan 04)
- `BulletContent.tsx:231` — `prevSibling` unused (Plan 04)
- `BulletNode.tsx:68` — duplicate `opacity` property (Plan 05)
- `bulletTree.test.tsx:3` — `isCursorAtEnd` unused import (Plan 01 scaffold)

These do not affect runtime. `npx tsc --noEmit` (composite project references) exits 0.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| Breadcrumb.tsx | FOUND |
| useUndo.ts | FOUND |
| 02-06-SUMMARY.md | FOUND |
| commit c084fbd | FOUND |
| commit de81c7d | FOUND |
| commit 6c8a563 | FOUND |
