---
phase: 04-attachments-comments-and-mobile
plan: "06"
subsystem: ui
tags: [react, typescript, touch-events, pointer-events, mobile, gestures]

# Dependency graph
requires:
  - phase: 04-04
    provides: FocusToolbar and mobile infrastructure context
  - phase: 04-05
    provides: Test scaffolds for swipe and long-press (RED state)
provides:
  - gestures.ts with swipeThresholdReached and createLongPressHandler pure functions
  - Mobile swipe-to-complete and swipe-to-delete on BulletNode
  - Long-press context menu for iOS Safari
  - UndoToast component with 4s auto-dismiss and Undo button
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Pure gesture factory functions (no React hooks) extracted for isolated unit testing
    - pointerType guard on touch-only swipe handlers prevents desktop activation
    - Directional lock (|dy| > |dx| * 1.5) suppresses swipe when user is scrolling vertically
    - touchAction:pan-y on outer row preserves vertical scroll while detecting horizontal swipe
    - useMemo-stable createLongPressHandler instance avoids re-creation across renders

key-files:
  created:
    - client/src/components/DocumentView/gestures.ts
    - client/src/components/DocumentView/UndoToast.tsx
  modified:
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/test/swipeGesture.test.ts
    - client/src/test/longPress.test.ts

key-decisions:
  - "gestures.ts uses plain closure-based mutable state (not useRef) so pure functions can be unit tested without React"
  - "Swipe handlers on outer row div only; dot div retains touchAction:none for dnd-kit PointerSensor"
  - "Long-press uses touch events (not pointer events) to avoid conflict with dnd-kit pointer capture"
  - "UndoToast calls /api/undo + invalidates ['bullets'] queryKey directly (same pattern as useGlobalKeyboard)"
  - "Snap-back animation: transition:transform 0.2s ease when isSwiping=false, none when swiping"

patterns-established:
  - "Pattern: gesture pure functions extracted to gestures.ts — import in components, import in tests"
  - "Pattern: pointerType==='touch' guard on all swipe/mobile handlers — keeps desktop unaffected"

requirements-completed: [MOB-01, MOB-02, MOB-03, BULL-16]

# Metrics
duration: 8min
completed: 2026-03-09
---

# Phase 4 Plan 06: Mobile Gestures Summary

**Swipe-to-complete (right) and swipe-to-delete (left) with colored reveal + UndoToast on BulletNode; long-press opens context menu on iOS Safari**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-09T21:40:00Z
- **Completed:** 2026-03-09T21:48:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Extracted `swipeThresholdReached` and `createLongPressHandler` pure functions to `gestures.ts` — 5 tests GREEN
- Wired swipe gesture handlers into BulletNode outer div with colored background reveal (green=complete, red=delete)
- Added long-press handler (500ms, 8px cancel) that opens ContextMenu at touch coordinates on iOS Safari
- Created UndoToast component appearing after swipe-left delete, with 4s auto-dismiss and Undo button

## Task Commits

Each task was committed atomically:

1. **Task 1: Extract gesture pure functions + make swipe/longPress tests pass** - `f7eb74f` (feat)
2. **Task 2: Wire swipe + long-press into BulletNode + UndoToast** - `12e9ed3` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `client/src/components/DocumentView/gestures.ts` - Pure `swipeThresholdReached` and `createLongPressHandler` exports
- `client/src/components/DocumentView/UndoToast.tsx` - Fixed-position toast with Undo button + 4s auto-dismiss
- `client/src/components/DocumentView/BulletNode.tsx` - Swipe handlers, long-press handlers, UndoToast render
- `client/src/test/swipeGesture.test.ts` - Updated to import from gestures.ts (was placeholder stub)
- `client/src/test/longPress.test.ts` - Updated to import from gestures.ts (was placeholder stub)

## Decisions Made
- `gestures.ts` uses plain closure-based mutable state (not `useRef`) so pure functions can be unit tested without React mounting
- Swipe pointer event handlers go on outer row div only; dot div retains `touchAction: none` for dnd-kit PointerSensor
- Long-press uses touch events (not pointer events) to avoid conflict with dnd-kit pointer capture
- `UndoToast` calls `/api/undo` + invalidates `['bullets']` queryKey directly — same pattern as `useGlobalKeyboard`
- Snap-back animation: `transition: transform 0.2s ease` when `isSwiping=false`, removed when actively swiping

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- Pre-existing `keyboardOffset.test.ts` RED stubs from plan 04-05 still failing — these are out of scope and were not touched.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All 4 mobile requirements (MOB-01, MOB-02, MOB-03, BULL-16) delivered
- Phase 4 mobile feature complete — swipe gestures and long-press context menu ready for device testing
- No blockers for phase completion

---
*Phase: 04-attachments-comments-and-mobile*
*Completed: 2026-03-09*
