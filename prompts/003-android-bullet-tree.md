<objective>
Implement the core bullet tree — the heart of the Notes app. Build a nested, collapsible, editable bullet tree rendered in a LazyColumn. Support all bullet operations: create, edit (with debounced save), delete, indent, outdent, collapse/expand, complete toggle, notes field, and drag-drop reorder.

This is the most complex phase. The bullet tree must feel reactive and responsive — every interaction should update the UI instantly via optimistic updates.
</objective>

<context>
This builds on prompts 001 (auth) and 002 (documents). The user can already log in and see their document list.

CRITICAL: Read these files carefully before implementing — they contain algorithms that must be ported to Kotlin:
- `client/src/components/DocumentView/BulletTree.tsx` — `buildBulletMap()`, `flattenTree()`, `getChildren()`, `computeDragProjection()` algorithms
- `client/src/hooks/useBullets.ts` — all bullet API calls, optimistic update patterns, request/response shapes
- `client/src/components/DocumentView/BulletContent.tsx` — editing behavior: Enter to split, Backspace to merge/delete, debounced save, cursor management

Bullet API:
- `GET /api/bullets/documents/:docId/bullets` → flat array of all non-deleted bullets for a document
- `POST /api/bullets` with `{ documentId, parentId?, afterId?, content? }` → new bullet
- `PATCH /api/bullets/:id` with `{ content?, isComplete?, isCollapsed?, note? }` → updated bullet
- `DELETE /api/bullets/:id` → soft delete (sets deletedAt)
- `POST /api/bullets/:id/indent` → moves bullet to be child of previous sibling
- `POST /api/bullets/:id/outdent` → moves bullet to be sibling of parent
- `POST /api/bullets/:id/move` with `{ parentId, afterId }` → move to new position

Bullet model: `{ id, documentId, parentId, content, position (float), isComplete, isCollapsed, note, deletedAt, createdAt, updatedAt }`

Tree structure: bullets form a tree via `parentId`. Root bullets have `parentId: null`. Siblings are ordered by `position` (float). The API returns a flat list — the client must build the tree and flatten it for display.
</context>

<requirements>
1. **BulletApi** Retrofit interface with all endpoints
2. **BulletRepository** holding `StateFlow<Map<String, Bullet>>` (the bullet map, keyed by ID)
3. **FlattenTreeUseCase** — port the web client's tree logic exactly:
   - `getChildren(map, parentId)` → filter by parentId, sort by position, exclude deletedAt != null
   - `flattenTree(map, parentId, depth)` → recursive: for each child, emit `FlatBullet(bullet, depth)`, then recurse into children (skip if collapsed)
4. **BulletTreeViewModel** with:
   - `flatBullets: StateFlow<List<FlatBullet>>` derived from the bullet map via flattenTree
   - All mutation methods with optimistic updates
   - Debounced content save (1 second delay per bullet, cancel-and-restart on each keystroke)
5. **BulletTreeScreen** — `LazyColumn` with `key = { it.id }`:
   - Each item indented by `depth * 24.dp` padding
   - Empty state when no bullets
6. **BulletRow** composable:
   - Collapse/expand chevron (animated 90-degree rotation) — only shown if bullet has children
   - Bullet dot
   - Content text field
   - Completed state: strikethrough + 50% opacity
   - Note indicator (if note exists)
7. **BulletContentField** — `BasicTextField`:
   - Local text state for instant feedback
   - Debounced save: on each change, cancel previous save job, schedule new one after 1 second
   - On blur: flush pending save immediately
   - Enter key: create new bullet after current one (same parent, afterId = current bullet). If cursor is mid-text, split: current bullet keeps text before cursor, new bullet gets text after cursor.
   - Backspace on empty bullet: delete it, focus previous bullet
   - Backspace at start of non-empty: merge content with previous bullet (append current content to previous)
8. **NoteField**: expandable `OutlinedTextField` below the bullet content, saves via PATCH on blur
9. **Indent/Outdent**: triggered from context menu (long-press on bullet). Indent = `POST /:id/indent`, Outdent = `POST /:id/outdent`. After API call, refetch all bullets (tree structure changes server-side).
10. **Complete toggle**: tap the bullet dot or via context menu. Optimistic toggle of `isComplete`.
11. **Collapse/expand**: tap the chevron. Optimistic toggle of `isCollapsed`. Children hide/show with `AnimatedVisibility`.
12. **Drag-drop reorder**:
    - Long-press on bullet dot starts drag
    - Port `computeDragProjection` from web client: compute `insertionIndex` from Y position, `projectedDepth` from X offset, clamp depth between min/max
    - Show drop indicator line at projected position and depth
    - Show floating drag overlay (the bullet being dragged, with shadow)
    - On drop: compute `newParentId` and `afterId` from projection, call `POST /:id/move`
    - After move API call, refetch bullets
