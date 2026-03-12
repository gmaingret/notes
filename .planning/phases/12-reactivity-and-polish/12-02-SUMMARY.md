---
phase: 12-reactivity-and-polish
plan: "02"
subsystem: ui
tags: [android, compose, swipe-gestures, attachments, bookmarks, coil, material3, coroutines]

# Dependency graph
requires:
  - phase: 12-01
    provides: AttachmentRepository, BookmarkRepository, Attachment/Bookmark domain models, use cases

provides:
  - toggleComplete() with optimistic update + rollback in BulletTreeViewModel
  - toggleBookmark(), toggleAttachmentExpansion(), downloadAttachment() in BulletTreeViewModel
  - SwipeToDismissBox with proportional green/red reveal, haptic feedback at threshold
  - Long-press context menu (Bookmark/Attachments/Delete) on BulletRow
  - Inline AttachmentList composable with Coil images and non-image file rows
  - Bookmark star indicator and paperclip indicator on BulletRow
  - onChipClick callback wired from BulletRow through InlineChip for Plan 03
  - BulletTreeViewModel as AndroidViewModel for DownloadManager access
  - isRefreshing StateFlow for Plan 04 pull-to-refresh
  - AttachmentRepositoryTest with DTO-to-domain mapping coverage

affects: [12-03, 12-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - AndroidViewModel pattern for DownloadManager access (app context via getApplication())
    - SwipeToDismissBox inside ReorderableItem — gesture ordering: drag outermost, swipe inner
    - Lazy attachment loading: load on first expansion, cache in Map<String, List<Attachment>>
    - Haptic feedback at swipe threshold using LaunchedEffect(dismissState.progress)
    - combinedClickable for tap (focus) + long-press (context menu) on bullet content

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/AttachmentList.kt
    - android/app/src/test/java/com/gmaingret/notes/data/repository/AttachmentRepositoryTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletRow.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeScreen.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt

key-decisions:
  - "BulletTreeViewModel refactored from ViewModel to AndroidViewModel — needed for DownloadManager.enqueue() which requires Application context; downloadAttachment() launches in viewModelScope and retrieves suspend TokenStore.getAccessToken() before building the request"
  - "SwipeToDismissBox placed inside ReorderableItem (not outside) — drag gesture owns the outermost position, swipe gesture is inner; swipe disabled when isFocused or isDragging to prevent gesture conflicts"
  - "Attachment paperclip indicator shown only after lazy load (not pre-fetched) — pre-fetching would require N+1 API calls for all visible bullets; acceptable UX trade-off"
  - "BulletTreeViewModelTest uses mockk<Application>(relaxed=true) for AndroidViewModel unit testing — avoids Android instrumentation requirement"

patterns-established:
  - "SwipeToDismissBox inside ReorderableItem: gesture ordering prevents swipe vs drag conflicts"
  - "Lazy attachment loading: toggle expansion triggers load, result cached in Map keyed by bulletId"
  - "Proportional swipe reveal: lerp(Color.Transparent, targetColor, dismissState.progress) for smooth color animation"

requirements-completed: [POLL-03, POLL-04, CONT-03]

# Metrics
duration: 35min
completed: 2026-03-12
---

# Phase 12 Plan 02: Swipe Gestures, Context Menu, Attachments, and Bookmark Indicators Summary

**Swipe-to-complete/delete with proportional color reveal, long-press context menu, inline Coil attachment list, bookmark/paperclip indicators, and onChipClick wiring on BulletRow**

## Performance

- **Duration:** 35 min
- **Started:** 2026-03-12T20:00:00Z
- **Completed:** 2026-03-12T20:35:00Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- BulletTreeViewModel extended with toggleComplete (optimistic + rollback), toggleBookmark, toggleAttachmentExpansion, downloadAttachment (AndroidViewModel + TokenStore), and refresh() with isRefreshing StateFlow
- SwipeToDismissBox wraps each BulletRow inside ReorderableItem — right swipe = complete (row stays), left swipe = delete (row slides off) with proportional green/red background and haptic feedback at threshold
- AttachmentList composable renders image thumbnails via Coil AsyncImage with gray placeholder, non-image files with type icon + size formatting; context menu triggers lazy load

## Task Commits

Each task was committed atomically:

1. **Task 1: ViewModel + tests (TDD)** - `2cdfbac` (feat)
2. **Task 2: BulletRow + AttachmentList + BulletTreeScreen** - `f103cdf` (feat)

**Plan metadata:** (see docs commit below)

_Note: Task 1 followed TDD: tests written first (RED), then implementation (GREEN)._

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/AttachmentList.kt` - New inline attachment composable with Coil image + file row rendering
- `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt` - Refactored to AndroidViewModel; added toggleComplete, toggleBookmark, toggleAttachmentExpansion, downloadAttachment, refresh, isRefreshing
- `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletRow.kt` - Added isBookmarked, isAttachmentsExpanded, attachments params; combinedClickable long-press menu; star/paperclip indicators; InlineChip onChipClick wiring
- `android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeScreen.kt` - SwipeToDismissBox wrapper per bullet, haptic feedback, collect new ViewModel flows, onChipClick parameter
- `android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt` - Updated for AndroidViewModel, added toggleComplete tests
- `android/app/src/test/java/com/gmaingret/notes/data/repository/AttachmentRepositoryTest.kt` - New: DTO-to-domain mapping, downloadUrl construction, failure case

## Decisions Made

- `BulletTreeViewModel` refactored to `AndroidViewModel` — `downloadAttachment()` needs `DownloadManager` which requires Application context; `TokenStore.getAccessToken()` is suspend so the method launches in `viewModelScope`
- `SwipeToDismissBox` placed inside `ReorderableItem` to prevent gesture conflicts — drag is outermost, swipe is inner; both disabled when `isFocused` or `isDragging`
- Attachment paperclip indicator shown lazily after first load — avoids N+1 API calls for all visible bullets on document open
- Unit tests for AndroidViewModel use `mockk<Application>(relaxed=true)` — avoids Android instrumentation while covering business logic

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None — compilation and tests passed on first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 03 (chip-to-search) can use `onChipClick` parameter now wired through BulletRow and BulletTreeScreen
- Plan 04 (pull-to-refresh) can use `refresh()` method and `isRefreshing` StateFlow already in ViewModel
- All swipe gestures, context menu, and attachment display ready for QA on device

---
*Phase: 12-reactivity-and-polish*
*Completed: 2026-03-12*
