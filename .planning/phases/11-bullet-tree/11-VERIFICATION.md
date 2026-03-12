---
phase: 11-bullet-tree
verified: 2026-03-12T14:30:00Z
status: passed
score: 28/28 must-haves verified
---

# Phase 11: Bullet Tree Verification Report

**Phase Goal:** Users can create, edit, and organize an infinitely nested bullet outline with full keyboard loop and rich text rendering
**Verified:** 2026-03-12
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Bullet list for a document can be fetched from the server | VERIFIED | `BulletApi.getBullets` + `BulletRepositoryImpl.getBullets` both exist and wrap Retrofit in `Result<T>` |
| 2 | Bullets render in flat LazyColumn with depth-based left padding and vertical guide lines | VERIFIED | `BulletRow.kt:154` applies `padding(start = indentPx)` and `drawBehind` loop draws guide lines per depth level |
| 3 | Tapping bullet text enters edit mode; Enter creates sibling; Enter on empty triggers enterOnEmpty | VERIFIED | `BulletRow.kt:206-256` BasicTextField with `onValueChange` newline scan; `onEnterWithContent` and `onEnterOnEmpty` wired to ViewModel |
| 4 | Backspace on empty BasicTextField triggers backspaceOnEmpty | VERIFIED | `BulletRow.kt:234-240` `onKeyEvent` Key.Backspace when `localText.isEmpty()` calls `onBackspaceOnEmpty()` |
| 5 | 7-button toolbar appears above keyboard when a bullet is focused | VERIFIED | `BulletTreeScreen.kt:295-326` `AnimatedVisibility(visible = focusedBulletId != null)` wraps `BulletEditingToolbar` with all 7 buttons |
| 6 | Unfocused bullets show markdown-rendered text (bold, italic, strikethrough, links) | VERIFIED | `BulletRow.kt:287-289` calls `buildMarkdownAnnotatedString` for pure-text bullets; `BulletMarkdownRenderer.kt` handles all 4 patterns |
| 7 | Unfocused bullets show #tags/@mentions/!!dates as colored chips | VERIFIED | `BulletRow.kt:276-312` `parseContentSegments` + `InlineChip` composable with blue/green/orange coloring |
| 8 | Completed bullets show strikethrough at 50% opacity | VERIFIED | `BulletRow.kt:259-273` `SpanStyle(textDecoration = LineThrough, color = onSurface.copy(alpha=0.5f))` |
| 9 | Parent bullets show collapse/expand arrow right-aligned | VERIFIED | `BulletRow.kt:330-343` ArrowRight/ArrowDropDown icon when `flatBullet.hasChildren` |
| 10 | User can indent/outdent bullets via toolbar | VERIFIED | `BulletTreeViewModel.kt:310-353` `indentBullet`/`outdentBullet` enqueue server calls with server-authoritative result |
| 11 | User can collapse/expand bullets with children (animated) | VERIFIED | `BulletTreeViewModel.kt:450-468` `toggleCollapse` optimistic flip; `animateItem()` in LazyColumn handles exit animation |
| 12 | User can mark bullets complete (strikethrough + opacity) | VERIFIED | `BulletTreeUiState.kt` carries `isComplete`; `BulletRow.kt` renders strikethrough; ViewModel has `patchBulletUseCase` with `updateIsComplete` factory |
| 13 | User can zoom into any bullet with breadcrumb navigation | VERIFIED | `BulletTreeViewModel.kt:545-549` `zoomTo` sets `_zoomRootId`, walks parentId chain for breadcrumb; `BreadcrumbRow.kt` renders strip |
| 14 | User can drag-reorder bullets with reparenting | VERIFIED | `BulletTreeScreen.kt:146-289` Reorderable + `pointerInput detectDragGestures` for horizontal offset; `commitBulletMove` fires on drop |
| 15 | User can add/edit notes field per bullet (TREE-10) | VERIFIED | `NoteField.kt` AnimatedVisibility inline field; `saveNote` wired at `BulletTreeScreen.kt:209-211` with 500ms debounce in ViewModel |
| 16 | User can view and add comments on a bullet (TREE-11) | VERIFIED | TREE-11 scoped in context to the "notes/comment field" pattern: toolbar comment button toggles `NoteField`; wired via `onToggleNote` and `onNoteChange` callbacks |
| 17 | Bullet text renders markdown (CONT-01) | VERIFIED | `BulletMarkdownRenderer.kt` `buildMarkdownAnnotatedString` — bold/italic/strikethrough/links with non-overlapping match priority |
| 18 | #tags, @mentions, !!dates render as clickable chips (CONT-02) | VERIFIED | `parseContentSegments` extracts chip segments; `InlineChip` renders with correct colors |
| 19 | Debounced content and note saves (500ms) do not fire per-keystroke | VERIFIED | `BulletTreeViewModel.kt:93-97, 112-127` `MutableSharedFlow(extraBufferCapacity=64)` + `debounce(500)` in `init{}` |
| 20 | All structural operations optimistic with snackbar + reload on failure | VERIFIED | Every operation in ViewModel (indentBullet, outdentBullet, createBullet, etc.) follows: optimistic mutate → enqueue → onFailure `_snackbarMessage.emit` + `reloadFromServer()` |
| 21 | FlattenTreeUseCase converts parent-linked bullets into depth-ordered flat list | VERIFIED | `FlattenTreeUseCase.kt:23-66` pure DFS with `groupBy parentId`, position sort, depth cap, collapse skip |
| 22 | FlattenTreeUseCase respects isCollapsed flag | VERIFIED | `FlattenTreeUseCase.kt:57-59` skips `dfs(bullet.id, ...)` when `bullet.isCollapsed` |
| 23 | FlattenTreeUseCase supports zoom mode (rootId) | VERIFIED | `FlattenTreeUseCase.kt:63` `dfs(rootId, 0)` — starts from specified root |
| 24 | BulletTreeViewModel loads bullets and exposes Loading/Success/Error states | VERIFIED | `BulletTreeViewModel.kt:142-158` sets Loading, calls getBulletsUseCase, emits Success or Error |
| 25 | Undo/redo call server endpoints and reload full tree | VERIFIED | `BulletTreeViewModel.kt:496-527` enqueues undoUseCase/redoUseCase, on success updates canUndo/canRedo and calls `reloadFromServer()` |
| 26 | Operation queue serializes all structural API calls | VERIFIED | `BulletTreeViewModel.kt:103-132` `Channel(UNLIMITED)` drained sequentially in init; every mutation calls `enqueue {}` |
| 27 | MainScreen content area shows BulletTreeScreen when a document is open | VERIFIED | `MainScreen.kt:42,206` imports and calls `BulletTreeScreen(documentId, documentTitle, Modifier.fillMaxSize())` |
| 28 | DI wiring complete for BulletApi and BulletRepository | VERIFIED | `NetworkModule.kt:69-70` `retrofit.create(BulletApi::class.java)`; `DataModule.kt:37` `bindBulletRepository` |