13. **Context menu** on long-press: Indent, Outdent, Toggle Complete, Add Note, Delete
</requirements>

<implementation>
New/modified files:
```
data/
  remote/
    api/BulletApi.kt
    dto/BulletDtos.kt                — BulletDto, CreateBulletRequest, PatchBulletRequest, MoveBulletRequest
  repository/BulletRepository.kt

domain/
  model/
    Bullet.kt                        — data class
    FlatBullet.kt                    — data class(bullet: Bullet, depth: Int)
  usecase/
    FlattenTreeUseCase.kt            — buildBulletMap, getChildren, flattenTree
  repository/
    IBulletRepository.kt

presentation/
  bullets/
    BulletTreeViewModel.kt
    BulletTreeScreen.kt
    BulletRow.kt
    BulletContentField.kt
    NoteField.kt
    DragOverlay.kt                   — Floating bullet during drag
    DropIndicator.kt                 — Horizontal line at drop position
  document/
    DocumentScreen.kt                — Updated: toolbar + BulletTreeScreen
```

Critical algorithm to port — `computeDragProjection` (from `BulletTree.tsx`):
```
Given: flatBullets list, draggedBulletId, currentY position, currentX offset
1. Remove dragged bullet from visible list
2. Find insertionIndex based on Y (which row gap is closest)
3. Calculate projectedDepth from X offset (horizontalDelta / indentWidth)
4. Get maxDepth = depth of item above + 1 (can become child of item above)
5. Get minDepth = depth of item below (must be at least as shallow as next item, or 0 if at end)
6. Clamp projectedDepth between minDepth and maxDepth
7. Walk up from insertionIndex to find newParentId at the clamped depth
8. Find afterId (last sibling before insertion point at the same depth under same parent)
Return: { parentId, afterId, depth, insertionIndex }
```

Optimistic update pattern for each operation:
- **Create**: Generate temp UUID, insert in bullet map at correct position, update StateFlow → flattenTree recomputes → LazyColumn updates. On API response, replace temp ID with real ID.
- **Edit content**: Local state in BulletContentField updates instantly. Debounced PATCH to server. No optimistic map update needed (local state IS the truth during editing).
- **Delete**: Set deletedAt in map → flattenTree excludes it → UI removes row. Call DELETE API. On error: clear deletedAt → row reappears.
- **Complete**: Toggle isComplete in map → UI updates. Call PATCH API. On error: toggle back.
- **Collapse**: Toggle isCollapsed in map → flattenTree shows/hides children. Call PATCH API. On error: toggle back.
- **Indent/Outdent/Move**: These change tree structure (parentId, position). Call API first, then refetch all bullets. These are NOT optimistically updated because the server computes the new position.

Focus management:
- Track `focusTargetId: String?` in the ViewModel
- After creating a bullet, set `focusTargetId` to the new bullet's ID
- In `BulletContentField`, use `LaunchedEffect` to request focus when `focusTargetId == bullet.id`
- Clear `focusTargetId` after focus is granted
</implementation>

<verification>
1. Navigate to a document → bullet tree loads with correct nesting
2. Tap empty area or "add bullet" → new bullet appears, cursor is focused
3. Type text → content saves after 1 second (check network logs)
4. Press Enter mid-text → bullet splits at cursor, new bullet focused
5. Backspace on empty bullet → bullet deleted, previous bullet focused
6. Tap bullet dot → toggles complete (strikethrough + opacity)
7. Tap chevron → children collapse/expand with animation
8. Long-press → context menu → Indent → bullet moves under previous sibling
9. Long-press → context menu → Outdent → bullet moves up one level
10. Long-press bullet dot → drag → drop at new position → bullet moves
11. Long-press → Add Note → note field appears → type → saves on blur
12. Pull-to-refresh reloads entire bullet tree
13. All operations feel instant (optimistic updates, no loading spinners for mutations)
</verification>

<success_criteria>
- Bullet tree renders identically to web client (same nesting, same order)
- All CRUD operations work against production backend
- Editing feels native — no lag, proper cursor behavior
- Drag-drop works smoothly with visual feedback (overlay + indicator)
- Optimistic updates make every interaction feel instant
- No data loss — debounced saves flush on blur/navigate-away
- Tree with 100+ bullets scrolls smoothly in LazyColumn
</success_criteria>
