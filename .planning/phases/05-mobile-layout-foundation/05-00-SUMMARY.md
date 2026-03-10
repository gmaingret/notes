---
phase: 05-mobile-layout-foundation
plan: "00"
subsystem: testing
tags: [vitest, react-testing-library, mobile, sidebar, tdd]

# Dependency graph
requires: []
provides:
  - Vitest RED test scaffold for mobile layout behaviors (MOBL-01..04, MOBL-06, MOBL-07)
  - 6 describe blocks with failing assertions covering hamburger, backdrop, X button, sidebar-open class, 100dvh, Ctrl+E
affects:
  - 05-mobile-layout-foundation (all subsequent plans use this scaffold for GREEN verification)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "TDD Wave 0 scaffold: write failing tests before production code exists"
    - "uiStore mock pattern with mutable module-level variable for controlling sidebarOpen per test"
    - "vi.mock of react-router-dom, useDocuments, AuthContext, uiStore for isolated component rendering"

key-files:
  created:
    - client/src/test/mobileLayout.test.tsx
  modified: []

key-decisions:
  - "MOBL-02 (backdrop) tests pass immediately because current Sidebar already renders .mobile-overlay when sidebarOpen=true — this is expected, the scaffold correctly tests the target behavior"
  - "Used mutable module-level mockSidebarOpen variable pattern instead of vi.fn() mock factory to allow per-test control of sidebarOpen state without re-importing the mock"

patterns-established:
  - "Mobile layout TDD: write all behavior tests in RED before implementing any production changes"
  - "uiStore mock: use mutable let variable + vi.mock factory to control sidebarOpen value per test"

requirements-completed: [MOBL-01, MOBL-02, MOBL-03, MOBL-04, MOBL-06, MOBL-07]

# Metrics
duration: 12min
completed: 2026-03-10
---

# Phase 5 Plan 00: Mobile Layout Test Scaffold Summary

**Vitest RED scaffold with 6 failing describe blocks covering hamburger toggle, backdrop dismiss, X close button, sidebar CSS class, 100dvh height, and Ctrl+E keyboard shortcut for mobile sidebar**

## Performance

- **Duration:** 12 min
- **Started:** 2026-03-10T14:24:52Z
- **Completed:** 2026-03-10T14:36:31Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Created `client/src/test/mobileLayout.test.tsx` with 6 describe blocks, 12 tests total
- All 6 MOBL requirements represented: MOBL-01 (hamburger), MOBL-02 (backdrop), MOBL-03 (X button), MOBL-04 (sidebar CSS class), MOBL-06 (100dvh), MOBL-07 (Ctrl+E)
- Tests run without import errors; exit non-zero (9 failing, 3 passing due to pre-existing backdrop implementation)
- Existing test suite (10 files, 52 tests) remains fully green

## Task Commits

Each task was committed atomically:

1. **Task 1: Write failing test scaffold for mobile layout behaviors** - `7078251` (test)

**Plan metadata:** *(docs commit follows)*

## Files Created/Modified
- `client/src/test/mobileLayout.test.tsx` - Wave 0 RED test scaffold for all mobile layout requirements

## Decisions Made
- MOBL-02 (backdrop) tests pass because `.mobile-overlay` div already exists in Sidebar when `sidebarOpen=true`. This is correct behavior — the backdrop tests verify the target state (close-on-click), not new DOM elements. The subsequent implementation plans will turn remaining MOBL tests GREEN.
- Used a mutable module-level `let mockSidebarOpen` variable combined with `vi.mock` factory to allow per-describe control of the store state. This is the cleanest pattern for tests that need to exercise both open and closed states.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## Self-Check: PASSED
- `client/src/test/mobileLayout.test.tsx` — FOUND
- Commit `7078251` — FOUND
- `npx vitest run src/test/mobileLayout.test.tsx` exits non-zero (9 failing tests confirm RED state)
- All 6 describe blocks present: MOBL-01, MOBL-02, MOBL-03, MOBL-04, MOBL-06, MOBL-07

## Next Phase Readiness
- Test harness is ready. Plans 05-01 through 05-0N will implement the mobile layout features and run this scaffold to confirm GREEN.
- MOBL-02 backdrop tests are already green; the implementation plans can treat MOBL-02 as partially done (overlay exists, mobile CSS visibility still needed).

---
*Phase: 05-mobile-layout-foundation*
*Completed: 2026-03-10*
