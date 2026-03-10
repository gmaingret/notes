---
phase: 06-dark-mode
plan: "01"
subsystem: css-tokens
tags: [dark-mode, css, tokens, wcag]
dependency_graph:
  requires: [06-00]
  provides: [css-token-system]
  affects: [client/src/index.css, client/src/components/DocumentView/BulletContent.tsx]
tech_stack:
  added: []
  patterns: [css-custom-properties, prefers-color-scheme-media-query, fouc-mirror-class]
key_files:
  created: []
  modified:
    - client/src/index.css
    - client/src/components/DocumentView/BulletContent.tsx
decisions:
  - "light mode --color-chip-date-text set to #b45309 (not #d97706) for WCAG AA 4.7:1 on #fef3c7"
  - "bullet-shake keyframe name preserved to match JSX className='bullet-shake' in BulletContent.tsx"
  - "FOUC-related tests (DRKM-03/04) remain red — index.html changes deferred to plan 06-02"
  - "--color-row-active-bg token added (rgba light/dark variants) for DocumentRow active state"
metrics:
  duration: "~2 min"
  completed: "2026-03-10"
  tasks_completed: 2
  files_modified: 2
---

# Phase 6 Plan 01: CSS Token System Foundation Summary

**One-liner:** Complete CSS custom property token system (light + dark + .dark FOUC mirror) established in index.css; BulletContent.tsx dynamic chip/shake injection deleted and replaced with static CSS classes using token vars.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Add CSS token block and dark override to index.css | 1388fa2 | client/src/index.css |
| 2 | Delete chip/shake injection from BulletContent.tsx | 3198a7f | client/src/components/DocumentView/BulletContent.tsx |

## What Was Built

### index.css additions
- `:root {}` block with full light-mode token set: 6 bg tokens, 5 text tokens, 2 border tokens, 5 accent tokens, 6 chip tokens, 2 swipe tokens, 1 highlight token, 2 misc tokens, 1 row-active token. Plus `color-scheme: light dark`.
- `@media (prefers-color-scheme: dark) { :root {} }` with dark-mode overrides for all tokens.
- `:root.dark {}` mirror block — exact copy of `@media` overrides, inert until Wave 3 FOUC script adds `.dark` class to `<html>`.
- `body { background: var(--color-bg-base); color: var(--color-text-secondary); }` rule.
- Sidebar rules updated: `border-right` and `background` now use token vars.
- Static `.chip`, `.chip-tag`, `.chip-mention`, `.chip-date` CSS classes with token vars.
- Static `.bullet-shake` animation using `@keyframes bullet-shake`.

### BulletContent.tsx deletions
- Removed `CHIP_STYLE` string constant (42-character hardcoded chip colors).
- Removed `ensureChipStyle()` function and `chipStyleInjected` flag.
- Removed `SHAKE_STYLE` string constant (bullet-shake keyframe definition).
- Removed `ensureShakeStyle()` function and `shakeStyleInjected` flag.
- Removed `useEffect` that called both injection functions.
- JSX unchanged: `className={isShaking ? 'bullet-shake' : undefined}` still references the class, now served statically.

## Verification Results

- `@media (prefers-color-scheme: dark)` in index.css: 1 match (PASS)
- `:root.dark` in index.css: 1 match (PASS)
- Injection symbols in BulletContent.tsx: 0 matches (PASS)
- DRKM-01 tests (index.css assertions): 2/2 PASS
- Full test suite: 59/68 pass — 9 pre-existing failures (4x DRKM-03/04 needing index.html, 5x mobile layout) — zero new failures introduced

## Deviations from Plan

### Minor Adjustments (within plan scope)

**1. [Rule 1 - Bug] shake animation class name preserved as `bullet-shake`**
- **Found during:** Task 1 — plan spec said add `.shake` class; BulletContent.tsx JSX uses `bullet-shake`
- **Issue:** Plan listed the new animation class as `.shake` in the `@keyframes shake` example, but the existing JSX (`className={isShaking ? 'bullet-shake' : undefined}`) references `bullet-shake`
- **Fix:** Added `.bullet-shake` class and `@keyframes bullet-shake` (matching existing SHAKE_STYLE exactly) rather than `.shake`/`@keyframes shake` as specified
- **Files modified:** client/src/index.css

**2. Additional token added: `--color-row-active-bg`**
- **Found during:** Task 1 — referenced in plan's `<interfaces>` block
- **Issue:** Token was listed in interfaces but not in RESEARCH.md token block
- **Fix:** Added `--color-row-active-bg: rgba(0,0,0,0.06)` in light and `rgba(255,255,255,0.08)` in dark (matching RESEARCH.md Pitfall 5 recommendation)
- **Files modified:** client/src/index.css

## Self-Check: PASSED
- client/src/index.css: exists and contains `@media (prefers-color-scheme: dark)` block
- client/src/components/DocumentView/BulletContent.tsx: contains zero injection symbols
- Commits 1388fa2 and 3198a7f: verified present in git log
