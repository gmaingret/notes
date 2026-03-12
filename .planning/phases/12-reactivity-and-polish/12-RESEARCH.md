# Phase 12: Reactivity and Polish - Research

**Researched:** 2026-03-12
**Domain:** Jetpack Compose / Material 3 — swipe gestures, search, attachments, bookmarks, pull-to-refresh, dark theme, animations
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Swipe Gestures**
- Proportional reveal: icon + color background grow as finger moves
- Swipe right = complete (green background + checkmark icon); swipe left = delete (red background + trash icon)
- On threshold reached: row slides off screen in swipe direction (~200ms), then mutation fires
- Complete: row reappears with strikethrough + 50% opacity after slide-off
- Delete: row removed, subtree deleted (matches server DELETE behavior). No confirmation dialog — undo is sufficient
- Swipe right toggles: swiping right on a completed bullet un-completes it
- Swipe disabled while a bullet is focused (keyboard open) to prevent accidental swipes during editing
- Haptic feedback on threshold reached

**Search UX**
- TopAppBar search icon (magnifying glass) — tapping opens inline search bar replacing the title area (Material 3 SearchBar pattern)
- Back arrow to close search, X button to clear query
- 300ms debounce on input before firing GET /api/search?q=...
- Results grouped by document name — each result shows bullet content with query term highlighted
- Tapping a result: closes search, opens target document, scrolls to matching bullet, brief highlight flash (~1s)
- Tapping a chip (#tag, @mention, !!date) in unfocused bullet opens search pre-filled with that chip's text

**Bookmarks Screen**
- Accessed via "Bookmarks" item in the navigation drawer, positioned above the document list with a separator
- Dedicated screen with list of bookmarked bullets
- Each row shows: bullet content as primary text, document name as secondary text below
- Tapping a bookmarked bullet navigates to it in its document (same behavior as search result tap)
- Bookmark a bullet via long-press context menu on any bullet row → "Bookmark" / "Remove bookmark"
- Long-press context menu (stationary hold, no movement) also includes: "Attachments", "Delete"
- Bookmarked bullets show a small filled star icon next to the bullet dot in the tree view

**Attachments View**
- Images render inline as thumbnails below the bullet content
- Non-image files show as file-type icon + tappable filename + file size below the bullet
- Tapping any attachment triggers system download via Android DownloadManager → open with system file handler (Downloads folder)
- Paperclip icon indicator on bullets that have attachments (similar to StickyNote2 note indicator pattern)
- Long-press context menu "Attachments" item expands/collapses the attachment list inline below the bullet (toggle)
- Image thumbnails lazy-loaded with Coil library, gray placeholder while loading
- Upload deferred to v2.1

**UI Size**
- Increase overall UI size slightly — larger text and touch targets across the app

### Claude's Discretion
- Dark theme implementation (Material 3 system preference, seed color #2563EB — already decided in Phase 9)
- Loading/error states (skeleton shimmer + error-with-retry — pattern from Phases 10-11, apply consistently)
- Pull-to-refresh (Material 3 PullToRefreshBox on document list and bullet tree)
- Animations (AnimatedVisibility, animateItemPlacement, Crossfade — standard Material 3 motion)
- Exact swipe threshold distance and animation curves
- Search bar transition animation details
- Bookmark star icon exact size and positioning
- Coil configuration (cache size, placeholder style)
- Attachment thumbnail dimensions
- Context menu styling and positioning
- Exact UI size increases (font scale, padding adjustments, touch target sizing)

### Deferred Ideas (OUT OF SCOPE)
- Upload attachments from Android — v2.1
- Tag browser sidebar — v2.1
- Inline image rendering in bullet content (markdown images) — v2.1
- Physical keyboard shortcuts (Tab, Ctrl+arrows) — v2.1
- Export document(s) as Markdown — v2.1
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| CONT-03 | User can view and download file attachments on bullets | Coil 3 for image thumbnails; Android DownloadManager + FileProvider for download/open; AttachmentApi (GET /api/attachments/bullets/:bulletId, GET /api/attachments/:id/download) |
| CONT-04 | User can view bookmarked bullets in a dedicated screen | BookmarksScreen with Navigation3 route; BookmarkApi (GET /api/bookmarks, POST /api/bookmarks, DELETE /api/bookmarks/:bulletId); long-press DropdownMenu in BulletRow |
| POLL-01 | Loading and error states on all screens | ShimmerBulletRows pattern already in BulletTreeScreen; apply to BookmarksScreen; MainScreen already has error+retry |
| POLL-02 | Pull-to-refresh on document and bullet views | PullToRefreshBox wrapping LazyColumn in BulletTreeScreen and DocumentDrawerContent |
| POLL-03 | Optimistic updates with rollback on failure | Already implemented for create/delete/move; extend to toggleComplete (swipe right) |
| POLL-04 | Swipe right to complete bullet, swipe left to delete | SwipeToDismissBox with state.progress for proportional color/icon; confirmValueChange for bidirectional behavior; disable when isFocused |
| POLL-05 | Search across documents and bullets with debounce | SearchApi (GET /api/search?q=); MutableSharedFlow+debounce(300ms) reusing content debounce pattern; SearchBar composable |
| POLL-06 | Undo/redo (server-side, 50 levels) | Already wired in BulletTreeViewModel (undo/redo use cases exist); toolbar buttons exist; this req is effectively satisfied — verify UI is connected |
| POLL-07 | Material 3 dark theme | DarkColorScheme already defined in Color.kt; Theme.kt already uses isSystemInDarkTheme(); this req is effectively satisfied — verify all screens use NotesTheme |
| POLL-08 | Material 3 animations (AnimatedVisibility, animateItemPlacement, Crossfade) | Already used in BulletTreeScreen (AnimatedVisibility for breadcrumb/toolbar, animateItem on LazyColumn items); add Crossfade for screen transitions and search bar |
</phase_requirements>

---

## Summary

Phase 12 is a polish and completion phase — no new backend features are needed, all APIs exist. The work is primarily Compose UI: wrapping BulletRow in SwipeToDismissBox, wiring the search UI with the existing search API, adding a BookmarksScreen, showing attachment lists, applying PullToRefreshBox, and verifying that dark theme and animations are consistently applied.

The codebase entering Phase 12 is already well-structured. BulletTreeViewModel has the operation queue, debounce pattern, optimistic updates, and undo/redo. The dark theme (DarkColorScheme) and basic animations (AnimatedVisibility, animateItem) are already in place. The main work is adding new Compose composables and wiring two new ViewModels (SearchViewModel, BookmarksViewModel) with their corresponding use cases, repositories, and Retrofit API interfaces.

The highest-complexity areas are: (1) swipe gestures — proportional color/icon reveal with confirmValueChange bidirectional handling while correctly disabling during focus; (2) search — inline SearchBar replacing the TopAppBar title with scroll-to-bullet behavior; (3) attachments — Coil image thumbnails with DownloadManager for non-image files.

**Primary recommendation:** Build in wave order: data layer (new APIs, models, use cases) → BulletRow enhancements (swipe, context menu, attachment list, bookmark indicator) → new screens (BookmarksScreen) → search wiring → pull-to-refresh → UI size tweaks.

---

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| Compose BOM | 2025.02.00 | All Compose libraries versioned together | In use |
| Material3 | (BOM-managed) | SwipeToDismissBox, PullToRefreshBox, SearchBar | In use |
| Hilt | 2.56.1 | DI — add new APIs/repositories/use cases | In use |
| Retrofit 3.0.0 + OkHttp 4.12.0 | locked | HTTP — add SearchApi, BookmarkApi, AttachmentApi | In use |
| kotlinx-coroutines | 1.10.1 | Debounce, StateFlow, SharedFlow | In use |
| Reorderable | 3.0.0 | Drag-reorder in LazyColumn | In use |

### New Dependencies Required
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| io.coil-kt.coil3:coil-compose | 3.4.0 | Async image loading for attachment thumbnails | Official Compose image loader, lazy-load + placeholder support |
| io.coil-kt.coil3:coil-network-okhttp | 3.4.0 | Network backend for Coil using existing OkHttpClient | Reuses auth-intercepted OkHttp instance for attachment URLs |

**Installation:**
```kotlin
// libs.versions.toml
[versions]
coil = "3.4.0"

[libraries]
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

// build.gradle.kts
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
```

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Coil 3 | Glide / Picasso | Coil is Kotlin-first, Compose-native, supports BOM BOM-managed path; Glide requires more boilerplate in Compose |
| DownloadManager | Custom OkHttp download | DownloadManager is system-integrated (notifications, Downloads folder, system viewer); custom approach requires more code for no user benefit |
| SwipeToDismissBox | Custom pointerInput swipe | SwipeToDismissBox handles snap physics, velocity, threshold; custom approach re-implements all of that |
| MutableSharedFlow debounce (search) | Timer/Handler | Already established pattern in this codebase (content/note debounce) — consistency matters |

---

## Architecture Patterns

### New Files Required
```
data/
├── api/
│   ├── SearchApi.kt            # GET /api/search?q=
│   ├── BookmarkApi.kt          # GET/POST/DELETE /api/bookmarks
│   └── AttachmentApi.kt        # GET /api/attachments/bullets/:id, GET /api/attachments/:id/download
├── model/
│   ├── SearchResultDto.kt      # { bulletId, content, documentId, documentTitle }
│   ├── BookmarkDto.kt          # { id, bulletId, bulletContent, documentId, documentTitle }
│   └── AttachmentDto.kt        # { id, bulletId, filename, mimeType, size, url }
└── repository/
    ├── SearchRepository.kt
    ├── BookmarkRepository.kt
    └── AttachmentRepository.kt

domain/
├── model/
│   ├── SearchResult.kt
│   ├── Bookmark.kt
│   └── Attachment.kt
├── repository/
│   ├── SearchRepository.kt     # interface
│   ├── BookmarkRepository.kt   # interface
│   └── AttachmentRepository.kt # interface
└── usecase/
    ├── SearchBulletsUseCase.kt
    ├── GetBookmarksUseCase.kt
    ├── AddBookmarkUseCase.kt
    ├── RemoveBookmarkUseCase.kt
    ├── GetAttachmentsUseCase.kt
    └── ToggleCompleteUseCase.kt   # reuses PatchBulletUseCase internally

presentation/
├── bullet/
│   └── BulletRow.kt            # add swipe wrapper, context menu, attachment list, bookmark indicator, chip click
├── bookmarks/
│   ├── BookmarksScreen.kt
│   ├── BookmarksViewModel.kt
│   └── BookmarksUiState.kt
├── search/
│   ├── SearchViewModel.kt      # debounce flow + search state
│   └── SearchResultItem.kt     # composable for a single search result row
└── main/
    ├── MainScreen.kt           # add SearchBar, search icon in TopAppBar, bookmarks drawer entry
    └── DocumentDrawerContent.kt # add bookmarks nav item + separator above document list
```

### Pattern 1: SwipeToDismissBox with Proportional Background

**What:** Wrap each BulletRow in SwipeToDismissBox. Use `state.progress` for proportional color/icon fill. Use `confirmValueChange` to wire complete vs delete.

**When to use:** For all bullet rows where `!isFocused` (swipe disabled when keyboard is open).

```kotlin
// Source: https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss
val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> {
                // Swipe right = toggle complete
                onToggleComplete()
                false  // row stays, content updates via optimistic update
            }
            SwipeToDismissBoxValue.EndToStart -> {
                // Swipe left = delete
                onDelete()
                true   // triggers slide-off animation
            }
            SwipeToDismissBoxValue.Settled -> false
        }
    }
)

SwipeToDismissBox(
    state = dismissState,
    enableDismissFromStartToEnd = !isFocused,
    enableDismissFromEndToStart = !isFocused,
    backgroundContent = {
        val direction = dismissState.dismissDirection
        val progress = dismissState.progress
        val backgroundColor = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> lerp(Color.Transparent, Color(0xFF22C55E), progress)
            SwipeToDismissBoxValue.EndToStart -> lerp(Color.Transparent, Color(0xFFEF4444), progress)
            else -> Color.Transparent
        }
        val icon = when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
            SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
            else -> null
        }
        Box(
            modifier = Modifier.fillMaxSize().background(backgroundColor),
            contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd)
                Alignment.CenterStart else Alignment.CenterEnd
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = progress.coerceIn(0f, 1f)),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
) {
    BulletRow(/* ... */)
}
```

**Haptic feedback on threshold:** `progress >= 1.0f` → `view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)`. Track with a `var hapticFired` flag reset when progress drops back below threshold.

### Pattern 2: Inline Search Bar in TopAppBar

**What:** TopAppBar actions slot shows a search icon in normal mode. When tapped, `isSearchActive` state switches the title slot to a `BasicTextField`-based search input (or Material3 `DockedSearchBar`). Back press / back icon closes search.

**Decision from CONTEXT.md:** Material 3 SearchBar pattern replacing title area. The cleanest approach for this codebase is a state-driven swap in `MainScreen.kt`:

```kotlin
// In MainScreen.kt
var isSearchActive by remember { mutableStateOf(false) }
var searchQuery by remember { mutableStateOf("") }

TopAppBar(
    title = {
        if (isSearchActive) {
            // Search input replacing title
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; searchViewModel.onQueryChange(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
            )
        } else {
            Text(appBarTitle)
        }
    },
    navigationIcon = {
        if (isSearchActive) {
            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                Icon(Icons.Default.ArrowBack, "Close search")
            }
        } else {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Default.Menu, "Open drawer")
            }
        }
    },
    actions = {
        if (isSearchActive) {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = ""; searchViewModel.onQueryChange("") }) {
                    Icon(Icons.Default.Close, "Clear")
                }
            }
        } else {
            IconButton(onClick = { isSearchActive = true }) {
                Icon(Icons.Default.Search, "Search")
            }
            // existing MoreVert menu...
        }
    }
)
```

**Search debounce (SearchViewModel):**
```kotlin
private val queryFlow = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)

init {
    viewModelScope.launch {
        queryFlow.debounce(300).collect { query ->
            if (query.isBlank()) {
                _uiState.value = SearchUiState.Empty
            } else {
                _uiState.value = SearchUiState.Loading
                searchBulletsUseCase(query).fold(
                    onSuccess = { results -> _uiState.value = SearchUiState.Success(results) },
                    onFailure = { _uiState.value = SearchUiState.Error }
                )
            }
        }
    }
}

fun onQueryChange(query: String) {
    viewModelScope.launch { queryFlow.emit(query) }
}
```

### Pattern 3: Pull-to-Refresh

**What:** Wrap LazyColumn in PullToRefreshBox. Add `isRefreshing` StateFlow in ViewModel. PullToRefreshBox handles indicator rendering.

```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/pull-to-refresh
@OptIn(ExperimentalMaterial3Api::class)
PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = { viewModel.refresh() }
) {
    LazyColumn(state = lazyListState) { /* items */ }
}
```

Apply to: `BulletTreeScreen` (wraps the bullet LazyColumn), `DocumentDrawerContent` (wraps the document LazyColumn). Both need a `refresh()` method on their respective ViewModels that sets `_isRefreshing = true`, calls load, then sets `false`.

### Pattern 4: Long-Press Context Menu

**What:** Use `combinedClickable` modifier on the BulletRow's non-focused content area. On long press (stationary hold), show a `DropdownMenu` anchored to the bullet row. Menu items: Bookmark/Remove bookmark, Attachments (toggle), Delete.

```kotlin
// In BulletRow — replace current clickable with combinedClickable
var showContextMenu by remember { mutableStateOf(false) }

Box(
    modifier = Modifier
        .weight(1f)
        .combinedClickable(
            onClick = { if (!isFocused) onFocusRequest() },
            onLongClick = { if (!isFocused) showContextMenu = true }
        )
) {
    // existing content rendering...

    DropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { showContextMenu = false }
    ) {
        DropdownMenuItem(
            text = { Text(if (isBookmarked) "Remove bookmark" else "Bookmark") },
            onClick = { onToggleBookmark(); showContextMenu = false }
        )
        DropdownMenuItem(
            text = { Text("Attachments") },
            onClick = { onToggleAttachments(); showContextMenu = false }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = { onDelete(); showContextMenu = false }
        )
    }
}
```

**Note:** `combinedClickable` requires `androidx.compose.foundation:foundation` which is already in the BOM.

### Pattern 5: Attachment List (Coil + DownloadManager)

**What:** Below bullet content, an animated expandable list of attachments. Images use `AsyncImage` from Coil. Non-images show icon + filename + size. Tapping triggers DownloadManager.

```kotlin
// Image attachment
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(attachment.url)
        .crossfade(true)
        .build(),
    contentDescription = attachment.filename,
    placeholder = painterResource(android.R.color.darker_gray),
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 200.dp)
        .clip(MaterialTheme.shapes.small)
        .clickable { downloadAttachment(context, attachment) }
)

// DownloadManager trigger
fun downloadAttachment(context: Context, attachment: AttachmentDto) {
    val request = DownloadManager.Request(Uri.parse(attachment.url))
        .setTitle(attachment.filename)
        .setDescription("Downloading...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, attachment.filename)
        .addRequestHeader("Authorization", "Bearer $token")
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}
```

**Coil singleton setup in NetworkModule:**
```kotlin
// Configure Coil to reuse the auth-intercepted OkHttpClient
val imageLoader = ImageLoader.Builder(context)
    .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
    .build()
```

**Note on DownloadManager + auth token:** The download URL (`/api/attachments/:id/download`) requires the Bearer token. DownloadManager does NOT use OkHttp, so the Authorization header must be added manually via `addRequestHeader`. The token must be retrieved from `DataStore` / `AuthRepository` at the call site.

### Pattern 6: Navigation3 Route for BookmarksScreen

Add a new `BookmarksRoute` to `NavRoutes.kt`:

```kotlin
@Serializable
object BookmarksRoute : NavKey
```

Add entry in `NotesApp.kt`. OR: render BookmarksScreen inline in `MainScreen.kt` similar to how `BulletTreeScreen` is conditionally shown based on `openDocumentId` — controlled by a `showBookmarks` flag in `MainViewModel`.

**Recommendation:** Keep the existing single-screen-with-drawer architecture (per CONTEXT.md specifics). Add `showBookmarks: Boolean` to `MainUiState.Success`. When `showBookmarks == true`, render `BookmarksScreen` in the content area instead of `BulletTreeScreen`. Tapping a bookmark result calls `viewModel.openDocument(docId)` and sets `showBookmarks = false`.

### Pattern 7: Bookmark Indicator in BulletRow

Small filled star icon next to the bullet dot. Added to BulletRow similarly to the existing note indicator (StickyNote2):

```kotlin
// In BulletRow — alongside the note indicator Spacer/Icon
if (isBookmarked) {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = "Bookmarked",
        modifier = Modifier.size(12.dp),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    )
}
```

The `isBookmarked` flag must be passed into `BulletRow` as a parameter. `BulletTreeViewModel` needs to hold the set of bookmarked bullet IDs (fetched from `GET /api/bookmarks` on load) and expose it as a `StateFlow<Set<String>>`.

### Anti-Patterns to Avoid

- **Nesting SwipeToDismissBox inside ReorderableItem while a long-press drag is active:** Gesture conflict. The swipe handler must be disabled when a drag is in progress (`isDragging`). Also disable when `isFocused` (keyboard open).
- **Fetching attachments for every visible bullet on load:** Attachment list is fetched lazily — only when the user taps "Attachments" in the context menu for a specific bullet. Do not pre-fetch all attachments.
- **Hardcoding the auth token for DownloadManager:** Token must be read from `DataStore` at download time (it may have been refreshed). Pass via Hilt or pass the `AuthRepository` reference to the use case.
- **Building a custom swipe gesture with pointerInput:** SwipeToDismissBox already handles velocity, snap physics, and threshold detection. Only use pointerInput for detecting the drag offset for BulletRow's existing indent/reparent drag (which is handled by Reorderable).
- **Creating a separate LazyColumn state for search results inside MainScreen:** Use a separate `SearchViewModel` scoped to `MainScreen`. The ViewModel handles lifecycle correctly with Hilt.
- **Re-using `BulletTreeViewModel` for toggle-complete during swipe:** Add a `toggleComplete(bulletId)` method to `BulletTreeViewModel` using the existing `enqueue` + optimistic update pattern. Call `PATCH /api/bullets/:id` with `{ isComplete: !current }`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Swipe gesture + snap physics | Custom pointerInput swipe | `SwipeToDismissBox` (Material3) | Handles velocity, threshold, bidirectional, state machine |
| Proportional color lerp | Manual interpolation | `lerp()` from `androidx.compose.ui.graphics.lerp` | Already imported, correct color space |
| Image loading + caching + placeholder | Custom Retrofit image fetch | Coil 3 `AsyncImage` | Cache management, cancelation on recomposition, placeholder/error states |
| Pull gesture detection | Custom NestedScrollConnection | `PullToRefreshBox` (Material3) | Handles nested scroll conflicts, indicators, state |
| Search debounce | Handler/Timer | `MutableSharedFlow.debounce()` | Already used for content/note in this codebase — consistent |
| File download with system notification | Custom download service | `DownloadManager` (system) | OS-managed, shows in notification tray, saves to Downloads, opens with system viewer |

**Key insight:** All the hard UI interaction problems in Phase 12 have first-class Material3 / AndroidX solutions. Custom implementations add bugs, not value.

---

## Common Pitfalls

### Pitfall 1: SwipeToDismissBox Gesture Conflict with Reorderable
**What goes wrong:** Long-press drag for reorder and horizontal swipe for dismiss both start from a touch-down event. If SwipeToDismissBox wraps ReorderableItem, the drag gesture detector may conflict with swipe detection.
**Why it happens:** Both gesture recognizers compete for the same pointer events.
**How to avoid:** Wrap in the correct order — `ReorderableItem` must be the outermost Compose wrapper (it owns the drag handle modifier). `SwipeToDismissBox` goes inside `ReorderableItem`, wrapping only the visible BulletRow content. Pass `gesturesEnabled = !isDragging` to disable swipe while a drag is active.
**Warning signs:** Swipe starts a drag, or drag starts a swipe — test both gestures independently after implementation.

### Pitfall 2: confirmValueChange Returns True for Complete → Row Disappears
**What goes wrong:** Returning `true` from `confirmValueChange` for `StartToEnd` triggers the dismiss animation, removing the row from the LazyColumn before the API call fires.
**Why it happens:** `true` = "dismiss this item" — the LazyColumn item animates out.
**How to avoid:** Return `false` for complete (StartToEnd). The optimistic update in `toggleComplete()` updates `isComplete` in the flat list, recomposing the BulletRow with strikethrough. The dismiss state resets to Settled.
**Warning signs:** Completed bullets disappear from view instead of showing strikethrough.

### Pitfall 3: DownloadManager Auth Headers Expire
**What goes wrong:** The JWT access token stored in the header at download time expires mid-download.
**Why it happens:** DownloadManager is a system service — it does not use OkHttp or the TokenAuthenticator.
**How to avoid:** Access tokens typically have 15-minute lifetimes. For downloads, fetch the current access token from `AuthRepository.getAccessToken()` at enqueue time. If the download fails with 401, DownloadManager will retry once — this is usually sufficient for the short attachment download window.
**Warning signs:** 401 error in DownloadManager notification.

### Pitfall 4: PullToRefreshBox Inside imePadding Causes Visual Glitch
**What goes wrong:** When the keyboard is visible, `imePadding` shifts content up. If `PullToRefreshBox` is outside the `imePadding` modifier, the refresh indicator may appear below the keyboard.
**Why it happens:** Modifier order in Compose changes layout bounds.
**How to avoid:** Apply `imePadding` to the Column/Box that wraps the LazyColumn, not to PullToRefreshBox. Structure: `Column(imePadding) { PullToRefreshBox { LazyColumn } Toolbar }`.
**Warning signs:** Pull indicator appears in the wrong position when keyboard is open.

### Pitfall 5: Coil Auth Headers for Protected Image URLs
**What goes wrong:** Attachment images behind `/api/attachments/...` return 401 because Coil uses its own OkHttpClient without the AuthInterceptor.
**Why it happens:** Coil creates its own singleton OkHttpClient by default.
**How to avoid:** Configure Coil's ImageLoader in `NetworkModule` or `NotesApplication.onCreate()` to use the same OkHttpClient already wired with `AuthInterceptor`:
```kotlin
val imageLoader = ImageLoader.Builder(context)
    .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
    .build()
Coil.setImageLoader(imageLoader)
```
**Warning signs:** Images show error placeholder instead of thumbnail for private URLs.

### Pitfall 6: Search Results Navigate to Document But Bullet Not Scrolled Into View
**What goes wrong:** Tapping a search result opens the correct document, but the matched bullet is not visible — the LazyColumn starts at position 0.
**Why it happens:** LazyColumn doesn't know which bullet to scroll to unless explicitly told.
**How to avoid:** After `viewModel.openDocument(docId)`, pass the target `bulletId` as a scroll target to `BulletTreeViewModel`. Use `lazyListState.scrollToItem(index)` or `animateScrollToItem(index)` after the document loads. Store `pendingScrollToBulletId` in `BulletTreeViewModel`, consume it once in `LaunchedEffect(state.flatList)` after load.
**Warning signs:** Correct document opens but user has to manually scroll to find the matched bullet.

### Pitfall 7: BookmarksViewModel Scoped Incorrectly
**What goes wrong:** If `BookmarksViewModel` is `hiltViewModel()` inside `BookmarksScreen`, it will not survive when the screen is swapped out and back in via `MainScreen`'s `showBookmarks` flag.
**Why it happens:** Composable scope + Hilt ViewModel lifecycle — ViewModel is cleared when the composable leaves composition.
**How to avoid:** Scope `BookmarksViewModel` to `MainScreen`'s `hiltViewModel` scope, or use `rememberSaveable`-based navigation so ViewModel survives. Simplest: pass it as a parameter from `MainScreen` which holds the Hilt-provided instance.
**Warning signs:** Bookmarks list reloads every time the screen is toggled.

---

## Code Examples

### Verified: SwipeToDismissBox Progress-Based Color
```kotlin
// Source: https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss
val progress = dismissState.progress
val backgroundColor = when (dismissState.dismissDirection) {
    SwipeToDismissBoxValue.StartToEnd -> lerp(Color.Transparent, Color(0xFF22C55E), progress)
    SwipeToDismissBoxValue.EndToStart -> lerp(Color.Transparent, Color(0xFFEF4444), progress)
    else -> Color.Transparent
}
```

### Verified: PullToRefreshBox
```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/pull-to-refresh
@OptIn(ExperimentalMaterial3Api::class)
PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = { viewModel.refresh() }
) {
    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
        items(flatList, key = { it.bullet.id }) { ... }
    }
}
```

### Verified: Coil AsyncImage with placeholder
```kotlin
// Source: https://coil-kt.github.io/coil/compose/
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .crossfade(true)
        .build(),
    contentDescription = filename,
    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 200.dp)
        .clip(RoundedCornerShape(8.dp))
)
```

### Verified: ToggleComplete in BulletTreeViewModel (new method)
```kotlin
// Follows existing deleteBullet() pattern
fun toggleComplete(bulletId: String) {
    val bullets = currentBullets()
    val target = bullets.find { it.id == bulletId } ?: return
    val newComplete = !target.isComplete

    // Optimistic update
    val optimistic = bullets.map { b ->
        if (b.id == bulletId) b.copy(isComplete = newComplete) else b
    }
    updateState(optimistic)

    enqueue {
        patchBulletUseCase(bulletId, PatchBulletRequest.updateIsComplete(newComplete))
            .onFailure {
                _snackbarMessage.emit("Failed to update bullet")
                reloadFromServer()
            }
    }
}
```

**Note:** `PatchBulletRequest.updateIsComplete()` factory method needs to be added following the same pattern as `updateContent()`, `updateNote()`, `updateIsCollapsed()`.

### Verified: BookmarksRoute (Navigation3)
```kotlin
// In NavRoutes.kt
@Serializable
object BookmarksRoute : NavKey

// In NotesApp.kt entry provider
entry<BookmarksRoute> {
    BookmarksScreen(
        onBulletClick = { docId, bulletId ->
            backStack.add(MainRoute)
            // pass scroll target via MainViewModel or saved state
        }
    )
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `SwipeToDismiss` (Material) | `SwipeToDismissBox` (Material3) | Compose Material3 migration | Different import, `confirmValueChange` deprecated in favor of `onDismissed` in newer versions — use `confirmValueChange` for now (stable API in BOM 2025.02) |
| `rememberPullRefreshState` + `PullRefreshIndicator` | `PullToRefreshBox` | Material3 1.3+ | Simpler API, single composable wrapping scrollable content |
| `SearchView` (XML) | `SearchBar` / `DockedSearchBar` (Compose M3) | Compose Material3 | Separated collapsed (SearchBar) and expanded (ExpandedFullScreenSearchBar / ExpandedDockedSearchBar) in newer releases |
| Coil 2.x (`io.coil-kt:coil-compose`) | Coil 3.x (`io.coil-kt.coil3:coil-compose`) | Coil 3.0 (2024) | Different artifact group (`coil-kt.coil3`), Kotlin Multiplatform-ready, requires explicit network fetcher module |

**Deprecated/outdated:**
- `confirmValueChange` on `SwipeToDismissBoxState` is deprecated in Compose Material3 HEAD (>BOM 2025.02) in favor of `onDismissed` callback — but BOM 2025.02.00 (what this project uses) still uses `confirmValueChange` as the stable API. Do NOT upgrade the BOM mid-phase.
- `io.coil-kt:coil-compose` (Coil 2) — use `io.coil-kt.coil3:coil-compose` (Coil 3) instead.

---

## Open Questions

1. **Search API response shape**
   - What we know: `GET /api/search?q=` exists on the backend (confirmed in CONTEXT.md)
   - What's unclear: Exact JSON response shape (fields returned per result)
   - Recommendation: Check backend search route in `/root/notes/server/` before writing `SearchResultDto`. Likely: `{ id, content, documentId, documentTitle }` — verify before coding the data model.

2. **Attachment download URL — absolute or relative?**
   - What we know: `GET /api/attachments/:id/download` returns the file
   - What's unclear: Whether `AttachmentDto.url` is an absolute URL or just an ID that must be prepended with base URL
   - Recommendation: Check backend `/api/attachments` route response. If relative, construct the full URL in the repository using the Retrofit base URL.

3. **PatchBulletRequest.updateIsComplete factory method**
   - What we know: `PatchBulletRequest` has factory methods for content, note, isCollapsed — the pattern is established
   - What's unclear: Whether `isComplete` field is already defined in the companion object or needs to be added
   - Recommendation: Check `PatchBulletRequest.kt` and add `fun updateIsComplete(value: Boolean)` if missing.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + MockK 1.13.14 + Coroutines Test 1.10.1 |
| Config file | `android/app/src/test/` (JVM unit tests) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "com.gmaingret.notes.presentation.bullet.*" -x lint` |
| Full suite command | `./gradlew :app:testDebugUnitTest -x lint` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CONT-03 | Attachment API data model mapping | unit | `./gradlew :app:testDebugUnitTest --tests "*AttachmentRepositoryTest*"` | Wave 0 |
| CONT-04 | BookmarksViewModel loads bookmarks, optimistic toggle | unit | `./gradlew :app:testDebugUnitTest --tests "*BookmarksViewModelTest*"` | Wave 0 |
| POLL-02 | BulletTreeViewModel.refresh() sets isRefreshing then clears | unit | `./gradlew :app:testDebugUnitTest --tests "*BulletTreeViewModelTest*"` | ✅ (extend existing) |
| POLL-03 | toggleComplete optimistic + rollback on failure | unit | `./gradlew :app:testDebugUnitTest --tests "*BulletTreeViewModelTest*"` | ✅ (extend existing) |
| POLL-04 | SwipeToDismissBox enabled/disabled based on isFocused | manual | n/a — gesture UI, not unit-testable | manual only |
| POLL-05 | SearchViewModel debounce fires after 300ms, not before | unit | `./gradlew :app:testDebugUnitTest --tests "*SearchViewModelTest*"` | Wave 0 |
| POLL-06 | Undo/redo already wired | unit | `./gradlew :app:testDebugUnitTest --tests "*BulletTreeViewModelTest*"` | ✅ (existing) |
| POLL-07 | Dark theme colors defined | unit | manual visual check | manual only |
| POLL-08 | AnimatedVisibility / animateItem in use | manual | n/a — animation not unit-testable | manual only |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest -x lint`
- **Per wave merge:** `./gradlew :app:testDebugUnitTest -x lint` (full JVM suite)
- **Phase gate:** Full JVM suite green + manual swipe/search/attachment smoke test on device before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/.../data/repository/AttachmentRepositoryTest.kt` — covers CONT-03 data mapping
- [ ] `app/src/test/.../presentation/bookmarks/BookmarksViewModelTest.kt` — covers CONT-04 ViewModel
- [ ] `app/src/test/.../presentation/search/SearchViewModelTest.kt` — covers POLL-05 debounce behavior

---

## Sources

### Primary (HIGH confidence)
- Official Android Docs: [Swipe to dismiss](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss) — SwipeToDismissBox API, confirmValueChange, progress-based color
- Official Android Docs: [Pull to refresh](https://developer.android.com/develop/ui/compose/components/pull-to-refresh) — PullToRefreshBox signature and usage
- Official Coil docs: [coil-kt.github.io/coil/compose](https://coil-kt.github.io/coil/compose/) — AsyncImage, OkHttp integration, Coil 3 artifacts
- Official Android Docs: [DownloadManager](https://developer.android.com/reference/kotlin/android/app/DownloadManager) — request setup, auth headers
- Project codebase: `BulletTreeViewModel.kt`, `BulletRow.kt`, `BulletTreeScreen.kt`, `MainScreen.kt`, `Color.kt`, `Theme.kt` — all read directly

### Secondary (MEDIUM confidence)
- [composables.com/material3/swipetodismissbox](https://composables.com/material3/swipetodismissbox) — verified against official Android docs
- [composables.com/material3/pulltorefreshbox](https://composables.com/material3/pulltorefreshbox) — verified against official Android docs
- [Coil Maven Central: coil3:coil-compose 3.4.0](https://central.sonatype.com/artifact/io.coil-kt.coil3/coil-compose) — version confirmed

### Tertiary (LOW confidence)
- None — all critical claims verified against official sources

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries verified against official docs and Maven Central
- Architecture patterns: HIGH — based on direct codebase reading + official API docs
- Pitfalls: HIGH (gesture conflicts, Coil auth) — verified against official docs; MEDIUM (DownloadManager token expiry) — inferred from DownloadManager behavior
- New dependencies (Coil 3): HIGH — version 3.4.0 confirmed on Maven Central

**Research date:** 2026-03-12
**Valid until:** 2026-04-12 (stable APIs — Compose BOM and Coil are unlikely to change within 30 days)
