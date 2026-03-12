---
phase: 12-reactivity-and-polish
plan: "03"
subsystem: android-ui
tags: [android, compose, search, bookmarks, hilt, debounce, coroutines, mockk]
dependency_graph:
  requires:
    - phase: 12-01
      provides: SearchBulletsUseCase, GetBookmarksUseCase, SearchResult domain model, Bookmark domain model
  provides:
    - SearchViewModel with 300ms debounce via MutableSharedFlow + debounce()
    - SearchUiState (Idle/Loading/Success/Empty/Error)
    - BookmarksViewModel loading bookmarks on init
    - BookmarksUiState (Loading/Success/Empty/Error)
    - SearchResultItem composable with query term highlighting
    - BookmarksScreen composable (list with document attribution)
    - Inline search bar in MainScreen TopAppBar (active/inactive modes)
    - Search results overlay with sticky document headers
    - Bookmarks entry in NavigationDrawer (above document list)
    - Chip-to-search wiring via BulletRow.onChipClick -> MainScreen
    - Scroll-to-bullet via pendingScrollToBulletId + LaunchedEffect in BulletTreeScreen
  affects:
    - Phase 12 plans 04 and 05 (can build on search/bookmarks patterns)
tech_stack:
  added: []
  patterns:
    - MutableSharedFlow(extraBufferCapacity=64) + debounce(300ms) in init{} collect for search debounce
    - hiltViewModel() scoped to MainScreen for SearchViewModel and BookmarksViewModel (separate lifecycles)
    - pendingScrollToBulletId in MainUiState + LaunchedEffect(id) in BulletTreeScreen for cross-screen scroll
    - onChipClick: ((String) -> Unit)? = null pattern — null disables clickability on focused bullets
key_files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/presentation/search/SearchUiState.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/search/SearchViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/search/SearchResultItem.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bookmarks/BookmarksUiState.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bookmarks/BookmarksViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bookmarks/BookmarksScreen.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/search/SearchViewModelTest.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/bookmarks/BookmarksViewModelTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainUiState.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainScreen.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/DocumentDrawerContent.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletRow.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeScreen.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt
decisions:
  - "SearchViewModel uses MutableSharedFlow(replay=0, extraBufferCapacity=64) + debounce(300) — replay=0 prevents stale queries firing when collector subscribes after query was already emitted"
  - "isSearchActive and searchQuery are local remembered state in MainScreen — not in ViewModel — because they are pure UI ephemeral state that doesn't need to survive config change"
  - "onChipClick passed as null to BulletRow when bullet is focused to avoid chip triggering search while editing"
  - "pendingScrollToBulletId cleared by onClearPendingScroll callback after LaunchedEffect fires animateScrollToItem"
requirements_completed:
  - POLL-05
  - CONT-04
metrics:
  duration_seconds: 580
  completed_date: "2026-03-12"
  tasks_completed: 2
  files_created: 8
  files_modified: 8
---

# Phase 12 Plan 03: Search UI and Bookmarks Screen Summary

**Inline search bar in TopAppBar with 300ms debounce, results grouped by document title, bookmarks screen from drawer, and chip-to-search wiring through BulletRow.onChipClick.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-03-12T20:02:00Z
- **Completed:** 2026-03-12T20:12:00Z
- **Tasks:** 2
- **Files modified:** 16 (8 created, 8 modified)

## Accomplishments
- SearchViewModel with 300ms debounce via MutableSharedFlow, groups results by documentTitle
- BookmarksViewModel that loads bookmarks on init with full Success/Empty/Error states
- SearchResultItem composable with buildAnnotatedString query term highlighting (case-insensitive)
- BookmarksScreen composable with LazyColumn of bullet content + document attribution
- MainScreen search bar: toggles TopAppBar between title/BasicTextField, overlay results with sticky headers, BackHandler, chip-to-search wiring
- BulletRow/InlineChip updated with onChipClick parameter (null when focused to disable on editing bullets)
- BulletTreeScreen: pendingScrollToBulletId + LaunchedEffect scroll-to-bullet on navigation from search/bookmark tap
- Full unit test coverage: 8 SearchViewModelTest cases + 5 BookmarksViewModelTest cases, all passing

## Task Commits

Each task was committed atomically:

1. **Task 1: SearchViewModel, BookmarksViewModel, and unit tests** - `c159daa` (feat)
2. **Task 2: Search bar, BookmarksScreen, drawer entry, chip-to-search** - `3dcff4b` (feat)
3. **Deviation fix: Wire Plan 02 BulletRow params in BulletTreeScreen** - `ed7b601` (fix)

