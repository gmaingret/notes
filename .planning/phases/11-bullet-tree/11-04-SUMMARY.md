---
phase: 11-bullet-tree
plan: "04"
subsystem: presentation
tags: [android, kotlin, compose, bullet-tree, drag-reorder, reparenting, breadcrumb, notes, animation]

# Dependency graph
requires:
  - phase: 11-bullet-tree
    plan: "02"
    provides: BulletTreeViewModel with moveBulletLocally/commitBulletMove/zoomTo/saveNote/breadcrumbPath
  - phase: 11-bullet-tree
    plan: "03"
    provides: BulletRow/BulletTreeScreen/BulletEditingToolbar LazyColumn structure
  - phase: 10-document-management
    provides: DocumentDrawerContent Reorderable pattern reference

provides:
  - BreadcrumbRow: horizontal scrollable breadcrumb strip with Home + chevron crumbs, auto-scroll to last
  - NoteField: AnimatedVisibility inline note/comment field with placeholder text
  - BulletRow: updated with isDragging (1.02x scale), isNoteExpanded, note indicator icon, NoteField inline
  - BulletTreeScreen: Reorderable drag-reorder, horizontal reparenting, cycle prevention, BreadcrumbRow, note expansion Set, SnackbarHost
  - BulletTreeViewModel: showSnackbar() public method

affects: 12-reactivity-and-polish

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Drag reparenting: pointerInput detectDragGestures accumulates dragHorizontalOffset; targetDepthDelta = (offset / indentPx).roundToInt().coerceIn(-depth, 1)"
    - "Cycle prevention: collect descendants between dragged index and next same/lower-depth item into Set<String>; reject if newParentId in set"
    - "Note expansion: mutableStateOf(setOf<String>()) in BulletTreeScreen; toolbar onComment and note indicator toggle the focused bullet's ID"
    - "Drag visual: graphicsLayer { scaleX=1.02f; scaleY=1.02f; shadowElevation=8f } applied when isDragging=true"
    - "BreadcrumbRow auto-scroll: LaunchedEffect(breadcrumbs.size) scrolls LazyRow to last item index"
    - "showSnackbar: viewModelScope.launch { _snackbarMessage.emit() } — allows UI to trigger snackbar without coroutine context"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BreadcrumbRow.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/NoteField.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletRow.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeScreen.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt

key-decisions:
  - "Cycle prevention uses flat-list position scan (not recursive tree walk) — avoids needing a separate descendant-lookup method since FlattenTreeUseCase already provides DFS order"
  - "showSnackbar() added to BulletTreeViewModel as public method — cleaner than a Channel or exposing _snackbarMessage directly"
  - "Note expansion state lives in BulletTreeScreen (local Set<String>) not ViewModel — pure UI state, no need to survive config change"
  - "longPressDraggableHandle available implicitly in ReorderableItem scope — no additional import needed"

requirements-completed:
  - TREE-08
  - TREE-09
  - TREE-10
  - TREE-11
  - TREE-06

# Metrics
duration: 5min
completed: 2026-03-12
---

# Phase 11 Plan 04: Drag-Reorder, Reparenting, BreadcrumbRow, NoteField, Collapse Animations Summary

**Drag-reorder with horizontal reparenting + cycle prevention + BreadcrumbRow composable + inline NoteField + drag visual (1.02x scale) — all wired to production ViewModel methods**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-12T13:59:21Z
- **Completed:** 2026-03-12T14:04:00Z
- **Tasks:** 1 auto + 1 checkpoint (auto-approved)
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments

