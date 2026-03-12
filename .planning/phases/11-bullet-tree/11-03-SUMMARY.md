---
phase: 11-bullet-tree
plan: "03"
subsystem: presentation
tags: [android, kotlin, compose, bullet-tree, ui, tdd, markdown, chips]

# Dependency graph
requires:
  - phase: 11-01
    provides: BulletTreeViewModel, BulletTreeUiState, FlatBullet, Bullet domain models
  - phase: 10-document-management
    provides: MainScreen integration point, Shimmer pattern reference

provides:
  - BulletMarkdownRenderer: AnnotatedString builder for bold/italic/strikethrough/links + chip parser
  - BulletRow: single bullet composable with edit/display toggle, guide lines, depth indent
  - BulletEditingToolbar: 7-button sticky toolbar (outdent/indent/move/undo/redo/note)
  - BulletTreeScreen: top-level composable with LazyColumn + imePadding + shimmer + breadcrumb
  - MainScreen updated: BulletTreeScreen replaces Phase 10 placeholder

affects: 11-04, 12-reactivity-and-polish

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "BulletMarkdownRenderer: process markdown by match ranges (sort by start, apply SpanStyles) — links → bold → strikethrough → italic priority order"
    - "BulletRow: Enter detection via onValueChange newline scan (primary for software keyboards); onKeyEvent for physical keyboards (secondary)"
    - "BulletTreeScreen: AnimatedVisibility slideInVertically/slideOutVertically for toolbar entrance/exit"
    - "Shimmer: InfiniteTransition animateFloat 0.3f→0.7f RepeatMode.Reverse at 800ms"
    - "@OptIn(ExperimentalTextApi) required for LinkAnnotation.Url + addLink in Compose 1.7"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletMarkdownRenderer.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletRow.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletEditingToolbar.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeScreen.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletMarkdownRendererTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainScreen.kt

key-decisions:
  - "LinkAnnotation.Url + addLink require @OptIn(ExperimentalTextApi) in Compose 1.7 BOM 2025.02 — applied to both implementation and test"
  - "Markdown processing uses non-overlapping match priority: links first, then bold, then strikethrough, then italic — prevents ** from being parsed as two *"
  - "getUrlAnnotations replaced by getLinkAnnotations in test — getUrlAnnotations is a deprecated thin wrapper in newer Compose versions"
  - "BulletRow uses FlowRow for mixed text+chip segments; pure-text bullets use AnnotatedString directly for performance"

requirements-completed:
  - TREE-01
  - TREE-02
  - TREE-03
  - TREE-04
  - TREE-07
  - CONT-01
  - CONT-02

# Metrics
duration: 13min
completed: 2026-03-12
---

# Phase 11 Plan 03: Bullet Tree UI Composables Summary

**BulletMarkdownRenderer (TDD, 16 tests) + BulletRow with edit/display modes + BulletEditingToolbar (7 buttons) + BulletTreeScreen (LazyColumn + imePadding + shimmer) + MainScreen wired to show bullet editor**

## Performance

- **Duration:** 13 min
- **Started:** 2026-03-12T13:43:06Z
- **Completed:** 2026-03-12T13:56:00Z
- **Tasks:** 2
- **Files modified:** 6 (5 created, 1 modified)

## Accomplishments

- BulletMarkdownRenderer implemented TDD-style (16 tests): `buildMarkdownAnnotatedString` handles bold, italic (both `*` and `_`), strikethrough, and links with proper non-overlapping priority; `parseContentSegments` extracts #tag/@mention/!!date as typed ChipSegments
- BulletRow renders depth-based indent with vertical guide lines via `drawBehind` Canvas, bullet icons (circle or completed checkbox), BasicTextField inline editing with Enter/Backspace intercept, markdown+chip display mode using FlowRow, and collapse/expand arrow for parent bullets
- BulletEditingToolbar provides 7 M3 IconButtons (outdent/indent/move-up/move-down/undo/redo/note) with enabled/disabled states driven by bullet position in flatList
- BulletTreeScreen collects all ViewModel flows, shows Loading shimmer, Success LazyColumn with `imePadding`, animated toolbar, breadcrumb row for zoom mode, and Error retry state
- MainScreen content area now shows BulletTreeScreen when a document is open — Phase 10 placeholder removed

## Task Commits

Each task was committed atomically:

