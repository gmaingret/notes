---
phase: 02-core-outliner
plan: "03"
subsystem: backend-api
tags: [routes, express, bullets, undo, http]
dependency_graph:
  requires: [02-02]
  provides: [bullet-http-api, undo-http-api]
  affects: [all-client-plans]
tech_stack:
  added: []
  patterns: [express-router, zod-validation, service-layer-routing, try-catch-error-mapping]
key_files:
  created:
    - server/src/routes/bullets.ts
    - server/src/routes/undo.ts
  modified:
    - server/src/index.ts
    - server/db/index.ts
    - server/tests/routes/bullets.routes.test.ts
decisions:
  - "Bullet PATCH dispatches to different services based on which field is present (isCollapsed → setCollapsed, isComplete → markComplete, content → direct DB update)"
  - "Content updates bypass undo service at route level; client calls POST /:id/undo-checkpoint after debounce to record text change undo events"
  - "undoRouter mounted at /api (not /api/undo) to allow three different paths: /undo, /redo, /undo/status"
  - "DB type changed from NodePgDatabase to PgDatabase<any> to accept both db and transaction handles"
metrics:
  duration: ~4min
  completed_date: "2026-03-09"
  tasks: 2
  files: 5
---

# Phase 2 Plan 03: Bullet and Undo HTTP Routes Summary

Express route wiring layer exposing bulletService and undoService via REST API, with Zod validation and consistent error mapping.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Bullet routes | 4c82437 | server/src/routes/bullets.ts, server/db/index.ts |
| 2 | Undo routes + register all new routers | ae8eab6 | server/src/routes/undo.ts, server/src/index.ts, server/tests/routes/bullets.routes.test.ts |

## What Was Built

**bulletsRouter** (`/api/bullets`):
- `GET /documents/:docId/bullets` — returns flat bullet array for authenticated user
- `POST /` — creates bullet with Zod-validated body (documentId UUID required)
- `PATCH /:id` — dispatches to setCollapsed/markComplete/direct-DB-update based on field present
- `DELETE /:id` — soft delete via service
- `POST /:id/indent` — indent bullet via service
- `POST /:id/outdent` — outdent bullet via service
- `POST /:id/move` — move bullet with cycle guard error mapping to 400
- `DELETE /documents/:docId/completed` — hard-delete all completed bullets (no undo, BULL-14)
- `POST /:id/undo-checkpoint` — record text change undo event after debounce

**undoRouter** (`/api`):
- `POST /undo` — undo most recent action, returns `{ canUndo, canRedo }`
- `POST /redo` — redo most recently undone action
- `GET /undo/status` — returns availability flags

Both routers registered in `server/src/index.ts`.

## Verification Results

- `npx tsc --noEmit` — no errors (clean)
- `npx vitest run tests/routes/bullets.routes.test.ts` — 19/19 tests passing
- `npx vitest run` (all tests) — 80/80 tests passing

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed DB type to accept transaction handles**
- **Found during:** Task 1 TypeScript compilation check
- **Issue:** `DB` type in `db/index.ts` was `typeof db` (NodePgDatabase) which has `$client: Pool` property. Transaction callback handles (PgTransaction) extend PgDatabase but lack `$client`, causing 6 TypeScript errors in `bulletService.ts` when passing `tx` to `recordUndoEvent`
- **Fix:** Changed `export type DB = typeof db` to `export type DB = PgDatabase<any, typeof schema>` using the shared base class that both NodePgDatabase and PgTransaction extend
- **Files modified:** `server/db/index.ts`
- **Commit:** 4c82437

**2. [Rule 3 - Blocking] Implemented test bodies in bullets.routes.test.ts**
- **Found during:** Task 2 verification
- **Issue:** Test file from Plan 02-01 contained only stub bodies (`throw new Error('not yet implemented')`). Done criteria required tests to pass, which is impossible with stubs
- **Fix:** Implemented all 19 test bodies with proper mocking of db, bulletService, undoService, and requireAuth. Added db mock for direct DB calls in PATCH content and DELETE completed endpoints. Used `mockImplementationOnce` pattern for 401 tests
- **Files modified:** `server/tests/routes/bullets.routes.test.ts`
- **Commit:** ae8eab6

## Self-Check: PASSED

All files exist and all commits verified:
- FOUND: server/src/routes/bullets.ts
- FOUND: server/src/routes/undo.ts
- FOUND: .planning/phases/02-core-outliner/02-03-SUMMARY.md
- FOUND commit: 4c82437 (feat(02-03): implement bullet HTTP routes)
- FOUND commit: ae8eab6 (feat(02-03): implement undo routes and register all new routers)