**Score:** 28/28 truths verified

---

### Required Artifacts

| Artifact | Min Lines | Actual | Status | Details |
|----------|-----------|--------|--------|---------|
| `data/api/BulletApi.kt` | — | 70 | VERIFIED | 11 endpoints: GET, POST, PATCH, DELETE, indent, outdent, move, undo, redo, undoStatus, undoCheckpoint |
| `domain/usecase/FlattenTreeUseCase.kt` | — | 67 | VERIFIED | Pure Kotlin DFS, `@Inject constructor()`, zoom/collapse/depth-cap |
| `presentation/bullet/BulletTreeViewModel.kt` | 300 | 657 | VERIFIED | All 17 operations implemented, operation queue, debounce flows, snackbar |
| `test/.../FlattenTreeUseCaseTest.kt` | 80 | 215 | VERIFIED | 10 test cases: empty, flat, ordering, nesting, deep, collapsed, zoom, depth-cap, hasChildren, multiple roots |
| `test/.../BulletTreeViewModelTest.kt` | 200 | 597 | VERIFIED | 27 test cases covering all major operations |
| `presentation/bullet/BulletRow.kt` | 100 | 393 | VERIFIED | Edit/display toggle, guide lines, Enter/Backspace intercept, chips, drag visual |
| `presentation/bullet/BulletEditingToolbar.kt` | 60 | 140 | VERIFIED | 7 M3 IconButtons with enabled states |
| `presentation/bullet/BulletTreeScreen.kt` | 120 | 418 | VERIFIED | Reorderable LazyColumn, shimmer, breadcrumb, SnackbarHost, toolbar |
| `presentation/bullet/BulletMarkdownRenderer.kt` | 80 | 233 | VERIFIED | bold/italic/strikethrough/links + chip parser |
| `test/.../BulletMarkdownRendererTest.kt` | 60 | 207 | VERIFIED | 16 tests (TDD) |
| `presentation/bullet/BreadcrumbRow.kt` | 40 | 113 | VERIFIED | LazyRow with Home + chevrons, auto-scroll, clickable crumbs |
| `presentation/bullet/NoteField.kt` | 30 | 78 | VERIFIED | AnimatedVisibility, expandVertically+fadeIn, placeholder text |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `BulletRepositoryImpl` | `BulletApi` | `try { bulletApi.X() }` | WIRED | All 11 methods in `BulletRepositoryImpl.kt` wrap `bulletApi.` calls in try/catch Result |
| `BulletTreeViewModel` | use cases | `@HiltViewModel @Inject constructor(...)` | WIRED | `BulletTreeViewModel.kt:44-57` — `@HiltViewModel` with all 11 use cases injected |
| `NetworkModule` | `BulletApi` | `retrofit.create(BulletApi::class.java)` | WIRED | `NetworkModule.kt:69-70` |
| `BulletTreeViewModel.createBullet` | `CreateBulletUseCase` | `enqueue { createBulletUseCase(...) }` | WIRED | `BulletTreeViewModel.kt:259-282` |
| `BulletTreeViewModel.updateContent` | `PatchBulletUseCase` | debounce flow PATCH | WIRED | `BulletTreeViewModel.kt:116` `patchBulletUseCase(bulletId, PatchBulletRequest.updateContent(content))` |
| `BulletTreeViewModel.undo` | `UndoUseCase + reloadFromServer` | enqueue then reload | WIRED | `BulletTreeViewModel.kt:498-508` |
| `BulletTreeScreen` | `BulletTreeViewModel` | `hiltViewModel() + collectAsState()` | WIRED | `BulletTreeScreen.kt:74-80` |
| `BulletRow` | ViewModel callbacks | lambda parameters `on\\w+: () -> Unit` | WIRED | `BulletRow.kt:82-90` + `BulletTreeScreen.kt:177-211` wires all callbacks |
| `MainScreen` | `BulletTreeScreen` | direct call in `Success` state | WIRED | `MainScreen.kt:206` |
| `BulletTreeScreen Reorderable` | `moveBulletLocally / commitBulletMove` | `rememberReorderableLazyListState` callbacks | WIRED | `BulletTreeScreen.kt:146-147, 271-276` |
| `BreadcrumbRow` | `BulletTreeViewModel.zoomTo` | `onCrumbClick` callback | WIRED | `BulletTreeScreen.kt:106` |
| `NoteField` | `BulletTreeViewModel.saveNote` | `onNoteChange` callback | WIRED | `BulletTreeScreen.kt:209-211` |