1. **Task 1 TDD RED: BulletMarkdownRendererTest (failing)** - `c7a0320` (test)
2. **Task 1 TDD GREEN: BulletMarkdownRenderer implementation** - `b5bb0ee` (feat)
3. **Task 2: BulletRow, BulletEditingToolbar, BulletTreeScreen, MainScreen wiring** - `ed57da0` (feat)

## Files Created/Modified

- `presentation/bullet/BulletMarkdownRenderer.kt` — AnnotatedString builder + ContentSegment parser with ChipType enum
- `presentation/bullet/BulletRow.kt` — single bullet composable with depth indent, guide lines, edit/display toggle, Enter/Backspace intercept, chip rendering
- `presentation/bullet/BulletEditingToolbar.kt` — 7-button M3 Surface toolbar
- `presentation/bullet/BulletTreeScreen.kt` — LazyColumn with imePadding, shimmer skeleton, AnimatedVisibility toolbar, breadcrumb navigation row
- `presentation/main/MainScreen.kt` — replaced placeholder Column with `BulletTreeScreen(documentId, documentTitle, Modifier.fillMaxSize())`
- `test/.../BulletMarkdownRendererTest.kt` — 16 unit tests (no Android deps needed beyond Compose text)

## Decisions Made

- `LinkAnnotation.Url` + `addLink` require `@OptIn(ExperimentalTextApi)` in Compose BOM 2025.02 — applied on `buildMarkdownAnnotatedString` function and the link test method
- Markdown processing priority order (links → bold → strikethrough → italic) prevents `**bold**` from being mis-parsed as two `*italic*` delimiters
- `getUrlAnnotations` replaced by `getLinkAnnotations` in the link test — the URL-specific API was not returning results in Robolectric; the generic `getLinkAnnotations` with `is LinkAnnotation.Url` type check works correctly
- `FlowRow` used for mixed text+chip bullets; pure-text bullets go through `buildMarkdownAnnotatedString` directly (avoids FlowRow overhead for the common all-text case)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `@OptIn(ExperimentalTextApi)` missing caused compile error for LinkAnnotation API**
- **Found during:** Task 1 TDD GREEN compilation
- **Issue:** `LinkAnnotation.Url`, `addLink`, and `getUrlAnnotations` are marked `@ExperimentalTextApi` in Compose 1.7 — using them without opt-in fails compilation
- **Fix:** Added `@OptIn(ExperimentalTextApi::class)` to `buildMarkdownAnnotatedString` in implementation, and to the link test method in the test file
- **Files modified:** `BulletMarkdownRenderer.kt`, `BulletMarkdownRendererTest.kt`
- **Commit:** b5bb0ee

**2. [Rule 1 - Bug] `getUrlAnnotations` returned empty list in Robolectric test environment**
- **Found during:** Task 1 TDD GREEN — link test failed at assertion `expected:<1> but was:<0>`
- **Issue:** `getUrlAnnotations` wraps `getLinkAnnotations` but did not surface `LinkAnnotation.Url` objects in the Robolectric test context; `getLinkAnnotations` works correctly
- **Fix:** Replaced `result.getUrlAnnotations(...)` with `result.getLinkAnnotations(...)` + `is LinkAnnotation.Url` type check in the test
- **Files modified:** `BulletMarkdownRendererTest.kt`
- **Commit:** b5bb0ee (same fix batch)

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs — both compile/API issues)
**Impact on plan:** Both were API compatibility issues, no scope changes.

## Issues Encountered

None beyond the auto-fixed issues above.

## Next Phase Readiness

- All BulletTreeViewModel stubs from Plan 01 are now wired to real UI callbacks
- BulletTreeScreen ready for Plan 02 operations (viewModel.createBullet, deleteBullet, etc. are called but stubbed)
- BulletMarkdownRenderer is self-contained and tested — can be enhanced without touching UI composables
- No blockers identified for Plan 04 (drag-to-reorder) or Phase 12 (reactivity polish)

## Self-Check: PASSED

All created files confirmed present on disk. All task commits verified in git log.

- `BulletMarkdownRenderer.kt` — FOUND
- `BulletRow.kt` — FOUND
- `BulletEditingToolbar.kt` — FOUND
- `BulletTreeScreen.kt` — FOUND
- `BulletMarkdownRendererTest.kt` — FOUND
- `c7a0320` (TDD RED) — FOUND
- `b5bb0ee` (TDD GREEN + implementation) — FOUND
- `ed57da0` (Task 2 UI composables) — FOUND

---
*Phase: 11-bullet-tree*
*Completed: 2026-03-12*
