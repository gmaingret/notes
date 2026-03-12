# Phase 10: Document Management - Research

**Researched:** 2026-03-12
**Domain:** Android Jetpack Compose — ModalNavigationDrawer, document CRUD, drag-reorder, DataStore persistence
**Confidence:** HIGH

## Summary

Phase 10 is a pure Android UI phase: no backend changes are required. All six document API endpoints (`GET /api/documents`, `POST /api/documents`, `PATCH /:id`, `PATCH /:id/position`, `POST /:id/open`, `DELETE /:id`) are verified working on the production server. The work is wiring those endpoints into a proper Retrofit `DocumentApi`, a clean-architecture `DocumentRepository`, and a fully functional `MainScreen` with `ModalNavigationDrawer`.

The dominant technical decision is drag-reorder. The Calvin-LL/Reorderable library (`sh.calvin.reorderable:reorderable:3.0.0`) is the correct choice: it uses `Modifier.animateItem`, supports long-press drag handles, provides `LocalHapticFeedback` integration hooks, and handles edge auto-scroll. The library aligns with the concern flagged in STATE.md ("confirm Calvin-LL/Reorderable library fits flat-tree model before committing") — this phase uses a flat document list, which is exactly the library's primary use case.

DataStore is already in the project (version 1.2.1, encrypted via Tink). Adding `lastDocId` storage follows the exact same pattern as `KEY_ACCESS_TOKEN` / `KEY_USER_EMAIL` in `TokenStore.kt`. No new infrastructure is needed — extend `TokenStore` with a new plain (unencrypted, low-sensitivity) key, or add a separate `AppPreferencesStore`.

**Primary recommendation:** Use Calvin-LL/Reorderable 3.0.0 for drag-reorder; extend the existing DataStore for `lastDocId`; follow the AuthRepository/AuthApi pattern exactly for DocumentApi/DocumentRepository; wrap everything in a single `MainViewModel` extended with document state.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- ModalNavigationDrawer (slides over content with scrim overlay)
- Drawer opens via hamburger icon tap ONLY — left-edge swipe gesture DISABLED (`gesturesEnabled = false`)
- Auto-closes when user taps a document
- Header: "Notes" title
- Footer: "+ New document" button, pinned at bottom (does not scroll with list)
- Logout stays in TopAppBar overflow menu (not in drawer)
- Document row: title only, no dates, no bullet counts
- Selected document highlighted with filled background tint (Material 3 NavigationDrawerItem primaryContainer style)
- Trailing "more_vert" icon on each row opens context menu (Rename / Delete)
- TopAppBar title shows currently selected document name; tapping title does nothing
- Hamburger icon on the left, overflow "more_vert" on the right
- Create: tap "+ New document" in drawer footer; new document appears at end with active inline TextField showing "Untitled" (pre-selected)
- Rename: tap "more_vert" -> context menu -> "Rename"; title becomes inline editable TextField; done on keyboard submits
- Delete: tap "more_vert" -> context menu -> "Delete"; AlertDialog "Delete [doc name]? This will delete all bullets in this document." with Cancel / Delete
- If deleting the currently open document: auto-open next document in list (or previous if last); if no docs remain, show empty state
- Drag: long-press anywhere on row to initiate drag; haptic feedback (HapticFeedbackConstants.LONG_PRESS) fires on lift
- Drag visual: elevation shadow + 1.02x scale; other items animate to make room; auto-scroll at edges
- Any drop position commits (no cancel); optimistic update with revert + Snackbar on failure
- Backend drag API: PATCH /:id/position with `afterId` (UUID or null for first position)
- Empty state: centered "No documents yet" + "+ Create document" button
- Loading: skeleton shimmer rows (3-4 rows) in drawer; CircularProgressIndicator in content area
- Error: "Couldn't load documents" + Retry button in drawer
- Content area placeholder: document title as heading + subtle "content area" text
- Document list refreshes on every drawer open; no caching
- Cold start: splash -> token refresh -> read lastDocId from DataStore -> MainScreen -> fetch list + auto-open lastDocId
- Last-opened: save lastDocId to DataStore; on cold start auto-open; if 404 fall back to first doc; if none, empty state
- Call POST /:id/open on document select

