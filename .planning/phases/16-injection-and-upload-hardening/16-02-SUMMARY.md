---
phase: 16-injection-and-upload-hardening
plan: 02
subsystem: api
tags: [multer, express, file-upload, security, attachments, drizzle-orm]

# Dependency graph
requires:
  - phase: 16-injection-and-upload-hardening/16-01
    provides: SVG force-download and filename sanitization in attachments route
provides:
  - Multer fileFilter rejecting disallowed file types (.html, .exe, .js, .bat, .sh, .php, etc.) with 400 error
  - verifyBulletOwnership() in attachmentService.ts — queries bullets table with userId check
  - Bullet ownership check in POST /bullets/:bulletId — returns 404 on ownership failure, deletes orphaned file
  - Bullet ownership check in GET /bullets/:bulletId — returns 404 on ownership failure
affects:
  - phase-17
  - phase-18

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ALLOWED_EXTENSIONS Set pattern for multer fileFilter allowlist"
    - "verifyBulletOwnership() service function for ownership gate before attachment ops"
    - "Return 404 (not 403) for non-owned resources to hide existence"
    - "Orphan file cleanup on ownership failure in upload route"

key-files:
  created: []
  modified:
    - server/src/routes/attachments.ts
    - server/src/services/attachmentService.ts
    - server/tests/attachments.test.ts

key-decisions:
  - "Return 404 (not 403) when bullet not owned by user — hides bullet existence per user decision from planning"
  - "fileFilter rejections come as plain Error (not MulterError), handled separately in multer callback"
  - "Ownership check happens AFTER multer processes file in POST route — file deleted on ownership failure to prevent orphans"

patterns-established:
  - "verifyBulletOwnership(userId, bulletId): Promise<boolean> — SELECT bullets WHERE id=bulletId AND userId=userId"

requirements-completed: [UPLD-01, UPLD-03]

# Metrics
duration: 6min
completed: 2026-03-15
---

# Phase 16 Plan 02: Upload Hardening — Type Allowlist and Bullet Ownership Summary

**Multer ALLOWED_EXTENSIONS fileFilter blocking executables/scripts and verifyBulletOwnership() guarding POST+GET attachment routes with 404 on ownership failure**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-15T09:02:22Z
- **Completed:** 2026-03-15T09:08:24Z
- **Tasks:** 1 (TDD: RED + GREEN)
- **Files modified:** 3

## Accomplishments
- Multer fileFilter added to attachment upload — .html, .exe, .js, .bat, .sh, .php and all other disallowed extensions rejected with HTTP 400 and descriptive error message listing supported categories
- `verifyBulletOwnership(userId, bulletId)` added to `attachmentService.ts` using Drizzle ORM, queries bullets table with both `id` and `userId` filter
- POST `/bullets/:bulletId` now verifies bullet ownership after file is received; deletes orphaned file and returns 404 if check fails
- GET `/bullets/:bulletId` now verifies bullet ownership before querying; returns 404 if check fails
- All 7 attachment service tests pass (3 new for verifyBulletOwnership)

## Task Commits

Each task was committed atomically (TDD):

1. **RED — failing tests for verifyBulletOwnership** - `b5048c8` (test)
2. **GREEN — file type allowlist + ownership verification** - `8ccff4e` (feat)

**Plan metadata:** (docs commit below)

_Note: TDD task has two commits: test (RED) then feat (GREEN)._

## Files Created/Modified
- `server/src/routes/attachments.ts` - Added ALLOWED_EXTENSIONS Set, multer fileFilter, verifyBulletOwnership call in POST and GET routes; orphaned file cleanup on ownership failure
- `server/src/services/attachmentService.ts` - Added verifyBulletOwnership() export, imported bullets from schema
- `server/tests/attachments.test.ts` - Added 3 new tests for verifyBulletOwnership (true, false-wrong-user, false-missing)

## Decisions Made
- Return 404 (not 403) when bullet is not owned by user — hides bullet existence (pre-decided in planning)
- fileFilter in multer callback handles plain Error rejections (not MulterError), so the err branch returns 400 directly without calling next(err)
- Orphan file cleanup happens inline in the POST route handler (try/catch ignore) on ownership failure

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Attachment routes are now fully hardened: type allowlist, bullet ownership, SVG force-download, filename sanitization all in place
- Ready for phase 17 (next security hardening phase)

---
*Phase: 16-injection-and-upload-hardening*
*Completed: 2026-03-15*
