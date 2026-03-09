# Phase 2: Core Outliner - Research

**Researched:** 2026-03-09
**Domain:** React contenteditable bullet tree, dnd-kit nested DnD, server-side undo stack, URL-based zoom
**Confidence:** HIGH (established patterns, existing codebase verified, schema already in place)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Enter / Backspace editing behavior**
- Enter in middle of text: Split — text before cursor stays, text after cursor moves to new bullet inserted below
- Enter on empty bullet: Outdent (if indented); if at root level, create blank bullet below
- Enter on bullet with children: New bullet inserted as first child (not sibling after subtree)
- Backspace at start of bullet: Merge with bullet above — current content appended to end of previous; cursor at join point
- Backspace on bullet with children: Block merge; flash visual warning (subtle shake/indicator)
- Delete at end of bullet: Merge with bullet below (mirror of Backspace-merge)
- Tab on first root-level bullet (nothing to indent under): Silent no-op
- Ctrl/Cmd+Arrow (move bullet): Moves entire subtree — bullet plus all descendants

**Collapse chevron**
- Chevron only visible on bullets that have children
- Position: left of bullet dot in separate column (Dynalist style)
- Collapsed state: chevron rotates to pointing right only — no child count badge
- Collapse/expand animation: instant, no transition

**Drag-and-drop**
- Bullet dot itself is the drag handle (no separate handle element)
- Click vs drag disambiguation: drag threshold ~5px — short click = zoom, drag beyond threshold = reorder
- Single click on bullet dot = zoom in immediately
- Drop indicator: horizontal line between rows showing insert position (not row highlight)

