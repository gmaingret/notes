# Phase 11: Bullet Tree - Research

**Researched:** 2026-03-12
**Domain:** Android Compose — nested outliner editor with inline editing, drag-reorder, markdown rendering, and server-backed undo
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Editing Toolbar**
- Sticky bottom bar above keyboard, visible only when a bullet is focused + keyboard open
- 7 icon buttons evenly spaced in a single row: outdent, indent, move up, move down, undo, redo, comment
- Buttons with disabled states (grayed out) when operation isn't valid
- No complete button, no delete button, no attachment button

**Tap & Focus**
- Tap bullet text to enter edit mode with cursor at tap position
- Tap bullet icon (dot/arrow) to zoom into that bullet (TREE-08) — does NOT enter edit
- Tap outside bullet text dismisses editing, saves content, dismisses keyboard
- Tapping another bullet's text switches focus to that bullet
- Auto-scroll (imePadding + BringIntoViewRequester) keeps focused bullet visible above keyboard

**Enter & Backspace**
- Dynalist-style Enter: creates sibling below with cursor there
- Enter on empty bullet outdents it instead of creating another
- Enter on empty root-level bullet does nothing (or unfocuses)
- Backspace on empty bullet deletes it and moves cursor to end of previous sibling
- If deleted bullet has children, they become children of the previous sibling

**Content Save**
- 500ms debounce after user stops typing
- PATCH /api/bullets/:id with { content: '...' }

**Move Up/Down**
- Cross-parent movement: move up past first sibling jumps to parent's previous sibling's last child
- Move down past last sibling jumps to parent's next sibling's first child
- Subtree moves with the bullet

**Bullet Icons**
- Normal bullet: small filled circle (dot)
- Completed bullet: filled checkbox with strikethrough text + 50% opacity (server state, no toggle Phase 11)
- Parent bullet with children: collapse/expand arrow right-aligned at screen edge
- Collapsed arrow points right; expanded arrow points down

**Collapse/Expand**
- Tap the right-aligned arrow to toggle
- Children animate in/out with AnimatedVisibility (slide + fade)
- No child count badge
- Collapsed state persisted to server via PATCH /api/bullets/:id with { isCollapsed: true/false }

**Drag-Reorder**
- Long-press anywhere on bullet row to initiate drag
- Haptic feedback (LONG_PRESS) on lift
- Dragged bullet: elevated card with shadow + 1.02x scale
- Horizontal displacement changes target indent level (reparenting)
- Drop indicator: horizontal line at drop position with offset showing target depth
- Whole subtree moves with dragged bullet
- Auto-scroll at edges when dragging near top/bottom
- Optimistic: UI reorders immediately, POST /:id/move fires in background

