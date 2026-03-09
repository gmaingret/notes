# Phase 2: Core Outliner - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Full bullet tree for a document: create, edit, indent/outdent, drag-reorder, collapse/expand, zoom in/out with breadcrumb, and server-side undo — all keyboard-driven on desktop, all surviving page refresh. Markdown rendering, tags, comments, attachments, and mobile touch gestures are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Enter / Backspace editing behavior

- **Enter in middle of text**: Split the bullet — text before cursor stays in current bullet, text after cursor moves into a new bullet inserted below
- **Enter on empty bullet**: Outdent it (if indented); if already at root level, create a new blank bullet below
- **Enter on a bullet with children**: New bullet inserted as the **first child** (not as a sibling after the subtree)
- **Backspace at start of bullet**: Merge with the bullet above — current bullet's content appended to end of previous bullet; cursor lands at the join point
- **Backspace on a bullet that has children**: Block the merge; flash a visual warning (subtle shake or indicator). No merge.
- **Delete at end of bullet**: Merge with bullet below (mirror of Backspace-merge)
- **Tab on first root-level bullet (nothing to indent under)**: Silent no-op — no animation, no error
- **Ctrl/Cmd+Arrow (move bullet)**: Moves the entire subtree — bullet plus all its descendants move together

### Collapse chevron

- Chevron is **only visible on bullets that have children** (leaf bullets have no chevron)
- Position: **left of the bullet dot** in a separate column (Dynalist style, not replacing the dot)
- Collapsed state indicator: chevron **rotates to ▶** (pointing right) only — no child count badge
- Collapse/expand animation: **instant**, no transition animation

### Drag-and-drop

- **The bullet dot itself is the drag handle** (no separate ⠿ handle element)
- Disambiguate click vs drag: **drag threshold** (~5px) — short click triggers zoom, drag beyond threshold triggers reorder
- Single click on bullet dot = zoom in immediately
- Drop indicator: **horizontal line between rows** showing insert position (not a row highlight)

### Zoom and breadcrumb

- Breadcrumb shows: **Document title › Ancestor 1 › … › Current bullet** — full path, middle ancestors truncated with `…` when path is long; doc title and current bullet always visible
- Breadcrumb placement: **replaces the document title area** when zoomed (h1 title disappears; breadcrumb takes its spot)
- Clicking any ancestor in the breadcrumb: **zooms directly to that level** (not step-by-step)
- Ctrl/Cmd+[ (KB-04): walks back one level (to parent)
- Zoom persistence: **URL-based** — zoom state encoded in URL hash or path so page refresh returns to the zoomed view

### Undo event recording

- **Text typing**: debounced — ~1s pause or word boundary creates one undo step (not per keystroke)
- **Structural operations** (indent, outdent, move, delete, collapse toggle): **immediately recorded** as a discrete undo event, no debounce
- Storage: **server-side only** (`undo_events` + `undo_cursors` tables, already set up in schema)
- Scope: **global per user** — Ctrl+Z can undo actions from any document (per UNDO-02 requirement)
- 50-step limit enforcement: **oldest event dropped (circular buffer / FIFO)** when 51st event is recorded

### Claude's Discretion

- Exact debounce timing (1s is a guideline, can tune)
- Visual design of the flash/shake warning for blocked Backspace-merge
- URL hash format for zoom state (`#bullet/<id>` or query param)
- How long paths truncate exactly (e.g., show 2 ancestors before `…` vs 3)
- Drag-and-drop library choice (STATE.md notes @dnd-kit community patterns for nested trees are sparse — research needed)
- Exact styling of the horizontal drop line indicator

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets

- `useUiStore` (Zustand + persist): Already handles `lastOpenedDocId` and `sidebarOpen` with localStorage persistence. Can be extended with `zoomedBulletId` or zoom state, but URL-based zoom state was chosen — so this store may not need extension for zoom.
- `apiClient` from `src/api/client`: Established pattern for all server calls including `.download()` for blobs. Bullet API hooks will follow the same shape.
- `useDocuments.ts` pattern: React Query hooks with optimistic updates (`onMutate` / `onError` rollback). Bullet hooks should follow this exact pattern.

### Established Patterns

- **React Query for server state**: All data fetching goes through `useQuery` / `useMutation` with `queryClient.invalidateQueries`. Bullets follow the same pattern.
- **Optimistic updates**: `useReorderDocument` shows the full pattern — `onMutate` applies local change, `onError` rolls back, `onSettled` revalidates. Bullet reorder, indent, delete should all use this.
- **Zustand with persist for UI state**: `uiStore.ts` shows the pattern. If any bullet UI state needs to persist client-side (beyond URL-based zoom), extend this store.
- **Plain contenteditable per bullet**: Locked from STATE.md — one `<div contentEditable>` per bullet row. No ProseMirror, no single-document model.
- **FLOAT8 midpoint positioning**: `position: doublePrecision` on bullets table — sibling order computed as midpoint between neighbors, never recomputed.

### Existing Schema

- `bullets` table: `id`, `documentId`, `userId`, `parentId` (nullable = root bullet), `content`, `position` (FLOAT8), `isComplete`, `isCollapsed`, `deletedAt` (soft delete)
- `undo_events` table: `id`, `userId`, `seq`, `schemaVersion`, `eventType`, `forwardOp` (jsonb), `inverseOp` (jsonb)
- `undo_cursors` table: `userId`, `currentSeq` — points to current undo position for each user
- All tables exist in DB from Phase 1 migrations. Phase 2 adds no schema changes needed (verify on migration run).

### Integration Points

- `DocumentView.tsx`: Placeholder — Phase 2 replaces the entire content with the real bullet tree component
- `AppPage.tsx`: Hosts `DocumentView`; will need to pass bullet zoom state via URL params
- React Router (or existing router): Zoom state goes in the URL — check existing router setup to determine hash vs param approach
- Server routes: New `server/src/routes/bullets.ts` and `server/src/services/bulletService.ts` mirror the document routes pattern

</code_context>

<specifics>
## Specific Ideas

- Inspiration: Workflowy and Dynalist — the split-on-Enter behavior and outdent-on-empty-Enter specifically match Workflowy's ergonomics
- The dot-as-drag-handle with drag-threshold-for-zoom is a deliberate choice to minimize UI elements while keeping two actions (drag and zoom) on one target
- Zoom persistence via URL is explicitly wanted — users should be able to bookmark or share a zoomed bullet URL

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-core-outliner*
*Context gathered: 2026-03-09*
