---
phase: 20-client-infrastructure
plan: "02"
subsystem: client
tags: [toast, sonner, error-handling, mutations, react-query, resilience]
dependency_graph:
  requires:
    - phase: 20-client-infrastructure plan 01
      provides: [sonner-toaster-mounted]
  provides: [toast-error-on-mutation-failure, bulk-delete-toast-coverage]
  affects: [client/src/hooks/useBullets.ts, client/src/components/DocumentView/DocumentToolbar.tsx]
tech_stack:
  added: []
  patterns: [toast.error() in useMutation onError callbacks, try/catch toast.error() for direct apiClient calls, visibleToasts=3 to prevent stacking]
key_files:
  created: []
  modified:
    - client/src/hooks/useBullets.ts
    - client/src/components/DocumentView/DocumentToolbar.tsx
    - client/src/main.tsx
key-decisions:
  - "visibleToasts=3 and duration=5000ms on Toaster prevents stacking into unreadable pile"
  - "DocumentToolbar handleDeleteCompleted wrapped in try/catch — direct apiClient calls need manual toast wrapping"
  - "useSetCollapsed and useMarkComplete get toast.error() even though not in original scope — consistent onError pattern across all mutations"
patterns-established:
  - "Mutation onError pattern: always call toast.error() with action description and err.message as description"
  - "Direct apiClient calls outside useMutation: wrap in try/catch and call toast.error() on catch"
requirements-completed: [RES-02]
duration: 5min
completed: "2026-03-19"
---

# Phase 20 Plan 02: Mutation Toast Notifications Summary

**All bullet mutation failures (save, delete, indent, outdent, move, reorder) now surface toast.error() notifications via sonner, with visibleToasts=3 to prevent stacking.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-19T16:03:53Z
- **Completed:** 2026-03-19T16:08:19Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Added `toast.error()` to onError handlers for all bullet mutations in useBullets.ts (create, patch, delete, indent, outdent, move, setCollapsed, markComplete)
- Added try/catch with `toast.error()` to `handleDeleteCompleted` in DocumentToolbar.tsx (was a silent failure)
- Configured Toaster with `visibleToasts={3}` and `duration={5000}` to prevent notification pile-up

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire toast.error() into bullet mutation onError handlers** - `7837363` (feat)
2. **Task 2: Add toast.error() to bulk-delete-completed in DocumentToolbar** - `b4418c8` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified

- `client/src/hooks/useBullets.ts` - Added `import { toast } from 'sonner'` and toast.error() calls in all mutation onError handlers
- `client/src/components/DocumentView/DocumentToolbar.tsx` - Added import toast and try/catch around bulk delete call
- `client/src/main.tsx` - Added `visibleToasts={3}` and `duration={5000}` to Toaster

## Decisions Made

1. **visibleToasts=3** — caps the number of simultaneously visible toasts to prevent wall-of-errors when multiple mutations fail at once
2. **duration=5000ms** — 5 seconds is long enough to read but not intrusive for transient errors
3. **All mutations covered, not just save/delete/reorder** — applied the pattern consistently to indent, outdent, setCollapsed, markComplete for uniform error handling

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added toast error handling to DocumentToolbar bulk delete**
- **Found during:** Task 2 review of all mutation surfaces
- **Issue:** `handleDeleteCompleted` in DocumentToolbar called `apiClient.delete` directly without any error handling — failures were silently swallowed
- **Fix:** Wrapped in try/catch with `toast.error('Failed to delete completed bullets', { description: (err as Error).message })`
- **Files modified:** client/src/components/DocumentView/DocumentToolbar.tsx
- **Verification:** TypeScript compiles clean, Docker build succeeds
- **Committed in:** b4418c8 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Fix ensures complete coverage of all mutation failure surfaces. No scope creep.

## Issues Encountered

- Server didn't have Plan 20-01 changes deployed (package.json missing sonner, DocumentErrorFallback.tsx and updated DocumentView.tsx missing). Copied all missing files and rebuilt successfully.

## Next Phase Readiness

- Phase 20 complete: error boundary + toast infrastructure fully wired
- Phase 21 (Token Refresh Interceptor) can now use toast.error() for "session expired" notifications

## Self-Check: PASSED

- commit 7837363 — FOUND
- commit b4418c8 — FOUND
- client/src/hooks/useBullets.ts modified — FOUND
- client/src/components/DocumentView/DocumentToolbar.tsx modified — FOUND
- client/src/main.tsx modified — FOUND

---
*Phase: 20-client-infrastructure*
*Completed: 2026-03-19*
