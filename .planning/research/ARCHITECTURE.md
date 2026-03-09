# Architecture Research

**Domain:** Self-hosted multi-user infinite outliner / PKM web app
**Researched:** 2026-03-09
**Confidence:** HIGH (tree model, undo pattern, file storage) / MEDIUM (API shape, frontend hierarchy)

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser)                          │
├──────────────────────────────────────────────────────────────────┤
│  ┌───────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │  Doc Sidebar  │  │  Tree Editor │  │  Toolbar / Search     │  │
│  │  (doc list)   │  │  (root view) │  │  (global actions)     │  │
│  └──────┬────────┘  └──────┬───────┘  └──────────┬────────────┘  │
│         │                  │                     │               │
│  ┌──────┴──────────────────┴─────────────────────┴────────────┐  │
│  │                   Client State Layer                        │  │
│  │   (document tree, collapsed state, optimistic mutations)    │  │
│  └─────────────────────────────┬───────────────────────────────┘  │
├────────────────────────────────┼─────────────────────────────────┤
│                                │  HTTP / REST + multipart        │
├────────────────────────────────┼─────────────────────────────────┤
│                        SERVER (Node.js / Express)                │
├────────────────────────────────┼─────────────────────────────────┤
│  ┌─────────────┐  ┌────────────┴──┐  ┌───────────────────────┐   │
│  │  Auth       │  │  Bullet API   │  │  Attachment API       │   │
│  │  (JWT/OAuth)│  │  (CRUD+ops)   │  │  (upload/download)    │   │
│  └──────┬──────┘  └───────┬───────┘  └──────────┬────────────┘   │
│         │                 │                     │                │
│  ┌──────┴─────────────────┴─────────────────────┴─────────────┐  │
│  │                    Service Layer                            │  │
│  │  (tree ops, undo stack manager, search, attachment mgmt)   │  │
│  └─────────────────────────────┬───────────────────────────────┘  │
├────────────────────────────────┼─────────────────────────────────┤
│                        DATA LAYER                                │
│  ┌──────────────────────┐      │      ┌──────────────────────┐   │
│  │   PostgreSQL         │◄─────┘      │  Docker Volume       │   │
│  │  (bullets, docs,     │             │  /data/attachments   │   │
│  │   undo_events,       │             │  (raw files)         │   │
│  │   users, comments)   │             └──────────────────────┘   │
│  └──────────────────────┘                                        │
└──────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| Doc Sidebar | List documents, switch active doc, rename/delete | React component, fetches doc list on mount |
| Tree Editor | Render bullet tree, handle keyboard/mouse/touch, show collapse state | Recursive React components with virtual DOM |
| Client State Layer | Hold loaded tree in memory, track optimistic changes, manage collapsed nodes | React state + useReducer or Zustand |
| Auth (server) | Issue JWTs, validate Google OAuth tokens, protect all routes | Express middleware + passport or custom |
| Bullet API | CRUD for documents + bullets, tree operations (move, indent, outdent) | Express routes → Service layer → PostgreSQL |
| Attachment API | Multipart upload, file serve, metadata, delete | Multer middleware → disk → PostgreSQL metadata |
| Service Layer | Orchestrate multi-step DB operations, enforce ownership, apply undo events | Plain JS modules called by route handlers |
| PostgreSQL | Persist all structured data: users, docs, bullets, undo stack, comments | Single DB, multiple tables |
| Docker Volume | Store raw attachment bytes at `/data/attachments` | Multer diskStorage pointing to mounted path |

---

## Tree Data Model (PostgreSQL)

### Recommended: Adjacency List + Float Position

Use a single `bullets` table with `parent_id` (NULL = root bullet of document) and a `position` float column for sibling ordering. This is the correct choice for an outliner because:

- Updates are frequent (drag, indent, outdent happen constantly)
- Nesting is arbitrarily deep but queries are per-document (bounded)
- PostgreSQL recursive CTEs (`WITH RECURSIVE`) handle all-descendants queries cleanly
- Float position enables insert-between without touching sibling rows

**Schema:**

```sql
CREATE TABLE documents (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title      TEXT NOT NULL DEFAULT 'Untitled',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bullets (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  parent_id   UUID REFERENCES bullets(id) ON DELETE CASCADE,
  content     TEXT NOT NULL DEFAULT '',
  position    FLOAT NOT NULL DEFAULT 1.0,   -- fractional index among siblings
  is_complete BOOLEAN NOT NULL DEFAULT false,
  is_collapsed BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX bullets_document_parent ON bullets(document_id, parent_id, position);
CREATE INDEX bullets_content_fts ON bullets USING GIN(to_tsvector('english', content));
```