---

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| TREE-01 | 11-01, 11-03 | Flat LazyColumn with depth-based indent | SATISFIED | `BulletRow.kt` depth indent; `BulletTreeScreen.kt` LazyColumn |
| TREE-02 | 11-02, 11-03 | Enter creates sibling; Enter on empty outdents | SATISFIED | `BulletRow.kt` Enter intercept; `BulletTreeViewModel.enterOnEmpty` |
| TREE-03 | 11-02, 11-03 | Debounced content save | SATISFIED | `updateContent` + 500ms `debounce(500)` flow in ViewModel |
| TREE-04 | 11-02, 11-03 | Backspace on empty deletes bullet | SATISFIED | `BulletRow.kt` Backspace intercept; `backspaceOnEmpty` with reparenting |
| TREE-05 | 11-02 | Indent/outdent via toolbar | SATISFIED | Toolbar buttons wired to `indentBullet`/`outdentBullet` ViewModel methods |
| TREE-06 | 11-02, 11-04 | Collapse/expand animated | SATISFIED | `toggleCollapse` optimistic; `animateItem()` for LazyColumn exit animation |
| TREE-07 | 11-01, 11-03 | Mark bullets complete (strikethrough + opacity) | SATISFIED | `BulletRow.kt:259-273` isComplete rendering |
| TREE-08 | 11-02, 11-04 | Zoom into bullet with breadcrumb | SATISFIED | `zoomTo`, `BreadcrumbRow.kt`, `BulletTreeScreen.kt` breadcrumb AnimatedVisibility |
| TREE-09 | 11-04 | Drag-reorder with reparenting | SATISFIED | Reorderable + horizontal displacement + `commitBulletMove` |
| TREE-10 | 11-02, 11-04 | Notes field per bullet | SATISFIED | `NoteField.kt`, `saveNote` debounce, toolbar comment button |
| TREE-11 | 11-02, 11-04 | View and add comments (scoped to notes field in context) | SATISFIED | Notes field = comment field per Phase 11 context; `NoteField.kt` inline below bullet |
| CONT-01 | 11-03 | Markdown rendering (bold, italic, strikethrough, links) | SATISFIED | `buildMarkdownAnnotatedString` with 16 passing tests |
| CONT-02 | 11-03 | #tags, @mentions, !!dates as clickable chips | SATISFIED | `parseContentSegments` + `InlineChip` with correct color coding |

