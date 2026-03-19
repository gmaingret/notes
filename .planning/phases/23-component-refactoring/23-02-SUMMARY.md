---
phase: 23-component-refactoring
plan: 02
subsystem: ui
tags: [react, hooks, refactoring, gestures, touch]

# Dependency graph
requires:
  - phase: 23-component-refactoring
    provides: "Phase 23-01 extracted BulletContent sub-components; same BulletNode.tsx is the target here"
provides:
  - "useSwipeGesture hook: swipe state, touch handlers, exit animation, context menu long-press"
  - "useDotDrag hook: dot long-press drag, mouse drag, tap-to-zoom"
  - "BulletNode.tsx reduced from 486 to 189 lines as thin orchestrator"
affects: [component-refactoring, gestures, mobile-ux]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Gesture logic extracted to custom hooks (useSwipeGesture, useDotDrag) — BulletNode delegates via callback interfaces"
    - "isDragActiveRef shared between useDotDrag and useSwipeGesture via ref parameter to coordinate drag vs swipe"
    - "onContextMenu callback in SwipeActions allows hook to trigger BulletNode state without circular dependency"

key-files:
  created:
    - client/src/components/DocumentView/useSwipeGesture.ts
    - client/src/components/DocumentView/useDotDrag.ts
  modified:
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/test/swipePolish.test.ts

key-decisions:
  - "isDragActiveRef passed as parameter to useSwipeGesture so context menu long-press can check if drag is active"
  - "onContextMenu callback added to SwipeActions interface — hook calls it instead of directly setting contextMenuPos state"
  - "SwipeBackground extracted as file-local helper component to keep BulletNode JSX concise"
  - "useDotDrag hook also created (not in original plan) to meet the 250-line must_have for BulletNode"
  - "swipePolish.test.ts updated: GEST-05 test now checks useDotDrag.ts for setTimeout instead of BulletNode.tsx"

patterns-established:
  - "Callback interfaces pattern: hooks receive action callbacks, not mutation hooks directly"
  - "Ref sharing pattern: isDragActiveRef created in useDotDrag, passed to useSwipeGesture for cross-hook coordination"

requirements-completed: [QUAL-02]

# Metrics
duration: 7min
completed: 2026-03-19
---

# Phase 23 Plan 02: Extract useSwipeGesture Hook Summary

**BulletNode.tsx reduced from 486 to 189 lines by extracting swipe gesture state machine into useSwipeGesture hook and dot drag logic into useDotDrag hook**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-19T17:39:28Z
- **Completed:** 2026-03-19T17:46:49Z
- **Tasks:** 1
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- Extracted `useSwipeGesture.ts` hook: encapsulates swipe state, touch pointer handlers, long-press context menu, visual state (swipeBackground, iconScale), and exit animation transition handler
- Extracted `useDotDrag.ts` hook: encapsulates dot long-press drag, mouse drag, and tap-to-zoom navigation
- BulletNode.tsx reduced from 486 to 189 lines — a thin orchestrator that calls hooks and renders JSX
- TypeScript compiles cleanly; all 141 passing tests remain passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract useSwipeGesture hook from BulletNode** - `0f5b56c` (feat)

**Plan metadata:** (to be committed with this SUMMARY.md)

## Files Created/Modified
- `client/src/components/DocumentView/useSwipeGesture.ts` - Swipe gesture state machine hook (swipeX, isSwiping, exitDirection, showUndoToast, pointer handlers, ctx touch handlers, transition end handler)
- `client/src/components/DocumentView/useDotDrag.ts` - Dot long-press drag + tap-to-zoom hook with isDragActiveRef shared ref
- `client/src/components/DocumentView/BulletNode.tsx` - Slimmed to 189 lines; imports both hooks and a local SwipeBackground helper component
- `client/src/test/swipePolish.test.ts` - Updated GEST-05 to look for setTimeout in useDotDrag.ts (where it now lives)

## Decisions Made
- `isDragActiveRef` is created in `useDotDrag` and passed as a parameter to `useSwipeGesture` so the context menu long-press can check whether a drag is in progress — avoids duplicate state
- `onContextMenu` callback added to `SwipeActions` interface so the hook can deliver context menu position to BulletNode without importing state setters
- `SwipeBackground` extracted as a file-local helper component to keep the JSX in BulletNode readable while meeting the line count target
- `useDotDrag` hook was not in the original plan but was necessary to meet the 250-line must_have truth

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Created useDotDrag hook (not in plan files_modified)**
- **Found during:** Task 1 (Extract useSwipeGesture hook from BulletNode)
- **Issue:** After extracting only swipe logic, BulletNode was still 277 lines (over the 250-line must_have). Dot drag handlers (~100 lines) needed extraction too.
- **Fix:** Created `useDotDrag.ts` encapsulating all dot pointer handlers; updated BulletNode to use it via `isDragActiveRef` ref sharing
- **Files modified:** client/src/components/DocumentView/useDotDrag.ts (created), BulletNode.tsx
- **Verification:** BulletNode is now 189 lines, wc -l confirms
- **Committed in:** 0f5b56c (Task 1 commit)

**2. [Rule 1 - Bug] Updated swipePolish.test.ts GEST-05 to check useDotDrag.ts**
- **Found during:** Task 1 verification (vitest run)
- **Issue:** GEST-05 test read BulletNode.tsx as string and asserted `setTimeout` was present; after refactoring setTimeout lives in useDotDrag.ts
- **Fix:** Added `dotDrag` file read to the test and updated assertion to check `dotDrag` for setTimeout
- **Files modified:** client/src/test/swipePolish.test.ts
- **Verification:** swipePolish.test.ts now passes (6/6 tests)
- **Committed in:** 0f5b56c (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 bug)
**Impact on plan:** useDotDrag creation was essential to meet the line-count must_have. Test fix was necessary to prevent introduced regression. No scope creep.

## Issues Encountered
- 7 pre-existing failures in `mobileLayout.test.tsx` (hamburger menu tests) — confirmed pre-existing before our changes, out of scope

## Next Phase Readiness
- BulletNode is now a 189-line thin orchestrator — ready for further phase 23 refactoring if needed
- useSwipeGesture and useDotDrag are well-separated hooks, each independently testable

---
*Phase: 23-component-refactoring*
*Completed: 2026-03-19*