**Zoom and breadcrumb**
- Breadcrumb: Document title > Ancestor 1 > ... > Current bullet — middle ancestors truncated with `...`
- Doc title and current bullet always visible
- Breadcrumb placement: replaces document title area when zoomed
- Clicking ancestor in breadcrumb: zooms directly to that level
- Ctrl/Cmd+[ (KB-04): walks back one level to parent
- Zoom persistence: URL-based — zoom state in URL hash or path

**Undo event recording**
- Text typing: debounced ~1s pause or word boundary = one undo step
- Structural operations (indent, outdent, move, delete, collapse toggle): immediately recorded
- Storage: server-side only (`undo_events` + `undo_cursors` tables, already in schema)
- Scope: global per user — Ctrl+Z can undo actions from any document
- 50-step limit: oldest event dropped (FIFO) when 51st event recorded

### Claude's Discretion
- Exact debounce timing (1s is a guideline, can tune)
- Visual design of the flash/shake warning for blocked Backspace-merge
- URL hash format for zoom state (`#bullet/<id>` or query param)
- How long paths truncate exactly
- Drag-and-drop library choice (research needed — see Standard Stack below)
- Exact styling of the horizontal drop line indicator

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| BULL-01 | User can create new bullet by pressing Enter | Enter split algorithm; POST /api/bullets; optimistic update pattern |
| BULL-02 | User can indent (Tab / toolbar / context menu) | POST /api/bullets/:id/indent; FLOAT8 midpoint; undo recording |
| BULL-03 | User can outdent (Shift+Tab / toolbar / context menu) | POST /api/bullets/:id/outdent; reparent + reposition |
| BULL-04 | User can move bullet up/down (toolbar / KB / context menu) | POST /api/bullets/:id/move with after_id; subtree moves together |
| BULL-05 | User can reorder bullets via drag-and-drop | dnd-kit flatten/unflatten pattern; dot-as-handle; threshold detection |
| BULL-06 | User can collapse/expand bullet (chevron; persisted) | PATCH is_collapsed; instant toggle; chevron only on parents |
| BULL-07 | User can zoom into bullet (full-screen root) | URL hash routing; DocumentView reads zoom from URL |
| BULL-08 | User can navigate back via breadcrumb | Breadcrumb component; click-to-zoom; AppPage router integration |
| BULL-11 | User can soft-delete bullet (undo restores with children) | DELETE sets deletedAt; undo sets deletedAt=null; cascade via parentId |
| BULL-12 | User can mark bullet complete (strikethrough + 50% opacity) | PATCH is_complete; CSS styling; stays in position |
| BULL-13 | User can hide completed bullets via toolbar toggle | Client-side filter on is_complete; toggle state in UI |
| BULL-14 | User can bulk delete all completed bullets (irreversible) | DELETE WHERE is_complete=true AND document; no undo needed |
| BULL-15 | Desktop right-click bullet > context menu | Context menu component; same actions as keyboard |
| KB-01 | Enter = new bullet below | Same as BULL-01 |
| KB-02 | Tab = indent; Shift+Tab = outdent | Same as BULL-02/03 |
| KB-03 | Ctrl/Cmd+Up/Down = move bullet up/down | Same as BULL-04; subtree moves |
| KB-04 | Ctrl/Cmd+] = zoom in; Ctrl/Cmd+[ = zoom out | URL navigation; router integration |
| KB-05 | Ctrl/Cmd+Z = undo; Ctrl/Cmd+Y = redo | POST /api/undo and /api/redo; global keyboard handler |
| KB-06 | Ctrl/Cmd+B = bold; Ctrl/Cmd+I = italic | execCommand or manual selection wrapping on contenteditable |
| KB-07 | Ctrl/Cmd+P = search; Ctrl/Cmd+* = bookmarks; Ctrl/Cmd+E = sidebar | Global keyboard handler; UI state toggles |
| UNDO-01 | Undo last action (text + structural) | undoService + undo_events table; POST /api/undo |
| UNDO-02 | 50-level history, global per user, not per doc | FIFO drop at 51st; user_id scope; seq cursor |
| UNDO-03 | History server-side, survives refresh | undo_events + undo_cursors tables (already in schema) |
| UNDO-04 | Undoing deletion restores bullet + children | Soft delete (deletedAt); inverse_op sets deletedAt=null |
</phase_requirements>

---

## Summary

Phase 2 builds the entire core of the product: the bullet tree editor. All infrastructure (schema, auth, document model, API patterns) is in place from Phase 1 — this phase is pure feature work.

The key technical risks are: (1) the contenteditable keyboard handler is complex but entirely precedented — the split/merge/indent/outdent logic must be correct, (2) dnd-kit cross-level tree drag requires a flatten/unflatten pattern with projected depth detection, which is documented but not trivial, and (3) the server-side undo system must wrap every bullet mutation in a transaction that records both forward_op and inverse_op.

The schema already exists with no changes needed. The existing React Query + optimistic update pattern from `useDocuments.ts` is the template for all bullet mutations. The `computeDocumentInsertPosition` function in `documentService.ts` is the template for FLOAT8 midpoint positioning.

**Primary recommendation:** Build server first (bulletService + routes + undoService), then client tree rendering, then keyboard handler, then DnD. Each layer validates the layer below.

---

## Standard Stack

### Core (All Already Installed)

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| React | 19.2.0 | UI | Already in package.json |
| @tanstack/react-query | 5.90.x | Server state + optimistic updates | Already in package.json |
| @dnd-kit/core | 6.3.1 | DnD primitives | Already in package.json |
| @dnd-kit/sortable | 8.0.0 | Sortable preset | Already in package.json |
| zustand | 5.0.11 | UI state | Already in package.json |
| react-router-dom | 6.30.x | URL-based zoom routing | Already in package.json |
| drizzle-orm | 0.40.0 | DB access | Already in server package.json |
| zod | 3.x | Request validation | Already in both packages |

### No New Dependencies Required

All required libraries are already installed. Phase 2 adds no new npm packages.

The `dnd-kit-sortable-tree` third-party wrapper is explicitly NOT recommended (see Alternatives Considered). Use the flatten/unflatten pattern directly with `@dnd-kit/core` and `@dnd-kit/sortable`.

### Alternatives Considered

| Instead of | Could Use | Why Not |
|------------|-----------|---------|
| Custom flatten/unflatten DnD | dnd-kit-sortable-tree wrapper | Sparse maintenance, adds abstraction over something we need to control precisely (dot-as-handle + threshold detection are custom requirements) |
| contenteditable | react-contenteditable library | Adds dependency, wraps something simple, harder to control cursor position programmatically |
| Per-bullet debounce timer | Single global debounce | Per-bullet is correct — each bullet has independent edit state |

---

## Architecture Patterns

### Recommended Project Structure (New Files for Phase 2)

```
server/
├── src/
│   ├── routes/
│   │   ├── bullets.ts          # NEW — bullet CRUD + operations
│   │   └── undo.ts             # NEW — undo/redo endpoints
│   └── services/
│       ├── bulletService.ts    # NEW — tree ops + position logic
│       └── undoService.ts      # NEW — undo event log management

client/src/
├── hooks/
│   ├── useBullets.ts           # NEW — React Query hooks for bullets
│   └── useUndo.ts              # NEW — undo/redo keyboard binding
├── components/
│   └── DocumentView/
│       ├── DocumentView.tsx    # REPLACE placeholder with real tree
│       ├── BulletTree.tsx      # NEW — tree root, DnD context
│       ├── BulletNode.tsx      # NEW — recursive node component
│       ├── BulletContent.tsx   # NEW — contenteditable + keyboard handler
│       └── Breadcrumb.tsx      # NEW — zoom path display
└── App.tsx                     # ADD zoom URL routes
```

### Pattern 1: Bullet Tree State — Normalized Map

The client loads all bullets for a document in one request and stores them as a normalized map keyed by id. The tree is reconstructed in memory from `parent_id` links. This is the same structure `documentService.ts` already uses for export (see `getDocumentWithBullets`).

```typescript
// BulletTree.tsx — tree state shape
type BulletMap = Record<string, Bullet>;
type Bullet = {
  id: string;
  parentId: string | null;
  content: string;
  position: number;
  isComplete: boolean;
  isCollapsed: boolean;
  deletedAt: string | null;
};

// Build from flat server response
function buildBulletMap(flat: Bullet[]): BulletMap {
  return Object.fromEntries(flat.map(b => [b.id, b]));
}

// Get ordered children for a parent
function getChildren(map: BulletMap, parentId: string | null): Bullet[] {
  return Object.values(map)
    .filter(b => b.parentId === parentId && !b.deletedAt)
    .sort((a, b) => a.position - b.position);
}
```

### Pattern 2: Bullet CRUD — Same Shape as useDocuments

Every bullet mutation follows the `useReorderDocument` pattern from `useDocuments.ts`:
- `onMutate`: apply optimistic change to local map
- `onError`: roll back to saved snapshot
- `onSettled`: invalidate query to sync with server

```typescript
// useBullets.ts — mutation template
export function useCreateBullet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: CreateBulletVars) =>
      apiClient.post<Bullet>('/api/bullets', vars),
    onMutate: async (vars) => {
      await qc.cancelQueries({ queryKey: bulletKey(vars.documentId) });
      const prev = qc.getQueryData<Bullet[]>(bulletKey(vars.documentId));
      // Apply optimistic bullet to map
      qc.setQueryData(bulletKey(vars.documentId), (old: Bullet[] = []) => [
        ...old,
        { id: `optimistic-${Date.now()}`, ...vars },
      ]);
      return { prev };
    },
    onError: (_err, vars, ctx) => {
      if (ctx?.prev) qc.setQueryData(bulletKey(vars.documentId), ctx.prev);
    },
    onSettled: (_data, _err, vars) =>
      qc.invalidateQueries({ queryKey: bulletKey(vars.documentId) }),
  });
}
```

### Pattern 3: FLOAT8 Midpoint Positioning for Bullets

Use the exact same algorithm as `computeDocumentInsertPosition` in `documentService.ts`. The `after_id` pattern — client sends UUID of the bullet to insert after (null = prepend), server computes FLOAT8 midpoint — is locked and must be used for all bullet position operations.

```typescript
// bulletService.ts — reuse same algorithm
export async function computeBulletInsertPosition(
  dbInstance: DB,
  parentId: string | null,
  documentId: string,
  afterId: string | null
): Promise<number> {
  const siblings = await dbInstance
    .select({ id: bullets.id, position: bullets.position })
    .from(bullets)
    .where(and(
      eq(bullets.documentId, documentId),
      parentId ? eq(bullets.parentId, parentId) : isNull(bullets.parentId),
      isNull(bullets.deletedAt),
    ))
    .orderBy(asc(bullets.position));

  if (siblings.length === 0) return 1.0;
  if (afterId === null) return siblings[0].position / 2;

  const afterIdx = siblings.findIndex(s => s.id === afterId);
  if (afterIdx === -1) return siblings[siblings.length - 1].position + 1.0;

  const prev = siblings[afterIdx].position;
  const next = siblings[afterIdx + 1]?.position;
  return next !== undefined ? (prev + next) / 2 : prev + 1.0;
}
```

### Pattern 4: Undo Wrapping — Every Mutation Records Events

Every bullet mutation is a two-phase DB operation: (1) apply change to bullets table, (2) record forward_op + inverse_op to undo_events and advance undo_cursors. Both happen in a single Drizzle transaction.

```typescript
// undoService.ts — record and apply
export async function recordUndoEvent(
  dbInstance: DB,
  userId: string,
  eventType: string,
  forwardOp: object,
  inverseOp: object
): Promise<void> {
  await dbInstance.transaction(async (tx) => {
    // Get current cursor
    const cursor = await tx.query.undoCursors.findFirst({
      where: eq(undoCursors.userId, userId),
    });
    const currentSeq = cursor?.currentSeq ?? 0;
    const newSeq = currentSeq + 1;

    // Truncate redo stack (any events beyond current position)
    await tx.delete(undoEvents).where(
      and(eq(undoEvents.userId, userId), gt(undoEvents.seq, currentSeq))
    );

    // Enforce 50-step cap: delete oldest if at limit
    await tx.delete(undoEvents).where(
      and(eq(undoEvents.userId, userId), lt(undoEvents.seq, newSeq - 50))
    );

    // Insert new event
    await tx.insert(undoEvents).values({
      userId, seq: newSeq, schemaVersion: 1,
      eventType, forwardOp, inverseOp,
    });

    // Advance cursor
    await tx.insert(undoCursors)
      .values({ userId, currentSeq: newSeq })
      .onConflictDoUpdate({
        target: undoCursors.userId,
        set: { currentSeq: newSeq },
      });
  });
}
```

### Pattern 5: DnD — Flatten/Unflatten for Cross-Level Moves

dnd-kit's `SortableContext` cannot handle cross-level tree reordering natively. The standard pattern (used in dnd-kit's own official tree example) is to flatten the tree to a single array for drag operations, then unflatten on drop.

