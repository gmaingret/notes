---
phase: 03-rich-content
plan: 02
subsystem: server-services
tags: [bookmark, tag, search, drizzle, postgresql]
dependency_graph:
  requires: [03-01]
  provides: [bookmarkService, tagService, searchService]
  affects: [03-05]
tech_stack:
  added: []
  patterns: [drizzle-ilike, drizzle-sql-template, onConflictDoNothing]
key_files:
  created:
    - server/src/services/bookmarkService.ts
    - server/src/services/tagService.ts
    - server/src/services/searchService.ts
  modified: []
decisions:
  - searchBullets strips leading chip prefixes (#/@/!) before ILIKE match so "#milk" finds "milk"
  - getBulletsForTag builds pattern per chipType: tag→#value, mention→@value, date→!![value]
  - getTagCounts uses db.execute(sql`...`) with regexp_matches — count column cast via Number() since pg driver returns string
  - addBookmark uses onConflictDoNothing — idempotent, no error on duplicate (userId, bulletId)
metrics:
  duration: 5min
  completed_date: "2026-03-09"
  tasks: 2
  files: 3
---

# Phase 3 Plan 02: Service Layer (Bookmark, Tag, Search) Summary

**One-liner:** Three pure data-access service modules — bookmarkService (CRUD via Drizzle ORM), tagService (regexp_matches chip extraction), and searchService (ILIKE full-text) — all compiling clean with no TS errors.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Implement bookmarkService | 26dfff8 | server/src/services/bookmarkService.ts |
| 2 | Implement tagService and searchService | 10db3e8 | server/src/services/tagService.ts, server/src/services/searchService.ts |

## Deviations from Plan

None — plan executed exactly as written.

## Decisions Made

- `addBookmark` uses `onConflictDoNothing()` so duplicate bookmarks are silently ignored without an error response — consistent with the UNIQUE constraint on (userId, bulletId).
- `getTagCounts` casts `count` with `Number(row.count)` because the PostgreSQL node driver returns numeric aggregates as strings in raw query mode.
- `searchBullets` strips `^[#@!]+` prefix before matching — this means `#milk`, `@milk`, and `milk` all hit the same bullets, matching the `must_haves` truth that `searchBullets(userId, '#milk')` returns bullets containing 'milk'.
- `getBulletsForTag` uses ILIKE with prefix pattern per chip type — `%#value%`, `%@value%`, `%!![value]%`.

## Self-Check: PASSED

- bookmarkService.ts: FOUND
- tagService.ts: FOUND
- searchService.ts: FOUND
- Commit 26dfff8 (bookmarkService): FOUND
- Commit 10db3e8 (tagService + searchService): FOUND
- TypeScript compile (`npx tsc --noEmit`): CLEAN (no errors)
