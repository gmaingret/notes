---
phase: 03-rich-content
plan: 04
subsystem: ui
tags: [react, marked, dompurify, contenteditable, chips, markdown]

# Dependency graph
requires:
  - phase: 03-01
    provides: test scaffolds for markdown, chips, bulletViewMode
  - phase: 03-03
    provides: uiStore setSidebarTab + setCanvasView for chip click routing
provides:
  - renderBulletMarkdown utility (marked.parseInline + DOMPurify, no <p> wrapper)
  - renderWithChips utility (tag/mention/date chip regex with negative lookbehind)
  - shouldShowEditMode helper (pass-through for test stub)
  - BulletContent with isEditing view/edit mode toggle + chip click routing
affects: [03-05, 04-mobile-polish]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "isEditing state pattern: span renders HTML in view mode, contenteditable div in edit mode"
    - "Singleton style injection: chip and shake CSS injected once via module-level guard"
    - "Event delegation on rendered span: chip clicks handled via dataset.chipType/chipValue"
    - "Invisible input date picker: position:fixed;opacity:0 native input for !! trigger"

key-files:
  created:
    - client/src/utils/markdown.ts
    - client/src/utils/chips.ts
    - client/src/utils/bulletViewMode.ts
  modified:
    - client/src/components/DocumentView/BulletContent.tsx

key-decisions:
  - "BulletContent isEditing: span for view mode + contenteditable div for edit mode — never set innerHTML on contenteditable"
  - "useLayoutEffect to set textContent + focus on edit mode entry — runs synchronously before browser paint"
  - "handleBlur flushes save timer immediately on blur — ensures content saved when switching to view mode"
  - "!! date picker: trigger on content containing !! but not !![ — avoids re-trigger on existing date chips"
  - "Escape key calls divRef.current.blur() which triggers handleBlur — single exit path for edit mode"

patterns-established:
  - "Chip click delegation: target.dataset.chipType drives action routing (tag/mention → sidebar filter, date → date picker)"
  - "Date chip edit: replaces !![oldDate] with !![newDate] in localContent via string replace"
  - "Enter key sets isEditing(false) before createBullet — current bullet renders in view mode immediately after split"

requirements-completed: [BULL-09, BULL-10, TAG-01, TAG-02, TAG-03]

# Metrics
duration: 5min
completed: 2026-03-09
---

# Phase 3 Plan 04: Inline Markdown Rendering + Chip Syntax Summary

**Bullets now render bold/italic/strikethrough/links and #tag/@mention/date chips as styled HTML in view mode, switching to raw contenteditable on click using isEditing state with marked + DOMPurify**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-09T13:22:00Z
- **Completed:** 2026-03-09T13:27:01Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- Created `renderBulletMarkdown` (marked.parseInline + DOMPurify, no `<p>` wrapping)
- Created `renderWithChips` (regex for #tag, @mention, !![date] with negative lookbehind for href attributes)
- Modified BulletContent with isEditing toggle: span+dangerouslySetInnerHTML in view mode, contenteditable div in edit mode
- Chip click handler routes tag/mention to sidebar filter, date to date picker
- `!!` trigger in handleInput opens native date picker and inserts !![YYYY-MM-DD]
- All 32 client tests pass (markdown x5, chips x4, bulletContent x2, bulletTree x21), tsc clean

## Task Commits

1. **Task 1: Implement renderBulletMarkdown and renderWithChips utilities** - `4c76b3a` (feat)
2. **Task 2: Add markdown toggle + chip rendering + date picker to BulletContent** - `bb6d0f6` (feat)

## Files Created/Modified

- `client/src/utils/markdown.ts` - renderBulletMarkdown using marked.parseInline + DOMPurify sanitize
- `client/src/utils/chips.ts` - renderWithChips with tag/mention/date chip regex replacements
- `client/src/utils/bulletViewMode.ts` - shouldShowEditMode pass-through for test stub
- `client/src/components/DocumentView/BulletContent.tsx` - isEditing state, view/edit mode swap, chip click routing, date picker trigger

## Decisions Made

- **isEditing + span pattern**: Never set innerHTML on contenteditable — separate span for view mode avoids cursor/selection corruption
- **useLayoutEffect for edit mode entry**: Synchronous DOM update ensures textContent is set before browser paint and focus
- **handleBlur flushes save timer**: Guarantees content is persisted when switching to view mode
- **!! date picker trigger**: Only when content has `!!` but not `!![` — prevents re-triggering on already-inserted date chips
- **Escape via blur**: Escape calls `divRef.current.blur()` which triggers handleBlur — single consistent exit path

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Markdown rendering and chip syntax fully operational in BulletContent
- uiStore chip click routing wired: #tag and @mention switch sidebar to Tags tab with filtered canvas view
- Date chip editing via date picker complete
- Ready for Phase 03-05 (remaining wave 2 plans)

---
*Phase: 03-rich-content*
*Completed: 2026-03-09*