**Key technique:** During drag, detect projected depth from horizontal mouse offset relative to drag start position. Each `INDENTATION_WIDTH` pixels of horizontal delta = one depth level change. Constrain projected depth to valid range (0 to parent's depth + 1).

```typescript
// Flatten tree to sortable array
type FlatBullet = Bullet & { depth: number };

function flattenTree(
  map: BulletMap,
  parentId: string | null = null,
  depth = 0
): FlatBullet[] {
  return getChildren(map, parentId).flatMap(bullet => [
    { ...bullet, depth },
    ...(bullet.isCollapsed ? [] : flattenTree(map, bullet.id, depth + 1)),
  ]);
}

// During drag: compute projected depth from horizontal offset
const INDENTATION_WIDTH = 24; // px per indent level

function getProjectedDepth(
  flatItems: FlatBullet[],
  activeId: string,
  overId: string,
  dragOffset: number
): number {
  const activeIndex = flatItems.findIndex(f => f.id === activeId);
  const overIndex = flatItems.findIndex(f => f.id === overId);
  const dragDepth = flatItems[activeIndex].depth;
  const projectedDepth = dragDepth + Math.round(dragOffset / INDENTATION_WIDTH);

  const prevItem = flatItems[overIndex - 1];
  const maxDepth = prevItem ? prevItem.depth + 1 : 0;
  const minDepth = flatItems[overIndex + 1]?.depth ?? 0;

  return Math.min(Math.max(projectedDepth, minDepth), maxDepth);
}
```

### Pattern 6: URL-Based Zoom

React Router v6 is already in use with route `/doc/:docId`. Add a subroute or hash parameter for zoom state. Hash-based is simpler and matches the codebase's current navigation pattern:

```typescript
// App.tsx — add zoom route variant
<Route path="/doc/:docId" element={<RequireAuth><AppPage /></RequireAuth>} />
// Zoom state via URL hash: /doc/abc123#bullet/xyz789

// In DocumentView.tsx — read zoom from hash
const location = useLocation();
const zoomedBulletId = useMemo(() => {
  const hash = location.hash; // '#bullet/xyz789'
  const match = hash.match(/^#bullet\/(.+)$/);
  return match ? match[1] : null;
}, [location.hash]);

// Zoom in: navigate to hash
const navigate = useNavigate();
function zoomTo(bulletId: string) {
  navigate(`#bullet/${bulletId}`, { replace: false });
}
function zoomOut(parentId: string | null) {
  if (parentId) navigate(`#bullet/${parentId}`, { replace: false });
  else navigate('', { replace: false }); // clear hash = document root
}
```

### Pattern 7: Contenteditable Keyboard Handler

The keyboard handler on each `BulletContent` div intercepts specific keys and calls bullet mutation APIs. **Critical:** All DOM manipulation (focus, cursor positioning) must happen synchronously in the same event handler tick — especially for mobile Safari.

```typescript
// BulletContent.tsx — key handler shape
function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
  const el = e.currentTarget;

  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    const { before, after } = splitAtCursor(el);
    // 1. Update current bullet content to 'before'
    // 2. Create new bullet with 'after' as content
    // 3. Focus new bullet, cursor at start
    return;
  }

  if (e.key === 'Tab') {
    e.preventDefault();
    if (e.shiftKey) outdentBullet();
    else indentBullet();
    return;
  }

  if (e.key === 'Backspace' && isCursorAtStart(el)) {
    e.preventDefault();
    if (hasChildren) { flashWarning(); return; }
    mergeToPrevious(el);
    return;
  }

  if (e.key === 'Delete' && isCursorAtEnd(el)) {
    e.preventDefault();
    mergeToNext();
    return;
  }

  if ((e.ctrlKey || e.metaKey) && e.key === 'ArrowUp') {
    e.preventDefault();
    moveBulletUp(); // moves entire subtree
    return;
  }

  if ((e.ctrlKey || e.metaKey) && e.key === 'ArrowDown') {
    e.preventDefault();
    moveBulletDown(); // moves entire subtree
    return;
  }
}