### Claude's Discretion

- Exact spacing, padding, and typography in drawer
- Skeleton shimmer implementation details
- ViewModel state management structure (single vs split ViewModels)
- Drag-and-drop library choice or custom implementation
- Exact animation curves and durations
- How inline rename TextField handles keyboard dismiss vs confirm

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DOCM-01 | User can view document list in ModalNavigationDrawer | Material3 ModalNavigationDrawer + NavigationDrawerItem API verified; gesturesEnabled=false confirmed as the disable-swipe mechanism |
| DOCM-02 | User can create a new document | POST /api/documents confirmed; inline TextField with ImeAction.Done confirmed pattern; DocumentApi follows AuthApi structure |
| DOCM-03 | User can rename a document | PATCH /api/documents/:id confirmed; same inline TextField + ImeAction.Done pattern; DropdownMenu context menu from MoreVert icon confirmed |
| DOCM-04 | User can delete a document | DELETE /api/documents/:id returns 204; AlertDialog pattern is standard Compose Material3; auto-open next/previous logic is pure ViewModel |
| DOCM-05 | User can drag-reorder documents in the drawer | Calvin-LL/Reorderable 3.0.0 confirmed for LazyColumn; PATCH /:id/position with afterId confirmed in server; optimistic update pattern documented |
| DOCM-06 | App remembers and re-opens last viewed document | DataStore already in project (1.2.1); add lastDocId key; POST /:id/open endpoint confirmed; cold start flow documented |
</phase_requirements>

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Material3 ModalNavigationDrawer | Compose BOM 2025.02.00 | Drawer UI shell | Already in project; Material3 standard drawer component |
| Material3 NavigationDrawerItem | Compose BOM 2025.02.00 | Per-document row with selection highlight | Built-in primaryContainer selected state; handles press ripple |
| Material3 DropdownMenu/MenuItem | Compose BOM 2025.02.00 | Context menu for Rename/Delete | Already used in MainScreen overflow menu |
| Material3 AlertDialog | Compose BOM 2025.02.00 | Delete confirmation | Already available in project |
| sh.calvin.reorderable:reorderable | 3.0.0 | Long-press drag-to-reorder in LazyColumn | Only maintained library using Modifier.animateItem; handles edge auto-scroll; haptic hooks |
| Retrofit3 + Gson | 3.0.0 (project) | DocumentApi interface | Same OkHttpClient — no new network setup needed |
| DataStore Preferences | 1.2.1 (project) | Persist lastDocId | Already in TokenStore; same pattern extension |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| valentinilk/compose-shimmer | 1.3.3 | Skeleton shimmer for loading rows | Use if shimmer discretion chooses a library over custom Box approach |
| LocalHapticFeedback (Compose) | BOM-included | Haptic on drag lift | Built-in; no extra dep needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Calvin-LL/Reorderable | Custom drag implementation | Custom avoids dep but ~300+ lines of touch tracking, offset math, auto-scroll logic — not worth it for a flat list |
| compose-shimmer library | Custom Box with animated alpha | Custom is ~30 lines and no dep, acceptable for this scope |
| Single MainViewModel | Separate DocumentViewModel | Single VM is simpler for this phase; SplashViewModel already separate; split only if VM exceeds ~200 lines |

**Installation (add to libs.versions.toml + build.gradle.kts):**
```toml
# versions
reorderable = "3.0.0"

# libraries
reorderable = { module = "sh.calvin.reorderable:reorderable", version.ref = "reorderable" }
```
```kotlin
// build.gradle.kts dependencies
implementation(libs.reorderable)
```

---

## Architecture Patterns

