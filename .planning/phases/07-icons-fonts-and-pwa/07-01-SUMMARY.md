---
phase: 07-icons-fonts-and-pwa
plan: "01"
subsystem: testing
tags: [vitest, tdd, icons, fonts, pwa, inter, jetbrains-mono, lucide-react]

# Dependency graph
requires: []
provides:
  - RED test scaffold for all 6 phase-7 requirements (VISL-01, VISL-02, VISL-03, PWA-01, PWA-02, PWA-03)
  - 20 failing tests covering icon replacement, font loading, and PWA manifest
affects:
  - 07-icons-fonts-and-pwa (phases 02+ will implement to make these tests GREEN)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "File-read assertions via readFileSync + not.toContain for Unicode absence testing"
    - "existsSync checks for binary file existence (PNG icons)"
    - "Lazy manifest reads inside test body (deferred) to avoid crash when file missing"

key-files:
  created:
    - client/src/test/iconsAndFonts.test.ts
  modified: []

key-decisions:
  - "TDD Wave 0: all 20 tests RED before any production code — establishes verification contract for phase 7"
  - "PWA-01 manifest tests use readFileSync inside test body (not top-level) to avoid test file crash when manifest.webmanifest doesn't exist yet"
  - "Unicode absence tests use not.toContain — tests fail today when chars are present, pass after replacement"

patterns-established:
  - "Unicode icon absence: expect(fileContent).not.toContain(char) — fails when char present, passes after Lucide replacement"
  - "Binary file existence: expect(existsSync(path)).toBe(true) — standard for PNG icon checks"

requirements-completed:
  - VISL-01
  - VISL-02
  - VISL-03
  - PWA-01
  - PWA-02
  - PWA-03

# Metrics
duration: 5min
completed: 2026-03-10
---

# Phase 07 Plan 01: Icons and Fonts TDD Wave 0 Summary

**20-test RED scaffold verifying Lucide icon replacement, Inter/JetBrains Mono variable font loading, and PWA manifest/icon existence across 6 requirements**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-10T17:15:00Z
- **Completed:** 2026-03-10T17:20:51Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- 20 RED tests created, all failing as expected, covering VISL-01 through PWA-03
- Icon absence assertions span 6 component files (Sidebar, DocumentRow, DocumentView, BulletNode, FilteredBulletList, AttachmentRow)
- Font assertions check both main.tsx import and index.css font-family rules
- PWA assertions check manifest existence, content fields, and PNG icon files
- Deferred manifest file reads (inside test body) prevent test file crash before manifest exists

## Task Commits

Each task was committed atomically:

1. **Task 1: RED test scaffold** - `d46b422` (test)

**Plan metadata:** _(docs commit follows)_

## Files Created/Modified
- `client/src/test/iconsAndFonts.test.ts` - 20 RED tests across 6 requirement groups, following darkMode.test.tsx file-read pattern

## Decisions Made
- PWA-01/PWA-03 manifest tests use `readFileSync` inside the `it()` body rather than at top-level. This prevents Vitest from crashing when `manifest.webmanifest` doesn't exist — the test still fails with a meaningful ENOENT error rather than aborting the whole test run.
- Unicode absence uses `not.toContain` semantics: tests fail today (chars present), pass after phase-7 implementation removes them.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- RED scaffold complete — phases 07-02 through 07-06 can now implement features to turn these tests GREEN
- Implementation order: icons first (VISL-01), then fonts (VISL-02, VISL-03), then PWA manifest and icons (PWA-01, PWA-02, PWA-03)
- All 12 Unicode characters identified and their exact file locations confirmed

---
*Phase: 07-icons-fonts-and-pwa*
*Completed: 2026-03-10*