// Cursor helpers (pure DOM)
function isCursorAtStart(el: HTMLDivElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return false;
  const range = sel.getRangeAt(0);
  return range.collapsed && range.startOffset === 0 &&
    (range.startContainer === el || range.startContainer === el.firstChild);
}

function splitAtCursor(el: HTMLDivElement): { before: string; after: string } {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return { before: el.textContent ?? '', after: '' };
  const range = sel.getRangeAt(0);
  const beforeRange = document.createRange();
  beforeRange.setStart(el, 0);
  beforeRange.setEnd(range.startContainer, range.startOffset);
  const before = beforeRange.toString();
  const after = (el.textContent ?? '').slice(before.length);
  return { before, after };
}
```

### Anti-Patterns to Avoid

- **Firing API on every keydown**: Debounce content saves at ~1000ms idle. Only structural operations (Enter, Tab, Backspace-at-start, Delete-at-end, move) fire immediately.
- **Nesting multiple SortableContexts for the tree**: Use a single flattened SortableContext with custom collision detection. Nested contexts prevent cross-level dragging.
- **Storing collapse state in local state only**: `is_collapsed` must be PATCHed to server — the requirement explicitly says "persisted per user."
- **Rebuilding full document tree after every mutation**: Merge server response into existing BulletMap. Only full refetch on page load.
- **Using browser's native undo (Ctrl+Z propagating to browser)**: Every contenteditable must call `e.preventDefault()` on Ctrl+Z/Y and POST to `/api/undo` instead.
- **Fetching bullets incrementally (child by child)**: Load full document in one call (`GET /api/documents/:id/bullets` returns all bullets flat). Client builds the tree.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Drag-and-drop with touch support | Custom mouse/touch event tracking | @dnd-kit/core + @dnd-kit/sortable (already installed) | Handles pointer, touch, keyboard accessibility, scroll containers |
| FLOAT8 midpoint position | Custom position recomputation | Same `computeDocumentInsertPosition` pattern from documentService.ts | Race condition safe, server-side only |
| Undo history persistence | localStorage undo stack | undo_events + undo_cursors tables (already in schema) | Survives refresh per UNDO-03; cross-document per UNDO-02 |
| Text cursor position tracking | Custom selection API | `window.getSelection()` / `Range` API | Standard DOM API, sufficient for plain text contenteditable |

**Key insight:** The DnD and undo infrastructure are the most complex custom pieces. Both have clear patterns from the existing codebase (DnD: dnd-kit flatten approach; undo: the schema is already designed for forward/inverse op pairs).

---

## Common Pitfalls

### Pitfall 1: Undo Not Recording All Mutations
**What goes wrong:** A bullet operation succeeds but no undo_event is created. Ctrl+Z does nothing.
**Why it happens:** The mutation and the undo recording are separate DB calls — if the recording is forgotten or the transaction rolls back, they get out of sync.
**How to avoid:** Every bulletService function that mutates bullets must call `recordUndoEvent` inside the same transaction. Create a service-layer wrapper or use a consistent transaction pattern that enforces this.
**Warning signs:** undo/status returns `can_undo: false` after a mutation.

### Pitfall 2: Cursor Position Lost After Optimistic Update
**What goes wrong:** User types, optimistic update re-renders BulletContent, cursor jumps to end of text.
**Why it happens:** React re-renders contenteditable and resets the DOM, losing cursor position.
**How to avoid:** Debounce content saves so re-renders don't happen mid-typing. During the debounce window, only the local `textContent` value changes (no React state update). Trigger React state update only when debounce fires (on save, not on keydown).
**Warning signs:** Cursor jumping while typing fast.

### Pitfall 3: Tab Key Loses Focus
**What goes wrong:** Pressing Tab inside a contenteditable moves browser focus to next focusable element instead of indenting.
**Why it happens:** Tab's default browser behavior is focus traversal.
**How to avoid:** `e.preventDefault()` on Tab keydown in the contenteditable handler. This is already accounted for in the keyboard handler pattern above.

### Pitfall 4: Enter on Mobile Creates `<br>` or `<div>`
**What goes wrong:** On mobile, pressing Enter in a contenteditable inserts a `<br>` or wraps in `<div>` instead of triggering the outliner split.
**Why it happens:** Mobile browsers handle Enter differently than desktop. `input` events fire instead of `keydown` on some mobile keyboards.
**How to avoid:** Use `onInput` event as a secondary handler to detect Enter on mobile. Also handle `e.preventDefault()` on keydown. Tested on the actual device before phase close (per STATE.md blocker).
**Warning signs:** Works on desktop, breaks on mobile Safari.

### Pitfall 5: DnD Dot-Click vs Drag Disambiguation
**What goes wrong:** Single click on bullet dot triggers a drag instead of zoom.
**Why it happens:** `PointerSensor` starts drag on any pointer move — even a tiny shake during click.
**How to avoid:** Configure `PointerSensor` with `activationConstraint: { distance: 5 }` (5px threshold). This ensures tiny jitter doesn't activate drag.
```typescript
const sensors = useSensors(
  useSensor(PointerSensor, {
    activationConstraint: { distance: 5 },
  })
);
```
**Warning signs:** Zoom not firing on dot click; drag activating on tap.

### Pitfall 6: Soft Delete Not Filtering in Client
**What goes wrong:** Deleted bullets reappear after undo because client map still contains them.
**Why it happens:** Server returns updated bullet with `deletedAt: null` after undo, but client may not re-filter.
**How to avoid:** Always filter `b.deletedAt === null` when rendering. React Query refetch after undo response should handle this, but ensure the tree render always checks `deletedAt`.

### Pitfall 7: Breadcrumb Ancestor Path When Bullets Load
**What goes wrong:** Breadcrumb shows "..." for all ancestors because the ancestor chain needs to be computed, but bullet data loads asynchronously.
**Why it happens:** On page load with a zoom hash, the bullet map may not be loaded yet.
**How to avoid:** Show loading state in breadcrumb (or no breadcrumb) until bullet query succeeds. Once bullets are loaded, walk up the parentId chain to build the ancestor array.

---

## Code Examples

### Server: Bullet Route Structure

```typescript
// server/src/routes/bullets.ts
import { Router } from 'express';
import { requireAuth } from '../middleware/auth.js';
import { z } from 'zod';

