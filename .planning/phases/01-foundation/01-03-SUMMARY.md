---
phase: 01-foundation
plan: 03
subsystem: api
tags: [express, drizzle-orm, typescript, archiver, zod, vitest, supertest]

# Dependency graph
requires:
  - phase: 01-01
    provides: Drizzle schema (documents + bullets tables), DB connection, test scaffolds
  - phase: 01-02
    provides: requireAuth middleware, JWT verification, Express app factory

provides:
  - GET/POST/PATCH/DELETE /api/documents/* — full document CRUD endpoints
  - GET /api/documents/export-all — ZIP archive of all user documents
  - GET /api/documents/:id/export — single Markdown file export
  - PATCH /api/documents/:id/position — FLOAT8 midpoint reorder (afterId, not float)
  - POST /api/documents/:id/open — last_opened_at tracking
  - documentService: computeDocumentInsertPosition, renderDocumentAsMarkdown, getDocumentWithBullets, getAllDocumentsWithBullets
  - DOC-01 through DOC-07 unit tests all passing (16 tests)

affects:
  - 01-05 (frontend sidebar consumes GET /api/documents, PATCH /:id, DELETE /:id, POST /:id/open)
  - 01-06 (deployment verification of document endpoints)

# Tech tracking
tech-stack:
  added:
    - archiver@7 — server-side ZIP streaming for export-all endpoint
    - lodash — required peer dependency for archiver-utils
  patterns:
    - FLOAT8 midpoint positioning: client passes afterId (UUID or null), server computes midpoint
    - Route ordering: /export-all registered before /:id to prevent Express param conflict
    - Mock requireAuth in tests via vi.mock('../src/middleware/auth.js') — cleaner than configuring passport

key-files:
  created:
    - server/src/services/documentService.ts
    - server/src/routes/documents.ts
  modified:
    - server/tests/documents.test.ts (stubs replaced with 16 passing tests)

key-decisions:
  - "export-all route must be registered BEFORE /:id/export — Express matches in order, 'export-all' would be caught by :id param otherwise"
  - "Mock requireAuth middleware in document tests instead of configuring passport-jwt with mocked db — avoids test complexity and passport strategy setup"
  - "computeDocumentInsertPosition takes afterId (UUID or null), never a raw float — client cannot compute position server-side logic is authoritative"
  - "renderDocumentAsMarkdown uses 2-space indent per nesting level — locked UX decision from CONTEXT.md"
  - "lodash added as explicit dependency — archiver-utils requires it but doesn't declare it (peer dep gap)"

patterns-established:
  - "Document service pattern: pure functions (renderDocumentAsMarkdown) separated from DB functions (getDocumentWithBullets) for testability"
  - "Test isolation: vi.mock middleware to inject req.user directly — avoids needing full passport setup in unit tests"
  - "FLOAT8 midpoint: midpoint = (prev + next) / 2; insert at end = last.position + 1.0; insert at beginning = first.position / 2"

requirements-completed: [DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07]

# Metrics
duration: 20min
completed: 2026-03-09
---

# Phase 1 Plan 03: Document Backend Endpoints Summary

**FLOAT8 midpoint document CRUD with streaming ZIP export and Markdown rendering via archiver and Express — all 16 DOC-01 through DOC-07 tests passing**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-03-09T07:47:23Z
- **Completed:** 2026-03-09T09:00:00Z
- **Tasks:** 2 (TDD: service + routes/tests)
- **Files modified:** 3

## Accomplishments
- Document CRUD endpoints (list, create, rename, delete) with ownership checks (403 for wrong user)
- FLOAT8 midpoint reorder: client passes afterId UUID, server computes position — client never touches floats
- Single document Markdown export with Content-Disposition attachment header
- All-documents ZIP export using archiver streaming (not buffering) with sanitized filenames
- POST /open endpoint updates last_opened_at timestamp
- 16 unit tests passing (DOC-01 through DOC-07) with clean mock isolation

## Task Commits

1. **Task 1: Document service (CRUD, position, export logic)** - `97d0e30` (feat)
2. **Task 2: Documents router and documents.test.ts** - `97d0e30` (feat, combined with task 1)
3. **Fix: Mock requireAuth directly in test** - `196bdf9` (test)

## Files Created/Modified
- `server/src/services/documentService.ts` — computeDocumentInsertPosition, renderDocumentAsMarkdown, getDocumentWithBullets, getAllDocumentsWithBullets, DocWithBullets type
- `server/src/routes/documents.ts` — 8 endpoints: GET /, POST /, PATCH /:id, PATCH /:id/position, POST /:id/open, DELETE /:id, GET /export-all, GET /:id/export
- `server/tests/documents.test.ts` — 16 passing tests covering all DOC-01 through DOC-07 requirements

## Decisions Made
- Route ordering: `/export-all` registered before `/:id/export` to prevent Express param collision
- Test isolation: mock `requireAuth` via `vi.mock('../src/middleware/auth.js')` rather than configuring passport with mocked DB
- `computeDocumentInsertPosition` is side-effect free relative to the document being moved — computes position among siblings without locking (acceptable for non-concurrent use)
- 2-space indent per nesting level in `renderDocumentAsMarkdown` — locked UX decision from CONTEXT.md

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Installed missing lodash dependency**
- **Found during:** Task 2 (running document tests)
- **Issue:** `archiver-utils` requires `lodash/defaults` but lodash was not in package.json; test suite crashed with `Cannot find module 'lodash/defaults'`
- **Fix:** `npm install lodash @types/lodash` — added explicit dependency
- **Files modified:** server/package.json, server/package-lock.json
- **Verification:** Tests ran successfully after install
- **Committed in:** `97d0e30`

**2. [Rule 1 - Bug] Replaced passport-jwt approach in tests with direct requireAuth mock**
- **Found during:** Task 2 (initial test run — 15 of 16 tests returning 500)
- **Issue:** `requireAuth` middleware calls `passport.authenticate('jwt', ...)` which in turn calls `db.query.users.findFirst` to verify the JWT subject. In tests, `vi.clearAllMocks()` cleared the user lookup mock so all authenticated requests returned 500 instead of forwarding to route handlers
- **Fix:** Changed from `configurePassport()` + real passport.initialize() to `vi.mock('../src/middleware/auth.js', ...)` — stub `requireAuth` injects `req.user` directly, completely bypasses passport-jwt
- **Files modified:** `server/tests/documents.test.ts`
- **Verification:** All 16 tests pass
- **Committed in:** `196bdf9`

---

**Total deviations:** 2 auto-fixed (1 blocking dependency, 1 test infrastructure bug)
**Impact on plan:** Both auto-fixes necessary for tests to run. No scope creep. renderDocumentAsMarkdown pure unit test uses `vi.importActual` to test the real function despite the module-level mock.

## Issues Encountered
- drizzle-kit `generate` initially failed due to a corrupted `_journal.json` with BOM character from a previous manual edit. Resolved by deleting the corrupted file and re-running `drizzle-kit generate` to produce a clean migration (`0000_workable_argent.sql`).

## Next Phase Readiness
- All document backend endpoints are ready for frontend consumption (Plan 05: App Shell / Sidebar)
- `GET /api/documents` sorted by position asc — ready for sidebar list
- `PATCH /api/documents/:id/position` accepts afterId UUID — ready for dnd-kit drag handler
- Export endpoints work independently of frontend
- No blockers for Plan 05 or 06

---
*Phase: 01-foundation*
*Completed: 2026-03-09*
