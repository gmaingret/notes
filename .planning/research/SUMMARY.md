# Project Research Summary

**Project:** Self-Hosted Outliner / PKM Web App (Dynalist/Workflowy Clone)
**Domain:** Infinite outliner, personal knowledge management, self-hosted SaaS replacement
**Researched:** 2026-03-09
**Confidence:** MEDIUM-HIGH

## Executive Summary

This is a self-hosted, multi-user infinite outliner in the tradition of Dynalist and Workflowy, differentiated by data ownership (no vendor lock-in), persistent server-side undo/redo, file attachments, and intentional mobile UX. Research across all four domains converges on a clear, well-trodden architecture: React 19 + TipTap 3 (or plain contenteditable) on the frontend, Express 5 + Drizzle ORM on the backend, PostgreSQL 16/17 with an adjacency list tree model and float fractional indexing for ordering. This stack is strongly supported by official documentation and community consensus. The core product loop — create a bullet, nest it, reorder it, collapse it, undo a mistake — is both the first thing to build and the hardest thing to get right.

The single most consequential architectural decision is the undo/redo system: it must be designed before any mutating operation ships, because retrofitting a server-persisted command log after features like drag-and-drop, bulk delete, and attachment upload are in place is prohibitively expensive. The second most consequential decision is tree data modeling — adjacency list with `FLOAT8` midpoint ordering is the correct choice; the fractional indexing string-key variant introduces a PostgreSQL collation trap that has caused production incidents at other projects. Both of these must be locked in at schema creation time, not revisited later.

The main risk profile is not technical novelty — all individual components (tree rendering, contenteditable editors, JWT auth, file uploads) are well-documented. The risk is accumulated complexity from the intersection of features: undo must cover drag-and-drop reorder, bulk delete, subtree delete with cascade, and attachment deletion. Mobile touch must coexist with native scroll and text selection. The mitigation is disciplined phase ordering: build the data layer and undo system correctly before adding any user-facing complexity on top of it.

---

## Key Findings

### Recommended Stack

The backend runs Node.js 22 LTS with Express 5 (stable since April 2025), Drizzle ORM 0.45.x for TypeScript-native DB access, and PostgreSQL 16/17 as the sole data store. Drizzle is preferred over Prisma because its code-first TypeScript schema avoids Docker build-time code generation and its SQL-close API makes recursive CTEs readable. Auth is JWT-only (no sessions): short-lived access tokens (15 min) plus a long-lived refresh token in an `httpOnly` cookie, issued by both email/password (passport-local) and Google OAuth (passport-google-oauth20).

The frontend is React 19 + Vite 6 with TanStack Query 5 for server state and Zustand 5 for UI state (expand/collapse, focused bullet, undo indicator). The editor per bullet is a plain `contenteditable` span with a custom keyboard handler — NOT a single TipTap document — because ProseMirror's document model conflicts with the outliner's tree-of-siblings model. TipTap is available for rich inline formatting if needed but each bullet remains its own isolated editor instance. Drag-and-drop uses @dnd-kit (react-beautiful-dnd is deprecated).

**Core technologies:**
- Node.js 22 LTS + Express 5.2.x: backend runtime — LTS through 2027, async error handling built in
- PostgreSQL 16/17: primary data store — recursive CTEs for tree traversal are first-class
- Drizzle ORM 0.45.x: DB access — TypeScript-native, SQL-close API, no code-gen step
- React 19 + Vite 6: frontend — largest ecosystem, concurrent features for complex tree renders
- TanStack Query 5 + Zustand 5: state — optimistic updates with rollback built in
- @dnd-kit/core + @dnd-kit/sortable: drag-and-drop — accessible, touch/keyboard, actively maintained
- passport + passport-local + passport-google-oauth20 + passport-jwt: auth — battle-tested, pluggable
- multer 1.4.x (disk storage): file uploads — writes to Docker volume; multer 2.0.x is available but confirm CVE status before upgrading
- zod 3.x: runtime validation — shared schemas between server and client