export const bulletsRouter = Router();
bulletsRouter.use(requireAuth);

// GET full document tree (flat list)
bulletsRouter.get('/documents/:docId/bullets', async (req, res) => {
  // Returns flat array, client builds tree
  // WHERE document_id = $1 AND user_id = $2 AND deleted_at IS NULL
  // ORDER BY position ASC
});

// POST create bullet
bulletsRouter.post('/', async (req, res) => {
  // body: { documentId, parentId, afterId, content }
  // Calls bulletService.createBullet (computes position, records undo event)
});

// PATCH update bullet (content, is_complete, is_collapsed)
bulletsRouter.patch('/:id', async (req, res) => { /* ... */ });

// DELETE soft-delete bullet
bulletsRouter.delete('/:id', async (req, res) => {
  // Sets deleted_at = now()
  // inverse_op = { type: 'restore', id: req.params.id }
});

// POST semantic indent (make child of previous sibling)
bulletsRouter.post('/:id/indent', async (req, res) => { /* ... */ });

// POST semantic outdent (become sibling of parent)
bulletsRouter.post('/:id/outdent', async (req, res) => { /* ... */ });

// POST semantic move (drag-and-drop result)
bulletsRouter.post('/:id/move', async (req, res) => {
  // body: { newParentId, afterId }
  // Moves bullet + all descendants (parentId change + new position)
});
```

### Client: BulletTree DnD Context

```typescript
// BulletTree.tsx — DnD setup
import {
  DndContext, DragOverlay, PointerSensor,
  useSensor, useSensors, closestCenter
} from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';

