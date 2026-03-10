---
phase: 06-dark-mode
plan: "03"
subsystem: ui-theming
tags: [dark-mode, css-tokens, sidebar, login, theming]
dependency_graph:
  requires: ["06-01"]
  provides: ["DRKM-01", "DRKM-02"]
  affects: ["Sidebar components", "LoginPage", "AppPage"]
tech_stack:
  added: []
  patterns: ["CSS custom properties via className", "BEM-style modifier classes (--active, --error)"]
key_files:
  created: []
  modified:
    - client/src/index.css
    - client/src/components/Sidebar/BookmarkBrowser.tsx
    - client/src/components/Sidebar/DocumentList.tsx
    - client/src/components/Sidebar/DocumentRow.tsx
    - client/src/components/Sidebar/Sidebar.tsx
    - client/src/components/Sidebar/TagBrowser.tsx
    - client/src/pages/AppPage.tsx
    - client/src/pages/LoginPage.tsx
decisions:
  - "DocumentRow active state applied via .document-row--active CSS class (not inline style) so var(--color-row-active-bg) token resolves correctly in both themes"
  - "LoginPage const styles object deleted entirely — all styles now in login-* CSS classes in index.css"
  - "Sidebar tab bar uses .sidebar-tab and .sidebar-tab--active classes — inline borderBottom and color removed"
metrics:
  duration: "~7 minutes"
  completed: "2026-03-10"
  tasks: 2
  files_modified: 8
---

# Phase 6 Plan 03: Sidebar and Pages Dark Mode Token Migration Summary

Converted all hardcoded hex color values in Sidebar components and application pages to CSS class references using the token system established in Plan 01. After this plan, all seven TSX files contain zero hardcoded hex colors and the login page has no inline styles object.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Convert Sidebar components (BookmarkBrowser, DocumentList, DocumentRow, Sidebar, TagBrowser) | 74095fa | 6 files |
| 2 | Convert AppPage and LoginPage | d8c0135 | 2 files |

## What Was Built

**Sidebar components (Task 1):**
- BookmarkBrowser: loading/empty states, bookmark date, content, empty-bullet, remove button — all converted to CSS classes
- DocumentList: loading/empty divs use `.doc-list-empty` class
- DocumentRow: active row background moved from inline `rgba(0,0,0,0.06)` to `.document-row--active { background: var(--color-row-active-bg) }` — the token has different values in light (`rgba(0,0,0,0.06)`) and dark (`rgba(255,255,255,0.08)`) themes; focus ring border uses `var(--color-focus-ring)`; text and button colors use `.doc-row-text` / `.doc-row-btn` / `.doc-row-delete` classes; dropdown uses `var(--color-bg-raised)` and `var(--color-border-default)`
- Sidebar: header border, tab bar border use `var(--color-border-default)`; tabs use `.sidebar-tab` / `.sidebar-tab--active` classes; icon buttons use `.sidebar-icon-btn`; dropdown uses CSS vars
- TagBrowser: filter input uses CSS token vars; loading, section headers, tag buttons, counts, empty state all use CSS classes

**Pages (Task 2):**
- AppPage: empty state `#999` converted to `.app-empty-state { color: var(--color-text-faint) }`
- LoginPage: entire `const styles = { ... }` object deleted. All 12 style properties replaced with `login-*` CSS classes in index.css. The login-submit button uses `background: var(--color-text-primary)` — which inverts correctly in dark mode (white bg on dark, black bg on light). Input background uses `var(--color-bg-base)` for legibility in both themes.

## CSS Classes Added to index.css

**Sidebar section:** `.bookmark-browser-loading`, `.bookmark-browser-empty`, `.bookmark-date`, `.bookmark-content`, `.bookmark-empty-bullet`, `.bookmark-remove-btn`, `.doc-list-empty`, `.document-row--active`, `.doc-row-text`, `.doc-row-btn`, `.doc-row-delete`, `.doc-row-dropdown-item`, `.sidebar-tab`, `.sidebar-tab--active`, `.sidebar-icon-btn`, `.sidebar-menu-item`, `.tag-browser-loading`, `.tag-section-header`, `.tag-btn`, `.tag-count`, `.tag-browser-empty`

**AppPage section:** `.app-empty-state`

**Login page section:** `.login-page`, `.login-card`, `.login-title`, `.login-tabs`, `.login-tab`, `.login-tab--active`, `.login-google-btn`, `.login-divider`, `.login-label`, `.login-input`, `.login-input--error`, `.login-error`, `.login-submit`, `.login-field`

## Verification Results

1. Tests: 59 passing / 9 failing (9 failures are pre-existing from Phase 5/Plan 01 TDD setup — not introduced by this plan)
2. Hex color grep: zero results across all 7 Sidebar and Pages TSX files
3. `const styles` check: 0 occurrences in LoginPage.tsx
4. Active row token: `.document-row--active { background: var(--color-row-active-bg) }` in index.css; `.document-row--active` class applied conditionally in DocumentRow.tsx

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

Files confirmed to exist:
- client/src/index.css (modified with all new classes)
- client/src/components/Sidebar/BookmarkBrowser.tsx (no hex colors)
- client/src/components/Sidebar/DocumentList.tsx (no hex colors)
- client/src/components/Sidebar/DocumentRow.tsx (no hex colors, document-row--active class)
- client/src/components/Sidebar/Sidebar.tsx (no hex colors)
- client/src/components/Sidebar/TagBrowser.tsx (no hex colors)
- client/src/pages/AppPage.tsx (no hex colors)
- client/src/pages/LoginPage.tsx (no hex colors, no const styles object)

Commits confirmed:
- 74095fa: feat(06-03): convert Sidebar components to CSS token classes
- d8c0135: feat(06-03): convert AppPage and LoginPage to CSS token classes
