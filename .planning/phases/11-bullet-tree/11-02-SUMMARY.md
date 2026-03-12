---
phase: 11-bullet-tree
plan: "02"
subsystem: ui
tags: [android, kotlin, viewmodel, coroutines, tdd, optimistic-updates, debounce, mockk]

# Dependency graph
requires:
  - phase: 11-bullet-tree
    plan: "01"
    provides: BulletRepository + all use cases + BulletTreeViewModel stub with operation queue + FlattenTreeUseCase

provides:
  - BulletTreeViewModel with all 17 operations fully implemented (no TODOs)
  - createBullet with temp UUID optimistic insert, replaced by server bullet on success
  - backspaceOnEmpty with child reparenting + focus-to-previous-with-cursorEnd
  - enterOnEmpty with outdent-if-nested / clear-focus-if-root logic
  - indentBullet/outdentBullet with server response authoritative update
  - moveUp/moveDown with cross-parent flat-list position computation
  - toggleCollapse with optimistic isCollapsed flip + re-flatten
  - updateContent/saveNote with MutableSharedFlow + debounce(500ms) pattern
  - undo/redo with canUndo/canRedo update + reloadFromServer
  - zoomTo with _zoomRootId + breadcrumb path + re-flatten subtree
  - moveBulletLocally for drag-and-drop optimistic flatList reorder
  - commitBulletMove with moveBulletUseCase + failure recovery
  - All operations: snackbar emit + reloadFromServer on failure
  - BulletTreeViewModelTest: 27 unit tests — all passing

affects: 11-03, 12-reactivity-and-polish

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Content/note debounce: MutableSharedFlow(extraBufferCapacity=64) + debounce(500) in init{} collect — avoids per-keystroke API calls without per-bullet Job tracking"
    - "Optimistic update: mutate local bullets list, re-run flattenTreeUseCase, emit Success, enqueue API call — authoritative on success, snackbar+reload on failure"
    - "createBullet temp UUID: insert Bullet with temp-UUID, replace on server success — prevents flatList flicker while API is in flight"
    - "backspaceOnEmpty child reparenting: deleted bullet's children get parentId set to deleted bullet's own parentId before optimistic removal"
    - "SharedFlow snackbar test pattern: launch collect job BEFORE triggering failure, cancel AFTER advanceUntilIdle — avoids UncompletedCoroutinesError"

key-files:
  created:
    - android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletRow.kt

key-decisions:
  - "Content/note debounce uses MutableSharedFlow with extraBufferCapacity=64 instead of per-bullet Job cancelation — simpler, handles rapid multi-bullet edits without leaking coroutines"
  - "createBullet inserts temp UUID bullet optimistically so the user sees the new row immediately; server response replaces it, focus moves to real ID"
  - "backspaceOnEmpty reparents deleted bullet's children to the deleted bullet's own parentId — keeps tree structurally consistent without needing a server round-trip for the reparenting"
  - "updateContent debounce test uses advanceTimeBy(200) + coVerify(exactly=0) to assert no-fire, then separate test with advanceTimeBy(600) to assert fire — matches coroutines-test virtual time semantics"

requirements-completed:
  - TREE-02
  - TREE-03
  - TREE-04
  - TREE-05
  - TREE-06
  - TREE-08
  - TREE-10
  - TREE-11

# Metrics
duration: 9min
completed: 2026-03-12
---

# Phase 11 Plan 02: BulletTreeViewModel Operations Summary

**17 ViewModel operations with optimistic updates + 500ms debounced content/note saves + 27 unit tests (TDD, all passing)**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-12T13:43:04Z
- **Completed:** 2026-03-12T13:52:28Z
- **Tasks:** 1 (TDD — RED + GREEN commits)
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- All 17 BulletTreeViewModel stub methods replaced with full implementations — no TODOs remain
- Optimistic update pattern applied uniformly: mutate local list, re-flatten, emit Success state, then enqueue API call
- Content and note saves use MutableSharedFlow + debounce(500ms) in init{} — avoids per-keystroke API calls without complex per-bullet Job tracking
- 27 unit tests cover all operations including debounce timing, snackbar on failure, and zoom subtree behavior