export function BulletTree({ documentId, zoomedBulletId }: Props) {
  const { data: flatBullets } = useBullets(documentId);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState(0);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  );

  const bulletMap = useMemo(() => buildBulletMap(flatBullets ?? []), [flatBullets]);
  const rootId = zoomedBulletId ?? null;
  const flatItems = useMemo(() => flattenTree(bulletMap, rootId), [bulletMap, rootId]);

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={({ active }) => setActiveId(active.id as string)}
      onDragMove={({ delta }) => setDragOffset(delta.x)}
      onDragEnd={handleDragEnd}
    >
      <SortableContext
        items={flatItems.map(f => f.id)}
        strategy={verticalListSortingStrategy}
      >
        {flatItems.map(item => (
          <BulletNode key={item.id} bullet={item} depth={item.depth} />
        ))}
      </SortableContext>
      <DragOverlay>
        {activeId ? <BulletNode bullet={bulletMap[activeId]} depth={0} isDragOverlay /> : null}
      </DragOverlay>
    </DndContext>
  );
}
```

### Server: Undo Route

```typescript
// server/src/routes/undo.ts
export const undoRouter = Router();
undoRouter.use(requireAuth);

undoRouter.post('/undo', async (req, res) => {
  const user = req.user as { id: string };
  const result = await undoService.undo(user.id);
  // result: { affectedBullets: Bullet[], undoStatus: { canUndo, canRedo } }
  return res.json(result);
});

