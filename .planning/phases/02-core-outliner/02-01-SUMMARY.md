---
phase: 02-core-outliner
plan: "01"
subsystem: testing
tags: [vitest, supertest, tdd, bullets, undo, keyboard]

# Dependency graph
requires:
  - phase: 01-foundation
    provides: server test infrastructure (vitest, supertest), documents.test.ts pattern, client vitest setup

provides:
  - server/tests/bullets.test.ts — 22 failing stubs for bullet service (BULL-01..12)
  - server/tests/undo.test.ts — 10 failing stubs for undo service (UNDO-01..04)
  - server/tests/routes/bullets.routes.test.ts — supertest stubs for all bullet/undo HTTP endpoints
  - client/src/test/bulletTree.test.tsx — 22 failing stubs for zoom, keyboard, flattenTree
  - server/tests/helpers/bulletFixtures.ts — shared makeBullet / makeBulletTree factories

affects:
  - 02-02 (bullet service implementation — GREEN these tests)
  - 02-03 (undo service implementation)
  - 02-04 (route implementation)
  - 02-05 (client BulletTree component)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wave 0 RED scaffold: test files created before implementation so every plan verifies against an automated gate"
    - "makeBullet / makeBulletTree factory pattern for consistent fixture building across server tests"
    - "Route tests follow documents.test.ts pattern: vi.mock auth + service, buildApp(), supertest stubs"

key-files:
  created:
    - server/tests/helpers/bulletFixtures.ts
    - server/tests/bullets.test.ts
    - server/tests/undo.test.ts
    - server/tests/routes/bullets.routes.test.ts
    - client/src/test/bulletTree.test.tsx
  modified: []

key-decisions:
  - "Route test import paths use ../../src/... (relative from tests/routes/) to mirror documents.test.ts convention"
  - "Client test imports BulletTree from ../components/DocumentView/BulletTree — establishes expected export path for Phase 02-05"

patterns-established:
  - "Bullet fixture factory (makeBullet/makeBulletTree): use uuid overrides, fixed doc-1/user-1 defaults"
  - "Server route test: vi.mock services before router import, buildApp() helper, throw new Error stubs"

requirements-completed: [BULL-01, BULL-02, BULL-03, BULL-04, BULL-05, BULL-06, BULL-11, BULL-12, UNDO-01, UNDO-02, UNDO-03, UNDO-04, KB-01, KB-02, KB-03, KB-05, KB-06, KB-07]

# Metrics
duration: 8min
completed: 2026-03-09
---

# Phase 2 Plan 01: Test Scaffolds (Wave 0 RED) Summary

**Vitest RED scaffolds for bullet service, undo service, HTTP routes, and client BulletTree — 54 failing stubs establish behavioral contracts before implementation**

## Performance

- **Duration:** 8 min
- **Started:** 2026-03-09T11:43:00Z
- **Completed:** 2026-03-09T11:51:00Z
- **Tasks:** 4
- **Files modified:** 5

## Accomplishments

- Created `server/tests/helpers/bulletFixtures.ts` with `makeBullet` and `makeBulletTree` factories (uuid-based, sensible defaults)
- Created 3 server test scaffold files (22 + 10 + 18 stubs) all confirmed RED against vitest
- Created 1 client test scaffold (22 stubs) confirmed RED against vitest

## Task Commits

Each task was committed atomically:

1. **Task 1: Bullet service test scaffold** - `ebfc6e4` (test)
2. **Task 2: Undo service test scaffold** - `7c9c75c` (test)
3. **Task 3: Route integration test scaffold** - `f530ddd` (test)
4. **Task 4: Client bullet tree test scaffold** - `3241585` (test)

## Files Created/Modified

- `server/tests/helpers/bulletFixtures.ts` — makeBullet/makeBulletTree factory for server tests
- `server/tests/bullets.test.ts` — 22 failing stubs for bulletService (createBullet, indentBullet, outdentBullet, moveBullet, softDeleteBullet, markComplete, setCollapsed, computeBulletInsertPosition)
- `server/tests/undo.test.ts` — 10 failing stubs for undoService (recordUndoEvent, undo, redo, getStatus)
- `server/tests/routes/bullets.routes.test.ts` — supertest scaffold for all bullet and undo HTTP endpoints, following documents.test.ts pattern
- `client/src/test/bulletTree.test.tsx` — 22 failing stubs for zoom URL encoding, keyboard handler, global shortcuts, flattenTree

## Decisions Made

- Route test mock paths use `../../src/...` (relative from `tests/routes/`) — consistent with project import convention
- Client test imports `flattenTree` from `../components/DocumentView/BulletTree` — establishes expected export path for the implementation plan

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All test contracts are established — Plans 02-02 through 02-05 can now make tests GREEN one by one
- RED state confirmed for all four files; no false negatives
- Fixture factory ready for reuse in service unit tests

---
*Phase: 02-core-outliner*
*Completed: 2026-03-09*
