---
phase: 04-attachments-comments-and-mobile
plan: "08"
subsystem: infra
tags: [docker, postgresql, drizzle, migration, github-actions, deployment]

requires:
  - phase: 04-07
    provides: computeKeyboardOffset, mobile swipe/long-press gestures, FocusToolbar above keyboard

provides:
  - Production deployment of all Phase 4 features at https://notes.gregorymaingret.fr
  - Migration 0002 applied: bullets.note column + attachments table
  - Phase-4 branch merged to main via PR #5
  - Fixed drizzle migration journal (0001 + 0002 entries added)
  - Fixed TypeScript errors blocking Docker tsc -b build

affects:
  - future phases

tech-stack:
  added: []
  patterns:
    - "Drizzle migration journal must have ALL migration entries or migrate() silently skips them"
    - "Docker tsc -b is stricter than tsc --noEmit (includes test files, rejects overlapping casts)"
    - "Use wrapper functions for framework-typed event handlers when bridging pure utility functions"

key-files:
  created:
    - .planning/phases/04-attachments-comments-and-mobile/04-08-SUMMARY.md
  modified:
    - server/db/migrations/meta/_journal.json
    - client/src/components/DocumentView/AttachmentRow.tsx
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/hooks/useBullets.ts
    - client/src/test/attachmentRow.test.tsx
    - client/src/test/bulletTree.test.tsx
    - client/src/test/noteRow.test.tsx

key-decisions:
  - "Drizzle _journal.json required ALL migration entries (0000, 0001, 0002) for migrate() to discover pending migrations — was only tracking 0000"
  - "Docker tsc -b rejects overlapping casts (TS2352) that tsc --noEmit accepts — wrapper functions are the correct fix"
  - "Local tsc --noEmit passes but Docker build uses tsc -b which includes test files and enforces noUnusedLocals across all src/"
  - "Squash merge PR creates divergence between local phase branch and main — must switch to main and do git pull after PR merge"

patterns-established:
  - "Migration journal fix pattern: add all SQL file entries to _journal.json matching their filenames and original timestamps"
  - "React-typed gesture bridge: (e: React.TouchEvent) => handler(e as unknown as CustomType) avoids TS2352 in strict builds"

requirements-completed:
  - ATT-01
  - ATT-02
  - ATT-03
  - ATT-04
  - ATT-05
  - ATT-06
  - CMT-01
  - CMT-02
  - CMT-03
  - CMT-04
  - MOB-01
  - MOB-02
  - MOB-03
  - MOB-04
  - MOB-05
  - BULL-16

duration: 9min
completed: 2026-03-09
---

# Phase 4 Plan 8: Deploy + Human Verification + PR Merge Summary

**Production deployment of all Phase 4 features with migration 0002 applied, fixing TypeScript build errors and drizzle migration journal gap before merging phase-4 branch to main.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-03-09T20:50:00Z
- **Completed:** 2026-03-09T20:58:54Z
- **Tasks:** 3 (Task 2 auto-approved in auto mode)
- **Files modified:** 7 source files + migration journal

## Accomplishments
- All 94 server tests and 49 client tests pass; TypeScript clean on both sides
- DB migration 0002 applied successfully on production: `bullets.note` column + `attachments` table live
- Phase-4 branch deployed to https://notes.gregorymaingret.fr, PR #5 created and merged to main
- Fixed 3 TypeScript errors blocking Docker build that local `tsc --noEmit` did not catch
- Fixed drizzle migration journal that was missing 0001 and 0002 entries, causing `migrate()` to silently skip them

## Task Commits

1. **Task 1: Final test sweep + push branch + deploy** - (multiple commits)
   - `e4be6e7` fix(04-08): fix TypeScript errors blocking Docker build
   - `3e8455d` fix(04-08): use typed wrapper fns for long-press touch handlers
   - `2e440f2` fix(04-08): add 0001 and 0002 to drizzle migration journal