### Recommended Project Structure
```
data/
├── api/DocumentApi.kt              # Retrofit interface for all 6 endpoints
├── model/DocumentDto.kt            # Data class matching server JSON shape
└── repository/DocumentRepositoryImpl.kt

domain/
├── model/Document.kt               # Domain model (id, title, position)
├── repository/DocumentRepository.kt
└── usecase/
    ├── GetDocumentsUseCase.kt
    ├── CreateDocumentUseCase.kt
    ├── RenameDocumentUseCase.kt
    ├── DeleteDocumentUseCase.kt
    ├── ReorderDocumentUseCase.kt
    └── OpenDocumentUseCase.kt

presentation/main/
├── MainScreen.kt                   # Scaffold + ModalNavigationDrawer host
├── MainViewModel.kt                # Owns document list state + open doc state
├── DocumentDrawerContent.kt        # Drawer composable (header, list, footer)
├── DocumentRow.kt                  # Single row: title, selection, context menu
└── MainUiState.kt                  # Sealed state: Loading, Success(docs, openDoc), Error

data/local/TokenStore.kt            # EXTEND with saveLastDocId / getLastDocId
di/NetworkModule.kt                 # ADD provideDocumentApi(retrofit)
di/DataModule.kt                    # ADD bindDocumentRepository
```

### Pattern 1: DocumentApi — follows AuthApi exactly
**What:** Retrofit interface with suspend functions for each document endpoint
**When to use:** All document CRUD and navigation calls
```kotlin
// Source: existing AuthApi.kt pattern
interface DocumentApi {
    @GET("api/documents")
    suspend fun getDocuments(): List<DocumentDto>

    @POST("api/documents")
    suspend fun createDocument(@Body request: CreateDocumentRequest): DocumentDto

    @PATCH("api/documents/{id}")
    suspend fun renameDocument(@Path("id") id: String, @Body request: RenameDocumentRequest): DocumentDto

    @PATCH("api/documents/{id}/position")
    suspend fun reorderDocument(@Path("id") id: String, @Body request: ReorderDocumentRequest): DocumentDto

    @POST("api/documents/{id}/open")
    suspend fun openDocument(@Path("id") id: String): Response<Unit>  // 204 No Content

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String): Response<Unit>  // 204 No Content
}
```

### Pattern 2: MainViewModel document state
**What:** Single ViewModel owns all document state; StateFlow drives Composables
**When to use:** All document-related UI state changes
```kotlin
// Pattern follows existing MainViewModel.kt structure
data class DocumentsState(
    val documents: List<Document> = emptyList(),
    val openDocumentId: String? = null,
    val isLoadingList: Boolean = false,
    val listError: String? = null,
    val inlineEditingDocId: String? = null   // null = no inline edit active
)
```

### Pattern 3: ModalNavigationDrawer with disabled swipe
**What:** Drawer controlled by programmatic state only
**When to use:** This is the locked pattern for this phase
```kotlin
// Source: Material3 docs — gesturesEnabled=false is the disable-swipe API
val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
val scope = rememberCoroutineScope()

ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = false,       // LOCKED: disable left-edge swipe (Phase 12 conflict)
    drawerContent = {
        ModalDrawerSheet {
            DocumentDrawerContent(
                onDocumentClick = { doc ->
                    viewModel.openDocument(doc)
                    scope.launch { drawerState.close() }
                }
            )
        }
    }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(openDocumentTitle) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                    }
                }
            )
        }
    ) { /* content area */ }
}
```

### Pattern 4: Calvin-LL/Reorderable drag-to-reorder
**What:** Long-press drag handle on each row, optimistic local reorder, background API call
**When to use:** Document row drag with haptic on lift
```kotlin
// Source: DeepWiki Calvin-LL/Reorderable LazyList Components
val hapticFeedback = LocalHapticFeedback.current
val lazyListState = rememberLazyListState()
val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
    // Optimistic update — reorder in-memory list immediately
    viewModel.moveDocumentLocally(from.index, to.index)
}

LazyColumn(state = lazyListState) {
    items(documents, key = { it.id }) { doc ->
        ReorderableItem(reorderableState, key = doc.id) { isDragging ->
            DocumentRow(
                document = doc,
                isDragging = isDragging,
                dragModifier = Modifier.longPressDraggableHandle(
                    onDragStarted = {
                        hapticFeedback.performHapticFeedback(
                            HapticFeedbackType.LongPress   // Locked: LONG_PRESS on lift
                        )
                    },
                    onDragStopped = {
                        viewModel.commitReorderToApi(doc.id)   // afterId computed in VM
                    }
                )
            )
        }
    }
}
```

