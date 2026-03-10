---
phase: 06-dark-mode
plan: "02"
subsystem: DocumentView Components
tags: [dark-mode, css-tokens, color-migration, documentview]
dependency_graph:
  requires: ["06-01"]
  provides: ["DocumentView dark-mode-aware color classes"]
  affects: ["client/src/index.css", "client/src/components/DocumentView/*"]
tech_stack:
  added: []
  patterns:
    - CSS classes with var(--color-*) tokens replacing inline style color values
    - context-menu-item CSS :hover rule replacing onMouseEnter/Leave JS handlers
    - var(--color-highlight-bg) in dangerouslySetInnerHTML HTML string
key_files:
  created: []
  modified:
    - client/src/index.css
    - client/src/components/DocumentView/BulletNode.tsx
    - client/src/components/DocumentView/BulletTree.tsx
    - client/src/components/DocumentView/ContextMenu.tsx
    - client/src/components/DocumentView/DocumentToolbar.tsx
    - client/src/components/DocumentView/DocumentView.tsx
    - client/src/components/DocumentView/FilteredBulletList.tsx
    - client/src/components/DocumentView/FocusToolbar.tsx
    - client/src/components/DocumentView/NoteRow.tsx
    - client/src/components/DocumentView/SearchModal.tsx
    - client/src/components/DocumentView/UndoToast.tsx
    - client/src/components/DocumentView/Breadcrumb.tsx
    - client/src/components/DocumentView/AttachmentRow.tsx
decisions:
  - "UndoToast uses var(--color-bg-raised) for background; white (#fff) text intentionally kept as it maintains contrast on both light and dark raised backgrounds"
  - "Lightbox overlay rgba(0,0,0,0.85) intentionally kept as-is — design element, not a themed surface"
  - "ContextMenu onMouseEnter/Leave JS color assignments fully removed in favor of CSS .context-menu-item:hover rule"
metrics:
  duration_minutes: 6
  completed_date: "2026-03-10"
  tasks_completed: 2
  files_modified: 14
requirements:
  - DRKM-01
  - DRKM-02
---

# Phase 06 Plan 02: DocumentView Color Token Migration Summary

Convert all 13 DocumentView TSX files from hardcoded hex color values to CSS class references using the CSS custom property token system established in Plan 01. After this plan, the document viewing area is fully dark-mode-aware.

## What Was Built

Systematic conversion of all hardcoded `style={{ color: '#xxx' }}` and `style={{ background: '#xxx' }}` inline styles in DocumentView components to CSS className references pointing to token-based CSS rules. Two task batches:

**Task 1 (3054106):** BulletNode, BulletTree, ContextMenu, DocumentToolbar, DocumentView
- BulletNode swipe reveal background now uses `var(--color-swipe-complete)` / `var(--color-swipe-delete)` in the JS ternary
- BulletNode row background changed from `var(--bg, #fff)` fallback to `var(--color-bg-base)` directly
- ContextMenu: all `onMouseEnter/Leave` JS color handlers removed; replaced with `.context-menu-item:hover:not(:disabled)` CSS rule; container now uses `var(--color-bg-raised)` and `var(--color-border-default)`
- DocumentToolbar border uses `var(--color-border-subtle)`; buttons use `.toolbar-btn`, `.toolbar-btn--active`, `.toolbar-btn--inactive`, `.toolbar-btn--destructive`
- DocumentView: hamburger uses `.doc-hamburger`, title uses `.doc-title`

**Task 2 (08ee3cd):** FilteredBulletList, FocusToolbar, Lightbox, NoteRow, SearchModal, UndoToast, Breadcrumb, AttachmentRow
- FilteredBulletList: search highlight `dangerouslySetInnerHTML` string updated from `background:#fff3cd` to `background:var(--color-highlight-bg)` — correctly resolves CSS custom properties at runtime
- FocusToolbar: background/border use var() tokens; all buttons use `.focus-toolbar-btn` with active state modifiers
- SearchModal: overlay uses `var(--color-bg-overlay)`, modal bg uses `var(--color-bg-raised)`
- Breadcrumb: separator/ancestor/current/ellipsis all use `.breadcrumb-*` CSS classes
- AttachmentRow: all color-bearing elements use `.attachment-*` CSS classes; PDF canvas border uses `var(--color-border-default)`
- Lightbox overlay `rgba(0,0,0,0.85)` intentionally kept — opaque overlay is correct regardless of theme

## Deviations from Plan

### Pre-existing Extra CSS in index.css

**Found during:** Task 1
**Issue:** `index.css` already contained CSS classes for Sidebar, AppPage, and Login page components (added by a prior context session run) at the time of execution.
**Fix:** Kept the pre-existing classes as they are all correct token references and will be used by later plans (06-03).
**Files modified:** `client/src/index.css` (no action needed — already correct)

## Verification Results

- Grep check: zero hardcoded hex color values remain in the 13 target DocumentView TSX files (BulletContent.tsx excluded — out of scope for this plan, handled in 06-01)
- `context-menu-item:hover` CSS rule confirmed present in index.css
- `var(--color-highlight-bg)` confirmed in FilteredBulletList HTML string
- `var(--color-swipe-complete/delete)` confirmed in BulletNode swipe ternary
- Full test suite: 9 pre-existing failures (darkMode DRKM-03/04, mobileLayout MOBL-01/02/03) — unchanged from baseline, no regressions introduced

## Self-Check: PASSED

- Task 1 commit 3054106: FOUND
- Task 2 commit 08ee3cd: FOUND
- All 13 target files modified with zero new hardcoded hex colors
- CSS classes added to index.css under `/* === DocumentView component classes === */` and `/* === Additional DocumentView classes === */` sections
