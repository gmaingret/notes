---
phase: 07-icons-fonts-and-pwa
plan: "03"
subsystem: ui
tags: [lucide-react, icons, react, typescript, css]

# Dependency graph
requires:
  - phase: 07-02
    provides: lucide-react installed, Sidebar/DocumentRow/DocumentView icons replaced
provides:
  - BulletNode swipe icons (Check, Trash2), collapse chevron (ChevronRight), bookmark (Star) via Lucide
  - FilteredBulletList bookmark star (Star) with star-filled/star-outline CSS classes
  - AttachmentRow generic file icon (Paperclip) via Lucide
  - .star-filled and .star-outline CSS utility classes in index.css using design tokens
affects: [07-04, 07-05, any plan touching BulletNode or FilteredBulletList]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Star icon fill state via CSS classes (star-filled/star-outline) not SVG fill prop"
    - "Lucide icons: size=16 for inline bullet-row icons, size=20 for standalone action icons"
    - "Never pass color prop directly to Lucide icons — use className or CSS inheritance"

key-files:
  created: []
  modified:
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/components/DocumentView/FilteredBulletList.tsx
    - client/src/components/DocumentView/AttachmentRow.tsx
    - client/src/index.css

key-decisions:
  - "Star fill state via .star-filled/.star-outline CSS classes (fill + color) instead of inline SVG props — consistent with design token approach"
  - "Removed unused ArrowRight import from FilteredBulletList (no nav arrow character existed in file)"
  - "swipeIcon variable removed entirely; conditional JSX rendering of Check/Trash2 replaces it"

patterns-established:
  - "star-filled: fill + color both set to --color-accent-amber so SVG fill and currentColor are both covered"
  - "star-outline: fill:none + color:--color-text-muted for empty bookmark state"

requirements-completed: [VISL-01]

# Metrics
duration: 8min
completed: 2026-03-10
---

# Phase 7 Plan 03: Replace BulletNode, FilteredBulletList, AttachmentRow icons Summary

**Swipe actions (Check/Trash2), collapse chevron (ChevronRight), bookmarks (Star), and file attachment icon (Paperclip) replaced with Lucide React SVG components; completing VISL-01 — zero Unicode interactive icons remain in the app**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-10T17:30:00Z
- **Completed:** 2026-03-10T17:38:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- BulletNode: removed `swipeIcon` string variable, replaced ✅/🗑️ with conditional Check/Trash2 Lucide components; replaced ▶ chevron with ChevronRight; replaced 🔖 with Star
- FilteredBulletList: replaced ★/☆ bookmark toggle characters with Star component using star-filled/star-outline CSS classes
- AttachmentRow: replaced 📎 span with Paperclip component, removing the wrapping span entirely
- Added .star-filled and .star-outline CSS utility classes to index.css using design tokens (--color-accent-amber, --color-text-muted)
- All 12 VISL-01 tests pass; TypeScript compiles without errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace BulletNode icons and add Star CSS classes** - `23e7c77` (feat)
2. **Task 2: Replace FilteredBulletList and AttachmentRow icons** - `42502ac` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `client/src/components/DocumentView/BulletNode.tsx` - Lucide imports added, swipeIcon removed, Check/Trash2/ChevronRight/Star render replacing Unicode characters
- `client/src/components/DocumentView/FilteredBulletList.tsx` - Star import, ★/☆ replaced with Star + className
- `client/src/components/DocumentView/AttachmentRow.tsx` - Paperclip import, 📎 span replaced
- `client/src/index.css` - .star-filled and .star-outline classes added after .chip section

## Decisions Made
- Star fill state controlled via CSS classes (.star-filled/.star-outline) that set both `fill` and `color` properties — ensures the SVG fill attribute and CSS currentColor are both correctly applied
- Removed unused ArrowRight import from FilteredBulletList — no navigation arrow character existed in the file (plan noted to check actual file first)
- swipeIcon variable removed entirely rather than converted — conditional JSX is cleaner than a nullable string

## Deviations from Plan

None - plan executed exactly as written (minus removing unused ArrowRight import which was a clean-up, not a deviation).

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- VISL-01 complete: zero Unicode interactive icons remain in any component file
- .star-filled / .star-outline CSS classes available for any future Star icon usage
- Ready for Plan 04 (favicon.svg) and Plan 05 (fonts: Inter Variable, JetBrains Mono Variable)

---
*Phase: 07-icons-fonts-and-pwa*
*Completed: 2026-03-10*