### Pattern 5: Inline editing TextField in document row
**What:** Row toggles between Text display and TextField when `inlineEditingDocId == doc.id`
**When to use:** Create new doc (pre-populated "Untitled" + selected) and Rename action
```kotlin
// Source: Compose TextField IME Action pattern
if (isEditing) {
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = editText,
        onValueChange = { editText = it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { viewModel.submitRename(doc.id, editText) }
        ),
        modifier = Modifier.focusRequester(focusRequester)
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
} else {
    Text(doc.title)
}
```

### Pattern 6: lastDocId in DataStore (extends TokenStore pattern)
**What:** Store/retrieve last-opened document ID in encrypted DataStore
**When to use:** On document open (save) and cold start (read)
```kotlin
// Follows existing TokenStore.kt pattern exactly
// Add to TokenStore:
companion object {
    private val KEY_LAST_DOC_ID = stringPreferencesKey("last_doc_id")
}

suspend fun saveLastDocId(docId: String) {
    context.authTokenDataStore.edit { prefs ->
        prefs[KEY_LAST_DOC_ID] = docId  // plain string, no encryption needed for doc ID
    }
}

suspend fun getLastDocId(): String? =
    context.authTokenDataStore.data.firstOrNull()?.get(KEY_LAST_DOC_ID)
```

### Anti-Patterns to Avoid
- **Caching document list in memory across sessions:** The locked decision is to fetch fresh on every drawer open — do not add a TTL cache or Flow-based document stream.
- **Using NavigationDrawerItem for the document rows:** NavigationDrawerItem has fixed padding/shape assumptions for nav destinations; a custom Row with `Modifier.selectable` gives more control for the inline edit toggle. Use NavigationDrawerItem only if it naturally accommodates the trailing icon.
- **Passing `afterId` based on UI index without validating against actual list:** After optimistic reorder, the list order in UI and in `documents` StateFlow may temporarily diverge. Compute `afterId` from the StateFlow list (post-optimistic-update) not the reorderable library's `from/to` indices directly.
- **Calling POST /:id/open and refreshing the list simultaneously on document select:** Open document and list refresh are independent — open fires when user taps a doc, list refresh fires when drawer opens; do not conflate them.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| LazyColumn drag-to-reorder | Custom touch tracking + offset animation | Calvin-LL/Reorderable 3.0.0 | Auto-scroll at edges, animateItem integration, haptic hooks are ~300 lines of subtle work |
| Drawer swipe disable | Gesture interceptor / custom Modifier | `gesturesEnabled = false` on ModalNavigationDrawer | It's a single parameter — the API exists |
| Snackbar for reorder failure | Custom toast/overlay | ScaffoldMessenger + SnackbarHostState | Already wired via Scaffold in MainScreen |
| IME Done handling | Global key event interceptor | `KeyboardActions(onDone = {...})` on TextField | Standard Compose API, handles all IME variants |

**Key insight:** The backend API is complete. This phase is entirely about wiring existing endpoints to a well-structured ViewModel and a polished drawer UI. The complex parts are drag-reorder (library handles it) and inline edit state (a single `inlineEditingDocId: String?` in ViewModel UiState handles it).

---

## Common Pitfalls

### Pitfall 1: `Response<Unit>` for 204 No Content endpoints
**What goes wrong:** `suspend fun deleteDocument(...)` declared as `Unit` return type causes Retrofit to throw when the server returns 204 with empty body
**Why it happens:** Retrofit's default Gson converter fails on empty body if return type is not `Response<Unit>` or `@HEAD`
**How to avoid:** Declare delete and open endpoints as `suspend fun ...: Response<Unit>` and check `response.isSuccessful` in the repository
**Warning signs:** `MalformedJsonException` or `EOFException` in logcat on delete/open calls