- BreadcrumbRow: LazyRow with Home icon, chevron separators, auto-scroll to last crumb; last crumb is onSurface (non-clickable), others are primary + clickable
- NoteField: AnimatedVisibility with expandVertically+fadeIn/shrinkVertically+fadeOut; Surface with 1.dp tonalElevation; BasicTextField with placeholder; debounce handled in ViewModel
- BulletRow updated: `isDragging` triggers `graphicsLayer { scaleX=1.02f; scaleY=1.02f; shadowElevation=8f }`; note indicator icon (StickyNote2, 40% alpha) shown when note exists and collapsed; NoteField rendered inline below content row; outer wrapper changed from Row to Column
- BulletTreeScreen updated: Reorderable LazyColumn (same pattern as Phase 10 DocumentDrawerContent); `pointerInput detectDragGestures` accumulates horizontal offset for depth targeting; onDragStopped walks flat list to compute newParentId and afterId; descendant-set cycle prevention with Snackbar; SnackbarHost overlay; BreadcrumbRow with AnimatedVisibility; note expansion tracked as `Set<String>` local state; toolbar onComment toggles note
- BulletTreeViewModel: added `showSnackbar()` public method so the BulletTreeScreen can emit snackbar messages from non-coroutine context

## Task Commits

1. **Task 1: Drag-reorder, reparenting, BreadcrumbRow, NoteField, collapse animations** - `2dd23e1` (feat)
2. **Task 2: checkpoint:human-verify** — Auto-approved (auto_advance=true)

## Files Created/Modified

- `presentation/bullet/BreadcrumbRow.kt` — 110 lines; horizontal scrollable breadcrumb strip
- `presentation/bullet/NoteField.kt` — 75 lines; AnimatedVisibility inline note field
- `presentation/bullet/BulletRow.kt` — updated; added isDragging, isNoteExpanded, note indicator, NoteField, Column wrapper
- `presentation/bullet/BulletTreeScreen.kt` — updated; Reorderable drag integration, reparenting logic, cycle prevention, note expansion state, SnackbarHost
- `presentation/bullet/BulletTreeViewModel.kt` — added showSnackbar() public method

## Decisions Made

- Cycle prevention uses flat-list position scan: collect all bullets between dragged bullet index and next same-or-lower depth item into `Set<String>`, then check if `newParentId` is in the set. Avoids recursive tree traversal since FlattenTreeUseCase already produces DFS order.
- `showSnackbar()` added to ViewModel as a public `fun` launching in `viewModelScope` — cleaner than exposing `_snackbarMessage` or passing a Channel reference to the UI.
- Note expansion state kept as local `Set<String>` in BulletTreeScreen rather than in ViewModel — this is pure ephemeral UI state with no server persistence, no need to survive configuration change.
- `longPressDraggableHandle` is an extension function in `ReorderableItemScope` — accessible implicitly inside the `ReorderableItem { }` lambda; no import needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Functionality] Added `showSnackbar()` to BulletTreeViewModel**
- **Found during:** Task 1 implementation
- **Issue:** BulletTreeScreen calls `viewModel.showSnackbar("Cannot move bullet under its own child")` for cycle prevention, but the method did not exist on the ViewModel
- **Fix:** Added `fun showSnackbar(message: String)` that emits on `_snackbarMessage` via `viewModelScope.launch`
- **Files modified:** `BulletTreeViewModel.kt`
- **Commit:** 2dd23e1

None beyond the above auto-fix.

## Issues Encountered

None. Debug APK builds successfully. All unit tests pass.

## Next Phase Readiness

- All Phase 11 requirements completed: drag-reorder, reparenting, breadcrumb zoom, notes field, collapse/expand animations, markdown rendering, chip syntax, undo/redo
- Phase 12 (Reactivity and Polish) can wire into existing ViewModel flows — no architectural changes needed
- BulletTreeViewModel is feature-complete for Phase 11 scope

## Self-Check: PASSED

All files confirmed present and commit verified.

- `BreadcrumbRow.kt` — FOUND
- `NoteField.kt` — FOUND
- `BulletRow.kt` — FOUND (modified)
- `BulletTreeScreen.kt` — FOUND (modified)
- `BulletTreeViewModel.kt` — FOUND (modified)
- `2dd23e1` (Task 1 feat commit) — FOUND

---
*Phase: 11-bullet-tree*
*Completed: 2026-03-12*
