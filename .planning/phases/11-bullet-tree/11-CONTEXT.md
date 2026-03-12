# Phase 11: Bullet Tree - Context

**Gathered:** 2026-03-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Flat LazyColumn outliner with all core editing interactions (create, edit, indent, outdent, move, collapse/expand, drag-reorder with reparenting), markdown rendering when unfocused, chip syntax (#tags/@mentions/!!dates), notes per bullet, zoom-into-bullet with breadcrumb, and undo/redo via server-side stack. No backend changes — all APIs exist. Complete toggle and delete are deferred to Phase 12 swipe gestures. Attachments deferred to v2.1.

</domain>

<decisions>
## Implementation Decisions

### Editing Toolbar
- Sticky bottom bar above keyboard, visible only when a bullet is focused + keyboard open
- 7 icon buttons evenly spaced in a single row: outdent, indent, move up, move down, undo, redo, comment
- Buttons with disabled states (grayed out) when operation isn't valid (e.g., can't outdent root-level, nothing to undo)
- No complete button — complete toggle deferred to Phase 12 swipe-right
- No delete button — delete deferred to Phase 12 swipe-left
- No attachment button — attachments deferred entirely to v2.1

### Tap & Focus
- Tap bullet text to enter edit mode with cursor at tap position
- Tap bullet icon (dot/arrow) to zoom into that bullet (TREE-08) — does NOT enter edit
- Tap outside bullet text (background, icon, etc.) dismisses editing, saves content, dismisses keyboard
- Tapping another bullet's text switches focus to that bullet
- Auto-scroll (imePadding + BringIntoViewRequester) keeps focused bullet visible above keyboard

### Enter & Backspace
- Dynalist-style Enter: creates sibling below with cursor there
- Enter on empty bullet outdents it instead of creating another
- Enter on empty root-level bullet does nothing (or unfocuses)
- Backspace on empty bullet deletes it and moves cursor to end of previous sibling
- If deleted bullet has children, they become children of the previous sibling

### Content Save
- 500ms debounce after user stops typing
- PATCH /api/bullets/:id with { content: '...' }

### Move Up/Down
- Cross-parent movement: move up past first sibling jumps to parent's previous sibling's last child; move down past last sibling jumps to parent's next sibling's first child
- Subtree moves with the bullet

### Bullet Icons
- Normal bullet: small filled circle (dot)
- Completed bullet: filled checkbox with strikethrough text + 50% opacity (rendered from server state, no toggle in Phase 11)
- Parent bullet with children: collapse/expand arrow right-aligned at screen edge
- Collapsed arrow points right; expanded arrow points down

### Collapse/Expand
- Tap the right-aligned arrow to toggle
- Children animate in/out with AnimatedVisibility (slide + fade)
- No child count badge — just the arrow
- Collapsed state persisted to server via PATCH /api/bullets/:id with { isCollapsed: true/false }

### Drag-Reorder
- Long-press anywhere on bullet row to initiate drag
- Haptic feedback (LONG_PRESS) on lift
- Dragged bullet: elevated card with shadow + 1.02x scale (same as Phase 10 document drag)
- Dragging supports reparenting: horizontal displacement changes target indent level
- Drop indicator: horizontal line at drop position with offset showing target depth
- Whole subtree moves with dragged bullet (children follow parent)
- Auto-scroll at edges when dragging near top/bottom of visible list
- No child count badge on dragged collapsed bullet
- Optimistic: UI reorders immediately, POST /:id/move fires in background

