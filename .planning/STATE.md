---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 03-rich-content-09-PLAN.md
last_updated: "2026-03-09T19:30:21.738Z"
last_activity: 2026-03-09 — Plan 02-01 complete (Wave 0 test scaffolds, RED state confirmed)
progress:
  total_phases: 4
  completed_phases: 3
  total_plans: 23
  completed_plans: 23
  percent: 100
---

---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 03-rich-content-08-PLAN.md
last_updated: "2026-03-09T13:38:41.791Z"
last_activity: 2026-03-09 — Plan 02-01 complete (Wave 0 test scaffolds, RED state confirmed)
progress:
  [██████████] 100%
  completed_phases: 2
  total_plans: 23
  completed_plans: 22
---

---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: Completed 02-core-outliner-08-PLAN.md
last_updated: "2026-03-09T11:22:14.702Z"
last_activity: 2026-03-09 — Plan 02-01 complete (Wave 0 test scaffolds, RED state confirmed)
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 14
  completed_plans: 14
  percent: 50
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-09)

**Core value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.
**Current focus:** Phase 2 — Core Outliner

## Current Position

Phase: 2 of 4 (Core Outliner)
Plan: 1 of 8 in current phase (02-01 complete — test scaffolds)
Status: Phase 2 in progress — Wave 0 RED scaffolds complete, ready for implementation plans
Last activity: 2026-03-09 — Plan 02-01 complete (Wave 0 test scaffolds, RED state confirmed)

Progress: [█████░░░░░] 50%

## Performance Metrics

**Velocity:**
- Total plans completed: 4
- Average duration: ~20min
- Total execution time: ~1.3 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-foundation | 4/6 | ~77min | ~19min |

**Recent Trend:**
- Last 5 plans: 01-01, 01-04, 01-02, 01-03
- Trend: stable

