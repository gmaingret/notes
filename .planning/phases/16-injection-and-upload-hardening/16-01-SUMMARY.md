---
phase: 16-injection-and-upload-hardening
plan: "01"
subsystem: server/security
tags: [security, ilike-injection, xss, attachments, search, tags]
dependency_graph:
  requires: []
  provides: [INJ-01, INJ-02, INJ-03, UPLD-02]
  affects:
    - server/src/services/searchService.ts
    - server/src/services/tagService.ts
    - server/src/routes/attachments.ts
tech_stack:
  added:
    - server/src/services/utils/escapeIlike.ts (new utility module)
  patterns:
    - PostgreSQL ILIKE backslash escaping (\% and \_ for literal metacharacters)
    - Content-Disposition: attachment for SVG to prevent stored XSS
key_files:
  created:
    - server/src/services/utils/escapeIlike.ts
    - server/tests/search.test.ts
    - server/tests/attachments-svg.test.ts
  modified:
    - server/src/services/searchService.ts
    - server/src/services/tagService.ts
    - server/src/routes/attachments.ts
decisions:
  - "Placed escapeIlike in a shared utils module (not inlined) to avoid duplication between searchService and tagService"
  - "SVG force-download chosen over sanitization per prior user decision (no server-side SVG sanitization)"
  - "Filename sanitization applied to ALL attachments at the single header-setting point in the route"
metrics:
  duration: "~6 minutes"
  completed: "2026-03-15"
  tasks_completed: 2
  tasks_total: 2
  files_created: 3
  files_modified: 3
  tests_added: 17
---

# Phase 16 Plan 01: Injection and Upload Hardening Summary

**One-liner:** PostgreSQL ILIKE metacharacter escaping via shared backslash-escape helper, plus SVG forced download and Content-Disposition filename sanitization to prevent stored XSS and header injection.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | ILIKE metacharacter escaping in search and tag services | dedebef | escapeIlike.ts, searchService.ts, tagService.ts, search.test.ts |
| 2 | SVG force-download via Content-Disposition: attachment | 2d08f9a | attachments.ts, attachments-svg.test.ts |

## What Was Built

### Task 1: ILIKE Metacharacter Escaping

Created `server/src/services/utils/escapeIlike.ts` — a shared helper that replaces `%` with `\%` and `_` with `\_` using PostgreSQL ILIKE's default backslash escape syntax.

Applied the helper in:
- `searchService.ts` line 17: `const pattern = \`%${escapeIlike(normalized)}%\``
- `tagService.ts` getBulletsForTag: `const escaped = escapeIlike(value)` before pattern construction for all three chip types (tag, mention, date)

Tests in `server/tests/search.test.ts` (9 tests):
- Direct pattern verification via mocked `ilike` from drizzle-orm
- Covers `%` escaping, `_` escaping, combined input, normal alphanumeric, and empty query

### Task 2: SVG Force-Download and Filename Sanitization

Updated the GET `/:id/file` route handler in `server/src/routes/attachments.ts`:
- SVG files (`image/svg+xml`): `Content-Disposition: attachment; filename="..."` — forces browser save dialog
- All other files: `Content-Disposition: inline; filename="..."` — unchanged behavior
- Filename sanitization applied to all files: strips control characters (0x00-0x1F), replaces `"` with `'`

Tests in `server/tests/attachments-svg.test.ts` (8 tests):
- SVG returns attachment disposition
- PNG/JPEG/PDF return inline disposition
- Double quotes in filename are replaced with single quotes
- Newlines, carriage returns, and control characters are stripped

## Test Results

All 111 tests pass across 11 test files (full regression run confirmed).

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check

### Created files exist

- `server/src/services/utils/escapeIlike.ts` — FOUND
- `server/tests/search.test.ts` — FOUND
- `server/tests/attachments-svg.test.ts` — FOUND

### Modified files

- `server/src/services/searchService.ts` — contains `escapeIlike` FOUND
- `server/src/services/tagService.ts` — contains `escapeIlike` FOUND
- `server/src/routes/attachments.ts` — contains `attachment` FOUND

### Commits exist

- dedebef — FOUND
- 2d08f9a — FOUND

## Self-Check: PASSED
