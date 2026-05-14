---
phase: 19-server-foundation
plan: "01"
subsystem: server
tags: [error-handling, env-vars, undo, express, multer]
dependency_graph:
  requires: []
  provides: [global-error-handler, undo-422, upload-env-vars]
  affects: [undoRouter, attachmentsRouter, index.ts]
tech_stack:
  added: []
  patterns: [express-error-middleware, env-var-config]
key_files:
  created:
    - server/tests/routes/undo.routes.test.ts
    - server/tests/routes/attachments-env.test.ts
    - server/tests/setup.ts
  modified:
    - server/src/routes/undo.ts
    - server/src/index.ts
    - server/src/routes/attachments.ts
    - .env.example
    - server/vitest.config.ts
    - server/tests/bullets.test.ts
    - server/tests/routes/bullets.routes.test.ts
requirements_completed: [ERR-01, ERR-02, CONF-01]
metrics:
  completed_date: "2026-05-14"
  tasks_completed: 2
  files_modified: 9
---

# Phase 19 Plan 01: Server Error Handling and Upload Env Var Wiring

**One-liner:** Replaced dead try/catch wrappers in undoRouter with explicit 422 responses on empty stacks, registered a global Express error handler that returns `{ error: 'Internal server error' }` JSON for any unhandled exception, and wired `UPLOAD_PATH`/`UPLOAD_MAX_SIZE_MB` env vars into multer config.

## Tasks Completed

| Task | Name | Files |
|------|------|-------|
| 1 | Fix undo/redo route handlers + global error middleware | `server/src/routes/undo.ts`, `server/src/index.ts` |
| 2 | Wire upload env vars + update `.env.example` | `server/src/routes/attachments.ts`, `.env.example` |

## What Was Built

- `POST /api/undo` now calls `getStatus()` first; if `canUndo === false`, returns `422 { error: 'Nothing to undo' }`. Otherwise invokes `undo()` and returns the resulting status (ERR-02).
- `POST /api/redo` follows the same pattern: `422 { error: 'Nothing to redo' }` on empty redo stack.
- `GET /api/undo/status` simplified — no dead try/catch.
- `server/src/index.ts` registers a 4-arg Express error handler after every `app.use('/api/...')` mount and before the static-file block. It logs the error and returns `500 { error: 'Internal server error' }` (ERR-01).
- `server/src/routes/attachments.ts` reads `UPLOAD_PATH` (defaults to `/data/attachments`) and `UPLOAD_MAX_SIZE_MB` (defaults to `100`) at module load. The `LIMIT_FILE_SIZE` error message now references the configured limit (CONF-01).
- `.env.example` grouped the upload settings under a comment indicating they're consumed by `server/src/routes/attachments.ts`.

## Tests Added

- `server/tests/routes/undo.routes.test.ts` — 6 tests covering: 422 on empty undo/redo stacks, 200 happy path for undo/redo, GET status, and the global JSON 500 handler path.
- `server/tests/routes/attachments-env.test.ts` — 3 source-level assertions confirming `attachments.ts` reads from `process.env.UPLOAD_PATH` / `process.env.UPLOAD_MAX_SIZE_MB` and references the configured limit in its error message.
- `server/tests/setup.ts` — vitest setup file that sets `UPLOAD_PATH` to a fresh `mkdtempSync` directory before any module imports `attachments.ts`. This eliminates the pre-existing local-dev failure where multer tried to mkdir `/data/attachments` (which only exists inside the production container).
- `server/tests/bullets.test.ts` — updated two `markComplete` tests to mock the new `getDescendantIds` select call (descendant cascade is unrelated to this plan; fix shipped alongside).
- `server/tests/routes/bullets.routes.test.ts` — added `getStatus` mocks to the existing undo/redo route happy-path tests (they previously hit the new pre-check and returned 500).

## Verification

```
$ cd server && npx tsc --noEmit
(clean)
$ npm test
Test Files  14 passed (14)
     Tests  144 passed (144)
$ npm run build
(clean)
```

## Acceptance Criteria

- [x] `undo.ts` contains `res.status(422).json({ error: 'Nothing to undo' })`
- [x] `undo.ts` contains `res.status(422).json({ error: 'Nothing to redo' })`
- [x] No dead `try { ... } catch (err) { throw err; }` in `undo.ts`
- [x] `index.ts` contains a 4-arg error handler returning `500 { error: 'Internal server error' }`
- [x] Error handler is registered AFTER `app.use('/api/attachments'` and BEFORE the static-file block
- [x] `attachments.ts` reads `process.env.UPLOAD_PATH || '/data/attachments'`
- [x] `attachments.ts` reads `(Number(process.env.UPLOAD_MAX_SIZE_MB) || 100) * 1024 * 1024`
- [x] LIMIT_FILE_SIZE message references the configured limit, not a hardcoded `100`
- [x] `.env.example` contains `UPLOAD_MAX_SIZE_MB=100` and `UPLOAD_PATH=/data/attachments`
- [x] `npx tsc --noEmit` exits clean
- [x] Full test suite passes
