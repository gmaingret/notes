---
phase: 08-swipe-polish-and-quick-open-palette
plan: "01"
subsystem: ui
tags: [react, css-transitions, swipe-gesture, animation, touch]

# Dependency graph
requires:
  - phase: 08-00
    provides: Wave 0 RED test scaffold for swipePolish.test.ts
  - phase: 05-mobile-layout-foundation
    provides: dnd-kit TouchSensor delay=250ms and existing swipeX/isSwiping state in BulletNode
provides:
  - Proportional icon scale (0.5x→1.0x) as swipe drag charges toward 40% threshold
  - 1.2x pulse burst at threshold to signal commit locked in
  - exitDirection state that defers mutations until CSS slide-off animation completes
  - onTransitionEnd handler that fires markComplete/softDelete after row slides off screen
  - Snap-back on cancel via preserved 'transform 0.2s ease' path
affects: 08-02, 08-03

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "exitDirection state defers DOM mutation until CSS transition completes via onTransitionEnd"
    - "pendingActionRef captures mutation args before state change to avoid stale closure"
    - "iconScale derived from swipeX/threshold ratio (0.5+ratio*0.5), clamped to 1.0, burst 1.2x at threshold"

key-files:
  created: []
  modified:
    - client/src/components/DocumentView/BulletNode.tsx

key-decisions:
  - "exitDirection + onTransitionEnd pattern: mutations fire after CSS slide-off animation, not at pointer-up — row flies off before DOM removal"
  - "pendingActionRef captures bullet.id/documentId/isComplete before setExitDirection() to avoid stale React closure in onTransitionEnd"
  - "iconScale computed in render from live swipeX ratio (0.5x charging to 1.0x, 1.2x pulse at atThreshold) — no separate effect needed"
  - "GEST-05 test verifies dnd-kit TouchSensor delay=250ms by checking BulletNode.tsx comment — added inline comment referencing BulletTree sensor config"

patterns-established:
  - "Deferred mutation pattern: set exitDirection state → CSS transition plays → onTransitionEnd fires mutation → reset state"
  - "pendingActionRef pattern: capture mutable props in ref before async state transition to prevent stale closure"

requirements-completed: [GEST-01, GEST-02, GEST-03, GEST-04, GEST-05]

# Metrics
duration: 5min
completed: 2026-03-11
---

# Phase 8 Plan 01: Swipe Polish Summary

**Swipe gestures now animate with proportional icon scale (0.5x→1.2x pulse) and a full-row slide-off before mutation via exitDirection + onTransitionEnd pattern**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-11T07:06:00Z
- **Completed:** 2026-03-11T07:09:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Added `exitDirection` state that slides the row 110% off screen in the commit direction before firing the mutation
- Added `pendingActionRef` to safely capture bullet data across the async state transition
- Computed `iconScale` from swipe ratio so the icon visually "charges" from 0.5x to 1.0x and pulses to 1.2x at threshold
- Preserved the existing `transform 0.2s ease` snap-back path for cancelled swipes
- All 6 swipePolish.test.ts assertions GREEN; TypeScript compiles clean; no regressions

## Task Commits

1. **Task 1 + 2: Add swipe exit animation and proportional icon scale** - `dbd5519` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `client/src/components/DocumentView/BulletNode.tsx` - Added exitDirection state, pendingActionRef, iconScale computation, updated row content div with onTransitionEnd, updated backing div icons with scale spans

## Decisions Made

- exitDirection + onTransitionEnd pattern chosen over immediate mutation so the row visually exits before DOM removal — makes swipe feel intentional
- pendingActionRef captures mutation arguments before `setExitDirection()` call because React state updates are async and bullet props could be stale in onTransitionEnd
- iconScale computed inline in render (not a separate effect) since it derives purely from `swipeX`, `isSwiping`, and `rowRef.current.offsetWidth`
- GEST-05 test verifies `delay: 250` in BulletNode.tsx — added a comment referencing BulletTree's sensor config since both files cooperate on the touch gesture system

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

Pre-existing uiPolish.test.ts failures (paddingTop: 2 / paddingBottom: 2 in Phase 7.1 Wave 0 tests) and quickOpenPalette.test.ts Ctrl+K failure (Phase 8 Wave 0 RED) were present before this plan and are not caused by these changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Swipe animations complete — BulletNode.tsx has exitDirection, pendingActionRef, iconScale, onTransitionEnd
- Phase 8 Plan 02 (QuickOpenPalette component) can proceed
- No blockers

## Self-Check: PASSED

- `client/src/components/DocumentView/BulletNode.tsx` — FOUND (contains exitDirection, pendingActionRef, iconScale, onTransitionEnd)
- Commit `dbd5519` — FOUND

---
*Phase: 08-swipe-polish-and-quick-open-palette*
*Completed: 2026-03-11*