**Sibling ordering with fractional indexing:**

When inserting between position 1.0 and 2.0, assign 1.5. Between 1.5 and 2.0, assign 1.75. This never requires touching adjacent rows. When positions get too dense (gap < 0.001), re-normalize the affected parent's children in a single batch UPDATE. This is the same technique Figma and Linear use for ordered lists.

**Querying a full document tree:**

```sql
WITH RECURSIVE tree AS (
  -- root bullets (parent_id IS NULL for this document)
  SELECT id, parent_id, content, position, is_complete, is_collapsed, 0 AS depth
  FROM bullets
  WHERE document_id = $1 AND parent_id IS NULL AND user_id = $2

  UNION ALL

  SELECT b.id, b.parent_id, b.content, b.position, b.is_complete, b.is_collapsed, t.depth + 1
  FROM bullets b
  JOIN tree t ON b.parent_id = t.id
  WHERE b.document_id = $1
)
SELECT * FROM tree ORDER BY depth, position;
```

The client receives a flat list of nodes and reconstructs the tree in memory using a `parent_id` map. This avoids complex recursive result formatting on the server.

### Why Not Closure Table

Closure table would be the right choice if the app needed frequent "find all ancestors of X" queries (like tag inheritance or permission trees). For an outliner, queries are nearly always top-down (document root → all descendants). Adjacency list with CTE covers this cleanly and has far simpler update logic.

### Why Not Nested Sets / Materialized Path

Nested sets require updating ALL right-values when inserting anywhere in the tree — catastrophic for an outliner where inserts are the primary operation. Materialized path (`ltree`) requires rebuilding paths on re-parent. Both are wrong for this write-heavy pattern.

---

## Undo/Redo System Architecture

### Design: Command Table (Server-Side Event Log)

The requirement is 50 levels, per-user, global (crosses document boundaries), survives page refresh. The correct architecture is a persisted command log in PostgreSQL, not client-side undo stacks.

**Schema:**

```sql
CREATE TABLE undo_events (
  id          BIGSERIAL PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- pointer into this user's linear history
  seq         INTEGER NOT NULL,  -- increments per user, 1-based
  event_type  TEXT NOT NULL,     -- 'bullet_create', 'bullet_update', 'bullet_delete', 'bullet_move', etc.
  forward_op  JSONB NOT NULL,    -- the operation as applied ("do")
  inverse_op  JSONB NOT NULL,    -- the inverse operation ("undo")
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

  UNIQUE(user_id, seq)
);

-- Track where in the user's history stack they currently are
CREATE TABLE undo_cursors (
  user_id    UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  current_seq INTEGER NOT NULL DEFAULT 0  -- 0 = nothing to undo
);
```

**How it works:**

1. Every mutating API call (create bullet, update content, move bullet, delete) is wrapped in a transaction that:
   - Applies the change to `bullets`
   - Appends a row to `undo_events` with `forward_op` (what was done) and `inverse_op` (how to reverse it)
   - Advances `undo_cursors.current_seq` for this user
   - Truncates any events with `seq > current_seq` (clears redo stack on new action)
   - Enforces 50-item cap: deletes events where `seq < current_seq - 50`

2. `POST /api/undo`: reads `inverse_op` at `current_seq`, applies it, decrements cursor
3. `POST /api/redo`: reads `forward_op` at `current_seq + 1`, applies it, increments cursor

**Example undo_event row:**

```json
{
  "event_type": "bullet_update",
  "forward_op": { "id": "abc123", "content": "New text" },
  "inverse_op": { "id": "abc123", "content": "Old text" }
}
```

```json
{
  "event_type": "bullet_move",
  "forward_op": { "id": "abc123", "parent_id": "parent-b", "position": 1.5 },
  "inverse_op": { "id": "abc123", "parent_id": "parent-a", "position": 3.0 }
}
```

**Why not event sourcing (replay-based):**
Full event sourcing requires replaying the entire event history to derive current state. For an outliner with thousands of bullets and 50-step undo, the forward/inverse command pair approach is simpler, faster, and sufficient. Each undo is O(1) — apply one inverse op, no replay needed.

---

## File Attachment Architecture

### Storage: Multer + Docker Volume

Files are stored on the Docker volume at `/data/attachments`. PostgreSQL stores metadata only. This is the simplest correct design for a single-server self-hosted app.

