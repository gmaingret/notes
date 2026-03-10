---
phase: 07-icons-fonts-and-pwa
plan: "02"
subsystem: ui
tags: [lucide-react, icons, svg, fontsource, sharp, react, typescript]

# Dependency graph
requires:
  - phase: 07-01
    provides: TDD test scaffold with all 20 VISL-01/VISL-02/VISL-03/PWA tests RED

provides:
  - lucide-react ^0.577.0 installed as production dependency
  - "@fontsource-variable/inter ^5.2.8 installed"
  - "@fontsource-variable/jetbrains-mono ^5.2.8 installed"
  - sharp ^0.34.5 installed as devDependency
  - Sidebar.tsx: all Unicode icons replaced with Lucide SVG (MoreHorizontal, X, Plus, FileText, Tag, Star)
  - Sidebar tabs are icon-only with title attributes for accessibility
  - DocumentRow.tsx: MoreHorizontal replaces ⋯ overflow button
  - DocumentView.tsx: Menu replaces &#9776; hamburger entity
  - 5 VISL-01 tests GREEN (Sidebar, DocumentRow, DocumentView)

affects:
  - 07-03 (BulletNode icons)
  - 07-04 (fonts and PWA)

# Tech tracking
tech-stack:
  added:
    - lucide-react ^0.577.0 (SVG icon library, tree-shakable)
    - "@fontsource-variable/inter ^5.2.8 (Inter variable font, self-hosted)"
    - "@fontsource-variable/jetbrains-mono ^5.2.8 (JetBrains Mono variable font)"
    - sharp ^0.34.5 (devDep — PWA icon generation)
  patterns:
    - Lucide icons always use size=20 strokeWidth=1.5, no hardcoded color (inherits via currentColor)
    - No wrapping span added around icons where none existed before
    - Tab bar uses tabIcon const map pattern with IIFE for JSX, title attribute for accessibility

key-files:
  created: []
  modified:
    - client/package.json
    - client/package-lock.json
    - client/src/components/Sidebar/Sidebar.tsx
    - client/src/components/Sidebar/DocumentRow.tsx
    - client/src/components/DocumentView/DocumentView.tsx

key-decisions:
  - "Sidebar tabs icon-only: tabIcon const map inside IIFE so JSX is valid inside .map() callback"
  - "DocumentView hamburger: removed fontSize: '1.25rem' style since SVG handles its own sizing via size prop"

patterns-established:
  - "Lucide icon standard: size=20 strokeWidth=1.5 — no color, no wrapping span"
  - "Tab accessibility: icon-only buttons get title attribute for screen readers"

requirements-completed: [VISL-01]

# Metrics
duration: 8min
completed: 2026-03-10
---

# Phase 07 Plan 02: Lucide Icons in Sidebar Components Summary

**lucide-react, fontsource (Inter/JetBrains Mono), and sharp installed; Sidebar.tsx/DocumentRow.tsx/DocumentView.tsx Unicode icons replaced with Lucide SVG at size=20 strokeWidth=1.5**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-10T17:25:00Z
- **Completed:** 2026-03-10T17:33:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Installed all 4 Phase 7 npm dependencies in one command (lucide-react, inter, jetbrains-mono, sharp)
- Replaced all Unicode/emoji icons in Sidebar.tsx: overflow (⋯→MoreHorizontal), close (✕→X), new doc (+→Plus), and tab bar (text/emoji→FileText/Tag/Star icon-only with title attributes)
- Replaced overflow icon in DocumentRow.tsx (⋯→MoreHorizontal) and hamburger in DocumentView.tsx (&#9776;→Menu, removed fontSize style)
- 5 VISL-01 tests now GREEN; remaining 19 tests remain RED as expected (BulletNode, fonts, PWA — later plans)
- TypeScript compiles with no errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Install npm dependencies** - `0e1b4ae` (chore)
2. **Task 2: Replace icons in Sidebar.tsx, DocumentRow.tsx, DocumentView.tsx** - `a0002ea` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `client/package.json` - Added lucide-react, @fontsource-variable/inter, @fontsource-variable/jetbrains-mono, sharp
- `client/package-lock.json` - Lockfile updated for new packages
- `client/src/components/Sidebar/Sidebar.tsx` - Lucide imports; MoreHorizontal/X/Plus/FileText/Tag/Star replacing all Unicode icons; tabs icon-only with title attribute
- `client/src/components/Sidebar/DocumentRow.tsx` - MoreHorizontal import; ⋯ replaced
- `client/src/components/DocumentView/DocumentView.tsx` - Menu import; &#9776; replaced; removed fontSize style from hamburger button

## Decisions Made

- Sidebar tabs icon-only using a `tabIcon` const map inside an IIFE — cleaner than defining the map at module scope since it contains JSX
- `fontSize: '1.25rem'` removed from DocumentView hamburger button — Lucide SVG controls its own size via the `size` prop, and the style was only there to scale the Unicode character

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- lucide-react and fontsource packages are installed and ready for plans 03 and 04
- VISL-01 pattern established: icons at size=20 strokeWidth=1.5, no hardcoded color
- Plan 03 can now add Lucide icons to BulletNode, FilteredBulletList, AttachmentRow
- Plan 04 can add fontsource imports and PWA manifest

---
*Phase: 07-icons-fonts-and-pwa*
*Completed: 2026-03-10*
