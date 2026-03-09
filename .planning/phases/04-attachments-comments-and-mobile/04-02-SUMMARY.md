---
phase: 04-attachments-comments-and-mobile
plan: "02"
subsystem: database
tags: [drizzle, postgres, migration, bullets, attachments, notes]

# Dependency graph
requires:
  - phase: 04-01
    provides: RED test stubs for note patch and attachment service
provides:
  - nullable note text column on bullets table via migration
  - attachments table with FK constraints (bullet_id, user_id) via migration
  - patchBullet() service function for note field persistence
  - extended PATCH /api/bullets/:id handling note field with empty-string-to-null normalization
affects:
  - 04-03 (attachments service consumes attachments table)
  - 04-04 (NoteRow UI calls PATCH /:id with note field)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - patchBullet service pattern for lightweight field updates without undo recording
    - empty-string normalization at route layer ('' -> null before service call)

key-files:
  created:
    - server/db/migrations/0002_phase4_attachments_notes.sql
  modified:
    - server/db/schema.ts
    - server/src/routes/bullets.ts
    - server/src/services/bulletService.ts
    - server/tests/bullets.test.ts

key-decisions:
  - "patchBullet has no undo recording — note updates are lightweight metadata, not structural ops"
  - "empty string normalized to null at route layer, not service layer — keeps service pure"
  - "note column added after isCollapsed, before deletedAt in bullets table definition"

patterns-established:
  - "patchBullet(userId, bulletId, fields) pattern for simple field updates without undo"

requirements-completed: [CMT-01, CMT-02, CMT-03, CMT-04]

# Metrics
duration: 8min
completed: 2026-03-09
---

# Phase 4 Plan 02: Notes DB + PATCH Extension Summary

**Nullable note column on bullets table via ALTER TABLE migration, patchBullet() service with empty-string-to-null normalization, and attachments table created for Plan 03 consumption**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-09T20:20:00Z
- **Completed:** 2026-03-09T20:28:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added nullable `note text` column to bullets table in schema.ts and migration 0002
- Created attachments table (id, bullet_id, user_id, filename, mime_type, size, storage_path, created_at) with two indexes — ready for Plan 03
- Extended PATCH /api/bullets/:id to accept note field with empty-string-to-null normalization
- Added patchBullet() to bulletService with Drizzle .update() pattern, no undo recording
- Converted 2 RED stub tests (throwing errors) into real passing tests

## Task Commits

Each task was committed atomically:

1. **Task 1: DB schema — note column + attachments table** - `1653704` (feat)
2. **Task 2: PATCH route + service + real tests** - `a1018c4` (feat)

## Files Created/Modified
- `server/db/schema.ts` - Added note column to bullets, attachments table export, bigint import
- `server/db/migrations/0002_phase4_attachments_notes.sql` - ALTER TABLE + CREATE TABLE + two indexes
- `server/src/routes/bullets.ts` - Extended patchBulletSchema, added note block with normalization, imported patchBullet
- `server/src/services/bulletService.ts` - Added note to Bullet type, added patchBullet() function
- `server/tests/bullets.test.ts` - Replaced 2 stubs with real tests, added note to makeBulletRow defaults, imported patchBullet

## Decisions Made
- No undo recording for patchBullet — note updates are metadata not structural changes; consistent with markComplete pattern
- Empty string normalization happens at the route layer (not service) to keep service semantics pure
- bigint imported from drizzle-orm/pg-core (not already imported) to support attachments.size column

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required. Migration 0002 will be applied on next server startup.

## Next Phase Readiness
- Attachments table ready for Plan 03 (attachment upload service)
- PATCH /api/bullets/:id note field ready for Plan 04 (NoteRow UI)
- Migration 0002 must be applied on server before Phase 4 features are tested end-to-end

---
*Phase: 04-attachments-comments-and-mobile*
*Completed: 2026-03-09*