*Updated after each plan completion*

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 01-foundation P01 | 6min | 3 tasks | 15 files |
| Phase 01-foundation P04 | 4min | 2 tasks | 12 files |
| Phase 01-foundation P02 | 35min | 2 tasks | 8 files |
| Phase 01-foundation P03 | 17 | 2 tasks | 3 files |
| Phase 01-foundation P05 | 2min | 2 tasks | 9 files |
| Phase 01-foundation P06 | 35min | 1 tasks | 8 files |
| Phase 02-core-outliner P01 | 8min | 4 tasks | 5 files |
| Phase 02-core-outliner P02 | 4min | 2 tasks | 4 files |
| Phase 02-core-outliner P03 | 4min | 2 tasks | 5 files |
| Phase 02-core-outliner P04 | 5min | 2 tasks | 6 files |
| Phase 02-core-outliner P05 | 2min | 2 tasks | 4 files |
| Phase 02-core-outliner P06 | 4min | 2 tasks | 5 files |
| Phase 02-core-outliner P07 | 3min | 2 tasks | 4 files |
| Phase 02-core-outliner P08 | 3min | 2 tasks | 1 files |
| Phase 03-rich-content P01 | 2min | 3 tasks | 9 files |
| Phase 03-rich-content P02 | 5min | 2 tasks | 3 files |
| Phase 03-rich-content P03 | 1min | 2 tasks | 2 files |
| Phase 03-rich-content P04 | 5 | 2 tasks | 4 files |
| Phase 03-rich-content P05 | 5min | 2 tasks | 7 files |
| Phase 03-rich-content P07 | 3min | 2 tasks | 4 files |
| Phase 03-rich-content P06 | 3min | 2 tasks | 5 files |
| Phase 03-rich-content P08 | 2min | 2 tasks | 4 files |
| Phase 03-rich-content P09 | 5 | 3 tasks | 1 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Pre-phase]: Use FLOAT8 midpoint positioning (not string fractional keys) — cannot change after data exists
- [Pre-phase]: Soft delete (`deleted_at`) on bullets from schema creation — enables undo of subtree deletions
- [Pre-phase]: Undo system uses `undo_events` + `undo_cursors` tables with `schema_version` column — must be created in Phase 1 even though undo ships in Phase 2
- [Pre-phase]: Plain contenteditable per bullet (not a single ProseMirror document) — tree model conflicts with ProseMirror document model
- [Phase 01-04]: AccessToken in React context (memory only) — not localStorage — prevents XSS token theft
- [Phase 01-04]: Google OAuth token received via URL hash fragment; AuthContext reads and cleans it via history.replaceState without navigation
- [Phase 01-04]: RequireAuth returns null during isLoading — prevents flash-of-login on valid session
- [Phase 01-01]: FLOAT8 double precision for all position columns — locked, cannot change after data exists
- [Phase 01-01]: undo_events.schema_version column present from migration 0 — prevents Phase 2/3 migration pain
- [Phase 01-01]: Docker port mapping 8000:3000 per MEMORY.md (app accessible at 192.168.1.50:8000)
- [Phase 01-02]: drizzle-orm 0.45.x has missing index.cjs in npm package — must use 0.40.0 + drizzle-kit 0.29.x
- [Phase 01-02]: requireAuth uses passport.authenticate callback pattern to return 401 JSON (not HTML)
- [Phase 01-02]: Google OAuth token sent as URL hash fragment (?token=) — hash not sent to server, prevents logging
- [Phase 01-foundation]: export-all route registered before /:id/export — Express param collision prevention
- [Phase 01-foundation]: computeDocumentInsertPosition accepts afterId (UUID or null) — client never computes FLOAT8 position
- [Phase 01-foundation]: renderDocumentAsMarkdown uses 2-space indent per nesting level — locked UX decision
- [Phase 01-foundation]: apiClient.download() added for blob/file responses — avoids storing token in localStorage
- [Phase 01-foundation]: 3-dot menu hidden by default; revealed via CSS opacity on hover/focus-within (locked UX decision, Phase 01-05)
- [Phase 01-foundation]: Dockerfile CMD path is server/dist/src/index.js — TypeScript rootDir='.' emits src/ and db/ to dist/src/ and dist/db/
- [Phase 01-foundation]: Nginx proxy must target port 8000 on Docker host — port 3000 is occupied by outlinergod-backend-1 container
- [Phase 01-foundation]: vite.config.ts must use defineConfig from vitest/config to support the 'test' property without TS error
- [Phase 02-core-outliner]: Route test mock paths use ../../src/... relative from tests/routes/ — consistent with project import convention
- [Phase 02-core-outliner]: Client test imports flattenTree from ../components/DocumentView/BulletTree — establishes expected export path for implementation
- [Phase 02-core-outliner]: Use node:crypto randomUUID instead of uuid package — uuid not in server/package.json; avoids new dependency
- [Phase 02-core-outliner]: recordUndoEvent takes dbInstance param — callers pass active transaction handle for atomic bullet mutation + undo event
- [Phase 02-core-outliner]: applyOp in undoService executes ops directly via Drizzle — prevents circular dependency with bulletService
- [Phase 02-core-outliner]: DB type changed from NodePgDatabase to PgDatabase<any> to accept both db and transaction handles in service functions
- [Phase 02-core-outliner]: undoRouter mounted at /api (not /api/undo) to handle /undo, /redo, /undo/status as separate paths
- [Phase 02-core-outliner]: Content PATCH bypasses undo at route level; client calls POST /:id/undo-checkpoint after debounce timeout
- [Phase 02-core-outliner]: BulletContent exports cursor helpers (isCursorAtStart, isCursorAtEnd, splitAtCursor) as named exports for unit testing without mounting the full component
- [Phase 02-core-outliner]: Shake animation uses singleton style tag injected on first render — self-contained in BulletContent, no Tailwind or external CSS dependency
- [Phase 02-core-outliner]: Enter on bullet with children creates new bullet as first child (parentId=bullet.id, afterId=null) per CONTEXT.md locked decision
- [Phase 02-core-outliner]: Single flat SortableContext over entire tree — nested SortableContexts block cross-level drag
- [Phase 02-core-outliner]: isDragOverlay prop skips BulletContent interactivity — renders plain text div in DragOverlay
- [Phase 02-core-outliner]: Click-vs-drag on dot: onPointerDown/Up with distance<5px check — 5px threshold matches PointerSensor activationConstraint
- [Phase 02-core-outliner]: GlobalKeyboard skips Ctrl+Z/Y when contenteditable has focus — BulletContent handles to prevent double-API-call
- [Phase 02-core-outliner]: invalidateQueries uses queryKey ['bullets'] prefix (no docId) for global per-user undo scope (UNDO-02)
- [Phase 02-core-outliner]: DocumentToolbar rendered outside SortableContext — dnd-kit would capture toolbar pointer events inside SortableContext
- [Phase 02-core-outliner]: BulletTree DnD logic uses visibleItems (not flatItems) when hide-completed active — prevents stale index lookups with hidden bullets
- [Phase 02-core-outliner]: RED scaffold tests implemented as pure logic tests without React mounting
- [Phase 03-rich-content]: bookmarks table uses uniqueIndex on (userId, bulletId) — enforced at DB level, prevents duplicate bookmarks
- [Phase 03-rich-content]: marked@17 + dompurify@3 installed together — marked renders markdown to HTML, dompurify sanitizes before display
- [Phase 03-rich-content]: Client chip tests operate on plain strings not HTML (renderWithChips input is plain text) — simplifies implementation surface
- [Phase 03-rich-content]: addBookmark uses onConflictDoNothing — idempotent, no error on duplicate (userId, bulletId)
- [Phase 03-rich-content]: searchBullets strips leading chip prefix before ILIKE — #milk searches for milk
- [Phase 03-rich-content]: getTagCounts uses Number(row.count) cast — pg driver returns numeric aggregates as strings in raw sql mode
- [Phase 03-rich-content]: canvasView and sidebarTab excluded from zustand persist via partialize — transient UI state resets on page reload
- [Phase 03-rich-content]: CanvasView type exported from uiStore.ts as single canonical import for all canvas view consumers
- [Phase 03-rich-content]: BulletContent isEditing: span for view mode + contenteditable div for edit mode — never set innerHTML on contenteditable
- [Phase 03-rich-content]: useLayoutEffect to set textContent + focus on edit mode entry — runs synchronously before browser paint
- [Phase 03-rich-content]: !! date picker trigger: only when content has !! but not !![ — prevents re-triggering on already-inserted date chips
- [Phase 03-rich-content]: Route handlers cast req.user as { id: string } — consistent with existing bullets.ts pattern, avoids Passport User type mismatch
- [Phase 03-rich-content]: ChipType cast in tags route — route layer accepts string URL param, service layer enforces valid ChipType values internally
- [Phase 03-rich-content]: SearchModal uses position:fixed mounted inside DocumentToolbar — renders correctly in viewport regardless of DOM tree
- [Phase 03-rich-content]: canvasView consumed in DocumentView with early return before main BulletTree render — keeps filtered vs document branches fully separate
- [Phase 03-rich-content]: DocumentRow (not DocumentList) holds setCanvasView reset — navigation happens in DocumentRow onClick; DocumentList is a pure DnD wrapper
- [Phase 03-rich-content]: BookmarkRow.id is the bullet ID (not the bookmark record ID) — getUserBookmarks returns bullets.id per service JOIN
- [Phase 03-rich-content]: ContextMenu calls useBookmarks() directly to determine isBookmarked state per bullet, not passed as prop
- [Phase 03-rich-content]: Server tests run locally via node_modules/.bin/vitest — Docker container only has dist/, not source/tests

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 2]: iOS Safari keyboard focus edge case — programmatic `.focus()` on new bullet must stay in the same synchronous event handler as the Enter keypress. Requires testing on a real iOS device before Phase 2 is considered done.
- [Phase 2]: @dnd-kit nested tree drag-and-drop (cross-level) — community patterns exist but are sparse. Consider targeted research before implementation.
- [Phase 4]: iOS `visualViewport` API and `touch-action` behavior varies across iOS versions — verify current state on iOS 17/18 before Phase 4 implementation.

## Session Continuity

Last session: 2026-03-09T19:25:20.059Z
Stopped at: Completed 03-rich-content-09-PLAN.md
Resume file: None