### Expected Features

Research against Dynalist, Workflowy, and community reviews establishes a clear feature hierarchy. Note that Dynalist is in maintenance mode (company focus shifted to Obsidian), which means users migrating from Dynalist are an addressable market.

**Must have — table stakes (users assume these exist):**
- Infinite nesting with unlimited depth
- Enter to create sibling, Tab/Shift+Tab to indent/outdent
- Collapse/expand with server-side persistence per user
- Zoom into any bullet as root with breadcrumb navigation
- Drag-and-drop reorder (desktop)
- Ctrl+Z / Ctrl+Y undo/redo (server-persisted, 50 steps)
- Full-text search across all documents
- Markdown rendering: bold, italic, strikethrough, inline code, links (WYSIWYG, not raw preview)
- Mark complete (checkbox), bulk delete completed
- Multiple documents (flat list, no folders)
- Full keyboard shortcut set matching Dynalist defaults
- #tag / @mention / !!date syntax rendered as chips
- Tag browser pane
- Bookmarks for bullets and documents
- Export document as Markdown
- New users start with an Inbox document

**Should have — differentiators that justify building this:**
- Server-side persisted undo/redo (50 steps, survives refresh) — Dynalist's undo is session-only
- Self-hosted, zero vendor lock-in — no SaaS paywall or shutdown risk
- File attachments per bullet (free, not gated behind Pro)
- Comments per bullet (flat, plain text) — Dynalist has no comments
- Mobile swipe gestures (swipe-right = complete, swipe-left = delete)
- Mobile long-press context menu for indent/outdent/move
- @mention and !!date chip syntax beyond basic #tags
- Bulk delete completed bullets
- Ctrl+P / Ctrl+O quick-open for keyboard document navigation

**Defer to v2+:**
- Offline mode (service worker + sync conflict resolution — large scope)
- Real-time collaboration (CRDT/OT — explicitly excluded; privacy-first positioning)
- Bidirectional backlinks / graph view (different information architecture)
- Native iOS/Android apps (responsive web + PWA covers most needs)
- Folder hierarchy for documents (flat list + search is sufficient)
- AI features (conflicts with self-hosted privacy positioning)
- OPML import (deferred to v1.x after Markdown export is validated)
- Daily note / journal mode (Logseq-style — conflicts with document-first model)

### Architecture Approach

The system is a standard three-tier web app: React SPA in the browser over HTTP/REST to a Node.js/Express API server, backed by a single PostgreSQL database and a Docker volume for file storage. There is no WebSocket layer (no real-time collaboration). The tree data model is an adjacency list (`bullets` table with `parent_id` self-reference and `FLOAT8 position` for sibling ordering). The client receives a flat array of all bullets on document load and builds the tree in memory using a `parent_id` map — this avoids N+1 queries and makes zoom-into-bullet instant since all nodes are already loaded.

The undo system is a server-side command table (`undo_events`) with `forward_op`/`inverse_op` JSONB pairs and a cursor pointer per user. Every mutating API call wraps its DB operation and undo event insertion in a single transaction. Undo/redo is O(1) — apply one inverse op, no event replay needed. The server owns undo entirely; the client only sends `POST /api/undo` or `POST /api/redo`.

**Major components:**
1. Auth layer (Express + passport) — issues JWT pairs, protects all API routes, verifies Google OAuth codes server-side
2. Bullet API + Service layer — CRUD, move/indent/outdent with semantic endpoints, undo event recording in every mutation transaction
3. Tree State (client, `useReducer`) — normalized `{ [id]: BulletNode }` map; optimistic updates with rollback; single document boundary
4. BulletNode (recursive React component) — contenteditable per bullet, keyboard handler, chip parsing, drag handle, collapse toggle
5. Undo system (server) — `undo_events` + `undo_cursors` tables; pruned to 50 per user; schema-versioned payloads
6. Attachment service — multer disk storage to `/data/attachments/{user_id}/`, metadata in `attachments` table, ownership-verified download
7. Full-text search — PostgreSQL GIN index on stored `tsvector` generated column; query via `GET /api/search?q=`