## Files Created/Modified
- `presentation/search/SearchUiState.kt` - Sealed interface (Idle/Loading/Success/Empty/Error)
- `presentation/search/SearchViewModel.kt` - Debounce 300ms, groups results by documentTitle
- `presentation/search/SearchResultItem.kt` - Query highlighting via buildAnnotatedString
- `presentation/bookmarks/BookmarksUiState.kt` - Sealed interface (Loading/Success/Empty/Error)
- `presentation/bookmarks/BookmarksViewModel.kt` - Init load, retry via loadBookmarks()
- `presentation/bookmarks/BookmarksScreen.kt` - LazyColumn with bullet + document name
- `presentation/main/MainUiState.kt` - Added showBookmarks, pendingScrollToBulletId to Success
- `presentation/main/MainViewModel.kt` - Added showBookmarks(), navigateToBullet(), clearPendingScroll()
- `presentation/main/MainScreen.kt` - Full inline search bar, overlay results, bookmarks routing
- `presentation/main/DocumentDrawerContent.kt` - Bookmarks NavigationDrawerItem above document list
- `presentation/bullet/BulletRow.kt` - onChipClick parameter, clickable InlineChip
- `presentation/bullet/BulletTreeScreen.kt` - onChipClick/pendingScrollToBulletId/onClearPendingScroll params

## Decisions Made
- SearchViewModel uses `replay=0` on MutableSharedFlow to prevent stale queries firing at startup
- `isSearchActive` and `searchQuery` are local Compose state in MainScreen (ephemeral UI, no config-change survival needed)
- `onChipClick` is passed as `null` when `flatBullet.bullet.id == focusedBulletId` to disable chip clicks during text editing
- `pendingScrollToBulletId` cleared via callback after `animateScrollToItem` to prevent re-triggering

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added Plan 02 use cases to BulletTreeViewModel**
- **Found during:** Task 1 (unit test compilation)
- **Issue:** BulletTreeViewModelTest already had getBookmarksUseCase, addBookmarkUseCase, removeBookmarkUseCase, getAttachmentsUseCase parameters in createViewModel(), but BulletTreeViewModel only had 11 use cases. Compilation failed.
- **Fix:** Added the 4 missing use cases and toggleComplete() method stub to BulletTreeViewModel constructor. Linter then expanded this to full AndroidViewModel with toggleBookmark, toggleAttachmentExpansion, downloadAttachment, refresh methods.
- **Files modified:** BulletTreeViewModel.kt, BulletTreeViewModelTest.kt
- **Committed in:** c159daa (Task 1 commit)

**2. [Rule 3 - Blocking] Wire linter-added BulletRow Plan 02 parameters in BulletTreeScreen**
- **Found during:** Post-Task 2 build verification
- **Issue:** Linter expanded BulletRow with Plan 02 parameters (isBookmarked, isAttachmentsExpanded, attachments, onToggleComplete, onToggleBookmark, onToggleAttachments, onDeleteFromMenu, onDownloadAttachment). BulletTreeScreen's call to BulletRow was missing these required parameters.
- **Fix:** Added bookmarkedBulletIds/attachments/expandedAttachments StateFlow collection in BulletTreeScreen; passed all new parameters to BulletRow from ViewModel state.
- **Files modified:** BulletTreeScreen.kt (also BulletRow.kt)
- **Committed in:** ed7b601

---

**Total deviations:** 2 auto-fixed (2 Rule 3 blocking issues)
**Impact on plan:** Both fixes were required for compilation. The linter pre-applied Plan 02 changes to BulletRow and BulletTreeViewModel during execution of Plan 03 — this advanced Plan 02's UI work as a side effect.

## Issues Encountered
- Debounce test `query does NOT fire API call before 300ms` initially failed because `advanceUntilIdle()` was called after `advanceTimeBy(200)`, which advanced past the 300ms threshold. Fixed by removing `advanceUntilIdle()` from that specific test.

## Next Phase Readiness
- Search and bookmarks screens are fully wired — Plan 04 (pull-to-refresh) can build on the same MainScreen/BulletTreeScreen architecture
- All Plan 02 features (swipe, context menu, attachments, bookmarks) are now also implemented in BulletRow/BulletTreeViewModel/BulletTreeScreen as a side effect of linter application

---
*Phase: 12-reactivity-and-polish*
*Completed: 2026-03-12*
