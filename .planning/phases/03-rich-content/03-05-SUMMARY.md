---
phase: 03-rich-content
plan: 05
subsystem: api
tags: [express, typescript, bookmarks, tags, search, vitest]

# Dependency graph
requires:
  - phase: 03-02
    provides: bookmarkService, tagService, searchService implementations
  - phase: 03-01
    provides: RED scaffold tests for bookmarks, tags, search routes
provides:
  - Express router for /api/bookmarks (POST, DELETE, GET)
  - Express router for /api/tags (GET counts, GET bullets by tag)
  - Express router for /api/search (GET with ?q param)
  - All three routes registered in server/src/index.ts
affects: [03-06, 03-07, client-side API integration]

# Tech tracking
tech-stack:
  added: []
  patterns: [thin-route-adapter, service-layer-separation, cast-req-user-pattern]

key-files:
  created:
    - server/src/routes/bookmarks.ts
    - server/src/routes/tags.ts
    - server/src/routes/search.ts
  modified:
    - server/src/index.ts
    - server/tests/routes/bookmarks.test.ts
    - server/tests/routes/tags.test.ts
    - server/tests/routes/search.test.ts

key-decisions:
  - "Route handlers cast req.user as { id: string } — consistent with existing bullets.ts pattern, avoids Passport User type mismatch"
  - "ChipType cast as ChipType in tags route — route layer accepts string param, service layer enforces the type internally"

patterns-established:
  - "Thin route adapter: route files import from services, cast req.user, validate minimal params, delegate all logic to service layer"
  - "Vitest mock factories must not reference top-level const variables (hoisting bug) — use inline literal strings in vi.mock() factories"

requirements-completed: [BM-01, BM-02, TAG-04, TAG-05, SRCH-01, SRCH-02, SRCH-04]

# Metrics
duration: 5min
completed: 2026-03-09
---

# Phase 3 Plan 05: Express route wiring for bookmarks, tags, and search

**Three thin Express adapters wiring Plan 02 service layer into HTTP endpoints, all registered in index.ts with 88/88 tests passing**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-09T14:24:46Z
- **Completed:** 2026-03-09T14:29:00Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Created server/src/routes/bookmarks.ts with POST/DELETE/GET endpoints returning correct HTTP status codes (201, 204, 200)
- Created server/src/routes/tags.ts with GET /api/tags and GET /api/tags/:type/:value/bullets
- Created server/src/routes/search.ts with GET /api/search validating required ?q param (400 if missing)
- Registered all three routers in server/src/index.ts after existing /api/bullets routes
- Fixed Vitest hoisting bug in RED scaffold test files (vi.mock factories referenced top-level const before initialization)
- Full server test suite passes: 88/88 tests GREEN, tsc --noEmit clean

## Task Commits

Each task was committed atomically:

1. **Task 1: Create bookmarks, tags, search route files** - `83130f0` (feat)
2. **Task 2: Register new routes in index.ts** - `75adfd2` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `server/src/routes/bookmarks.ts` - Express router for /api/bookmarks: POST (201), DELETE /:bulletId (204), GET (200)
- `server/src/routes/tags.ts` - Express router for /api/tags: GET counts and GET /:type/:value/bullets
- `server/src/routes/search.ts` - Express router for /api/search: GET with ?q validation, 400 if missing
- `server/src/index.ts` - Added imports and app.use() for all three new routers
- `server/tests/routes/bookmarks.test.ts` - Fixed Vitest hoisting bug in mock factory
- `server/tests/routes/tags.test.ts` - Fixed Vitest hoisting bug in mock factory
- `server/tests/routes/search.test.ts` - Fixed Vitest hoisting bug in mock factory

## Decisions Made
- Route handlers cast `req.user as { id: string }` — consistent with existing bullets.ts pattern, avoids Passport `User` type mismatch
- `ChipType` cast in tags route — route layer accepts plain string URL param, service layer enforces valid values

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Vitest hoisting bug in RED scaffold test mocks**
- **Found during:** Task 1 (route test execution)
- **Issue:** Test files from Plan 01 used top-level `const BULLET_ID`, `DOC_ID`, `USER_ID` inside `vi.mock()` factory functions. Vitest hoists `vi.mock()` calls before `const` initialization, causing `ReferenceError: Cannot access 'BULLET_ID' before initialization`
- **Fix:** Replaced all variable references in vi.mock() factories with inline literal strings
- **Files modified:** server/tests/routes/bookmarks.test.ts, server/tests/routes/tags.test.ts, server/tests/routes/search.test.ts
- **Verification:** All 8 route tests turned GREEN after fix
- **Committed in:** 83130f0 (Task 1 commit)

**2. [Rule 1 - Bug] Fixed TypeScript type errors in route handlers**
- **Found during:** Task 2 (tsc --noEmit verification)
- **Issue:** `req.user!.id` fails TS check (Passport `User` type has no `id` property); `req.params.type` typed as `string | string[]` not assignable to `ChipType`
- **Fix:** Cast `req.user as { id: string }` per existing bullets.ts pattern; cast `type as ChipType` for service call
- **Files modified:** server/src/routes/bookmarks.ts, server/src/routes/tags.ts, server/src/routes/search.ts
- **Verification:** tsc --noEmit exits clean, all 88 tests pass
- **Committed in:** 75adfd2 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - Bug)
**Impact on plan:** Both fixes necessary for correct compilation and test execution. No scope creep.

## Issues Encountered
None beyond the auto-fixed TS and Vitest issues above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All three server routes wired and tested: /api/bookmarks, /api/tags, /api/search
- Services from Plan 02 are fully accessible via HTTP with auth protection
- Ready for client-side integration (Plan 06+): Flutter can now call these endpoints
- Server test suite fully GREEN: 88/88 tests

---
*Phase: 03-rich-content*
*Completed: 2026-03-09*