### Pitfall 2: Reorderable key mismatch causes incorrect item animations
**What goes wrong:** Items animate to wrong positions or jump on drag end
**Why it happens:** The `key` in `items(documents, key = { it.id })` must exactly match the `key` in `ReorderableItem(reorderableState, key = doc.id)` — any mismatch causes the library to lose item identity
**How to avoid:** Use the same stable UUID field (document ID) as key in both places
**Warning signs:** Items snapping to wrong positions after drag-drop; duplicated rows briefly appearing

### Pitfall 3: Drawer state not hoisted leads to drawer not opening
**What goes wrong:** `rememberDrawerState` inside `DocumentDrawerContent` composable means hamburger onClick in TopAppBar can't access the state
**Why it happens:** State defined below the Scaffold scope is unreachable from the navigation icon
**How to avoid:** Hoist `drawerState = rememberDrawerState(...)` and `scope = rememberCoroutineScope()` at the `MainScreen` level, above the `ModalNavigationDrawer` call
**Warning signs:** Hamburger button does nothing; drawer never opens

### Pitfall 4: Optimistic reorder state diverges on rapid drags
**What goes wrong:** User drags item A, then quickly drags item B before the first API call returns; second drag computes `afterId` from the stale pre-first-drag list
**Why it happens:** `documents` StateFlow updated optimistically for first drag, but second drag's `onDragStopped` fires before the first PATCH response
**How to avoid:** Compute `afterId` at the time `onDragStopped` fires from the current (already-optimistically-updated) StateFlow list; the API calls can be queued in sequence via `viewModelScope.launch { } ` — they don't need to be serialized, the server handles concurrent position writes
**Warning signs:** Reorder appears correct visually but reverts after second drag

### Pitfall 5: `FocusRequester.requestFocus()` called before TextField is in composition
**What goes wrong:** `IllegalStateException: FocusRequester is not initialized` when switching a row to inline edit mode
**Why it happens:** `LaunchedEffect(Unit)` runs synchronously with the first composition, but the `focusRequester` must be attached via `Modifier.focusRequester(...)` first
**How to avoid:** Always wrap focus request in `LaunchedEffect(Unit)` triggered by the `isEditing` state flip; never call `requestFocus()` in the `onClick` handler directly
**Warning signs:** Crash with `FocusRequester is not initialized` stack trace

### Pitfall 6: Document list fetch fired on every recomposition
**What goes wrong:** Drawer opens, fetch is triggered; a state update during fetch causes recomposition, which triggers another fetch
**Why it happens:** `LaunchedEffect(drawerIsOpen)` key based on a Boolean that flips true repeatedly as state updates arrive
**How to avoid:** Use `LaunchedEffect(drawerState.isOpen)` — the `DrawerState.isOpen` only changes value when the drawer actually transitions, not on content recomposition; or use a `snapshotFlow { drawerState.currentValue }` collector in the ViewModel
**Warning signs:** Network logs showing dozens of GET /api/documents calls per drawer open

---

## Code Examples

### DropdownMenu Context Menu on Row (verified pattern)
```kotlin
// Source: MainScreen.kt existing overflow menu pattern (same technique)
var menuExpanded by remember { mutableStateOf(false) }
Box {
    IconButton(onClick = { menuExpanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "Document options")
    }
    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = { menuExpanded = false; onRename() }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = { menuExpanded = false; onDelete() }
        )
    }
}
```

### Delete Confirmation AlertDialog
```kotlin
// Source: Material3 Compose AlertDialog API
if (showDeleteConfirmation) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false },
        title = { Text("Delete ${doc.title}?") },
        text = { Text("This will delete all bullets in this document.") },
        confirmButton = {
            TextButton(onClick = { viewModel.deleteDocument(doc.id) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteConfirmation = false }) {
                Text("Cancel")
            }
        }
    )
}
```

