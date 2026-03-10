---
phase: 06-dark-mode
verified: 2026-03-10T16:47:00Z
status: human_needed
score: 12/12 must-haves verified
re_verification: true
  previous_status: gaps_found
  previous_score: 10/12
  gaps_closed:
    - "No hardcoded hex color values remain in any DocumentView TSX file — color: '#333' removed from both BulletContent.tsx render paths"
    - "Hard-refreshing in dark OS preference shows correct text color from first paint — inline style no longer overrides cascade"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "OS dark mode live switching — all surfaces including bullet text"
    expected: "Switching OS to dark mode causes background, sidebar, and all bullet body text to adopt dark tokens. Bullet text must become near-white (#c9d1d9)."
    why_human: "Requires OS preference toggle and visual inspection; cannot verify CSS cascade result programmatically"
  - test: "FOUC — background and text color on hard refresh"
    expected: "With OS in dark mode, network throttled to Slow 3G, hard refresh: background is dark from the very first paint AND bullet text is light from the very first paint"
    why_human: "Requires network throttle + dark OS + first-paint visual inspection"
  - test: "WCAG AA contrast for all text/background pairs in dark mode"
    expected: "All text elements readable — primary content near-white on dark background, chips distinct, muted text meets minimum 3:1"
    why_human: "Contrast ratios require visual or tool-assisted measurement"
  - test: "Browser scrollbars and form inputs adopt dark theme"
    expected: "Scrollbars appear dark, text inputs have dark background and light text when OS is in dark mode"
    why_human: "color-scheme meta tag effect is browser-rendered; only visible in a real browser"
  - test: "Context menu hover state in dark mode"
    expected: "Hovering over context menu items shows subtle dark highlight (surface token), not a white or light gray background"
    why_human: "CSS :hover rule verified present; visual result requires browser inspection"
---

# Phase 6: Dark Mode Verification Report

**Phase Goal:** Users with a dark OS preference see a fully themed dark interface with no white flash on load and no unthemed elements
**Verified:** 2026-03-10T16:47:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (BulletContent.tsx inline color removed)

## Re-verification Summary

| Item | Previous | Now |
|------|----------|-----|
| `BulletContent.tsx` — `color: '#333'` in both render paths | FAILED (blocker) | VERIFIED — property absent |
| FOUC text color on hard refresh | PARTIAL | VERIFIED — cascade now unobstructed |
| All other 10 truths | VERIFIED | VERIFIED (regression check passed) |

**Gap closure confirmed:** Both occurrences of `color: '#333'` have been removed from `BulletContent.tsx`. The `isDragOverlay` render path (lines 648-660) and the normal render path (lines 667-691) each have a style object with layout/typography properties only; no `color` property is present. Bullet text now inherits from `body { color: var(--color-text-secondary) }` which is overridden by `:root.dark` tokens.

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | FOUC script in `index.html` adds `.dark` class synchronously before paint | VERIFIED | `client/index.html` lines 6-10: synchronous `<script>` with `window.matchMedia` check and `classList.add('dark')`, placed before `<link>` tags |
| 2 | `color-scheme` meta tag present for browser chrome theming | VERIFIED | `client/index.html` line 5: `<meta name="color-scheme" content="light dark" />` |
| 3 | CSS token system switches all bg/text colors via `@media (prefers-color-scheme: dark)` | VERIFIED | `client/src/index.css` lines 52-91: complete `@media` override block with all 20+ tokens |
| 4 | `:root.dark` mirror class exists for synchronous FOUC paint | VERIFIED | `client/src/index.css` lines 94-131: exact copy of `@media` dark values |
| 5 | `body` background and color use token vars (live switching) | VERIFIED | `client/src/index.css` lines 133-136: `body { background: var(--color-bg-base); color: var(--color-text-secondary); }` |
| 6 | No hardcoded hex colors in Sidebar TSX files | VERIFIED | Grep across `client/src/components/Sidebar/**/*.tsx` — zero matches |
| 7 | No hardcoded hex colors in Pages TSX files | VERIFIED | Grep across `client/src/pages/**/*.tsx` — zero matches |
| 8 | No hardcoded hex colors in DocumentView TSX files | VERIFIED | Grep across `client/src/components/DocumentView/**/*.tsx` — only `UndoToast.tsx` `#fff` (documented intentional exception; toast background is always dark `#1f2937`). `BulletContent.tsx` `color: '#333'` confirmed absent from both render paths. |
| 9 | ContextMenu hover uses CSS class, no inline JS color assignment | VERIFIED | No `onMouseEnter`/`onMouseLeave` handlers in `ContextMenu.tsx`; `.context-menu-item:hover:not(:disabled)` rule exists in `index.css` line 227 |
| 10 | FilteredBulletList search highlight uses CSS token | VERIFIED | `FilteredBulletList.tsx` line 23: `style="background:var(--color-highlight-bg);border-radius:2px"` in HTML string |
| 11 | BulletNode swipe reveal uses CSS token vars | VERIFIED | `BulletNode.tsx` lines 186, 188: `'var(--color-swipe-complete)'` and `'var(--color-swipe-delete)'` |
| 12 | DocumentRow active state uses themed token | VERIFIED | `DocumentRow.tsx` line 76: conditional `document-row--active` className; `index.css` provides `.document-row--active { background: var(--color-row-active-bg); }` with different light/dark token values |