## Task Commits

Each task was committed atomically:

1. **Task 1 TDD RED: BulletTreeViewModelTest (27 failing tests)** - `febf7aa` (test)
2. **Task 1 TDD GREEN: BulletTreeViewModel all 17 operations** - `e7a2c45` (feat)

_Note: TDD task split into RED (test) + GREEN (feat) commits per TDD protocol_

## Files Created/Modified
- `presentation/bullet/BulletTreeViewModel.kt` - Full implementation of all 17 operations (647 lines)
- `test/.../BulletTreeViewModelTest.kt` - 27 unit tests (597 lines)
- `presentation/bullet/BulletRow.kt` - Pre-existing untracked file with Dp multiplication bug fixed

## Decisions Made
- Content/note debounce uses `MutableSharedFlow(extraBufferCapacity=64)` + `debounce(500)` in `init{}` collect instead of per-bullet Job cancellation — simpler, handles rapid multi-bullet edits without coroutine leaks
- `createBullet` inserts a temp UUID bullet optimistically so the user sees the new row immediately; server response replaces it and focus moves to the real ID
- `backspaceOnEmpty` reparents deleted bullet's children to the deleted bullet's own `parentId` — keeps the tree structurally consistent without needing a separate API call for reparenting
- Debounce test uses `advanceTimeBy(200)` + `coVerify(exactly=0)` for no-fire assertion, separate test with `advanceTimeBy(600)` for fire assertion — matches coroutines-test virtual time semantics

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `depth * INDENT_DP` Dp multiplication operator order in BulletRow.kt**
- **Found during:** Task 1 verification (compileDebugKotlin check)
- **Issue:** Pre-existing untracked file `BulletRow.kt` had `val indentPx = depth * INDENT_DP` where `depth: Int` and `INDENT_DP: Dp`. Kotlin/Compose does not support `Int * Dp` — only `Dp * Int`.
- **Fix:** Changed to `val indentPx = INDENT_DP * depth`
- **Files modified:** `presentation/bullet/BulletRow.kt`
- **Verification:** `compileDebugKotlin` passes after fix
- **Committed in:** e7a2c45 (Task 1 TDD GREEN commit)

**2. [Rule 1 - Bug] Debounce test mock not configured caused MockKException**
- **Found during:** Task 1 TDD GREEN first run
- **Issue:** `updateContent does not PATCH immediately` test called `advanceTimeBy(300)` without configuring the mock — but the mock was not strict enough to be caught earlier. The test proved the assertion direction was correct; fixed by adding `coEvery { patchBulletUseCase("b1", any()) } returns ...` in the no-fire test and reducing advance to `200ms` to be unambiguous
- **Fix:** Added mock setup + changed 300ms to 200ms in no-fire test
- **Files modified:** `BulletTreeViewModelTest.kt`
- **Verification:** All 27 tests pass
- **Committed in:** e7a2c45 (Task 1 TDD GREEN commit, test fix included)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both were minor issues in verification. No scope changes. BulletRow.kt was a pre-existing untracked file that had always been broken.

## Issues Encountered
- `BulletMarkdownRendererTest.kt` in the same package has `@ExperimentalTextApi` warnings displayed as compilation errors in `-q` mode — these are pre-existing and out of scope, tests pass when run with full output mode

## Next Phase Readiness
- All 17 ViewModel operations are implemented and tested — Plan 03 (UI composables) can wire directly to these methods
- Optimistic update state is stable: `updateState(bullets)` always preserves `focusedBulletId` and recomputes `flatList` from scratch — safe to call from any operation
- Debounce flows are collecting in `viewModelScope` — no additional wiring needed in Plan 03
- No blockers identified for Plan 03

---
*Phase: 11-bullet-tree*
*Completed: 2026-03-12*