### Critical Pitfalls

All seven pitfalls identified in research are consequential. The top five are:

1. **Fractional indexing collation trap** — String-key fractional indexing (the npm library pattern) breaks under PostgreSQL's glibc collation, which compares `aZ > aa` case-insensitively. This hit PayloadCMS in production (May 2025). Prevention: use `FLOAT8` midpoint positioning from schema creation, not string keys. This decision cannot be changed after data exists.

2. **Undo subtree corruption on delete** — Undoing a bullet delete restores the parent but not the cascade-deleted children unless the undo payload explicitly records all descendant IDs. Prevention: use soft delete (`deleted_at`) throughout; the inverse of "delete subtree" is "set `deleted_at = NULL` on bullet X" — PostgreSQL cascades handle descendants. The undo event stores a compact `{ id, subtree_ids[] }` payload, not full snapshots.

3. **Drag parent-into-child cycle** — Dropping a bullet onto its own descendant creates a cycle in the adjacency list. Any recursive CTE then loops infinitely. Prevention: server-side ancestry check on every move endpoint (rejects if target parent_id is in the dragged node's descendant set); client-side marks descendant drop zones invalid at drag start.

4. **iOS virtual keyboard breaks layout and focus** — iOS Safari refuses programmatic `.focus()` calls not originating from a direct user interaction event, so Enter-to-create-new-bullet fails to open the keyboard on iOS. `position: fixed` toolbars appear behind the keyboard. Prevention: keep new-bullet focus in the same synchronous event handler as the Enter keypress; use `visualViewport.addEventListener('resize')` for layout adjustment; no bottom fixed elements.

5. **Undo history schema corruption after deploy** — Payload format changes in `undo_events` JSONB break old records silently. Prevention: add `schema_version INTEGER` column to `undo_events` at table creation; handlers skip records with unknown versions; background cron prunes to 50 per user.

---

## Implications for Roadmap

Based on combined research, the dependency graph forces a specific ordering. The undo system must wrap every mutation, so it must be designed before any feature that mutates data ships. The tree model must be schema-correct from day one (float ordering, soft delete, GIN index). Mobile UX is a layered concern that sits on top of a working desktop editor.

### Phase 1: Foundation — Auth, Schema, and Document CRUD

**Rationale:** Everything else requires auth and a stable DB schema. The schema cannot be refactored after data exists (position column type, soft-delete pattern, FTS generated column, undo table schema version). Auth must be end-to-end before any protected route can be meaningfully tested. This is the riskiest phase from a correctness standpoint.

**Delivers:** Working login (email/password + Google OAuth), document creation/rename/delete, flat document list, new-user Inbox creation. Backend API only; minimal frontend.

**Addresses:** Auth, Document CRUD, Inbox onboarding (from FEATURES.md table stakes)

**Avoids:**
- Fractional indexing collation trap: lock in `FLOAT8` position from schema creation
- Soft delete missing: add `deleted_at` to bullets table at creation
- GIN index missing: add stored `search_vector` generated column + GIN index at migration time
- Undo schema version: add `schema_version` column to `undo_events` at table creation

**Research flag:** Standard patterns — JWT auth with passport and Drizzle migrations are well-documented. Skip `research-phase`.

---

### Phase 2: Core Tree Editor — Bullet CRUD, Keyboard, Collapse

**Rationale:** The core outliner loop (create, nest, collapse, navigate) must work end-to-end before undo is layered on top. Architecture research explicitly sequences this before undo because undo requires knowing the exact inverse of each operation — impossible to define until operations are stable.

**Delivers:** Bullet create/update/delete, Tab/Shift+Tab indent/outdent, collapse/expand (server-persisted), zoom + breadcrumb, keyboard shortcuts (Enter, Backspace on empty, Ctrl+Up/Down move), and the basic tree rendering in React.

**Uses:** contenteditable per bullet (not ProseMirror document), `useReducer` normalized state map, optimistic updates, `GET /api/documents/:id/bullets` bulk load with client-side tree reconstruction.

**Implements:** BulletNode, BulletContent (contenteditable + keyboard handler), BulletTree (recursive), DocSidebar, Breadcrumbs

**Avoids:**
- iOS keyboard focus: keep new-bullet focus synchronous in Enter handler; test on real iOS device before phase is considered done
- Integer position: float midpoint everywhere; `SELECT ... FOR UPDATE` on sibling rows inside move transaction
- localStorage for collapse state: `is_collapsed` in DB, debounced PATCH

**Research flag:** Tree rendering patterns are well-documented. The keyboard handler (Enter/Tab/Backspace edge cases) has no single canonical reference — consider a targeted `research-phase` for keyboard event edge cases on mobile Safari.

---

### Phase 3: Undo/Redo System

**Rationale:** FEATURES.md flags undo as "must be designed before bulk operations or it becomes very hard to retrofit." ARCHITECTURE.md sequences undo after mutation endpoints are stable for the same reason. This phase wraps all Phase 2 mutations with undo event recording in a single transaction per operation.

**Delivers:** Server-persisted 50-step undo/redo, Ctrl+Z/Ctrl+Y keyboard bindings, undo status indicator, undo covering all Phase 2 operations (create, update, delete, move, indent, outdent, collapse).

**Implements:** `undo_events` + `undo_cursors` tables (already created in Phase 1 with `schema_version`), `undoService.record()` wrapper called by every mutation, `POST /api/undo`, `POST /api/redo`, `GET /api/undo/status`

**Avoids:**
- Subtree undo corruption: soft-delete inverse covers entire subtree; undo payload stores `subtree_ids[]` for hard-delete recovery scenarios
- Unbounded growth: cron job prunes to 50 per user after each undo write
- Schema mismatch after deploy: `schema_version` checked before parsing; unknown versions skipped

**Research flag:** Command-pattern undo is well-documented. The exact forward/inverse payload schema for each operation type is project-specific — no research needed, define it internally.

---

### Phase 4: Drag-and-Drop Reorder

**Rationale:** Drag-and-drop depends on stable tree state (Phase 2) and must be undoable (Phase 3). Sequencing it after undo means drag-and-drop's undo event can be wired into the existing system rather than requiring the undo system to be retrofitted around it.

**Delivers:** Desktop drag-and-drop reorder within and across nesting levels, insertion line indicator (not full ghost), drag undo.

**Uses:** @dnd-kit/core + @dnd-kit/sortable, `POST /api/bullets/:id/move` with semantic `after_id` rather than raw position float

**Avoids:**
- Parent-into-child cycle: client marks descendant drop zones invalid at drag start; server rejects any move where target parent_id is in dragged node's descendant set
- Race condition on position: `SELECT ... FOR UPDATE` on sibling rows inside move transaction; position computed inside DB transaction

**Research flag:** @dnd-kit tree patterns exist in community docs but nested tree drag-and-drop (cross-level) is complex. Consider a targeted `research-phase` for @dnd-kit tree sortable patterns before implementation.

---

### Phase 5: Markdown, Search, and Syntax Chips

**Rationale:** These three features share a dependency on stable bullet content (Phase 2) and benefit from being built together: the FTS index is already created in Phase 1 schema; chip parsing (#tag, @mention, !!date) informs the search filter schema; tag browser is a natural companion to chip rendering.

**Delivers:** WYSIWYG inline markdown (bold, italic, strikethrough, code, links), full-text search with instant-as-you-type, #tag / @mention / !!date chip rendering, tag browser pane, bookmarks.

**Uses:** PostgreSQL GIN index on `search_vector` (already in schema), regex-based chip parsing in `BulletContent` (does not modify stored text), `GET /api/search?q=` endpoint

**Avoids:**
- tsvector at query time: use stored generated column — `EXPLAIN ANALYZE` must show GIN index hit, not Seq Scan
- N+1 for tag counts: single JOIN query for tag browser, not one query per tag
- Inline markdown cursor jump: only re-render markdown decorations outside cursor paragraph

**Research flag:** Standard patterns — PostgreSQL FTS and TipTap/contenteditable formatting are well-documented. Skip `research-phase`.

---

### Phase 6: Attachments and Comments

**Rationale:** Both depend on stable bullet CRUD (Phase 2) and undo (Phase 3). File uploads are independent of tree logic and can be parallelized within this phase. Comments are the simplest feature in scope.

**Delivers:** File attachments per bullet (upload, download, delete), attachment undo, flat comments per bullet (add, delete), comment count badge on BulletNode.

**Uses:** multer 1.4.x disk storage to `/data/attachments/{user_id}/`, `attachments` table, `comments` table, `GET /api/bullets/:id/comments`, `POST /api/attachments`

**Avoids:**
- Docker volume permissions: `RUN chown -R node:node /data/attachments` in Dockerfile; test fresh-deploy → upload → restart → re-download cycle
- MIME type validation: check both `Content-Type` header and magic bytes; serve via `Content-Disposition: attachment` to prevent inline execution
- File size: enforce 100MB limit in Multer config before file reaches disk

**Research flag:** Standard patterns — multer + Docker volume is well-documented. Skip `research-phase`.

---

### Phase 7: Mobile Touch Gestures

**Rationale:** Mobile UX is a layered concern on top of the working desktop editor. Sequencing it last avoids iOS-specific constraints bleeding into the core keyboard handling decisions made in Phase 2. This phase also benefits from having undo (Phase 3) available to power the "swipe-delete undo" toast.

**Delivers:** Swipe-right (complete), swipe-left (delete with undo toast), long-press context menu (indent/outdent/move/bookmark), responsive layout with visualViewport-aware keyboard compensation.

**Avoids:**
- iOS touch gesture conflicts with native scroll: `touch-action: pan-y` on bullet rows; minimum 60px swipe threshold; 30-degree angle tolerance
- Long-press vs text selection conflict: timer-based detection that cancels if text selection starts
- Fixed bottom toolbar behind keyboard: `visualViewport` offset compensation; no `position: fixed` at bottom

**Research flag:** iOS Safari touch behavior is non-standard and evolves. Targeted `research-phase` recommended to check current state of `visualViewport` API and `touch-action` on iOS 17/18 before implementation.

---

### Phase 8: Polish and Export

**Rationale:** Polish features (export, PWA manifest, keyboard shortcut completeness, onboarding) are independent of all other phases and best deferred until the core product loop is validated.

**Delivers:** Export document as Markdown, complete keyboard shortcut set, Ctrl+P / Ctrl+O quick-open, PWA manifest + install prompt, list density options.

**Research flag:** Standard patterns. Skip `research-phase`.

---

### Phase Ordering Rationale

The ordering derives directly from the dependency graph in FEATURES.md and the build order in ARCHITECTURE.md:

- **Schema first:** Position column type, soft delete, FTS index, and undo schema version cannot be changed after data exists. All must be set in Phase 1 migrations.
- **Undo before complexity:** Undo (Phase 3) must come before drag-and-drop (Phase 4), bulk delete (Phase 5), and attachments (Phase 6) — each of those operations needs an undo record, and the system must be designed before the operations are finalized.
- **Desktop before mobile:** The mobile gesture layer (Phase 7) sits on top of a stable desktop interaction model. Building mobile gestures before the keyboard handler is finalized would cause repeated rework.
- **Avoiding the three traps:** The sequencing prevents the three most expensive retrofits identified in research: adding undo after features exist, changing the position column type after data exists, and adding ancestry validation to drag-and-drop after the move endpoint is already in use.

### Research Flags

Phases needing deeper research during planning:
- **Phase 2 (Core Tree Editor):** Keyboard event edge cases on mobile Safari (Enter → new bullet focus; Tab in contenteditable). No single canonical reference. Targeted research recommended before sprint planning.
- **Phase 4 (Drag-and-Drop):** @dnd-kit nested tree drag patterns for cross-level moves. Community docs exist but are sparse for this specific use case. Targeted research recommended.
- **Phase 7 (Mobile Gestures):** iOS Safari `visualViewport` API behavior and `touch-action` support on iOS 17/18. Behavior evolves between OS versions; verify current state before implementation.

Phases with standard, well-documented patterns (skip `research-phase`):
- **Phase 1 (Foundation):** JWT auth + passport + Drizzle migrations are canonical patterns with official documentation.
- **Phase 3 (Undo):** Command-pattern undo with forward/inverse op pairs is well-documented.
- **Phase 5 (Search/Markdown):** PostgreSQL FTS with stored tsvector + GIN index is covered by official PostgreSQL docs and multiple high-quality tutorials.
- **Phase 6 (Attachments/Comments):** Multer + Docker volume pattern is straightforward; official multer docs are sufficient.
- **Phase 8 (Polish/Export):** All standard web patterns; no novel integration.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | Core technologies verified via official docs and npm. Drizzle 1.0-beta status is evolving — stay on 0.45.x stable. Multer 2.0.x CVE fix noted; confirm before using. |
| Features | HIGH | Cross-referenced against Dynalist/Workflowy official docs, help centers, and user reviews. Dynalist being in maintenance mode (confirmed via forum) strengthens the case for a self-hosted alternative. |
| Architecture | HIGH | Tree model, undo pattern, and file storage approach are verified against multiple authoritative sources including PostgreSQL official docs. API shape and frontend hierarchy are MEDIUM (project-specific, no single reference). |
| Pitfalls | HIGH (DB/tree) / MEDIUM (mobile UX) | Fractional indexing collation trap is documented by a real production incident (PayloadCMS May 2025). Mobile Safari keyboard behavior sourced from ProseMirror/CodeMirror issue threads — behavior may shift across OS versions. |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **Drizzle 1.0-beta:** Drizzle ORM 1.0-beta exists as of research date. Stay on 0.45.x stable for this project. Revisit when 1.0 reaches stable release.
- **Multer version:** Research recommends 1.4.x stable but also notes 2.0.2 fixes two high-severity CVEs. Confirm the CVE scope and whether they apply to this use case before locking the version.
- **contenteditable vs TipTap per bullet:** ARCHITECTURE.md recommends plain contenteditable. STACK.md recommends TipTap per bullet instance. Both sources agree that each bullet is its own editor instance (not a single document). The choice between raw contenteditable and TipTap should be resolved in Phase 2 planning — TipTap adds formatting extension support with less keyboard-handler boilerplate; raw contenteditable avoids ProseMirror abstraction overhead.
- **iOS Safari keyboard focus:** This is the highest-risk mobile interaction. Testing on a real iOS device is explicitly required before Phase 2 is considered done. Simulator behavior does not match device behavior for this specific case.
- **Registration gating:** With open registration and file attachments, disk exhaustion is a realistic risk for a self-hosted instance with exposed registration. Consider an invite-code or admin-approved registration model before public deployment.

---

## Sources

### Primary (HIGH confidence)
- [TipTap 3.0 stable announcement](https://tiptap.dev/blog/release-notes/tiptap-3-0-is-stable) — TipTap 3.20.x current stable
- [Express 5 stable announcement](https://expressjs.com/2024/10/15/v5-release.html) — Express 5.2.x current
- [React versions page](https://react.dev/versions) — React 19.2 confirmed current
- [PostgreSQL WITH Queries (CTE) — Official Docs](https://www.postgresql.org/docs/current/queries-with.html) — recursive CTE patterns
- [PostgreSQL ltree docs](https://www.postgresql.org/docs/18/ltree.html) — GIST index size limits confirming adjacency list preference
- [PayloadCMS Issue #12397](https://github.com/payloadcms/payload/issues/12397) — fractional indexing collation bug production incident (May 2025)
- [The PostgreSQL Collation Trap That Breaks Fractional Indexing — Jökull Sólberg](https://www.solberg.is/fractional-indexing-gotcha) — January 2026
- [Optimizing Full Text Search with Postgres tsvector — Thoughtbot](https://thoughtbot.com/blog/optimizing-full-text-search-with-postgres-tsvector-columns-and-triggers) — GIN index pattern
- [Cycle Detection for Recursive Search — SQLforDevs](https://sqlfordevs.com/cycle-detection-recursive-query) — drag cycle prevention
- [Handling Permissions with Docker Volumes — Deni Bertovic](https://denibertovic.com/posts/handling-permissions-with-docker-volumes/) — volume ownership pattern
- [VirtualKeyboard API — MDN Web Docs](https://developer.mozilla.org/en-US/docs/Web/API/VirtualKeyboard_API) — iOS keyboard handling
- [TanStack Query v5 optimistic updates docs](https://tanstack.com/query/v5/docs/react/guides/optimistic-updates) — mutation/optimistic pattern
- [Dynalist Features Full List](https://dynalist.io/features/full) — table stakes verification
- [Dynalist Keyboard Shortcut Reference](https://help.dynalist.io/article/91-keyboard-shortcut-reference) — shortcut set
- [Dynalist Forum: Product Discontinued Thread](https://talk.dynalist.io/t/product-discontinued/8761) — maintenance mode confirmation

### Secondary (MEDIUM confidence)
- [Drizzle ORM npm](https://www.npmjs.com/package/drizzle-orm) — 0.45.x current; 1.0-beta noted
- [Better Stack: Drizzle vs Prisma](https://betterstack.com/community/guides/scaling-nodejs/drizzle-vs-prisma/) — Drizzle recommendation for self-hosted Express
- [Ackee blog: Hierarchical models in PostgreSQL](https://www.ackee.agency/blog/hierarchical-models-in-postgresql) — adjacency list analysis
- [Steve Ruiz: Fractional Indexing](https://www.steveruiz.me/posts/reordering-fractional-indices) — fractional indexing technique
- [Liveblocks: Rich text editor framework 2025](https://liveblocks.io/blog/which-rich-text-editor-framework-should-you-choose-in-2025) — TipTap vs ProseMirror vs Slate
- [Undo/redo state with event sourcing — Eric Jinks (2025)](https://ericjinks.com/blog/2025/event-sourcing/) — command-based undo pattern
- [Building Complex Nested Drag and Drop UIs — Kustomer Engineering](https://medium.com/kustomerengineering/building-complex-nested-drag-and-drop-user-interfaces-with-react-dnd-87ae5b72c803) — nested DnD patterns
- [PostgreSQL: Speeding up recursive queries — CYBERTEC](https://www.cybertec-postgresql.com/en/postgresql-speeding-up-recursive-queries-and-hierarchic-data/) — CTE performance
- [ProseMirror iOS mobile keyboard issues — GitHub Issues](https://github.com/ProseMirror/prosemirror/issues/627) — iOS Safari keyboard behavior
- [Mobile Gesture Best Practices — Material Design 3](https://m3.material.io/foundations/interaction/gestures) — swipe gesture UX
- [Workflowy vs Dynalist Comparison — Slant](https://www.slant.co/versus/4412/15546/~workflowy_vs_dynalist) — feature comparison
- npmtrends cross-reference — passport, jsonwebtoken, bcryptjs version confirmation

---

*Research completed: 2026-03-09*
*Ready for roadmap: yes*