**Schema:**

```sql
CREATE TABLE attachments (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bullet_id   UUID NOT NULL REFERENCES bullets(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  filename    TEXT NOT NULL,       -- original uploaded filename
  stored_name TEXT NOT NULL,       -- UUID-based name on disk (no collisions)
  mime_type   TEXT NOT NULL,
  size_bytes  BIGINT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Server file path convention:**

```
/data/attachments/{user_id}/{attachment_id}_{stored_name}
```

User-based subdirectories prevent directory entry explosion at scale. `stored_name` is a UUID so no two uploads ever collide regardless of original filename.

**Upload flow:**

```
Browser → POST /api/attachments (multipart/form-data)
  → Multer diskStorage (validates size ≤ 100MB, writes to /data/attachments/{user_id}/)
  → INSERT into attachments table
  → Return attachment metadata JSON

Download: GET /api/attachments/:id
  → Verify ownership (user_id match)
  → res.sendFile() pointing at disk path
  → Content-Disposition: attachment for forced download
```

**Critical configuration:**

```javascript
// Multer must validate size BEFORE writing to disk (limits.fileSize)
// Directory must exist at container start (use mkdirSync in startup)
// Never expose raw filesystem path to client
```

---

## API Design: Bullet Operations

### Principle: Semantic Operations, Not Raw SQL

The client sends intent ("move bullet X to be after bullet Y under parent Z"), not field patches. The server translates intent to the correct position float and undo event. This keeps client code simple and undo reliable.

### Endpoint Map

```
Documents:
  GET    /api/documents                    list user's documents
  POST   /api/documents                    create document
  GET    /api/documents/:id/bullets        fetch full document tree (flat list + parent_id)
  PATCH  /api/documents/:id                rename
  DELETE /api/documents/:id                delete + cascade

Bullets:
  POST   /api/bullets                      create (body: document_id, parent_id, after_id, content)
  PATCH  /api/bullets/:id                  update content or is_complete or is_collapsed
  DELETE /api/bullets/:id                  delete (soft-delete with is_deleted flag OR hard delete)
  POST   /api/bullets/:id/move             semantic move (body: new_parent_id, after_id)
  POST   /api/bullets/:id/indent           indent under previous sibling
  POST   /api/bullets/:id/outdent          outdent to grandparent, after current parent

Undo/Redo:
  POST   /api/undo                         undo last event for authenticated user
  POST   /api/redo                         redo next event for authenticated user
  GET    /api/undo/status                  { can_undo: bool, can_redo: bool }

Attachments:
  POST   /api/attachments                  upload (multipart, body: bullet_id)
  GET    /api/attachments/:id              download/serve file
  DELETE /api/attachments/:id              delete file + metadata

Comments:
  GET    /api/bullets/:id/comments         list comments for a bullet
  POST   /api/bullets/:id/comments         add comment
  DELETE /api/comments/:id                 delete comment

Search:
  GET    /api/search?q=...&doc=...         full-text search, optional doc filter

Auth:
  POST   /api/auth/register               email + password
  POST   /api/auth/login                  email + password → JWT
  POST   /api/auth/google                 Google OAuth code → JWT
  POST   /api/auth/logout                 invalidate token (if using refresh tokens)
```

### Key Design Decisions

**`after_id` for positioning:** Rather than sending a raw `position` float, the client sends `after_id` (UUID of the bullet this new bullet should appear after, null = prepend). The server computes the new position float as `(position[after] + position[next_sibling]) / 2`. This decouples client from the float ordering internals.

**Indent/outdent as semantic endpoints:** These are not just moves — they also record the correct inverse operation for undo. A generic PATCH would force the client to compute the undo state. Dedicated endpoints let the server own undo entirely.

**Bulk document load:** `GET /api/documents/:id/bullets` returns all bullets in the document in a single request as a flat array. The client builds the tree in memory. This avoids N+1 and makes zoom-into-bullet fast (client already has all nodes).

**Soft delete vs hard delete for bullets:** Use `is_deleted` + `deleted_at` columns. This enables undo of bullet deletions without storing the entire subtree in the undo_events JSONB. Undo just sets `is_deleted = false`. Periodic cleanup job permanently removes rows older than 30 days with `is_deleted = true`.

---

## Frontend Component Hierarchy

```
<App>
  ├── <AuthProvider>          -- JWT in memory (not localStorage), refresh via cookie
  │
  └── <OutlinerLayout>
        ├── <Sidebar>
        │     ├── <DocumentList>    -- fetch GET /api/documents, sorted by updated_at
        │     └── <BookmarkList>    -- bullets with is_bookmarked=true
        │
        └── <MainPanel>
              ├── <Breadcrumbs>     -- zoom path from root to current zoom node
              ├── <SearchBar>       -- Ctrl+P opens, calls GET /api/search
              │
              └── <DocumentView>
                    ├── <DocumentTitle>     -- editable H1
                    └── <BulletTree>        -- root component, renders from zoom node down
                          └── <BulletNode> (recursive)
                                ├── <BulletHandle>    -- drag target + collapse toggle
                                ├── <BulletContent>   -- contenteditable span
                                ├── <BulletMeta>      -- tags, mentions, dates as chips
                                ├── <AttachmentList>  -- pills for attached files
                                ├── <CommentBadge>    -- count, opens comment drawer
                                └── <BulletChildren>  -- recursive BulletNode instances
