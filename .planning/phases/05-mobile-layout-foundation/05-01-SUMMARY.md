---
phase: 05-mobile-layout-foundation
plan: "01"
subsystem: client/layout
tags: [mobile, sidebar, off-canvas, css, keyboard-shortcut]
dependency_graph:
  requires: ["05-00"]
  provides: ["MOBL-02", "MOBL-03", "MOBL-04", "MOBL-06", "MOBL-07"]
  affects: ["client/src/pages/AppPage.tsx", "client/src/components/Sidebar/Sidebar.tsx", "client/src/index.css"]
tech_stack:
  added: []
  patterns: ["off-canvas drawer with CSS translateX", "always-rendered backdrop for fade-out transition", "matchMedia for mobile init", "Ctrl+E keyboard shortcut with cleanup"]
key_files:
  created: []
  modified:
    - client/src/pages/AppPage.tsx
    - client/src/components/Sidebar/Sidebar.tsx
    - client/src/index.css
    - client/src/test/mobileLayout.test.tsx
decisions:
  - "sidebar-backdrop always rendered (not conditional) so CSS fade-out transition plays when sidebar closes"
  - "!important on position and height in mobile CSS required to override inline styles on aside element"
  - "window.matchMedia mock added to test file — jsdom does not implement matchMedia natively"
  - "Hamburger button (MOBL-01) auto-added by linter with all tests GREEN — included in this plan"
metrics:
  duration: "~4 minutes"
  completed: "2026-03-10"
  tasks_completed: 2
  files_changed: 4
---

# Phase 5 Plan 01: Mobile Layout Foundation — Core Sidebar Transform Summary

Implemented off-canvas sidebar drawer with CSS translateX transform, always-rendered backdrop with opacity fade, X close button, 100dvh viewport fix, and Ctrl+E keyboard shortcut toggle for mobile layout foundation.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 (RED) | Add failing mobile layout tests | ea05622 | client/src/test/mobileLayout.test.tsx |
| 1 (GREEN) | AppPage — 100dvh, mobile init, Ctrl+E shortcut | c233c40 | client/src/pages/AppPage.tsx, mobileLayout.test.tsx |
| 2 (GREEN) | Sidebar off-canvas transform, backdrop, X button | 294ff83 | client/src/components/Sidebar/Sidebar.tsx, client/src/index.css |

## What Was Built

**AppPage.tsx:**
- Root div `height` changed from `100vh` to `100dvh` — prevents address bar clipping on mobile browsers
- Added `sidebarOpen` and `setSidebarOpen` to `useUiStore` destructure
- Mobile init `useEffect` (runs once at mount): calls `setSidebarOpen(false)` when `window.matchMedia('(max-width: 768px)').matches`
- Ctrl+E / Cmd+E keyboard shortcut `useEffect` with event listener cleanup on unmount

**Sidebar.tsx:**
- Replaced conditional `{sidebarOpen && <div class="mobile-overlay" />}` with always-rendered `<div className="sidebar-backdrop ...">`
- `sidebar-open` class toggled on backdrop div based on `sidebarOpen` state
- `aside` element now has `className={sidebar${sidebarOpen ? ' sidebar-open' : ''}}` — removed `height: '100vh'` from inline style
- X close button with `className="mobile-only"` and `aria-label="Close sidebar"` added between `⋯` menu and `+` button

**index.css:**
- Added Phase 5 mobile block with:
  - `.sidebar { position: fixed; height: 100dvh; transform: translateX(-100%) }` with `transition: transform 250ms ease-out`
  - `.sidebar.sidebar-open { transform: translateX(0) }`
  - `.sidebar-backdrop` with `opacity: 0; pointer-events: none` — always visible, fades via CSS
  - `.sidebar-backdrop.sidebar-open { opacity: 1; pointer-events: auto }`
  - `.mobile-only { display: none }` on desktop (min-width: 769px), `display: flex` on mobile
  - `!important` on `position` and `height` overrides required due to inline styles on `aside`

## Test Results

| Test ID | Description | Status |
|---------|-------------|--------|
| MOBL-02 | Backdrop always present, fades in/out | GREEN (3 tests) |
| MOBL-03 | X button with mobile-only class present | GREEN (2 tests) |
| MOBL-04 | aside has sidebar-open class when open | GREEN (2 tests) |
| MOBL-06 | AppPage root uses 100dvh | GREEN (2 tests) |
| MOBL-07 | Ctrl+E toggles sidebar | GREEN (2 tests) |
| MOBL-01 | Hamburger button opens sidebar | GREEN (bonus — auto-added by linter) |

Full suite: 62 passing, 0 failing.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added window.matchMedia mock in test file**
- **Found during:** Task 1 GREEN phase
- **Issue:** jsdom does not implement `window.matchMedia` — AppPage's mobile init `useEffect` crashed tests with "window.matchMedia is not a function"
- **Fix:** Added global `window.matchMedia` mock at top of `mobileLayout.test.tsx` with `matches: false` default (desktop viewport)
- **Files modified:** `client/src/test/mobileLayout.test.tsx`
- **Commit:** c233c40

**2. [Rule 2 - Test Update] Updated MOBL-02 and MOBL-03 test assertions to match Phase 5 architecture**
- **Found during:** TDD RED phase
- **Issue:** Existing MOBL-02 tests checked `.mobile-overlay` class (old conditional approach); MOBL-03 checked button by accessible name. Plan specifies `sidebar-backdrop` always-rendered pattern and `button.mobile-only` class selector.
- **Fix:** Updated MOBL-02 tests to query `.sidebar-backdrop`; updated MOBL-03 to query `button.mobile-only` via container selector
- **Files modified:** `client/src/test/mobileLayout.test.tsx`
- **Commit:** ea05622

## Self-Check

### Files Exist
- [x] `client/src/pages/AppPage.tsx` — contains "100dvh" and both useEffect blocks
- [x] `client/src/components/Sidebar/Sidebar.tsx` — contains "sidebar-backdrop" and "sidebar-open" class toggle
- [x] `client/src/index.css` — contains "@media (max-width: 768px)" Phase 5 block

### Commits Exist
- [x] ea05622 — test(05-01): add failing tests for MOBL-02/03/04/06/07
- [x] c233c40 — feat(05-01): AppPage — 100dvh, mobile init, Ctrl+E shortcut
- [x] 294ff83 — feat(05-01): Sidebar off-canvas transform, always-rendered backdrop, X close button

## Self-Check: PASSED
