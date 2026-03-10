---
phase: 06-dark-mode
plan: "00"
subsystem: testing
tags: [vitest, dark-mode, tdd, css, html]

# Dependency graph
requires: []
provides:
  - "Failing test suite for DRKM-01, DRKM-03, DRKM-04 dark mode requirements (RED gate)"
affects:
  - "06-01 (Wave 1: CSS tokens) — must turn DRKM-01 tests GREEN"
  - "06-03 (Wave 3: FOUC + meta tag) — must turn DRKM-03 and DRKM-04 tests GREEN"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "File content assertion pattern: readFileSync + process.cwd() for Vitest test files"
    - "TDD Wave 0 RED scaffold written before any implementation"

key-files:
  created:
    - "client/src/test/darkMode.test.tsx"
  modified: []

key-decisions:
  - "Used process.cwd() + path.resolve() instead of import.meta.url for file path resolution — import.meta.url is not a file:// URL in jsdom environment"
  - "String contains assertion (no eval) for FOUC script test — sufficient to verify correct code pattern is present"
  - "6 tests written (plan specified 5) — split DRKM-04 and DRKM-03 into separate assertions for clearer failure messages"

patterns-established:
  - "Dark mode test pattern: readFileSync HTML/CSS files and assert string content — avoids DOM manipulation complexity"

requirements-completed:
  - DRKM-03
  - DRKM-04

# Metrics
duration: 5min
completed: 2026-03-10
---

# Phase 6 Plan 00: Dark Mode Tests (Wave 0 RED Scaffold) Summary

**Vitest test file asserting FOUC script pattern, color-scheme meta tag, and CSS dark token presence — all 6 tests RED before implementation**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-10T15:22:25Z
- **Completed:** 2026-03-10T15:25:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Created `darkMode.test.tsx` with 6 failing tests covering DRKM-01, DRKM-03, and DRKM-04 requirements
- Confirmed all 6 tests fail RED at commit time — no implementation exists yet
- Confirmed pre-existing test suite failures (mobileLayout.test.tsx) are pre-existing and not caused by this plan
- Fixed path resolution issue: `import.meta.url` is not a `file://` URL in jsdom — switched to `process.cwd()` + `path.resolve()`

## Task Commits

Each task was committed atomically:

1. **Task 1: Write darkMode.test.tsx (RED state)** - `591ab53` (test)

## Files Created/Modified
- `client/src/test/darkMode.test.tsx` - 6 failing tests for DRKM-01, DRKM-03, DRKM-04 requirements

## Decisions Made
- Used `process.cwd()` + `path.resolve()` instead of `import.meta.url` — jsdom environment provides a non-file URL for `import.meta.url`, causing `fileURLToPath` to throw "The URL must be of scheme file"
- Used string-contains assertions only (no eval/Function constructor) for FOUC script test — simpler and more robust
- Wrote 6 tests instead of the plan's 5 — split DRKM-04 into two `it()` blocks (name attribute + content attribute) for clearer failure messages on partial implementation

## Deviations from Plan

None — plan executed as specified. Minor enhancement: 6 tests instead of 5 (split DRKM-04 into two assertions for better failure granularity).

## Issues Encountered
- `import.meta.url` path resolution failed in jsdom — `fileURLToPath(new URL(..., import.meta.url))` throws "The URL must be of scheme file". Fixed by switching to `process.cwd()` which is `client/` in Vitest runs.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Wave 0 gate established: 6 RED tests will turn GREEN as implementation progresses across Waves 1-3
- Wave 1 (06-01) must make DRKM-01 tests GREEN by adding `@media (prefers-color-scheme: dark)` block and `--color-bg-base` token to index.css
- Wave 3 (06-03) must make DRKM-03 and DRKM-04 tests GREEN by adding FOUC script and color-scheme meta tag to index.html

---
*Phase: 06-dark-mode*
*Completed: 2026-03-10*