**Score:** 12/12 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `client/index.html` | FOUC script + color-scheme meta | VERIFIED | Script is synchronous, placed after charset meta and before icon link. Meta tag has correct `content="light dark"`. |
| `client/src/test/darkMode.test.tsx` | 6 tests covering DRKM-01, DRKM-03, DRKM-04 | VERIFIED | 6 tests all PASS (confirmed via `npx vitest run src/test/darkMode.test.tsx` — 6/6 green) |
| `client/src/index.css` | Token system with `:root`, `@media dark`, `:root.dark`, chip classes, body rule | VERIFIED | All sections present. Full token set (backgrounds, text, borders, accents, chips, swipe, highlight, shadow, focus ring, row active) |
| `client/src/components/DocumentView/BulletContent.tsx` | Chip/shake injection deleted | VERIFIED | Zero matches for `CHIP_STYLE`, `SHAKE_STYLE`, `ensureChipStyle`, `ensureShakeStyle` |
| `client/src/components/DocumentView/BulletContent.tsx` | No hardcoded hex colors | VERIFIED | Both render paths (`isDragOverlay` and normal) confirmed clean — no `color` property in either style object |
| `client/src/components/DocumentView/ContextMenu.tsx` | `.context-menu-item` class used | VERIFIED | Class present across all 11 menu items in JSX; CSS rule with `:hover` in `index.css` |
| `client/src/components/Sidebar/DocumentRow.tsx` | `document-row--active` with token var | VERIFIED | Line 76: conditional `document-row--active` className; token wired in `index.css` |
| `client/src/pages/LoginPage.tsx` | `const styles` deleted, all `login-*` className | VERIFIED | No `const styles =` found; `login-*` className references replace former inline styles |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `index.html` FOUC script | `document.documentElement.classList.add('dark')` | Synchronous inline `<script>` before `<link>` tags | VERIFIED | Exact pattern present at line 8 |
| `index.html` meta | Browser chrome theming | `<meta name="color-scheme" content="light dark">` | VERIFIED | Line 5; `name` and `content` attributes confirmed |
| `index.css :root` | `@media (prefers-color-scheme: dark) :root` | CSS custom property override | VERIFIED | Lines 52-91; all tokens overridden |
| `index.css :root.dark` | `@media` block (FOUC mirror) | Identical token values | VERIFIED | `:root.dark` lines 94-131 match `@media` dark values |
| `BulletContent.tsx` text | `var(--color-text-secondary)` via cascade | `body { color: var(--color-text-secondary) }` — inline `color` property absent | VERIFIED | Both render-path style objects contain no `color` property; cascade is unobstructed |
| `FilteredBulletList.tsx` | `var(--color-highlight-bg)` | `dangerouslySetInnerHTML` string template | VERIFIED | Line 23: `style="background:var(--color-highlight-bg)..."` |
| `BulletNode.tsx` | `var(--color-swipe-complete/delete)` | JS ternary for swipeBackground | VERIFIED | Lines 186, 188: token strings in ternary expression |
| `index.css` | `.context-menu-item:hover` | CSS `:hover` rule | VERIFIED | `.context-menu-item:hover:not(:disabled) { background: var(--color-bg-surface); }` |
| `DocumentRow.tsx` | `--color-row-active-bg` | `.document-row--active` CSS class | VERIFIED | Class conditionally applied in JSX; CSS rule in `index.css` |
| `LoginPage.tsx` | `index.css` `.login-*` classes | `className` references replacing `styles.*` object | VERIFIED | All `login-*` classes defined in `index.css` |