### Retrofit 204 No Content (verified pattern)
```kotlin
// Source: AuthApi.kt logout() uses Unit return — for 204 bodies use Response<Unit>
@DELETE("api/documents/{id}")
suspend fun deleteDocument(@Path("id") id: String): Response<Unit>

// In repository:
val response = documentApi.deleteDocument(id)
if (!response.isSuccessful) {
    return Result.failure(HttpException(response))
}
return Result.success(Unit)
```

### DataStore lastDocId extension to TokenStore
```kotlin
// Source: TokenStore.kt existing pattern — add to companion object and functions
private val KEY_LAST_DOC_ID = stringPreferencesKey("last_doc_id")

suspend fun saveLastDocId(docId: String) {
    context.authTokenDataStore.edit { prefs ->
        prefs[KEY_LAST_DOC_ID] = docId
    }
}

suspend fun getLastDocId(): String? =
    context.authTokenDataStore.data.firstOrNull()?.get(KEY_LAST_DOC_ID)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Modifier.animateItemPlacement()` | `Modifier.animateItem()` | Compose Foundation 1.7.0-alpha06 | Calvin-LL/Reorderable 3.0.0 uses the new API — any tutorial older than mid-2024 may reference deprecated modifier |
| `EncryptedSharedPreferences` | DataStore + Tink | Phase 9 decision | Already done; do not use EncryptedSharedPreferences in this phase |
| Nav2 (NavController) | Navigation3 1.0.1 | Phase 9 decision | No new routes needed; MainRoute stays; no Navigation3 changes |

**Deprecated/outdated:**
- `Modifier.animateItemPlacement()`: deprecated in Compose Foundation 1.7; use `Modifier.animateItem()` (Calvin-LL 3.0.0 handles this internally)
- `JavaNetCookieJar` in-memory: already replaced in Phase 9 with `DataStoreCookieJar`

---

## Open Questions

1. **Should `lastDocId` be encrypted in DataStore or stored plain?**
   - What we know: `TokenStore` encrypts all values via Tink; document IDs are UUIDs with no PII
   - What's unclear: Whether encryption overhead is worth it for a non-sensitive UUID
   - Recommendation: Store plain (no Tink wrapping) to avoid the encrypt/decrypt boilerplate for a non-sensitive field; if consistency is preferred, encrypt it — either is correct

2. **Single MainViewModel vs. separate DocumentViewModel?**
   - What we know: Existing MainViewModel is small (~40 lines); CONTEXT.md leaves this to Claude's discretion
   - What's unclear: How large the document state will grow
   - Recommendation: Start with a single extended MainViewModel. Split into DocumentViewModel only if the combined VM exceeds ~200 lines. The SplashViewModel is already separate, so there is precedent for splitting.

