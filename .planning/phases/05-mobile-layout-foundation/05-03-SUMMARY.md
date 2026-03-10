---
phase: 05-mobile-layout-foundation
plan: "03"
subsystem: ui
tags: [react, css, touch-targets, mobile, accessibility]

# Dependency graph
requires:
  - phase: 05-02
    provides: Hamburger button and X close button already at 44x44px; mobile sidebar off-canvas animation; 100dvh layout
provides:
  - Sidebar header buttons (menu, new doc) expanded to 44x44px via iconButtonStyle
  - Bullet dot drag handle expanded to 44x44px tap area via .bullet-dot::after pseudo-element
  - FocusToolbar icon buttons expanded to 44x44px via btnStyle
  - Breadcrumb clickable spans expanded to minHeight 44px via inline-flex
  - index.css .bullet-dot utility class for pseudo-element tap expansion
affects:
  - Phase 8 swipe polish (BulletNode drag handle tap area set here)
  - Any future components adding icon buttons (iconButtonStyle pattern)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "CSS pseudo-element ::after with inset:-14px to expand 16px dot to 44px tap area without layout impact"
    - "Inline-flex with alignItems:center and minHeight:44 for reliable touch target on span elements"
    - "minWidth/minHeight:44 + display:flex + center alignment on icon button style objects"

key-files:
  created: []
  modified:
    - client/src/index.css
    - client/src/components/Sidebar/Sidebar.tsx
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/components/DocumentView/FocusToolbar.tsx
    - client/src/components/DocumentView/Breadcrumb.tsx

key-decisions:
  - "Used CSS ::after pseudo-element (inset:-14px) for bullet dot tap expansion — zero layout impact, visual dot size unchanged"
  - "Switched Breadcrumb clickable spans from inline-block to inline-flex for reliable minHeight enforcement across browsers"
  - "No global CSS touch-target rule — targeted per-element fix to avoid breaking toolbar and tab bar layouts (CONTEXT.md locked decision)"

patterns-established:
  - "bullet-dot CSS class: apply to any drag handle dot that needs expanded tap area without layout change"
  - "iconButtonStyle pattern: minWidth/minHeight 44 + display:flex + center — reusable for any sidebar icon button"

requirements-completed: [MOBL-05]

# Metrics
duration: 1min
completed: 2026-03-10
---

# Phase 5 Plan 03: Touch Target Audit Summary

**Targeted 44x44px touch target expansion on sidebar buttons, bullet dot drag handle, FocusToolbar icons, and breadcrumb navigation links — zero visual change, pure tap area expansion**

## Performance

- **Duration:** 1 min
- **Started:** 2026-03-10T13:24:46Z
- **Completed:** 2026-03-10T13:25:46Z
- **Tasks:** 1 of 1 auto tasks completed (checkpoint pending human verification)
- **Files modified:** 5

## Accomplishments

- Sidebar header icon buttons (menu, new doc) now have 44x44px minimum bounding box via updated `iconButtonStyle`
- Bullet dot drag handle tap area expanded to 44px using `.bullet-dot::after` pseudo-element (visual dot size unchanged at 16px)
- FocusToolbar icon buttons (indent, outdent, move, undo, redo, attach, note, bookmark, complete, delete) all at 44x44px
- Breadcrumb clickable ancestor spans switched to `inline-flex` with `minHeight: 44` for reliable cross-browser touch targets
- Full Vitest suite: 49/49 tests GREEN, no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Expand touch targets on sidebar buttons, bullet dot, and FocusToolbar** - `548cb1c` (feat)

**Plan metadata:** TBD (docs: complete plan)

## Files Created/Modified

- `client/src/index.css` — Added `.bullet-dot` and `.bullet-dot::after` rules for pseudo-element tap expansion
- `client/src/components/Sidebar/Sidebar.tsx` — `iconButtonStyle` updated with minWidth/minHeight 44, display flex, center
- `client/src/components/DocumentView/BulletNode.tsx` — Dot drag handle div gets `className="bullet-dot"`
- `client/src/components/DocumentView/FocusToolbar.tsx` — `btnStyle` updated with minWidth/minHeight 44, display flex, center
- `client/src/components/DocumentView/Breadcrumb.tsx` — `clickableStyle` switched from `inline-block` to `inline-flex` with `minHeight: 44`

## Decisions Made

- Used CSS `::after` pseudo-element (`inset: -14px`) for bullet dot — zero layout impact, visual dot stays 16px, tap area becomes 44px
- Switched Breadcrumb spans from `inline-block` to `inline-flex` — `inline-block` does not reliably enforce `minHeight` on spans in all browsers
- Applied targeted per-element fixes only (no global CSS rule) per CONTEXT.md locked decision to avoid breaking toolbar/tab bar layouts

## Deviations from Plan

None — plan executed exactly as written. All 5 elements updated as specified.

## Issues Encountered

None.

## User Setup Required

**Deploy to server required before verification:**
```bash
scp -r client root@192.168.1.50:/root/notes/ && ssh root@192.168.1.50 "cd /root/notes && docker compose up -d --build"
```

Then verify all 7 MOBL requirements at https://notes.gregorymaingret.fr using mobile viewport (375px wide).

## Next Phase Readiness

- All Phase 5 (MOBL-01 through MOBL-07) implementation complete — awaiting human verification at https://notes.gregorymaingret.fr
- Phase 8 (swipe polish) can reference `.bullet-dot` class and `iconButtonStyle` pattern established here
- No blockers — all automated tests GREEN

## Self-Check: PASSED

- FOUND: client/src/index.css (with .bullet-dot::after rule)
- FOUND: client/src/components/Sidebar/Sidebar.tsx (iconButtonStyle minWidth/minHeight 44)
- FOUND: client/src/components/DocumentView/BulletNode.tsx (className="bullet-dot")
- FOUND: client/src/components/DocumentView/FocusToolbar.tsx (btnStyle minWidth/minHeight 44)
- FOUND: client/src/components/DocumentView/Breadcrumb.tsx (minHeight 44)
- FOUND: .planning/phases/05-mobile-layout-foundation/05-03-SUMMARY.md
- FOUND commit: 548cb1c (task 1 feat commit)
- Server deployed and container rebuilt successfully

---
*Phase: 05-mobile-layout-foundation*
*Completed: 2026-03-10*