---

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|---------------|-------------|--------|---------|
| DRKM-01 | 06-01, 06-02, 06-03 | App automatically switches to dark theme when OS is in dark mode | VERIFIED | `@media` block and `:root.dark` mirror cover all surfaces. `BulletContent.tsx` inline color gap is closed — bullet text now inherits from body token cascade. Live switching fully unobstructed. |
| DRKM-02 | 06-01, 06-02, 06-03 | All text/bg pairs meet WCAG AA in both themes | NEEDS HUMAN | Token pairs pre-verified in RESEARCH.md. `BulletContent.tsx` gap is closed (previous `#333` deviation no longer present). Visual confirmation of all surfaces required. |
| DRKM-03 | 06-00, 06-04 | No white flash on hard refresh in dark OS preference | VERIFIED | FOUC script adds `.dark` to `<html>` synchronously; `:root.dark` applies dark tokens before first CSS paint; `body` background and `color` use those tokens; no inline `color` override in BulletContent. Both background and text FOUC are solved. |
| DRKM-04 | 06-00, 06-04 | Browser chrome adopts active theme via color-scheme | VERIFIED | `<meta name="color-scheme" content="light dark" />` present in `index.html` |

**Orphaned requirements:** None — all four DRKM IDs appear in plan frontmatter and are accounted for.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | — | — | — |

**Intentional non-token colors** (documented exceptions, not gaps):
- `UndoToast.tsx` lines 32, 47: `#fff` — toast background is always dark (`var(--color-bg-raised)` = `#1f2937`); white text/border intentionally works in both themes. Documented in Plan 02.
- `Lightbox.tsx`: `rgba(0,0,0,0.85)` — intentionally opaque regardless of theme. Documented in Plan 02.
- `ContextMenu.tsx`: `rgba(0,0,0,0.15)` — box-shadow opacity; structural, not a themed surface color.
- `SearchModal.tsx`: `rgba(0,0,0,0.2)` — box-shadow opacity; structural, not a themed surface color.

---

### Human Verification Required

#### 1. OS Dark Mode Live Switching — All Surfaces

**Test:** Open the app with OS in light mode. Switch OS to dark mode (Windows: Settings > Personalization > Colors > Dark).
**Expected:** Background switches to `#0d1117`, sidebar to `#161b22`, and all bullet body text changes to near-white (`#c9d1d9`). All chips, borders, and accent colors adopt dark tokens simultaneously.
**Why human:** Requires OS preference toggle and visual inspection.

#### 2. FOUC — Background and Text Color on Hard Refresh

**Test:** OS in dark mode. Browser DevTools Network: throttle to Slow 3G. Hard refresh (Ctrl+Shift+R).
**Expected:** Background is dark from the very first paint AND bullet text is light from the very first paint. No visible light flash on any surface.
**Why human:** Requires network throttle + dark OS + first-paint visual inspection.

#### 3. WCAG AA Contrast — Dark Theme

**Test:** In dark mode, inspect text/background pairs with browser DevTools accessibility checker or axe extension. Key pairs: bullet text (`#c9d1d9` on `#0d1117`), chip text on chip backgrounds, sidebar items, muted timestamps.
**Expected:** All pairs meet minimum 4.5:1 for body text, 3:1 for large text and UI components.
**Why human:** Contrast ratios require visual or tool-assisted measurement.

#### 4. Browser Chrome — Scrollbars and Input Theming

**Test:** OS in dark mode. Scroll through a long document. Click into search box and login form inputs.
**Expected:** Scrollbars appear dark. Text inputs have dark background and light placeholder/input text.
**Why human:** `color-scheme` meta tag effect is browser-rendered; only visible in a real browser.

#### 5. Context Menu Hover in Dark Mode

**Test:** OS in dark mode. Right-click a bullet. Hover over menu items.
**Expected:** Hover state shows a subtle dark highlight (surface token `#161b22`) — not a white or light gray background.
**Why human:** CSS `:hover` rule verified present; visual result requires browser inspection.

---

### Goal Achievement Summary

All automated verification checks pass at 12/12. The single blocker from the previous verification pass — `color: '#333'` inline style in `BulletContent.tsx` — has been removed. Both the `isDragOverlay` and normal render paths now contain only layout and typography properties in their style objects; the `color` property is absent from both, allowing the `body { color: var(--color-text-secondary) }` cascade to apply correctly in both light and dark modes.

The phase goal ("fully themed dark interface with no white flash and no unthemed elements") is satisfied at the code level. Five human verification items remain to confirm the visual experience in a real browser with OS dark mode enabled.

---

_Verified: 2026-03-10T16:47:00Z_
_Verifier: Claude (gsd-verifier)_
