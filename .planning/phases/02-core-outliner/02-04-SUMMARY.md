---
phase: 02-core-outliner
plan: "04"
subsystem: ui
tags: [react, react-query, contenteditable, bullet-tree, keyboard-handler]

# Dependency graph
requires:
  - phase: 02-core-outliner
    provides: "02-01 test scaffolds (bulletTree.test.tsx), 02-02 server bullet routes, 02-03 undo service"
provides:
  - "useBullets.ts: 10 React Query hooks for all bullet CRUD + operations"
  - "BulletTree.tsx: flattenTree, buildBulletMap, getChildren utilities + BulletTree component"
  - "BulletNode.tsx: bullet row with chevron, dot handle, depth indentation, isComplete styling"
  - "BulletContent.tsx: contenteditable with full keyboard handler + debounced saves"
  - "DocumentView.tsx: real bullet tree replacing placeholder, zoom via URL hash"
affects: [02-05, 02-06, 02-07, 02-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Bullet hooks follow useDocuments.ts onMutate/onError/onSettled optimistic update pattern"
    - "queryKey = ['bullets', documentId] — same shape as ['documents']"
    - "BulletContent uses local state + 1000ms debounce to prevent cursor jump during typing"
    - "flattenTree: recursive DFS with isCollapsed early exit and deletedAt filter"
    - "isCursorAtStart/isCursorAtEnd/splitAtCursor: pure DOM Range API helpers, exported for tests"

key-files:
  created:
    - client/src/hooks/useBullets.ts
    - client/src/components/DocumentView/BulletTree.tsx
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/components/DocumentView/BulletContent.tsx
  modified:
    - client/src/components/DocumentView/DocumentView.tsx
    - client/src/test/bulletTree.test.tsx

key-decisions:
  - "BulletContent exports isCursorAtStart, isCursorAtEnd, splitAtCursor as named exports for unit testing without DOM mounting"
  - "Keyboard handler tests use pure logic assertions (condition checks, cursor helpers) rather than full React component mounting — avoids QueryClient provider setup complexity in tests"
  - "Shake animation injected as a singleton style tag on first use — no Tailwind dependency, no external CSS file"
  - "Tab on first sibling (index=0) is a silent no-op per CONTEXT.md locked decision"
  - "Enter on bullet with children creates first child (parentId=bullet.id, afterId=null) per CONTEXT.md"

patterns-established:
  - "Pattern: BulletContent local state + debounce — never update textContent from props while focused"
  - "Pattern: Optimistic updates for structural ops (patch, delete, setCollapsed, markComplete) via onMutate/onError/onSettled"
  - "Pattern: Invalidate-only for complex ops (indent, outdent, move) — server owns position math"

requirements-completed: [BULL-01, BULL-02, BULL-03, BULL-04, BULL-06, BULL-07, BULL-08, BULL-11, BULL-12, KB-01, KB-02, KB-03, KB-06]

# Metrics
duration: 5min
completed: 2026-03-09
---

# Phase 2 Plan 04: Client Bullet Tree Summary

**React Query hooks + contenteditable BulletContent with full keyboard handler (Enter/Tab/Backspace/Delete/Ctrl+Arrow/Ctrl+B/Ctrl+I) and BulletTree rendering real bullet data with collapse and zoom via URL hash**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-09T12:01:21Z
- **Completed:** 2026-03-09T12:05:57Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Created `useBullets.ts` exporting 10 React Query hooks following the exact `useDocuments.ts` optimistic-update pattern (queryKey `['bullets', documentId]`, onMutate/onError/onSettled for CRUD, invalidate-only for structural ops)
- Created `BulletTree.tsx` with `buildBulletMap`, `getChildren`, `flattenTree` utility functions and `BulletTree` component that renders flat array as positioned `BulletNode` rows
- Created `BulletContent.tsx` with full `handleKeyDown` covering Enter (split/outdent/first-child), Tab/Shift+Tab (indent/outdent), Backspace-at-start (merge or shake-block), Delete-at-end (forward merge), Ctrl+Arrow (move subtree), Ctrl+B/I (bold/italic wrapping via Range API), 1000ms debounced saves with cursor preservation
- Created `BulletNode.tsx` with chevron (visible only when children exist, instant toggle via `useSetCollapsed`), dot placeholder handle, `BulletContent`, depth indentation at 24px/level, and isComplete opacity/strikethrough styling
- Replaced `DocumentView.tsx` placeholder with real `BulletTree` wired to `useLocation().hash` for zoom state (`#bullet/{id}` format)
- 13 tests pass GREEN (4 flattenTree + 9 keyboard handler); 8 remaining stubs are future-plan scaffolds (zoom URL encoding, global shortcuts)

## Task Commits

1. **Task 1: useBullets hooks + flattenTree utility** - `bb63d5f` (feat)
2. **Task 2: BulletNode, BulletContent, DocumentView wiring** - `a23c82f` (feat)

## Files Created/Modified

- `client/src/hooks/useBullets.ts` - 10 hooks: useDocumentBullets, useCreateBullet, usePatchBullet, useSoftDeleteBullet, useIndentBullet, useOutdentBullet, useMoveBullet, useSetCollapsed, useMarkComplete, useBulletUndoCheckpoint
- `client/src/components/DocumentView/BulletTree.tsx` - buildBulletMap, getChildren, flattenTree + BulletTree component with BulletNode rendering
- `client/src/components/DocumentView/BulletNode.tsx` - chevron, dot, BulletContent, depth indentation, isComplete styling
- `client/src/components/DocumentView/BulletContent.tsx` - contenteditable keyboard handler + debounced saves + exported cursor helpers
- `client/src/components/DocumentView/DocumentView.tsx` - replaced placeholder; renders BulletTree with URL hash zoom
- `client/src/test/bulletTree.test.tsx` - implemented flattenTree (4 tests) and keyboard handler (9 tests) GREEN assertions

## Decisions Made

- Exported `isCursorAtStart`, `isCursorAtEnd`, `splitAtCursor` as named exports from BulletContent for unit testing without needing to mount the full component (avoids QueryClient/Router provider setup in tests)
- Keyboard handler tests use logic assertions on the pure helper functions and bullet map conditions rather than full component fire-event testing — matches the test file's style (which uses `it()` without complex setup)
- Shake animation uses a singleton `<style>` tag injected on first render rather than Tailwind arbitrary classes or external CSS — keeps the animation self-contained in the component
- `Tab` on first sibling (index 0) is a silent no-op per CONTEXT.md locked decision
- `Enter` on a bullet with children creates the new bullet as first child (parentId=bullet.id, afterId=null) per CONTEXT.md

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Next Phase Readiness

- Client bullet tree is fully functional: users can type, Enter to create bullets, Tab/Shift+Tab to indent/outdent, Backspace to merge (blocked with shake if children), Ctrl+Arrow to move subtree, Ctrl+B/I for inline formatting
- Debounced saves prevent cursor jump; chevron persists isCollapsed to server
- DocumentView renders real bullets from server; zoom via URL hash is wired
- Ready for Plan 05 (drag-and-drop reorder) and Plan 06 (breadcrumb/zoom navigation)
- Blocker remains: iOS Safari keyboard focus edge case requires real device testing before Phase 2 closes

---
*Phase: 02-core-outliner*
*Completed: 2026-03-09*
