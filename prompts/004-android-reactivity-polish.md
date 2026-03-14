<objective>
Polish the Android Notes app with proper loading/error states, swipe gestures, search, undo/redo, and Material 3 animations. The focus is on REACTIVITY — every interaction should feel smooth, fast, and native. This phase transforms a functional app into a delightful one.
</objective>

<context>
This builds on prompts 001-003. The app has auth, document management, and the full bullet tree. Now we add the finishing touches.

Read these files for behavior reference:
- `client/src/components/DocumentView/BulletRow.tsx` — swipe gesture behavior (thresholds, icon scaling, animations)
- `client/src/components/QuickOpenPalette.tsx` — search UI behavior
- `client/src/hooks/useUndo.ts` — undo/redo flow
- `client/src/hooks/useSearch.ts` — debounced search

Additional APIs:
- `GET /api/search?q=query` → `[{ id, content, documentId, documentTitle }]` (min 2 chars)
- `GET /api/undo/status` → `{ canUndo, canRedo }`
- `POST /api/undo` → performs undo, returns updated state
- `POST /api/redo` → performs redo, returns updated state
</context>

<requirements>

## Swipe Gestures on Bullet Rows
1. **Swipe right** → complete toggle (green background, checkmark icon)
2. **Swipe left** → delete (red background, trash icon)
3. Threshold: ~40% of row width
4. Icon scales from 0.5x to 1.2x as swipe approaches threshold (matching web behavior)
5. On commit: animate row off-screen, execute action
6. After delete: show Snackbar with "Undo" action
7. Use Material 3 `SwipeToDismissBox` or `AnchoredDraggable`

## Search
1. **SearchApi** Retrofit interface
2. **SearchViewModel**: `query` StateFlow, debounced 300ms, only fires when >= 2 chars
3. **Search UI**: Material 3 `SearchBar` accessible from document toolbar
4. Results show bullet content + document title in a `LazyColumn`
5. Tap result → navigate to document, scroll to bullet (set `focusTargetId`)
6. Clear button to reset search

## Undo/Redo
1. **UndoApi** Retrofit interface for status, undo, redo
2. Undo/redo buttons in document toolbar (enabled/disabled based on status)
3. After undo/redo: refetch all bullets for current document
4. After any delete: show Snackbar with "Undo" action that calls the undo endpoint
5. Poll undo status after each mutation (or fetch on-demand when toolbar is visible)

## Loading States
1. Initial document/bullet load: show `CircularProgressIndicator` centered
2. Button loading states: disable + show small spinner during auth/create operations
3. Skeleton/shimmer effect optional — at minimum show a clear loading indicator

## Error Handling
1. Wrap all repository calls in `runCatching`
2. Use sealed class `UiState<T>` = `Loading | Success(data) | Error(message, retry: () -> Unit)`
3. Network errors → `Snackbar` with message
4. Validation errors (auth forms) → inline field errors
5. Optimistic update failures → rollback + Snackbar
6. No network at all → show error state with "Retry" button

## Pull-to-Refresh
1. `PullToRefreshBox` (Material 3) wrapping bullet tree
2. Also on document list in drawer
3. Triggers full data refetch

## Animations
1. **Collapse/expand**: `AnimatedVisibility` with vertical slide for bullet children
2. **List reorder**: `animateItemPlacement()` modifier on LazyColumn items
3. **Transitions**: `Crossfade` or `AnimatedContent` for loading → content → error states
4. **Swipe**: smooth spring animations for icon scaling and row dismissal
5. **Drag overlay**: floating bullet with elevation shadow, 0.8 opacity
6. **Drop indicator**: blue line at projected depth during drag
7. **Navigation**: default Compose navigation transitions (slide)