```

**Contenteditable vs ProseMirror for bullets:**

Use a plain `contenteditable` span per bullet with a custom keyboard handler, not ProseMirror. Reason: ProseMirror models documents as flat rich text; an outliner models a tree of nodes where Enter creates a new sibling node (not a new paragraph within the same node). ProseMirror's document model fights this structure. The per-bullet contenteditable pattern is what Workflowy, Dynalist, Roam Research, and Logseq all use.

Each `<BulletContent>` is a single-line contenteditable that:
- Handles Enter → create sibling bullet via API call
- Handles Tab → indent bullet via API call
- Handles Shift+Tab → outdent bullet via API call
- Handles Backspace on empty bullet → delete + focus previous
- Handles Ctrl+Z/Y → POST /api/undo or /api/redo
- Parses `#tag`, `@mention`, `!!date` in real-time for chip rendering (regex over text content, does not modify the stored text)

**State management:**

Use React's `useReducer` at the `<DocumentView>` level. The tree state is a normalized map: `{ [id]: BulletNode }`. All operations dispatch actions that update the map optimistically, then confirm or rollback on server response. Do not use a global state library (Redux, Zustand) for v1 — single-document state is bounded and component-local is sufficient.

**Collapse state:** Stored in `bullets.is_collapsed` on the server and fetched as part of the document load. Toggling collapse calls `PATCH /api/bullets/:id` with `{ is_collapsed: true/false }`. Do not store collapse state in frontend-only localStorage; the requirement says "persisted per user."

---

## Recommended Project Structure

```
server/
├── routes/
│   ├── auth.js           # login, register, google oauth
│   ├── documents.js      # document CRUD + tree fetch
│   ├── bullets.js        # bullet CRUD + move/indent/outdent
│   ├── attachments.js    # upload/download/delete
│   ├── comments.js       # bullet comments
│   ├── search.js         # full-text search
│   └── undo.js           # undo/redo stack
├── services/
│   ├── bulletService.js  # tree operations, position calculation
│   ├── undoService.js    # event log, cursor management
│   ├── searchService.js  # FTS query construction
│   └── attachmentService.js
├── middleware/
│   ├── auth.js           # JWT verification middleware
│   └── upload.js         # multer configuration
├── db/
│   ├── pool.js           # pg Pool singleton
│   └── migrations/       # sequential SQL migration files
└── app.js                # Express setup, route mounting

client/src/
├── components/
│   ├── Sidebar/
│   ├── BulletTree/
│   │   ├── BulletNode.jsx
│   │   ├── BulletContent.jsx   # contenteditable + keyboard handler
│   │   └── BulletHandle.jsx    # drag + collapse toggle
│   ├── Search/
│   ├── Attachments/
│   └── Comments/
├── hooks/
│   ├── useDocument.js    # fetch + state for one document
│   ├── useUndo.js        # undo/redo keyboard bindings
│   └── useSearch.js
├── api/
│   └── client.js         # fetch wrapper with JWT header
└── App.jsx
```

---

## Data Flow

### Bullet Edit Flow

```
User types in <BulletContent>
    ↓ (onBlur or debounced onChange)
PATCH /api/bullets/:id { content: "new text" }
    ↓
bulletService.update(id, userId, { content })
    ↓ (single transaction)
UPDATE bullets SET content = $1 WHERE id = $2 AND user_id = $3
INSERT INTO undo_events (user_id, seq, event_type, forward_op, inverse_op)
UPDATE undo_cursors SET current_seq = seq WHERE user_id = $1
    ↓
200 OK { bullet: {...}, undo_status: { can_undo: true, can_redo: false } }
    ↓
Client updates local state + undo UI indicator
```

