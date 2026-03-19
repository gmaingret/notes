---
phase: 22-undo-coverage-extension
plan: 02
subsystem: api
tags: [undo, bulk-delete, soft-delete, drizzle, typescript]

# Dependency graph
requires:
  - phase: 22-01
    provides: markComplete and patchBullet undo recording with note field support
provides:
  - Batch op type in UndoOp union enabling compound undo operations
  - applyOp batch handler that replays sub-ops sequentially
  - Bulk delete completed route uses soft delete with single undo event (one Ctrl+Z restores all)
affects:
  - future undo extensions using compound operations

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Recursive UndoOp union: batch variant holds UndoOp[] for compound operations
    - Snapshot-before-delete pattern: query IDs before soft delete to build undo event
    - db.transaction wrapping soft delete + recordUndoEvent for atomicity

key-files:
  created: []
  modified:
    - server/src/services/undoService.ts
    - server/src/routes/bullets.ts

key-decisions:
  - "Batch UndoOp variant is recursive (UndoOp[] as sub-ops) to allow arbitrary nesting"
  - "Bulk delete uses snapshot-before-delete pattern to capture IDs before soft delete modifies them"
  - "Single db.transaction wraps soft delete + recordUndoEvent to guarantee atomicity"
  - "Early return when no completed bullets exist avoids recording an empty batch event"

patterns-established:
  - "Batch undo pattern: snapshot IDs -> soft delete in tx -> recordUndoEvent with batch forward/inverse ops"

requirements-completed:
  - UNDO-03

# Metrics
duration: 2min
completed: 2026-03-19
---

# Phase 22 Plan 02: Undo Coverage Extension - Bulk Delete Summary

**Recursive batch UndoOp variant with applyOp handler enabling single Ctrl+Z to restore all soft-deleted completed bullets**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-19T17:26:05Z
- **Completed:** 2026-03-19T17:28:06Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Added `| { type: 'batch'; ops: UndoOp[] }` variant to the UndoOp discriminated union (recursive type)
- Added `case 'batch'` to applyOp that iterates and applies sub-ops sequentially, enabling compound undo/redo
- Converted bulk-delete-completed route from hard DELETE to soft delete with full undo recording via a single batch event

## Task Commits

Each task was committed atomically:

1. **Task 1: Add batch op type to UndoOp and handle in applyOp** - `d6d50fc` (feat)
2. **Task 2: Convert bulk delete completed to soft delete with undo recording** - `cf5f186` (feat)

**Plan metadata:** (docs commit pending)

## Files Created/Modified
- `server/src/services/undoService.ts` - Added batch variant to UndoOp union and case 'batch' to applyOp
- `server/src/routes/bullets.ts` - Replaced hard DELETE with soft delete + recordUndoEvent in transaction

## Decisions Made
- Batch UndoOp variant is recursive (holds UndoOp[]) enabling arbitrary compound operations in a single undo step
- Bulk delete snapshots IDs before soft delete so the undo event carries exact bullet IDs
- Single db.transaction wraps soft delete + recordUndoEvent to ensure atomicity
- Early return when completedBullets.length === 0 avoids recording an empty batch event

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 22 plans complete: markComplete undo (22-01) and bulk-delete undo (22-02) both shipped
- Undo/redo coverage for all major bullet mutations is now complete
- No blockers for next phase

---
*Phase: 22-undo-coverage-extension*
*Completed: 2026-03-19*