## Dark Theme
1. `darkColorScheme()` + `lightColorScheme()` with custom colors
2. Respect `isSystemInDarkTheme()` by default
3. Optional manual toggle in drawer (persisted in DataStore)
4. Swipe colors: green (#4CAF50) for complete, red (#F44336) for delete
5. Accent/primary color matching web's blue accent

## Toolbar
1. Document title (editable — tap to rename)
2. Hamburger menu icon to open drawer
3. Search icon to open search
4. Undo/redo buttons
5. More menu: Export as Markdown
</requirements>

<implementation>
New/modified files:
```
data/
  remote/
    api/SearchApi.kt
    api/UndoApi.kt
    dto/SearchDtos.kt
    dto/UndoDtos.kt
  repository/SearchRepository.kt
  repository/UndoRepository.kt

domain/
  model/SearchResult.kt
  model/UndoStatus.kt

presentation/
  common/
    UiState.kt                       — sealed class: Loading, Success, Error
    ErrorSnackbar.kt                 — Reusable snackbar error display
    PullRefreshWrapper.kt            — Reusable pull-to-refresh wrapper
  search/
    SearchViewModel.kt
    SearchScreen.kt                  — Search overlay or bottom sheet
  bullets/
    BulletRow.kt                     — Updated: add SwipeToDismissBox wrapping
    BulletTreeScreen.kt              — Updated: pull-to-refresh, animateItemPlacement
    SwipeBulletRow.kt                — Swipe wrapper with background + icon
  documents/
    DocumentRow.kt                   — Updated: swipe to delete
  document/
    DocumentScreen.kt                — Updated: full toolbar with search, undo/redo
    DocumentViewModel.kt             — Toolbar state, undo/redo
  theme/
    Theme.kt                         — Updated: full light/dark schemes
    Color.kt                         — Updated: complete color palette
```

Swipe implementation detail:
```kotlin
SwipeToDismissBox(
    state = dismissState,
    backgroundContent = {
        // Green background + check icon (start-to-end) or Red + trash (end-to-start)
        val direction = dismissState.dismissDirection
        val color = when (direction) {
            StartToEnd -> Color(0xFF4CAF50)  // complete
            EndToStart -> Color(0xFFF44336)  // delete
            else -> Color.Transparent
        }
        val icon = when (direction) {
            StartToEnd -> Icons.Default.Check
            EndToStart -> Icons.Default.Delete
            else -> null
        }
        val progress = dismissState.progress
        val iconScale = if (progress >= 1f) 1.2f else 0.5f + progress * 0.7f
        // Render background with scaled icon
    }
)
```

Search debounce pattern:
```kotlin
// In SearchViewModel init block
viewModelScope.launch {
    snapshotFlow { _query.value }
        .debounce(300)
        .filter { it.length >= 2 }
        .collectLatest { query ->
            _results.value = UiState.Loading
            searchRepository.search(query)
                .onSuccess { _results.value = UiState.Success(it) }
                .onFailure { _results.value = UiState.Error(it.message ?: "Search failed") }
        }
}
```

SnackbarHostState should be provided at the MainScreen scaffold level and passed down (or via a shared state holder injected by Hilt) so any screen can show snackbars.
</implementation>

<verification>
1. Swipe right on bullet → green background + check icon scales → toggles complete
2. Swipe left on bullet → red background + trash icon → deletes → "Undo" snackbar appears
3. Tap "Undo" on snackbar → bullet reappears
4. Open search → type query → results appear after 300ms debounce
5. Tap search result → navigates to correct document and bullet
6. Tap undo button → last action undone, bullets refresh
7. Tap redo button → redone
8. Pull down on bullet tree → refresh indicator → data reloads
9. Switch to dark mode (system or manual toggle) → all colors correct
10. Collapse bullet → children slide away smoothly
11. Reorder bullets → items animate to new positions
12. Lose network → error snackbar → tap retry → data loads
13. All loading states show indicators (no blank screens)
</verification>

<success_criteria>
- Every interaction feels instant and reactive (< 100ms visual feedback)
- Swipe gestures match web client behavior (thresholds, icon scaling, animations)
- Search works with proper debouncing and navigation
- Undo/redo works reliably
- Error states are handled gracefully everywhere — no crashes, no blank screens
- Dark and light themes are complete and consistent
- Animations are smooth (no jank on mid-range devices)
- The app feels like a polished, native Android app — not a web wrapper
</success_criteria>
