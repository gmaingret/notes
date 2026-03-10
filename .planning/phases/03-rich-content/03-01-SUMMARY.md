---
phase: 03-rich-content
plan: "01"
subsystem: test-scaffolding
tags: [tdd, red-state, bookmarks, schema, markdown, chips, search, tags]
dependency_graph:
  requires: []
  provides:
    - bookmarks-schema
    - bookmarks-migration
    - markdown-test-stubs
    - chips-test-stubs
    - bulletviewmode-test-stubs
    - tags-route-test-stubs
    - search-route-test-stubs
    - bookmarks-route-test-stubs
  affects:
    - server/db/schema.ts
    - client/package.json
tech_stack:
  added:
    - marked@^17.0.4 (markdown rendering)
    - dompurify@^3.3.2 (HTML sanitization)
    - "@types/dompurify@^3.0.5"
  patterns:
    - TDD RED state scaffolding (Wave 0)
    - Drizzle ORM pgTable with uniqueIndex
key_files:
  created:
    - server/db/migrations/0001_bookmarks.sql
    - client/src/test/markdown.test.ts
    - client/src/test/chips.test.ts
    - client/src/test/bulletContent.test.ts
    - server/tests/routes/tags.test.ts
    - server/tests/routes/search.test.ts
    - server/tests/routes/bookmarks.test.ts
  modified:
    - server/db/schema.ts (bookmarks table added)
    - client/package.json (marked + dompurify added)
decisions:
  - "bookmarks table uses uniqueIndex on (userId, bulletId) — enforced at DB level, prevents duplicate bookmarks"
  - "marked@17 + dompurify@3 installed together — marked renders markdown to HTML, dompurify sanitizes before display"
  - "TDD RED state: test stubs import from non-existent modules — import errors ARE the expected failure mode for Wave 0"
  - "Client chip tests operate on plain strings not HTML (renderWithChips input is plain text) — simplifies implementation surface"
metrics:
  duration: "~2min"
  completed_date: "2026-03-09"
  tasks_completed: 3
  files_created: 7
  files_modified: 2
---

# Phase 3 Plan 01: Wave 0 Test Scaffolding + Bookmarks Schema Summary

**One-liner:** Wave 0 RED-state scaffolding — 6 failing test stubs (3 client, 3 server), bookmarks DB schema with unique constraint, and marked+dompurify installed.

## What Was Built

### Task 1: Bookmarks Schema + Migration + Client Deps
- Added `uniqueIndex` to the drizzle-orm imports in `server/db/schema.ts`
- Appended `bookmarks` table export with UUID primary key, `userId`/`bulletId` FK references (cascade delete), unique constraint on `(userId, bulletId)`, and a user-id index
- Created `server/db/migrations/0001_bookmarks.sql` with full DDL: CREATE TABLE, FK constraints via DO/BEGIN blocks (idempotent), and btree index
- Installed `marked@^17.0.4`, `dompurify@^3.3.2`, and `@types/dompurify@^3.0.5` in client

### Task 2: Client Test Stubs (RED state)
- `markdown.test.ts`: 5 tests for `renderBulletMarkdown` imported from `../utils/markdown` (does not exist yet)
- `chips.test.ts`: 4 tests for `renderWithChips` imported from `../utils/chips` (does not exist yet)
- `bulletContent.test.ts`: 2 tests for `shouldShowEditMode` imported from `../utils/bulletViewMode` (does not exist yet)
- All 3 files fail with Vite import resolution errors — RED state confirmed

### Task 3: Server Route Test Stubs (RED state)
- `tags.test.ts`: 2 tests for GET /api/tags and GET /api/tags/:type/:value/bullets — mocks tagService
- `search.test.ts`: 3 tests for GET /api/search including 400 on missing param — mocks searchService
- `bookmarks.test.ts`: 3 tests for POST/DELETE/GET /api/bookmarks — mocks bookmarkService
- All 3 files fail with module-not-found errors for routes that don't exist yet — RED state confirmed

## Commits

| Hash | Task | Description |
|------|------|-------------|
| c66f56c | Task 1 | feat(03-01): add bookmarks schema, migration SQL, install marked+dompurify |
| 396cfae | Task 2 | test(03-01): add failing client test stubs for markdown, chips, bulletViewMode |
| 35b3f41 | Task 3 | test(03-01): add failing server route stubs for tags, search, bookmarks |

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

- [x] `server/db/schema.ts` contains `export const bookmarks`
- [x] `server/db/migrations/0001_bookmarks.sql` exists
- [x] `client/package.json` contains `marked` and `dompurify`
- [x] `client/src/test/markdown.test.ts` exists and fails (RED)
- [x] `client/src/test/chips.test.ts` exists and fails (RED)
- [x] `client/src/test/bulletContent.test.ts` exists and fails (RED)
- [x] `server/tests/routes/tags.test.ts` exists and fails (RED)
- [x] `server/tests/routes/search.test.ts` exists and fails (RED)
- [x] `server/tests/routes/bookmarks.test.ts` exists and fails (RED)
- [x] All 3 task commits present: c66f56c, 396cfae, 35b3f41