undoRouter.post('/redo', async (req, res) => {
  const user = req.user as { id: string };
  const result = await undoService.redo(user.id);
  return res.json(result);
});

undoRouter.get('/undo/status', async (req, res) => {
  const user = req.user as { id: string };
  const status = await undoService.getStatus(user.id);
  return res.json(status);
});
```

---

## State of the Art

| Old Approach | Current Approach | Impact for This Project |
|--------------|------------------|------------------------|
| react-beautiful-dnd (deprecated 2022) | @dnd-kit/core + @dnd-kit/sortable | Already using @dnd-kit — correct choice |
| Nested SortableContexts for tree DnD | Single flattened SortableContext with projected depth | Must use flatten approach for cross-level moves |
| Client-side undo stack (array of states) | Server-side forward/inverse op log | Already decided — undo_events table exists |
| ProseMirror / single document model | Plain contenteditable per bullet | Already decided — locked in CONTEXT.md |
| Integer position for sibling order | FLOAT8 fractional midpoint | Already in schema — locked decision |
| localStorage for collapse/zoom state | DB (is_collapsed) + URL (zoom) | Phase 2 implements both correctly |

---

## Open Questions

1. **iOS Safari Enter key on mobile**
   - What we know: STATE.md flags this as a blocker: "programmatic `.focus()` on new bullet must stay in the same synchronous event handler as the Enter keypress"
   - What's unclear: Whether the `keydown` event fires synchronously on all iOS keyboard types (custom keyboard apps may differ)
   - Recommendation: Implement desktop first, test on iOS device before marking phase done. Have a fallback: if `keydown` doesn't fire, use `beforeinput` event with `inputType === 'insertParagraph'` as the mobile Enter trigger.

2. **Drag-and-drop on mobile (Phase 2 scope)**
   - What we know: BULL-05 says "desktop + mobile" — but MOB-04 (touch drag handles) is Phase 4
   - What's unclear: Whether basic pointer-based DnD (dnd-kit's PointerSensor works on touch) is sufficient for Phase 2, or if mobile DnD should be deferred entirely to Phase 4
   - Recommendation: PointerSensor in dnd-kit supports touch natively. Ship it as-is for Phase 2. Phase 4 adds dedicated touch handles (MOB-04).

3. **Undo for bulk-delete completed (BULL-14)**
   - What we know: BULL-14 says "irreversible; permanently purges" — no undo
   - What's unclear: Should this operation still be recorded in undo_events (even if not undoable) to preserve the undo sequence numbering?
   - Recommendation: Do NOT record in undo_events. The irreversible nature is explicit. Just hard-DELETE WHERE is_complete=true AND document_id=X.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | vitest 4.0.x (client) / vitest 3.0.x (server) |
| Client config | `client/vite.config.ts` — jsdom environment, setupFiles: src/test/setup.ts |
| Server config | `server/vitest.config.ts` — node environment |
| Client quick run | `cd client && npx vitest run` |
| Server quick run | `ssh root@192.168.1.50 "cd /root/notes/server && .venv/bin/pytest --tb=short -q"` (or locally: `cd server && npx vitest run`) |
| Full suite | Both client and server vitest run |

Note: The server already uses `vitest` + `supertest` in `server/tests/`. The MEMORY.md references `.venv/bin/pytest` but this is likely a MEMORY.md error — the server uses `vitest`, not pytest. Use `cd server && npx vitest run` locally or check the actual server test runner.

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BULL-01 | Create bullet via Enter splits at cursor | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| BULL-02 | Indent: bullet becomes child of previous sibling | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| BULL-03 | Outdent: bullet becomes sibling of parent | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| BULL-04 | Move up/down: entire subtree moves | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| BULL-05 | Drag-and-drop reorder maintains children | unit (service) | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| BULL-06 | Collapse persisted (is_collapsed PATCH) | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| BULL-07 | Zoom URL encodes bullet id in hash | unit (client) | `cd client && npx vitest run` | ❌ Wave 0 |
| BULL-11 | Soft delete sets deletedAt; bullets excluded from fetch | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| BULL-12 | Mark complete: is_complete persisted | unit | `cd server && npx vitest run tests/bullets.test.ts` | ❌ Wave 0 |
| UNDO-01 | Undo last action reverses bullet mutation | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ Wave 0 |
| UNDO-02 | 50-step cap: oldest event dropped at 51st | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ Wave 0 |
| UNDO-03 | undo_events persists (DB-level, covered by UNDO-01) | unit | same as UNDO-01 | ❌ Wave 0 |
| UNDO-04 | Undo deletion: sets deletedAt=null, restores children | unit | `cd server && npx vitest run tests/undo.test.ts` | ❌ Wave 0 |
| KB-01..07 | Keyboard shortcuts fire correct operations | unit (client) | `cd client && npx vitest run` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `cd server && npx vitest run tests/bullets.test.ts` (service logic tests)
- **Per wave merge:** `cd server && npx vitest run && cd ../client && npx vitest run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `server/tests/bullets.test.ts` — covers BULL-01 through BULL-12 service functions (position computation, indent/outdent logic, soft delete)
- [ ] `server/tests/undo.test.ts` — covers UNDO-01 through UNDO-04 (recordUndoEvent, undo, redo, 50-step cap)
- [ ] `client/src/test/bulletTree.test.tsx` — covers BULL-07 (URL zoom), KB shortcuts, BulletContent keyboard handler split/merge logic
- [ ] `server/tests/helpers/` — shared fixtures for bullet tree setup (already exists as directory, needs bullet-specific fixtures)