**Tree Display**
- Flat LazyColumn with depth-based left padding + vertical guide lines connecting parent to children
- Visual indentation capped at 6-8 levels (deeper bullets don't indent further)
- New bullet creation: subtle fade-in + slide animation (~150ms)
- Indent/outdent: animated horizontal slide (~200ms)

**Zoom & Breadcrumb**
- Tap bullet icon to zoom into that bullet as new root
- Breadcrumb trail appears below TopAppBar, only when zoomed in
- Breadcrumb horizontally scrollable, auto-scrolls to rightmost item
- Tap any breadcrumb crumb to zoom to that level
- TopAppBar back arrow goes up one level
- TopAppBar title always shows document name (not zoomed bullet content)

**Empty Document**
- Opening a document with no bullets auto-creates one empty bullet and focuses it

**Markdown Rendering**
- Render formatted text only when bullet is unfocused (bold, italic, strikethrough, links)
- When editing (focused): show raw markdown syntax
- Links tappable when unfocused — open in device browser

**Chip Syntax**
- #tags, @mentions, !!dates render as Material 3 AssistChip-style inline pills
- Color coding: #tags blue, @mentions green, !!dates orange
- Chips render only when bullet is unfocused
- Chips are NOT tappable in Phase 11

**Notes (Comment Field)**
- Expand inline below bullet text when toggled via toolbar comment button
- Small note icon indicator shown on bullets that have a note (when collapsed)
- Plain text only, 500ms debounce save
- PATCH /api/bullets/:id with { note: '...' }

**Undo/Redo**
- Toolbar undo/redo buttons hit server-side undo stack (POST /api/undo, POST /api/redo)
- Disabled when nothing to undo/redo
- After undo/redo: reload full tree from server

**Optimistic Updates**
- All structural operations optimistic: create, indent, outdent, move up/down, drag-move, collapse/expand
- On failure: reload full tree from server + Snackbar
- Operations queued sequentially against server (serialized)

**Loading & Error States**
- Loading: skeleton shimmer rows (3-5 rows at varying indent levels)
- Error: centered "Couldn't load bullets" with Retry button
- Operation failure: Snackbar only, tree reloads

### Claude's Discretion
- Exact toolbar icon choices (Material Icons selection)
- Exact spacing, padding, typography
- FlattenTreeUseCase implementation (recursive DFS, pure Kotlin)
- Operation queue implementation (Channel, Mutex, etc.)
- Undo cursor tracking approach (query on load vs. track locally)
- Skeleton shimmer implementation details
- Exact animation curves and durations
- BringIntoViewRequester integration details
- Drag projection algorithm for reparenting depth calculation

### Deferred Ideas (OUT OF SCOPE)
- Attachments — deferred to v2.1
- Complete toggle — Phase 12 swipe-right gesture
- Delete — Phase 12 swipe-left gesture
- Chip tap actions (navigate to search) — Phase 12 search
- Physical keyboard shortcuts (Tab, Ctrl+arrows) — v2.1
- Bulk delete completed bullets — not in scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| TREE-01 | User can view nested bullets in flat LazyColumn with depth-based indent | FlattenTreeUseCase (recursive DFS), depth-based paddingStart per item, guide lines via Canvas/Box |
| TREE-02 | User can create new bullets (Enter creates sibling; Enter on empty outdents instead) | KeyboardActions on TextField intercept Enter; ViewModel creates or outdents based on text being empty |
| TREE-03 | User can edit bullet content with debounced save | 500ms debounce via Flow.debounce in ViewModel; PATCH /api/bullets/:id {content} |
| TREE-04 | User can delete bullets (Backspace on empty) | KeyboardActions intercept Backspace on empty; soft DELETE /api/bullets/:id; reparent orphan children |
| TREE-05 | User can indent/outdent bullets (toolbar buttons) | POST /api/bullets/:id/indent and /outdent; optimistic parentId/depth update in flattened list |
| TREE-06 | User can collapse/expand bullets with children (animated) | AnimatedVisibility with slide+fade; isCollapsed persisted to server; FlattenTreeUseCase respects isCollapsed |
| TREE-07 | User can mark bullets complete (strikethrough + opacity) | Read-only rendering from server isComplete field; no toggle in Phase 11 |
| TREE-08 | User can zoom into any bullet with breadcrumb navigation | zoomRootId state in ViewModel; FlattenTreeUseCase accepts rootId parameter; breadcrumb LazyRow |
| TREE-09 | User can drag-reorder bullets with projection algorithm | Calvin-LL/Reorderable 3.0.0 (already in catalog); horizontal displacement maps to new parentId+depth |
| TREE-10 | User can add/edit notes field per bullet | Inline expand below bullet text; PATCH /api/bullets/:id {note}; 500ms debounce |
| TREE-11 | User can view and add comments on a bullet | notes field is "comments" — same PATCH endpoint; note field in BulletDto |
| CONT-01 | Bullet text renders markdown (bold, italic, strikethrough, links) | AnnotatedString with SpanStyle; unfocused bullets show rendered; focused shows raw |
| CONT-02 | #tags, @mentions, !!dates render as clickable chips | Regex parse unfocused text; render as InlineContent with AssistChip-style colored chips |
</phase_requirements>

---

## Summary

Phase 11 builds the entire bullet tree editor — the core of the Notes app. All backend APIs already exist; this is a pure Android Compose UI and ViewModel work. The scope is large: flat LazyColumn with depth rendering, inline editing with keyboard interception, toolbar-driven operations, drag-reorder with reparenting, collapse/expand, zoom+breadcrumb, markdown + chip rendering, notes field, and server-backed undo/redo.

The architecture mirrors Phase 10 exactly: Clean Architecture with BulletRepository → use cases → BulletTreeViewModel → BulletTreeScreen. The key technical challenges are (1) FlattenTreeUseCase converting server's parentId tree to an ordered flat list with depth metadata, (2) the operation serialization queue to prevent race conditions, (3) Enter/Backspace keyboard intercept in BasicTextField, and (4) the drag reparenting projection algorithm. All four have known solutions documented here.

The Reorderable library (sh.calvin.reorderable 3.0.0) is already in the version catalog and was proven in Phase 10. Markdown and chip rendering use built-in Compose `AnnotatedString` / `InlineContent` — no additional library is needed.

**Primary recommendation:** Implement in 3 plans: (1) data layer + FlattenTreeUseCase + BulletTreeViewModel scaffold + basic list rendering, (2) inline editing + toolbar + all structural operations + undo/redo, (3) drag-reorder + markdown/chip rendering + notes field + zoom/breadcrumb.

---

## Standard Stack

### Core (all already in version catalog — no new dependencies required)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Compose Material3 | BOM 2025.02.00 | All UI components | Already used throughout |
| sh.calvin.reorderable | 3.0.0 | Drag-reorder in LazyColumn | Proven in Phase 10 DocumentDrawerContent; already in catalog |
| Retrofit 3.0.0 + OkHttp 4.12.0 | As locked | HTTP calls to bullet APIs | Already used; do NOT upgrade OkHttp |
| Hilt 2.56.1 | As locked | DI for repository + use cases + ViewModel | Already used; 2.56.1 required for AGP 8.x |
| kotlinx.coroutines 1.10.1 | As locked | Channel/Mutex for operation queue, debounce | Already used |
| MockK 1.13.14 + coroutines-test | As locked | ViewModel unit tests | Already used |

### No New Dependencies Needed

All Phase 11 features are implementable with the existing dependency set:
- Markdown rendering: `AnnotatedString` + `SpanStyle` (built-in Compose)
- Chip rendering: `InlineContent` in `BasicText` / chip-shaped `Box` composables
- Tree flattening: pure Kotlin recursive DFS
- Operation queue: `Channel<suspend () -> Unit>` consumed by single coroutine (or Mutex)
- Auto-scroll: `BringIntoViewRequester` (androidx.compose.foundation, already in BOM)
- Keyboard intercept: `BasicTextField` with custom `KeyboardActions` + `onKeyEvent`

---

## Architecture Patterns

### Recommended Project Structure

```
data/
  api/
    BulletApi.kt           # Retrofit interface for all bullet endpoints
  model/
    BulletDto.kt           # JSON DTO matching server Bullet shape
    CreateBulletRequest.kt
    PatchBulletRequest.kt
    MoveBulletRequest.kt
  repository/
    BulletRepositoryImpl.kt

domain/
  model/
    Bullet.kt              # Domain model (id, documentId, parentId, content, depth, position, isComplete, isCollapsed, note)
    FlatBullet.kt          # Bullet + computed depth, used in LazyColumn
  repository/
    BulletRepository.kt    # interface
  usecase/
    GetBulletsUseCase.kt
    CreateBulletUseCase.kt
    PatchBulletUseCase.kt  # content, isCollapsed, note
    DeleteBulletUseCase.kt
    IndentBulletUseCase.kt
    OutdentBulletUseCase.kt
    MoveBulletUseCase.kt
    FlattenTreeUseCase.kt  # pure Kotlin, unit-testable; converts List<Bullet> to List<FlatBullet>
    UndoUseCase.kt
    RedoUseCase.kt
    GetUndoStatusUseCase.kt

presentation/
  bullet/
    BulletTreeScreen.kt    # top-level composable inserted into MainScreen content area
    BulletTreeViewModel.kt
    BulletTreeUiState.kt   # sealed interface: Loading / Success / Error
    BulletRow.kt           # single bullet row composable
    BulletEditingToolbar.kt # sticky 7-button bottom bar
    BreadcrumbRow.kt       # horizontal breadcrumb strip below TopAppBar
    BulletMarkdownRenderer.kt # AnnotatedString builder for markdown + chips
```

### Pattern 1: FlattenTreeUseCase — Recursive DFS

**What:** Converts the flat `List<Bullet>` returned by the server (linked by parentId) into an ordered `List<FlatBullet>` with computed `depth` and respecting `isCollapsed`. Server returns bullets ordered by `position` ascending — FlattenTreeUseCase uses that ordering within each sibling group.

**When to use:** Called every time the bullet list state changes (after load, after any structural operation, after undo/redo reload).

**Example:**
```kotlin
// FlattenTreeUseCase.kt — pure Kotlin, no Android deps, fully unit-testable
data class FlatBullet(
    val bullet: Bullet,
    val depth: Int
)

class FlattenTreeUseCase {
    operator fun invoke(
        bullets: List<Bullet>,
        rootId: String? = null,   // null = document root; non-null = zoomed view
        maxDisplayDepth: Int = 7  // visual cap — bullets deeper still render at maxDisplayDepth
    ): List<FlatBullet> {
        // Build child map from server-ordered list (preserves fractional position ordering)
        val childMap = bullets.groupBy { it.parentId }

        fun traverse(parentId: String?, depth: Int): List<FlatBullet> {
            val children = childMap[parentId] ?: return emptyList()
            val result = mutableListOf<FlatBullet>()
            for (bullet in children) {
                result.add(FlatBullet(bullet, minOf(depth, maxDisplayDepth)))
                if (!bullet.isCollapsed) {
                    result.addAll(traverse(bullet.id, depth + 1))
                }
            }
            return result
        }

        return traverse(rootId, 0)
    }
}
```

**Key insight:** Server returns bullets ordered by `position ASC` so `groupBy` preserves sibling order. No client-side sort needed — trust the server ordering.

### Pattern 2: BulletTreeViewModel — State and Operation Queue

**What:** Central ViewModel holding bullet list state and serializing all structural operations via a coroutine-based queue.

**Example:**
```kotlin
@HiltViewModel
class BulletTreeViewModel @Inject constructor(
    private val getBulletsUseCase: GetBulletsUseCase,
    private val createBulletUseCase: CreateBulletUseCase,
    // ... other use cases
    private val flattenTreeUseCase: FlattenTreeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<BulletTreeUiState>(BulletTreeUiState.Loading)
    val uiState: StateFlow<BulletTreeUiState> = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Zoom state
    private val _zoomRootId = MutableStateFlow<String?>(null)
    val zoomRootId: StateFlow<String?> = _zoomRootId.asStateFlow()

    // Breadcrumb path (list of Bullet for crumb trail)
    private val _breadcrumbPath = MutableStateFlow<List<Bullet>>(emptyList())
    val breadcrumbPath: StateFlow<List<Bullet>> = _breadcrumbPath.asStateFlow()

    // Undo/redo availability
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)

    // Operation queue — single coroutine consumer prevents race conditions
    private val operationQueue = Channel<suspend () -> Unit>(capacity = Channel.UNLIMITED)

    init {
        // Start queue consumer
        viewModelScope.launch {
            for (op in operationQueue) { op() }
        }
    }

    private fun enqueue(op: suspend () -> Unit) {
        operationQueue.trySend(op)
    }

    fun loadBullets(documentId: String) {
        viewModelScope.launch {
            _uiState.value = BulletTreeUiState.Loading
            getBulletsUseCase(documentId).fold(
                onSuccess = { bullets ->
                    updateState(bullets)
                    loadUndoStatus()
                },
                onFailure = { e ->
                    _uiState.value = BulletTreeUiState.Error(e.message ?: "Failed to load")
                }
            )
        }
    }

    private fun updateState(bullets: List<Bullet>) {
        val flatList = flattenTreeUseCase(bullets, rootId = _zoomRootId.value)
        _uiState.value = BulletTreeUiState.Success(
            bullets = bullets,
            flatList = flatList
        )
    }
    // ... operations enqueue ops, apply optimistically, then fire API
}
```

### Pattern 3: Operation Queue for Serialized Mutations

**What:** All structural ops (create, indent, outdent, move, delete, collapse) are enqueued and processed sequentially. Optimistic update applied immediately to local state; API call happens inside the queued lambda.

**Example — indent operation:**
```kotlin
fun indentBullet(bulletId: String) {
    // 1. Optimistic update immediately (before enqueue)
    applyOptimisticIndent(bulletId)

    // 2. Enqueue the API call
    enqueue {
        val current = (_uiState.value as? BulletTreeUiState.Success)?.bullets ?: return@enqueue
        indentBulletUseCase(bulletId).fold(
            onSuccess = { updatedBullet ->
                // Replace the updated bullet in state (server returns new parentId/position)
                val newBullets = current.map { if (it.id == bulletId) updatedBullet else it }
                updateState(newBullets)
            },
            onFailure = {
                _snackbarMessage.emit("Couldn't indent bullet")
                reloadFromServer()  // full reload on any failure
            }
        )
    }
}
```

### Pattern 4: Inline Editing with Enter/Backspace Intercept

**What:** Use `BasicTextField` with `onKeyEvent` to intercept Enter and Backspace before they reach the IME. Enter when text is empty triggers outdent instead of newline.

**Example:**
```kotlin
// Inside BulletRow composable
BasicTextField(
    value = textFieldValue,
    onValueChange = { newValue ->
        onContentChange(newValue.text)
        textFieldValue = newValue
    },
    modifier = Modifier
        .weight(1f)
        .focusRequester(focusRequester)
        .onKeyEvent { event ->
            when {
                event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                    if (textFieldValue.text.isEmpty()) {
                        onEnterOnEmpty()  // triggers outdent or unfocus at root
                    } else {
                        onEnterWithContent()  // create sibling below
                    }
                    true  // consume event
                }
                event.key == Key.Backspace && event.type == KeyEventType.KeyDown
                    && textFieldValue.text.isEmpty() -> {
                    onBackspaceOnEmpty()  // delete bullet, move cursor to previous
                    true
                }
                else -> false
            }
        }
)
```

**Critical:** `onKeyEvent` fires for physical key events and software keyboard key events on some OEMs. For Gboard and most software keyboards, Enter/Backspace via `imeAction` or key events are the reliable intercept points. Testing on physical device is essential.

### Pattern 5: Markdown Rendering with AnnotatedString

**What:** Parse bullet content and build an `AnnotatedString` with inline SpanStyles for bold/italic/strikethrough. Links use `LinkAnnotation`.

**Example:**
```kotlin
fun buildMarkdownAnnotatedString(text: String): AnnotatedString = buildAnnotatedString {
    // Bold: **text**
    val boldRegex = Regex("""\*\*(.+?)\*\*""")
    // Italic: *text* or _text_
    val italicRegex = Regex("""\*(.+?)\*|_(.+?)_""")
    // Strikethrough: ~~text~~
    val strikeRegex = Regex("""~~(.+?)~~""")
    // Links: [label](url)
    val linkRegex = Regex("""\[(.+?)\]\((.+?)\)""")

    // Apply ranges iteratively with buildAnnotatedString spans
    // ... regex-apply each pattern as SpanStyle
}
```

**Chip rendering** uses `InlineContent` mapping or renders as chip-shaped `Box` composables in a `FlowRow`-style layout. The simpler approach: parse chips out of text, render non-chip segments as Text and chip matches as small colored `SuggestionChip` composables in a `Row` that wraps — use `FlowRow` from `accompanist-flowlayout` or just `Row` with `wrapContentWidth`.

**Simpler chip approach:** Since chips only render when unfocused, use a composable that splits text on chip regex patterns and renders alternating Text + chip composables in a custom paragraph layout.

### Pattern 6: BringIntoViewRequester for IME Auto-scroll

**What:** Keeps the focused bullet row visible above the software keyboard when it slides up.

**Example:**
```kotlin
val bringIntoViewRequester = remember { BringIntoViewRequester() }
val coroutineScope = rememberCoroutineScope()

LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .imePadding()  // critical: resizes column when keyboard appears
) {
    items(flatBullets, key = { it.bullet.id }) { flatBullet ->
        BulletRow(
            flatBullet = flatBullet,
            isFocused = focusedBulletId == flatBullet.bullet.id,
            bringIntoViewRequester = bringIntoViewRequester,
            onFocused = {
                coroutineScope.launch {
                    bringIntoViewRequester.bringIntoView()
                }
            }
        )
    }
}
```

### Pattern 7: Drag with Reparenting Projection

**What:** Uses Calvin-LL Reorderable 3.0.0 (same as Phase 10). For bullet reparenting, horizontal displacement during drag determines the target depth. After drop, compute `newParentId` from target depth and position in flat list.

**Calvin-LL Reorderable usage** (same API as Phase 10 DocumentDrawerContent):
```kotlin
val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
    // Called during drag — update flat list optimistically
    viewModel.moveBulletLocally(from.index, to.index, horizontalOffset)
}

// In bullet item:
ReorderableItem(reorderableState, key = flatBullet.bullet.id) { isDragging ->
    BulletRow(
        modifier = Modifier.longPressDraggableHandle(
            onDragStarted = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            },
            onDragStopped = {
                viewModel.commitBulletMove()
            }
        ),
        isDragging = isDragging,
        // ...
    )
}
```

**Reparenting depth projection:** The `horizontalOffset` from the drag handle can be converted to a target depth by dividing by the per-level indentation constant (e.g., 24.dp per level). The target `newParentId` is then found by walking up the flat list from the drop position to find the bullet at depth `targetDepth - 1`.

### Anti-Patterns to Avoid

- **LazyColumn key conflicts:** FlatBullet keys MUST be stable bullet IDs. Never use list indices as keys — drag reorder will break.
- **Compose recomposition on every keystroke:** Don't hold bullet content in ViewModel state — hold it in local `TextFieldValue` state and only push to ViewModel on debounce/action.
- **Race conditions from parallel API calls:** All structural mutations MUST go through the operation queue. Never fire two structural API calls concurrently.
- **Tree root ambiguity in zoom mode:** FlattenTreeUseCase must handle `rootId != null` by starting traversal from that bullet's children (depth 0), not from null. The zoomed bullet itself is not in the flat list.
- **Markdown rendering in editing state:** Only render AnnotatedString when `isFocused == false`. Switch to raw `BasicTextField` when focused — toggling between the two via `if (isFocused)` avoids jank.
- **Collapse state lost on reload:** After undo/redo forces a full reload, the server's `isCollapsed` field is the source of truth. Don't maintain a separate local collapse map.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Drag-to-reorder in LazyColumn | Custom drag gesture + item swap | sh.calvin.reorderable 3.0.0 | Already used in Phase 10; handles all scroll-during-drag edge cases; auto-scroll built in |
| Skeleton shimmer animation | Custom painter | Existing ShimmerDrawerRows pattern from Phase 10 | Copy and adapt with variable-indent rows |
| Operation serialization | Ad-hoc mutex per operation | `Channel<suspend () -> Unit>` consumed by single coroutine | Channel guarantees FIFO; much simpler than per-operation mutexes |
| Keyboard Enter intercept | KeyboardOptions.imeAction only | `onKeyEvent` on BasicTextField | imeAction alone cannot distinguish "Enter on empty" from "Enter on text" without checking value |

**Key insight:** The entire markdown/chip rendering is achievable with Compose's built-in `AnnotatedString`, `SpanStyle`, and `InlineContent` — no third-party markdown library is needed. A markdown library (like Markwon) would be overkill and add 500KB+ to the APK for a simple bold/italic/strikethrough/links subset.

---

## Common Pitfalls

### Pitfall 1: Enter key consumed by Gboard before `onKeyEvent`
**What goes wrong:** On some Android versions, the software keyboard sends Enter as an IME action, not a key event. `onKeyEvent` misses it.
**Why it happens:** IME input and key events are separate channels. Gboard sends `ImeAction.Done` or newline via `commitText`, not `KeyEvent`.
**How to avoid:** Use a custom `VisualTransformation`-less `BasicTextField` with a `KeyboardOptions(imeAction = ImeAction.None)` to suppress the Done button, then intercept via `onValueChange` — compare previous and new text to detect Enter (text gains a newline character `\n`). Remove the newline and trigger the create-sibling action.
**Warning signs:** Enter key handler fires on physical keyboard but not on emulator/device software keyboard.

### Pitfall 2: FlatBullet depth wrong after indent/outdent
**What goes wrong:** After optimistic indent, depth in flat list doesn't update because FlattenTreeUseCase wasn't called with the updated parentId.
**Why it happens:** Optimistic update modifies the bullets list but doesn't re-flatten.
**How to avoid:** Any state update — even optimistic — must re-run `flattenTreeUseCase(bullets)` to recompute the flat list. Never patch `flatList` directly.

### Pitfall 3: BringIntoViewRequester fires before layout completes
**What goes wrong:** `bringIntoView()` called immediately on focus, but the focused row hasn't been measured yet — scroll overshoots or doesn't scroll at all.
**Why it happens:** Compose layout pass hasn't run when `onFocused` callback fires.
**How to avoid:** Call `bringIntoView()` inside a `LaunchedEffect(focusedBulletId)` that waits one frame: `delay(50)` or observe `WindowInsets.ime` to trigger after keyboard animation completes.

### Pitfall 4: Operation queue growing unbounded on rapid tapping
**What goes wrong:** User taps indent/outdent rapidly 20 times; 20 API calls queue up; when the first fails, full reloads cascade.
**Why it happens:** Unbounded `Channel.UNLIMITED` capacity accepts all items.
**How to avoid:** For idempotent operations (collapse, indent by same key), deduplicate in the queue by replacing the pending operation for the same bulletId. For create/delete, preserve all. Alternatively, use `Channel.CONFLATED` per bullet, but this is complex. Simplest: cap indent/outdent at one pending per bullet using a `Set<String>` of in-flight bulletIds.

### Pitfall 5: Reorderable library incompatibility with nested scrolling
**What goes wrong:** BulletTree LazyColumn inside a `Scaffold` with `imePadding` may conflict with Reorderable's internal scroll handling.
**Why it happens:** Multiple nested scroll consumers fight over gesture events.
**How to avoid:** Apply `imePadding()` to the `LazyColumn` modifier directly (not to an outer Box), and don't wrap the LazyColumn in a `NestedScrollConnection` that might intercept drag events.

### Pitfall 6: Undo/redo leaves UI stale
**What goes wrong:** POST /api/undo returns `{ canUndo, canRedo }` status only — it does NOT return the new bullet state. The tree is stale until refreshed.
**Why it happens:** Undo endpoint returns `UndoStatus` (confirmed from undoService.ts). After undo, client must reload bullets from `GET /api/bullets/documents/:docId/bullets`.
**How to avoid:** After undo/redo success: (1) update canUndo/canRedo from response, (2) call `reloadFromServer()` which calls `getBulletsUseCase` and re-renders. This is already the decided pattern (CONTEXT.md "After undo/redo: reload full tree from server").

### Pitfall 7: Drag reparenting cycle detection
**What goes wrong:** Dragging a parent bullet under one of its children creates a cycle. The server rejects with 400 "Cannot move a bullet under one of its own descendant".
**Why it happens:** The drag projection algorithm allows any drop target without checking ancestry.
**How to avoid:** In `moveBulletLocally` (optimistic), prevent the drop target from being a descendant of the dragged bullet. Build a quick descendant set from the flat list before allowing the drop. On server rejection, still reload + Snackbar.

---

## Code Examples

Verified patterns from existing codebase and official sources:

### BulletApi.kt — All Bullet Endpoints
```kotlin
// Source: mirrors DocumentApi.kt pattern from Phase 10
interface BulletApi {

    @GET("api/bullets/documents/{docId}/bullets")
    suspend fun getBullets(@Path("docId") docId: String): List<BulletDto>

    @POST("api/bullets")
    suspend fun createBullet(@Body request: CreateBulletRequest): BulletDto

    @PATCH("api/bullets/{id}")
    suspend fun patchBullet(
        @Path("id") id: String,
        @Body request: PatchBulletRequest
    ): BulletDto

    @DELETE("api/bullets/{id}")
    suspend fun deleteBullet(@Path("id") id: String): Response<Unit>

    @POST("api/bullets/{id}/indent")
    suspend fun indentBullet(@Path("id") id: String): BulletDto

    @POST("api/bullets/{id}/outdent")
    suspend fun outdentBullet(@Path("id") id: String): BulletDto

    @POST("api/bullets/{id}/move")
    suspend fun moveBullet(
        @Path("id") id: String,
        @Body request: MoveBulletRequest
    ): BulletDto

    @POST("api/undo")
    suspend fun undo(): UndoStatusDto

    @POST("api/redo")
    suspend fun redo(): UndoStatusDto

    @GET("api/undo/status")
    suspend fun getUndoStatus(): UndoStatusDto

    @POST("api/bullets/{id}/undo-checkpoint")
    suspend fun undoCheckpoint(
        @Path("id") id: String,
        @Body request: UndoCheckpointRequest
    ): Response<Unit>
}
```

### BulletDto.kt — Server Shape
```kotlin
// Source: matches bulletService.ts Bullet type exactly
data class BulletDto(
    @SerializedName("id") val id: String,
    @SerializedName("documentId") val documentId: String,
    @SerializedName("parentId") val parentId: String?,
    @SerializedName("content") val content: String,
    @SerializedName("position") val position: Double,
    @SerializedName("isComplete") val isComplete: Boolean,
    @SerializedName("isCollapsed") val isCollapsed: Boolean,
    @SerializedName("note") val note: String?
) {
    fun toDomain() = Bullet(
        id = id,
        documentId = documentId,
        parentId = parentId,
        content = content,
        position = position,
        isComplete = isComplete,
        isCollapsed = isCollapsed,
        note = note
    )
}
```

### Shimmer for Bullet Rows (varying indents)
```kotlin
// Adapts existing ShimmerDrawerRows pattern from Phase 10 DocumentDrawerContent
@Composable
fun ShimmerBulletRows() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shimmer_alpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val indents = listOf(0.dp, 24.dp, 48.dp, 24.dp, 0.dp)  // varying depths for realism

    Column(modifier = Modifier.fillMaxWidth()) {
        indents.forEach { indent ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(start = 16.dp + indent, end = 16.dp, top = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerColor)
            )
        }
    }
}
```

### MainScreen Integration Point
```kotlin
// In MainScreen.kt — replace the placeholder Text block with:
is MainUiState.Success -> {
    if (state.openDocumentId != null) {
        BulletTreeScreen(
            documentId = state.openDocumentId,
            documentTitle = state.documents.find { it.id == state.openDocumentId }?.title ?: "Notes",
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Text("Select a document")
    }
}
```

---

## API Reference: Complete Bullet Endpoints

Confirmed from `/c/Users/gmain/Dev/Notes/server/src/routes/bullets.ts` and `undo.ts`:

| Method | Path | Body | Returns | Use |
|--------|------|------|---------|-----|
| GET | /api/bullets/documents/:docId/bullets | — | List<BulletDto> | Load all bullets |
| POST | /api/bullets | { documentId, parentId?, afterId?, content? } | BulletDto | Create bullet |
| PATCH | /api/bullets/:id | { content? / isComplete? / isCollapsed? / note? } | BulletDto | Update fields |
| DELETE | /api/bullets/:id | — | { ok: true } | Soft-delete bullet |
| POST | /api/bullets/:id/indent | — | BulletDto | Indent bullet |
| POST | /api/bullets/:id/outdent | — | BulletDto | Outdent bullet |
| POST | /api/bullets/:id/move | { newParentId, afterId? } | BulletDto | Move/reparent |
| POST | /api/bullets/:id/undo-checkpoint | { content, previousContent? } | { ok: true } | Record text undo |
| POST | /api/undo | — | { canUndo, canRedo } | Undo |
| POST | /api/redo | — | { canUndo, canRedo } | Redo |
| GET | /api/undo/status | — | { canUndo, canRedo } | Check undo availability |

**Critical notes:**
- `DELETE /api/bullets/:id` returns `{ ok: true }` (JSON body), NOT 204. Use `BulletDto` or a response type — but actually a plain `suspend fun deleteBullet(): Response<Unit>` with `isSuccessful` check works since the body is ignored.
- Actually from the route: `return res.json({ ok: true })` — this is a 200 with body. Use `Response<JsonObject>` or just `Response<Unit>` and ignore body. The `Response<Unit>` pattern (learned in Phase 10) still works — just check `isSuccessful`.
- `PATCH /api/bullets/:id` accepts only ONE field at a time (the server checks `isCollapsed !== undefined` first, then `isComplete`, then `content`, then `note`). Do NOT send multiple fields in one PATCH call.
- indent/outdent return the moved bullet's new state. The client must also update children's depths in the flat list (by re-running FlattenTreeUseCase).

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RecyclerView + DiffUtil for tree | LazyColumn with stable keys + FlattenTreeUseCase | Compose stable | No RecyclerView needed |
| Editable spans for markdown in EditText | AnnotatedString with SpanStyle in BasicTextField | Compose stable | Simpler, no TextWatcher |
| Custom drag framework | sh.calvin.reorderable | 2023 | Drop-in, handles scroll |
| EncryptedSharedPreferences | DataStore + Tink | Phase 9 decision | Already done |

**No deprecated APIs in scope:** All patterns use current stable Compose APIs.

---

## Open Questions

1. **Enter key intercept reliability on all software keyboards**
   - What we know: `onKeyEvent` is unreliable for software keyboards; Enter via `onValueChange` detecting `\n` in new text is more reliable.
   - What's unclear: Whether `\n` detection in `onValueChange` fires before or after IME processes it.
   - Recommendation: Implement `\n` detection in `onValueChange` as primary; add `onKeyEvent` as secondary for physical keyboards. Validate on physical device early.

2. **Drag reparenting UX in edge cases**
   - What we know: Horizontal displacement maps to depth. The projection is discretionary (Claude's Discretion).
   - What's unclear: Minimum horizontal swipe threshold to trigger depth change (too sensitive = accidental reparenting).
   - Recommendation: Use 32-40dp per level change threshold (larger than tap slop). A drop indicator line showing current depth before release is essential for UX feedback.

3. **Comment field vs note field naming**
   - What we know: CONTEXT.md says "The web app uses `note` field as 'comments' — same server field, same API". TREE-11 requirement is "view and add comments". The API field is `note`.
   - What's unclear: Whether the UI label should say "Comments" or "Notes".
   - Recommendation: Use "Note" as the UI label (matching the API field) to avoid confusion. The CONTEXT.md "comment button" in the toolbar is the trigger icon — the expanded field can be labeled "Note".

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + MockK 1.13.14 + coroutines-test 1.10.1 |
| Config file | android/app/build.gradle.kts (testOptions.unitTests.isIncludeAndroidResources = true) |
| Quick run command | `./gradlew testDebugUnitTest --tests "*.Bullet*"` (from android/ directory) |
| Full suite command | `./gradlew testDebugUnitTest` (from android/ directory) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TREE-01 | FlattenTreeUseCase produces correct depth and order | unit | `./gradlew testDebugUnitTest --tests "*.FlattenTreeUseCaseTest"` | Wave 0 |
| TREE-01 | FlattenTreeUseCase respects isCollapsed (hides children) | unit | same | Wave 0 |
| TREE-01 | FlattenTreeUseCase with rootId (zoom mode) | unit | same | Wave 0 |
| TREE-02 | BulletTreeViewModel.createBullet adds bullet and sets focus | unit | `./gradlew testDebugUnitTest --tests "*.BulletTreeViewModelTest"` | Wave 0 |
| TREE-02 | BulletTreeViewModel Enter-on-empty triggers outdent | unit | same | Wave 0 |
| TREE-03 | Debounce: content save fires after 500ms, not on each keystroke | unit | same | Wave 0 |
| TREE-04 | BulletTreeViewModel.deleteBullet reparents orphan children | unit | same | Wave 0 |
| TREE-05 | indentBullet/outdentBullet optimistic update re-flattens tree | unit | same | Wave 0 |
| TREE-06 | collapseExpand hides/shows children in flat list | unit | same | Wave 0 |
| TREE-08 | zoomTo sets zoomRootId; FlattenTreeUseCase returns zoomed subtree | unit | same | Wave 0 |
| TREE-09 | moveBulletLocally reorders flat list optimistically | unit | same | Wave 0 |
| TREE-10 | saveNote debounces; PATCH called with note field | unit | same | Wave 0 |
| CONT-01 | Markdown AnnotatedString builder: bold/italic/strikethrough spans | unit | `./gradlew testDebugUnitTest --tests "*.BulletMarkdownRendererTest"` | Wave 0 |
| CONT-02 | Chip parser extracts #tags/@mentions/!!dates from text | unit | same | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest --tests "*.FlattenTreeUseCaseTest" --tests "*.BulletTreeViewModelTest"` (from android/ dir)
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/FlattenTreeUseCaseTest.kt` — covers TREE-01 (zoom, collapse, depth)
- [ ] `android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModelTest.kt` — covers TREE-02 through TREE-10
- [ ] `android/app/src/test/java/com/gmaingret/notes/presentation/bullet/BulletMarkdownRendererTest.kt` — covers CONT-01, CONT-02
- [ ] No framework install needed — existing JUnit4 + MockK + coroutines-test infrastructure covers all tests

---

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection — `server/src/routes/bullets.ts`, `server/src/services/bulletService.ts`, `server/src/services/undoService.ts` — API endpoints and return shapes verified
- Direct codebase inspection — `android/.../DocumentDrawerContent.kt` — Reorderable 3.0.0 usage pattern confirmed
- Direct codebase inspection — `android/.../DocumentRepositoryImpl.kt` — Result<T> + Response<Unit> pattern confirmed
- Direct codebase inspection — `android/gradle/libs.versions.toml` — All library versions confirmed; no new deps needed
- Direct codebase inspection — `MainViewModelTest.kt` — test patterns (MockK, coroutines-test, Channel snackbar test) confirmed

### Secondary (MEDIUM confidence)
- Calvin-LL Reorderable library API — inferred from Phase 10 working implementation using `rememberReorderableLazyListState`, `ReorderableItem`, `longPressDraggableHandle`
- `onKeyEvent` vs `onValueChange` for Enter intercept — inferred from Compose documentation patterns and known Gboard IME behavior

### Tertiary (LOW confidence — flag for validation)
- Enter-key intercept via `\n` detection in `onValueChange` — standard community pattern but requires device validation
- Drag reparenting threshold of 32-40dp per level — reasonable estimate; tune during implementation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verified all library versions from version catalog; confirmed no new deps needed
- API contract: HIGH — read actual server route + service code
- Architecture patterns: HIGH — directly mirrors Phase 10 patterns proven in production
- FlattenTreeUseCase: HIGH — standard recursive DFS, pure Kotlin
- Enter key intercept: MEDIUM — IME behavior varies by keyboard; onValueChange approach more reliable than onKeyEvent for software keyboards
- Drag reparenting: MEDIUM — projection algorithm is Claude's Discretion; depth threshold needs empirical tuning

**Research date:** 2026-03-12
**Valid until:** 2026-04-12 (all dependencies stable; no fast-moving packages in scope)