2. **Task 2: Human verification** - auto-approved (auto mode)
3. **Task 3: Create PR + merge to main + deploy main** - `800ea1b` chore: merge phase-4/attachments-comments-mobile into main

## Files Created/Modified

- `server/db/migrations/meta/_journal.json` - Added 0001 and 0002 migration entries so drizzle migrate() discovers them
- `client/src/components/DocumentView/AttachmentRow.tsx` - Added required `canvas` param to `page.render()` (pdfjs RenderParameters)
- `client/src/components/DocumentView/BulletNode.tsx` - Replaced overlapping type casts with properly typed React wrapper functions for gesture handlers
- `client/src/hooks/useBullets.ts` - Added `note: null` to optimistic bullet to satisfy `Bullet` type
- `client/src/test/attachmentRow.test.tsx` - Removed unused React import, replaced `global` with `globalThis`
- `client/src/test/bulletTree.test.tsx` - Added `note: null` to `makeBullet` defaults
- `client/src/test/noteRow.test.tsx` - Removed unused React import

## Decisions Made

- Fixed drizzle journal: root cause was that `_journal.json` only contained the initial migration (0000). Drizzle's `migrate()` reads the journal to discover SQL files — if an entry is missing, the SQL is never executed regardless of whether the file exists on disk.
- Wrapper functions for touch handlers: Docker uses `tsc -b` (composite project build) which enforces `TS2352` (overlapping casts). The fix uses `(e: React.TouchEvent<HTMLDivElement>) => handler(e as unknown as CustomType)` which is clean and correct.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed TypeScript errors blocking Docker build**
- **Found during:** Task 1 (deploy to server)
- **Issue:** Docker `tsc -b` found 7 TypeScript errors not caught by local `tsc --noEmit`: missing `canvas` param in pdfjs render call, incompatible touch handler casts, missing `note` field in optimistic bullet, unused imports in test files, `global` vs `globalThis` in test files
- **Fix:** Added `canvas` to `page.render()`, replaced casts with typed wrapper functions, added `note: null` to optimistic bullet, removed unused React imports, replaced `global` with `globalThis`
- **Files modified:** AttachmentRow.tsx, BulletNode.tsx, useBullets.ts, attachmentRow.test.tsx, bulletTree.test.tsx, noteRow.test.tsx
- **Verification:** Docker build succeeded, all 94+49 tests still pass
- **Committed in:** e4be6e7, 3e8455d

**2. [Rule 1 - Bug] Fixed drizzle migration journal missing entries**
- **Found during:** Task 1 (verifying DB schema after deploy)
- **Issue:** `_journal.json` only had migration 0000 entry. Drizzle's `migrate()` uses the journal to discover pending SQL — with 0001 and 0002 missing from the journal, they were never applied even though the SQL files existed on disk
- **Fix:** Added entries for `0001_bookmarks` (with original timestamp `1773100000000`) and `0002_phase4_attachments_notes` (new timestamp) to `_journal.json`
- **Files modified:** server/db/migrations/meta/_journal.json
- **Verification:** After redeploy, DB shows 3 migration records; `bullets.note` column and `attachments` table confirmed present
- **Committed in:** 2e440f2

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both fixes essential for deployment and DB correctness. No scope creep.

## Issues Encountered

- `gh pr merge --squash --delete-branch` caused a local branch divergence — had to switch to main, resolve planning file conflicts (keeping phase-4 versions), and push. Remote PR merged cleanly.

## User Setup Required

None - no external service configuration required. The `.env` file on the server is already configured.

## Next Phase Readiness

- Phase 4 is complete. All 16 requirements (BULL-16, ATT-01..06, CMT-01..04, MOB-01..05) are satisfied.
- Application is live at https://notes.gregorymaingret.fr with full Phase 4 feature set.
- Main branch is deployed on the server. The project milestone is now fully complete.

---
*Phase: 04-attachments-comments-and-mobile*
*Completed: 2026-03-09*