---

## Sources

### Primary (HIGH confidence)
- Existing codebase: `server/db/schema.ts` — verified bullets, undo_events, undo_cursors tables exactly match what Phase 2 needs
- Existing codebase: `client/src/hooks/useDocuments.ts` — verified optimistic update pattern (onMutate/onError/onSettled)
- Existing codebase: `server/src/services/documentService.ts` — verified FLOAT8 midpoint algorithm
- Existing codebase: `client/package.json` — verified @dnd-kit/core 6.3.1 and @dnd-kit/sortable 8.0.0 already installed
- [@dnd-kit docs](https://docs.dndkit.com/presets/sortable) — official sortable preset docs

### Secondary (MEDIUM confidence)
- [dnd-kit official tree example](https://github.com/clauderic/dnd-kit/blob/master/stories/3%20-%20Examples/Tree/SortableTree.tsx) — flatten/projected-depth pattern; confirmed as the standard dnd-kit tree approach
- [React + dnd-kit tree sortable (Dec 2024)](https://dev.to/fupeng_wang/react-dnd-kit-implement-tree-list-drag-and-drop-sortable-225l) — flatten/ancestorIds pattern cross-verified
- [dnd-kit discussions #1070](https://github.com/clauderic/dnd-kit/discussions/1070) — cross-level drag patterns

### Tertiary (LOW confidence)
- [dnd-kit-sortable-tree](https://github.com/Shaddix/dnd-kit-sortable-tree) — third-party wrapper, not recommended; LOW confidence on maintenance status

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already installed and in use
- Architecture: HIGH — schema exists, patterns established in Phase 1 codebase
- DnD flatten/unflatten: MEDIUM — documented pattern in official dnd-kit examples, not verified against actual working code in this codebase
- Pitfalls: HIGH — iOS Safari issue flagged in STATE.md; others from standard contenteditable/React patterns

**Research date:** 2026-03-09
**Valid until:** 2026-06-09 (stable ecosystem — @dnd-kit, React Query, React Router are stable releases)