3. **Inline edit TextField focus dismiss behavior: what happens on back press or tap outside?**
   - What we know: CONTEXT.md says "how inline rename TextField handles keyboard dismiss vs confirm" is at Claude's discretion
   - What's unclear: Should focus loss (tapping outside the TextField) auto-submit or auto-cancel the rename?
   - Recommendation: On keyboard dismiss without Done action, cancel the rename (restore original title). Use `onFocusChanged` on the TextField — when `it.isFocused == false` after an edit session started, call `cancelRename()`. This matches typical mobile UX (Done = confirm, back/tap-away = cancel).

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + MockK 1.13.14 + Kotlin Coroutines Test 1.10.1 |
| Config file | `android/app/build.gradle.kts` (testOptions.unitTests.isIncludeAndroidResources = true) |
| Quick run command | `./gradlew testDebugUnitTest --tests "*.document.*"` (from `android/`) |
| Full suite command | `./gradlew testDebugUnitTest` (from `android/`) |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DOCM-01 | Document list loads from API and emits to StateFlow | unit | `./gradlew testDebugUnitTest --tests "*.GetDocumentsUseCaseTest"` | Wave 0 |
| DOCM-02 | CreateDocumentUseCase calls API and returns Result.success(Document) | unit | `./gradlew testDebugUnitTest --tests "*.CreateDocumentUseCaseTest"` | Wave 0 |
| DOCM-03 | RenameDocumentUseCase calls PATCH and updates title | unit | `./gradlew testDebugUnitTest --tests "*.RenameDocumentUseCaseTest"` | Wave 0 |
| DOCM-04 | DeleteDocumentUseCase calls DELETE; auto-open-next logic in ViewModel | unit | `./gradlew testDebugUnitTest --tests "*.DeleteDocumentUseCaseTest"` | Wave 0 |
| DOCM-05 | ReorderDocumentUseCase computes afterId and calls PATCH /position | unit | `./gradlew testDebugUnitTest --tests "*.ReorderDocumentUseCaseTest"` | Wave 0 |
| DOCM-06 | TokenStore saveLastDocId/getLastDocId roundtrip; cold-start fallback logic | unit | `./gradlew testDebugUnitTest --tests "*.OpenDocumentUseCaseTest"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest --tests "*.document.*" --tests "*.DocumentRepository*"` (from `android/`)
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Full unit test suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `android/app/src/test/.../domain/usecase/GetDocumentsUseCaseTest.kt` — covers DOCM-01
- [ ] `android/app/src/test/.../domain/usecase/CreateDocumentUseCaseTest.kt` — covers DOCM-02
- [ ] `android/app/src/test/.../domain/usecase/RenameDocumentUseCaseTest.kt` — covers DOCM-03
- [ ] `android/app/src/test/.../domain/usecase/DeleteDocumentUseCaseTest.kt` — covers DOCM-04
- [ ] `android/app/src/test/.../domain/usecase/ReorderDocumentUseCaseTest.kt` — covers DOCM-05
- [ ] `android/app/src/test/.../domain/usecase/OpenDocumentUseCaseTest.kt` — covers DOCM-06 (lastDocId persistence + POST /open)

---

## Sources

### Primary (HIGH confidence)
- Existing codebase: `android/app/src/main/java/com/gmaingret/notes/` — AuthApi.kt, TokenStore.kt, NetworkModule.kt, DataModule.kt, MainScreen.kt, MainViewModel.kt, NavRoutes.kt
- Existing codebase: `server/src/routes/documents.ts` — all 6 document endpoint contracts verified
- `android/gradle/libs.versions.toml` — exact dependency versions in project
- Material3 Compose BOM 2025.02.00 — ModalNavigationDrawer, NavigationDrawerItem, DropdownMenu, AlertDialog all included; `gesturesEnabled=false` confirmed as the disable-swipe API
- Calvin-LL/Reorderable 3.0.0 on Maven Central — verified latest version, LazyColumn support confirmed

### Secondary (MEDIUM confidence)
- WebFetch from deepwiki.com/Calvin-LL/Reorderable — LazyList Components usage pattern; haptic integration
- WebSearch: ModalNavigationDrawer `gesturesEnabled=false` — multiple sources confirm the parameter
- WebSearch: ImeAction.Done in TextField KeyboardOptions — confirmed by multiple Compose docs sources
- valentinilk/compose-shimmer 1.3.3 — confirmed via WebSearch + GitHub

### Tertiary (LOW confidence)
- Recommendation to cancel rename on focus loss (tap-away behavior) — inferred from standard mobile UX patterns, not from an authoritative Compose source

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are in-project or verified on Maven Central with exact versions
- Architecture: HIGH — patterns are direct extensions of Phase 9 code already in the repository
- Pitfalls: HIGH for Retrofit 204/key mismatch/drawer state hoisting (directly observed patterns); MEDIUM for optimistic reorder race (inferred from async behavior)
- Drag-reorder library: HIGH — Calvin-LL/Reorderable 3.0.0 confirmed on Maven Central; STATE.md concern ("confirm library fits flat-tree model") resolved: flat document list is the library's primary use case

**Research date:** 2026-03-12
**Valid until:** 2026-04-12 (stable libraries; Material3 BOM and Reorderable 3.0.0 unlikely to have breaking changes in 30 days)