### Undo Flow

```
User presses Ctrl+Z
    ↓
POST /api/undo
    ↓
undoService.undo(userId)
    ↓
SELECT inverse_op FROM undo_events WHERE user_id=$1 AND seq=current_seq
    ↓ (apply inverse_op in transaction)
UPDATE bullets ... (inverse operation)
UPDATE undo_cursors SET current_seq = current_seq - 1
    ↓
200 OK { affected_bullets: [...], undo_status: { can_undo: bool, can_redo: bool } }
    ↓
Client merges affected_bullets into local tree state
```

### Document Load Flow

```
User opens document (or page refresh)
    ↓
GET /api/documents/:id/bullets
    ↓ (WITH RECURSIVE CTE, all bullets in one query)
Flat array of all bullets with { id, parent_id, position, content, ... }
    ↓
Client builds tree map: { [id]: node }
    ↓
<BulletTree> renders from zoom node (or document root)
```

---

## Suggested Build Order

This ordering reflects hard data dependencies:

1. **Database schema + migrations** — everything depends on this. Tables: users, documents, bullets, undo_events, undo_cursors, attachments, comments.

2. **Auth (server)** — all other endpoints require authentication. JWT middleware must exist before any protected route can be tested.

3. **Document + Bullet CRUD (server)** — core data model. Create/read/update/delete for documents and bullets. No move/indent/outdent yet. Validates the tree schema.

4. **Document load + basic tree render (client)** — fetch flat bullet list, build tree map, render `<BulletNode>` recursively. Validates the data model from the UI side.

5. **Keyboard editing (client)** — Enter to create sibling, Backspace to delete, Tab/Shift+Tab for indent/outdent. This is the core interaction loop and validates the API design before undo is added.

6. **Move/indent/outdent (server)** — tree mutation endpoints with position float logic. Depends on stable tree CRUD.

7. **Undo/redo system (server + client)** — wraps all existing mutations. Must come after the mutation endpoints are stable, because it requires knowing the exact inverse for each operation.

8. **Search** — depends on bulletservice and FTS index. Can be deferred until tree editing is solid.

9. **Attachments** — file upload/download is independent of tree logic. Can be parallelized with undo/redo but depends on stable bullet CRUD (needs bullet_id FK).

10. **Comments** — simplest feature, depends on bullets. Defer to near-end.

11. **Mobile touch interactions** — swipe gestures, long press. Layered on top of the working desktop tree editor.

---

## Scaling Considerations

This is a self-hosted single-user-per-instance app. The following is realistic, not theoretical:

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1-10 users | Single Docker container, single PostgreSQL instance. No changes needed. |
| 10-100 users | Add GIN indexes on FTS columns. Index `(document_id, parent_id, position)` on bullets. Monitor slow queries. Still monolith. |
| 100+ users | Connection pooling with PgBouncer. Consider separate document load endpoint with `EXPLAIN ANALYZE`. Still single-process. |
| 1000+ users | Read replicas for search. Consider caching document trees in Redis. Separate file-serving to nginx directly (bypass Node). Not in scope for v1. |

First bottleneck is almost certainly document load query performance for large documents (10,000+ bullets). Mitigate with the composite index and lazy loading of collapsed subtrees (skip fetching children of `is_collapsed=true` nodes).

---

## Anti-Patterns

### Anti-Pattern 1: Per-Bullet Network Requests on Keystroke

**What people do:** Fire a PATCH request on every keydown event in the bullet contenteditable.
**Why it's wrong:** 100ms debounce minimum. Raw keydown fires at 150+ events/second — will overwhelm the server and create a backlog of undo events.
**Do this instead:** Debounce content saves at 500ms idle. Only fire the request when the user pauses. Use onBlur as a final flush. The undo event is only created when the save fires, not on each keystroke.

### Anti-Pattern 2: Storing Collapsed State in localStorage

**What people do:** Track `collapsedNodes = Set<id>` in localStorage for "performance."
**Why it's wrong:** The requirement explicitly says collapse state is "persisted per user" — localStorage is per-browser, not per-user. Logging in from another browser loses all collapse state.
**Do this instead:** `is_collapsed` column on the `bullets` table. Debounce collapse PATCH calls at 200ms (fast enough to feel instant).

### Anti-Pattern 3: Storing Full Subtree Snapshot in undo_events