All 13 requirements satisfied. No orphaned requirements found.

---

### Anti-Patterns Found

| File | Pattern | Severity | Notes |
|------|---------|----------|-------|
| `BulletRow.kt:131-132` | `animateFloatAsState` for collapse rotation assigned to unused `collapseRotation` val | Info | The icon switching (ArrowRight/ArrowDropDown) handles visual state; rotation is redundant but harmless |

No blockers. No stub implementations. No TODO/FIXME in delivered artifacts.

---

### Human Verification Required

The following items require a device or emulator to verify:

#### 1. Keyboard Backspace on Empty (Software Keyboard)
**Test:** Open document on Android, tap a bullet, delete all text, then press Backspace on soft keyboard
**Expected:** Bullet deleted, cursor moves to end of previous bullet
**Why human:** `onKeyEvent` for Backspace is reliable on physical keyboards and Gboard; some soft keyboards may not send Backspace key events. The plan acknowledges this limitation.

#### 2. Drag Reparenting Visual (Drop Indicator)
**Test:** Long-press a bullet, drag horizontally while dragging vertically
**Expected:** Drop indicator line shifts horizontally to preview target indent depth
**Why human:** The plan specifies a drop indicator line (`Divider` or `Box` at target depth), but reading BulletTreeScreen code shows `targetDepthDelta` is computed but no separate drop indicator composable is rendered. The reparenting logic fires on drop, but the visual preview line may be absent.

#### 3. Collapse/Expand Animation Quality
**Test:** Tap collapse arrow on a parent with children
**Expected:** Children animate out smoothly with AnimatedVisibility slide+fade
**Why human:** LazyColumn `animateItem()` provides placement animation; actual visual quality of collapse exit animation must be confirmed on device.

#### 4. End-to-End Content Save with Undo
**Test:** Type in a bullet, wait 1s, then press undo in toolbar
**Expected:** Content reverts to previous value on server; tree reloads with reverted content
**Why human:** Full round-trip through server undo stack cannot be verified by code reading alone.

#### 5. imePadding Behavior
**Test:** Focus a bullet near the bottom of the list on a device
**Expected:** The focused bullet is visible above the software keyboard; list scrolls accordingly
**Why human:** `imePadding` + `BringIntoViewRequester` behavior depends on IME window insets at runtime.

---

### Note on TREE-11 Scoping

REQUIREMENTS.md states TREE-11 as "User can view and add comments on a bullet." The Phase 11 Context document (`11-CONTEXT.md`) explicitly scopes this to the inline notes/comment field pattern: "Expand inline below bullet text when toggled via toolbar comment button." There is no separate threading/comment data model in the server API for Phase 11. The `NoteField.kt` inline field, toggled by the toolbar comment button, satisfies the Phase 11 scoping of this requirement. A separate threaded-comment system is not part of Phase 11 scope.

---

### Gaps Summary

None. All 28 verifiable truths pass. All artifacts exist and are substantive. All key links are wired.

The two items noted are:
1. A possible missing "drop indicator line" visual during drag (functional reparenting works; visual preview not confirmed). This is a polish item, not a goal blocker — the plan deferred drop indicator details to "Claude's Discretion."
2. Soft keyboard Backspace detection is a runtime behavior that cannot be verified statically.

Neither blocks the phase goal.

---

_Verified: 2026-03-12_
_Verifier: Claude (gsd-verifier)_