### Tree Display
- Flat LazyColumn with depth-based left padding + vertical guide lines connecting parent to children
- Visual indentation capped at 6-8 levels (deeper bullets don't indent further; use zoom to work with deep nesting)
- New bullet creation: subtle fade-in + slide animation (~150ms)
- Indent/outdent: animated horizontal slide (~200ms)

### Zoom & Breadcrumb
- Tap bullet icon to zoom into that bullet as new root
- Breadcrumb trail appears below TopAppBar, only when zoomed in (hidden at root level)
- Breadcrumb horizontally scrollable, auto-scrolls to rightmost item
- Tap any breadcrumb crumb to zoom to that level
- TopAppBar back arrow goes up one level (not all the way to root)
- TopAppBar title always shows document name (not zoomed bullet content)

### Empty Document
- Opening a document with no bullets auto-creates one empty bullet and focuses it with keyboard open
- User starts typing immediately — no empty state UI needed

### Markdown Rendering
- Render formatted text only when bullet is unfocused (bold, italic, strikethrough, links)
- When editing (focused): show raw markdown syntax
- Links tappable when unfocused — open in device browser
- No live preview while editing

### Chip Syntax
- #tags, @mentions, !!dates render as Material 3 AssistChip-style inline pills
- Color coding: #tags blue, @mentions green, !!dates orange
- Chips render only when bullet is unfocused (raw syntax while editing)
- Chips are NOT tappable in Phase 11 (no action) — wired to search in Phase 12

### Notes (Comment Field)
- Expand inline below bullet text when toggled via toolbar comment button
- Small note icon indicator shown on bullets that have a note (when note is collapsed)
- Tapping note icon expands the note inline
- Plain text only — no markdown rendering in notes
- 500ms debounce save (same as bullet content), PATCH /api/bullets/:id with { note: '...' }

### Undo/Redo
- Toolbar undo/redo buttons hit server-side undo stack (existing POST /api/undo, POST /api/redo)
- Disabled state when nothing to undo/redo (track cursor state)
- After undo/redo: reload full tree from server (server is source of truth)

### Optimistic Updates
- All structural operations optimistic: create, indent, outdent, move up/down, drag-move, collapse/expand
- Content save is debounced (not optimistic in the traditional sense)
- On failure: reload full tree from server + Snackbar with error message
- Operations queued sequentially against server (serialized, prevents race conditions)

### Loading & Error States
- Loading: skeleton shimmer rows (3-5 rows at varying indent levels) while bullets load
- Error: centered "Couldn't load bullets" with Retry button
- Operation failure: Snackbar only (e.g., "Couldn't indent bullet"), tree reloads

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

</decisions>

<specifics>
## Specific Ideas

- Toolbar layout: 7 icon buttons evenly spaced, icon-only, no labels — must fit comfortably on phone width
- Collapse arrow is specifically right-aligned at screen border (not next to bullet text)
- Drag-reparenting: horizontal line indicator shifts left/right to show target depth during drag
- The web app uses `note` field as "comments" — same server field, same API
- Move up/down crosses parent boundaries (not just sibling reorder)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MainScreen.kt`: Content area placeholder ("Phase 11 will add bullet tree editor here") — direct replacement point
- `MainViewModel.kt`: Has document state, snackbar SharedFlow, coroutine scope patterns — BulletTreeViewModel follows same patterns
- `MainUiState.kt`: Sealed interface with Loading/Success/Error/Empty — same pattern for bullet tree state
- `DocumentRepositoryImpl.kt`: Retrofit + Result<T> pattern — BulletRepository follows same approach
- `DocumentRow.kt`: Reorderable library integration (Calvin-LL/Reorderable 3.0.0) — same library for bullet drag
- Reorderable library already in version catalog from Phase 10

### Established Patterns
- Clean Architecture: data/domain/presentation with use cases as `operator fun invoke()`
- Kotlin `Result<T>` for all repository/use case error handling
- StateFlow in ViewModels, collected with `collectAsState()` in Composables
- Hilt DI: NetworkModule provides Retrofit, DataModule provides DataStore
- Optimistic update + Snackbar + revert pattern (Phase 10 document reorder)
- Response<Unit> for 204 endpoints (learned in Phase 10)

### Integration Points
- Backend API: all bullet endpoints exist under `/api/bullets/*` — no backend changes
- Undo/redo: existing `POST /api/undo`, `POST /api/redo` endpoints
- Bullet schema: id, documentId, parentId, content, position (FLOAT8), isComplete, isCollapsed, note, deletedAt
- MainScreen content area is the insertion point for BulletTreeScreen composable
- `DocumentApi.kt` pattern for new `BulletApi.kt` interface

</code_context>

<deferred>
## Deferred Ideas

- Attachments (view/download/upload) — deferred to v2.1
- Complete toggle — Phase 12 swipe-right gesture
- Delete — Phase 12 swipe-left gesture
- Chip tap actions (navigate to search) — Phase 12 search
- Physical keyboard shortcuts (Tab, Ctrl+arrows) — v2.1
- Bulk delete completed bullets — not in scope

</deferred>

---

*Phase: 11-bullet-tree*
*Context gathered: 2026-03-12*