**What people do:** On any mutation, store the entire pre-mutation bullet subtree as the `inverse_op` JSONB.
**Why it's wrong:** Deleting a bullet with 500 descendants stores ~500 bullet snapshots per undo event. With 50 levels, this is potentially 25,000 bullet rows in JSONB blobs. It also makes the undo event non-atomic (replay vs. direct inverse).
**Do this instead:** Soft delete (`is_deleted = true`). The inverse of "delete bullet X and descendants" is "set `is_deleted = false` on bullet X" — PostgreSQL cascades handle the descendants automatically since they share `parent_id`. The undo event JSONB is one row: `{ "id": "X" }`.

### Anti-Pattern 4: Rebuilding the Full Tree on Every Mutation

**What people do:** After any bullet change, re-fetch `GET /documents/:id/bullets` to ensure consistency.
**Why it's wrong:** A 1000-bullet document takes a non-trivial recursive CTE query plus JSON serialization plus network round-trip — all to update one bullet's content.
**Do this instead:** Optimistic updates in the client state map. The server returns only the affected bullet(s) in its response. The client merges them into the existing local tree. Full refetch only on page load or explicit sync.

### Anti-Pattern 5: Using Integer Position for Sibling Ordering

**What people do:** `position INTEGER` with values 1, 2, 3... Reorder by UPDATE-ing all siblings.
**Why it's wrong:** Moving bullet 1 to position 50 in a 100-item list requires 50 UPDATE statements, all of which create undo events. Any concurrent operations race. The undo for a reorder becomes a batch of 50 inverse ops.
**Do this instead:** Float fractional indexing. One row touched for insert. One row touched for reorder. Periodic renormalization handles float precision limits (trivially rare in practice).

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Google OAuth | Server-side token exchange (auth code → user info), issue app JWT | Client ID in `.env`, never in client bundle |
| PostgreSQL | `pg` library with connection pool, no ORM | Direct SQL, migrations via sequential `.sql` files |
| Docker volume | Multer `diskStorage` pointing to `/data/attachments` | Directory must be pre-created at startup |
| Nginx (reverse proxy) | Passes all traffic to Node on port 3000, handles TLS | No changes to Node needed; Nginx owns HTTPS |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Route handler ↔ Service | Direct function call | Services return data objects, never Express res/req |
| Service ↔ Database | `pg` pool queries | Services own transactions; routes never call db directly |
| Service ↔ Undo system | undoService.record(userId, event) called at end of each mutation | All mutations must call this — easy to miss; enforce via wrapper |
| Client ↔ Server | REST JSON over HTTP, multipart for attachments | No WebSockets needed; no real-time collaboration requirement |
| Client state ↔ UI | React state/useReducer | `<BulletTree>` owns document state; child nodes read via props |

---

## Sources

- [Hierarchical models in PostgreSQL — Ackee blog](https://www.ackee.agency/blog/hierarchical-models-in-postgresql)
- [Modeling Hierarchical Tree Data in PostgreSQL — Leonard Marcq](https://leonardqmarcq.com/posts/modeling-hierarchical-tree-data)
- [Representing Trees in PostgreSQL — Graeme Mathieson](https://medium.com/notes-from-a-messy-desk/representing-trees-in-postgresql-cbcdae419022)
- [PostgreSQL WITH Queries (CTE) — Official Docs](https://www.postgresql.org/docs/current/queries-with.html)
- [TIL: Fractional Indexing — Daniel Feldroy (2024)](https://daniel.feldroy.com/posts/til-2024-11-fractional-indexing)
- [Undo/redo state with event sourcing — Eric Jinks (2025)](https://ericjinks.com/blog/2025/event-sourcing/)
- [Command-based undo for JS apps — DEV Community](https://dev.to/npbee/command-based-undo-for-js-apps-34d6)
- [Docker and Multer Upload Volumes — copyprogramming.com](https://copyprogramming.com/howto/upload-files-from-express-js-and-multer-to-persistent-docker-volume)
- [React Arborist — tree component for React](https://github.com/brimdata/react-arborist)
- [Building outstanding rich-text experiences with ProseMirror — Bruno Scheufler (2024)](https://brunoscheufler.com/blog/2024-02-11-building-outstanding-rich-text-experiences-with-prosemirror-and-react)
- [The Closure Table Pattern for Hierarchical Filters — Boyan Balev](https://balevdev.medium.com/the-closure-table-pattern-for-hierarchical-filters-with-sql-31644e760c09)

---

*Architecture research for: self-hosted infinite outliner / PKM web app*
*Researched: 2026-03-09*
