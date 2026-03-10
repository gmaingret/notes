# Architecture Research

**Domain:** v1.1 Mobile & UI Polish — Integration with existing React/Vite outliner
**Researched:** 2026-03-10
**Confidence:** HIGH (integration points read directly from source) / MEDIUM (PWA plugin approach)

---

## Standard Architecture

### System Overview — Current State

```
index.html
  └── main.tsx
        └── App.tsx  (router, RequireAuth, GlobalKeyboard)
              ├── LoginPage
              └── AppPage  ←── LAYOUT ROOT — flexbox row, 100vh
                    ├── Sidebar (fixed 240px width, inline styles)
                    │     ├── DocumentList
                    │     ├── TagBrowser
                    │     └── BookmarkBrowser
                    └── <main> (flex: 1, overflow: auto, inline styles)
                          └── DocumentView
                                └── BulletTree (DndContext + SortableContext)
                                      ├── BulletNode[]
                                      │     └── BulletContent (contenteditable)
                                      ├── FocusToolbar (fixed, above keyboard)
                                      ├── DocumentToolbar
                                      └── ContextMenu
```

Global state: `uiStore.ts` (Zustand + persist)
- `sidebarOpen: boolean` — already exists, persisted
- `sidebarTab`, `canvasView`, `searchOpen`, `focusedBulletId`, `lastOpenedDocId`

CSS: `index.css` — minimal (~13 lines), inline styles dominate everywhere.
PWA: none — `public/` contains only `vite.svg`. `index.html` has no manifest link.

### Component Responsibilities

| Component | Responsibility | Current State |
|-----------|----------------|---------------|
| `AppPage` | Layout root — flex row, routes, auto-navigate | Inline styles, no mobile breakpoint |
| `Sidebar` | Nav panel — docs/tags/bookmarks tabs | Hard-coded 240px, overlay div present but only shown via CSS media query |
| `uiStore` | Global UI state | `sidebarOpen` already exists; toggles work but sidebar never hides visually |
| `useGlobalKeyboard` | Keyboard shortcuts | Ctrl+E sidebar toggle already wired |
| `SearchModal` | Full-text search overlay | Fully working — backdrop + modal at `top: 20%`, triggered by `searchOpen` |
| `FocusToolbar` | Mobile action bar above soft keyboard | Fixed-positioned using `visualViewport`, lives inside BulletTree |
| `gestures.ts` | Swipe + long-press pure functions | Closure-based, no React, unit-tested |
| `index.css` | Global reset + one hover rule + one mobile media query | Minimal; all theming is inline |

---

## Integration Points for v1.1 Features

### Feature 1: Responsive Mobile Layout + Hamburger Sidebar

**What needs to change:**

`AppPage.tsx` — the flex layout uses `style={{ display: 'flex' }}` directly. The sidebar must be conditionally positioned (off-canvas on mobile) based on `sidebarOpen` and a `isMobile` breakpoint. Two approaches:

- **Approach A (CSS classes, recommended):** Move layout to `index.css` with a `.app-layout` class. CSS handles the breakpoint; `sidebarOpen` becomes a `data-sidebar-open` attribute that CSS reads. No JS media query needed in the render path.
- **Approach B (JS matchMedia):** Add a `useIsMobile()` hook that reads `window.matchMedia('(max-width: 768px)')`. Pass `isMobile` to layout. More explicit but harder to test visually.

Recommendation: Approach A. It keeps the JS state (`sidebarOpen`) as the single source of truth while CSS handles breakpoint-specific positioning (translate off-screen vs visible).

`Sidebar.tsx` — the overlay `<div>` already exists and is shown via the `.mobile-overlay` CSS class. It correctly calls `setSidebarOpen(false)` on click. What is missing: the `<aside>` itself must slide off-screen on mobile when `sidebarOpen` is false. Currently `sidebarOpen` is read from `uiStore` but never actually hides the sidebar element.

**New component:** `HamburgerButton` — a small button rendered in the `<main>` header area (or in a new `TopBar` component) that calls `setSidebarOpen(true)`. On desktop it can be hidden via CSS. Alternatively, add the hamburger to the existing `DocumentToolbar`.

**Modification summary:**

| File | Change |
|------|--------|
| `AppPage.tsx` | Replace inline flex div with CSS class; conditionally apply `data-sidebar-open` |
| `Sidebar.tsx` | Add CSS class to `<aside>` for mobile translate; no logic change needed |
| `index.css` | Add `.app-layout`, `.app-sidebar`, `.app-content` with `@media (max-width: 768px)` rules |
| `uiStore.ts` | No change needed — `sidebarOpen` is already there |
| `useGlobalKeyboard` | No change — Ctrl+E already toggles `sidebarOpen` |

**New component needed:** `HamburgerButton` (or inline in `DocumentToolbar`/`DocumentView`).

---

### Feature 2: CSS Dark Mode Tokens

**What needs to change:**

The codebase uses inline styles almost exclusively (e.g., `color: '#111'`, `background: '#fafafa'`, `borderRight: '1px solid #e0e0e0'`). Switching these to CSS custom properties requires either:

- **Option A (CSS vars on `:root` + `@media prefers-color-scheme`):** Define all color tokens as CSS variables on `:root`, override them in a `@media (prefers-color-scheme: dark)` block. Components must switch from inline hex values to `var(--color-*)` references. This is the only approach that works without touching every component's logic.
- **Option B (data-theme attribute):** Same as A but toggled via `document.documentElement.setAttribute('data-theme', 'dark')`. Allows a manual toggle in addition to system preference.

Recommendation: Option A first (system-preference only), add Option B toggle later if desired. The manual toggle would need a `theme` field in `uiStore` + a toggle button in the sidebar header.

**Scope of change:** Every component that uses inline color/background/border values must be updated to reference CSS variables. This is the largest surface-area change in v1.1 — it touches nearly every component file.

**Build order implication:** CSS tokens must be established in `index.css` first, before any component switches to `var()` references. This is a blocking dependency.

**Modification summary:**

| File | Change |
|------|--------|
| `index.css` | Add `--color-*` token definitions for light + dark under `@media prefers-color-scheme: dark` |
| All component files | Replace inline hex colors with `var(--color-*)` — no logic change, purely presentational |
| `uiStore.ts` | Optional: add `theme: 'system' | 'light' | 'dark'` if manual toggle is wanted |

---

### Feature 3: Icon Library (Lucide) + Font Pairing

**What needs to change:**

Current icons are unicode characters and emoji (e.g., `⋯`, `+`, `&#8677;`, `&#128206;`). Lucide React provides tree-shakeable SVG icons. Install `lucide-react` and replace icon characters component by component.

FocusToolbar has the highest icon density (11 buttons). DocumentToolbar and Sidebar header have a few more.

**Fonts:** `index.html` needs `<link>` tags for Google Fonts (Inter + JetBrains Mono) or self-hosted variants. `index.css` body font-family must change. Monospace font applies to `BulletContent`'s contenteditable if code blocks are used (currently markdown renders inline, not code-block heavy — apply selectively).

**Modification summary:**

| File | Change |
|------|--------|
| `index.html` | Add font preconnect + link tags |
| `index.css` | Update `body { font-family }`, add monospace token |
| `package.json` | Add `lucide-react` dependency |
| `FocusToolbar.tsx` | Replace all unicode button content with Lucide icon components |
| `Sidebar.tsx` | Replace `⋯` and `+` characters |
| `DocumentToolbar.tsx` | Replace any unicode icons |
| `BulletNode.tsx` | Replace collapse arrow / bullet dot unicode |

---

### Feature 4: PWA Manifest

**What needs to change:**

`public/manifest.json` — new file. Standard Web App Manifest with `name`, `short_name`, `icons`, `start_url`, `display: standalone`, `theme_color`, `background_color`.

`index.html` — add `<link rel="manifest" href="/manifest.json">` and `<meta name="theme-color">` in `<head>`.

`public/` — add icon assets (192x192, 512x512 PNG). The vite build copies `public/` contents to dist root unchanged, so no vite config change is needed.

**Vite PWA plugin (optional but recommended):** `vite-plugin-pwa` auto-generates the manifest, injects the link tag, and optionally generates a service worker for offline caching. For this milestone, offline mode is out of scope, but the plugin in "injectManifest" mode with an empty precache list is cleaner than manual manifest management. Without the plugin, a static `manifest.json` in `public/` is sufficient.

Recommendation: static `manifest.json` in `public/` with no service worker. Keep it simple; the plugin adds complexity only justified when offline support is needed.

**Modification summary:**

| File | Change |
|------|--------|
| `public/manifest.json` | New file |
| `public/icon-192.png` | New file |
| `public/icon-512.png` | New file |
| `index.html` | Add manifest link + theme-color meta |
| `vite.config.ts` | No change needed (static public files work by default) |

---

### Feature 5: Quick-Open Palette (Ctrl+K)

**What already exists:**

`SearchModal.tsx` is a working full-text search modal triggered by `searchOpen` in `uiStore`. It uses `useSearch` (API call to `/api/search`). The modal renders a backdrop + centered box with debounced input and a `FilteredBulletList` result list.

The quick-open palette (Ctrl+K) is conceptually similar but with different scope: fuzzy document title matching + optionally bullet search. Two implementation paths:

- **Path A (new component `QuickOpenPalette`):** Separate from `SearchModal`. Uses `useDocuments()` (already cached) for document fuzzy match client-side, falls back to `useSearch` for bullet content. Triggered by a separate `quickOpenOpen` flag in `uiStore`.
- **Path B (extend SearchModal):** Add a mode prop. When triggered by Ctrl+K, show document results first. When triggered by Ctrl+F, show full-text bullet results.

Recommendation: Path A. `SearchModal` is Ctrl+F full-text search and should stay as-is. Quick-open (Ctrl+K) has different UX expectations: instant results from cached document list, keyboard navigation with arrow keys, and navigating to a document (not just scrolling to a bullet). Conflating them complicates both.

**What QuickOpenPalette needs:**

- Fuzzy document match: filter `useDocuments()` result client-side — no new API endpoint needed for documents. `documents` are already fetched and cached in every session.
- Bullet fuzzy match: reuse `/api/search` via `useSearch` — same endpoint SearchModal uses.
- Keyboard navigation: arrow keys move selection, Enter opens, Escape closes.
- Trigger: Ctrl+K registered in `useGlobalKeyboard`.

**State change in uiStore:**

Add `quickOpenOpen: boolean` + `setQuickOpenOpen`. It does NOT need to be persisted (transient UI state).

**Where rendered:** In `App.tsx` alongside where `SearchModal` is rendered (inside `RequireAuth` but outside the page tree — or in `AppPage.tsx` if co-located with `SearchModal`). Check where `SearchModal` is currently mounted.

**Modification summary:**

| File | Change |
|------|--------|
| `uiStore.ts` | Add `quickOpenOpen: boolean`, `setQuickOpenOpen` (not persisted) |
| `useUndo.ts` (useGlobalKeyboard) | Add Ctrl+K handler calling `setQuickOpenOpen(true)` |
| `components/QuickOpenPalette.tsx` | New component |
| `AppPage.tsx` or `App.tsx` | Mount `<QuickOpenPalette>` when `quickOpenOpen` is true |

---

### Feature 6: Ctrl+E Desktop Sidebar Toggle

**Status: Already implemented.**

`useGlobalKeyboard` in `useUndo.ts` already handles `Ctrl+E → setSidebarOpen(!sidebarOpen)`. The store action exists. The only missing piece is that the sidebar does not visually respond to `sidebarOpen` changes — that gap is closed by Feature 1 (layout work).

No new code needed for this feature specifically.

---

## Recommended Project Structure Changes

Current `src/` layout is flat and component-tree-based. No restructure needed for v1.1. Add:

```
client/
├── public/
│   ├── manifest.json          # NEW — PWA manifest
│   ├── icon-192.png           # NEW — PWA icon
│   └── icon-512.png           # NEW — PWA icon
└── src/
    ├── index.css              # MODIFY — add CSS tokens, layout classes, dark mode
    ├── store/
    │   └── uiStore.ts         # MODIFY — add quickOpenOpen
    ├── hooks/
    │   └── useUndo.ts         # MODIFY — add Ctrl+K to useGlobalKeyboard
    ├── components/
    │   ├── Sidebar/
    │   │   └── Sidebar.tsx    # MODIFY — CSS class on <aside>, icon replacements
    │   ├── DocumentView/
    │   │   ├── FocusToolbar.tsx     # MODIFY — Lucide icons
    │   │   ├── DocumentToolbar.tsx  # MODIFY — Lucide icons
    │   │   └── BulletNode.tsx       # MODIFY — Lucide icons, CSS vars
    │   └── QuickOpenPalette.tsx     # NEW
    ├── pages/
    │   └── AppPage.tsx        # MODIFY — CSS layout classes, mount QuickOpenPalette
    └── main.tsx / index.html  # MODIFY — font links, manifest link
```

---

## Architectural Patterns

### Pattern 1: CSS Custom Properties for Theming

**What:** Define all color/surface tokens as `--color-*` CSS variables on `:root`. Override in `@media (prefers-color-scheme: dark)`. Components reference `var(--color-bg-primary)` etc. rather than hex literals.

**When to use:** Always for any value that changes between light and dark. Do not use for layout values (padding, width) — those don't theme.

**Trade-offs:** All inline styles touching color must be converted (large surface area). But CSS variables work without any JS, are inherited, and can be overridden per-subtree if needed later.

```css
/* index.css */
:root {
  --color-bg-primary: #ffffff;
  --color-bg-sidebar: #fafafa;
  --color-border: #e0e0e0;
  --color-text-primary: #111111;
  --color-text-secondary: #666666;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-bg-primary: #1a1a1a;
    --color-bg-sidebar: #141414;
    --color-border: #333333;
    --color-text-primary: #f0f0f0;
    --color-text-secondary: #999999;
  }
}
```

### Pattern 2: Off-Canvas Sidebar via CSS Transform

**What:** Sidebar is always in the DOM. On mobile when `sidebarOpen` is false, it is translated off-screen with `transform: translateX(-100%)`. When open, `transform: none`. CSS `transition` provides the slide animation. `sidebarOpen` in `uiStore` drives a `data-sidebar-open` attribute on the layout root.

**When to use:** When sidebar content must persist across open/close (avoids React unmount/remount). Consistent with how the existing mobile-overlay div already works.

**Trade-offs:** Sidebar is always rendered even when hidden (minor memory cost). But it avoids re-mounting Sidebar which would re-trigger queries.

```css
/* Mobile: sidebar slides in from left */
@media (max-width: 768px) {
  .app-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    height: 100vh;
    z-index: 10;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }
  .app-layout[data-sidebar-open="true"] .app-sidebar {
    transform: translateX(0);
  }
}
```

### Pattern 3: Client-Side Fuzzy Filter for QuickOpenPalette

**What:** Document list is already fetched and cached via `useDocuments()`. Filter it client-side with a simple `toLowerCase().includes()` or a lightweight fuzzy algorithm. No network request for document matching.

**When to use:** Any list that is already in the React Query cache and small enough to filter in memory (document list is typically < 100 items).

**Trade-offs:** Keeps quick-open instant. Bullet search still hits the API (reuse `useSearch`). The palette shows docs first, bullets below — two result sections in one modal.

---

## Data Flow

### Sidebar Toggle Flow

```
User taps hamburger                User presses Ctrl+E
        ↓                                  ↓
HamburgerButton.onClick      useGlobalKeyboard onKeyDown
        ↓                                  ↓
     setSidebarOpen(true)        setSidebarOpen(!sidebarOpen)
              ↓
         uiStore (Zustand)
              ↓ (persisted to localStorage via partialize)
     AppPage re-renders
              ↓
     data-sidebar-open attr on .app-layout div
              ↓
     CSS transform applies to .app-sidebar
              ↓ (mobile only — overlay is also shown)
     .mobile-overlay visible → tap closes sidebar
```

### Quick-Open Palette Flow

```
User presses Ctrl+K
        ↓
useGlobalKeyboard → setQuickOpenOpen(true)
        ↓
QuickOpenPalette mounts
        ↓
User types
        ↓
Filter useDocuments() cache client-side  → instant results
  AND  useSearch(query) if query >= 2 chars → API results
        ↓
User arrows to result, presses Enter
        ↓
navigate(`/doc/${docId}`)
setQuickOpenOpen(false)
```

### Dark Mode Flow

```
System changes color preference
        ↓
@media (prefers-color-scheme: dark) activates
        ↓
CSS custom properties re-resolve
        ↓
All elements using var(--color-*) repaint
(No React state, no JS involved)
```

---

## Anti-Patterns

### Anti-Pattern 1: Adding isMobile State to Zustand

**What people do:** Create a `isMobile: boolean` in `uiStore`, derive it from `window.matchMedia`, update it on resize.

**Why it's wrong:** Layout breakpoints belong in CSS, not JS state. Every media query change triggers a React re-render of every component subscribed to the store. CSS media queries are free.

**Do this instead:** Use CSS classes + `@media` rules. Let `sidebarOpen` remain the only sidebar-related state. The CSS handles whether "open" means "translate in from left" (mobile) or "occupy flex space" (desktop).

### Anti-Pattern 2: Conditionally Unmounting Sidebar Based on sidebarOpen

**What people do:** `{sidebarOpen && <Sidebar />}` — unmount sidebar when closed.

**Why it's wrong:** React Query caches associated with the sidebar (DocumentList, TagBrowser) are destroyed on unmount and re-fetched on remount. Creates flicker and unnecessary network requests. The existing overlay pattern implies the sidebar stays mounted.

**Do this instead:** Keep sidebar always mounted. Use CSS transform to hide it off-screen. The overlay handles the dimming and tap-to-close.

### Anti-Pattern 3: Creating a Separate ThemeContext for Dark Mode

**What people do:** Build a React context for `theme`, wrap the app, inject `theme` as a prop into every styled component.

**Why it's wrong:** Massively overengineered for a single prefers-color-scheme media query. CSS handles this natively with zero JS.

**Do this instead:** CSS custom properties + `@media (prefers-color-scheme: dark)`. If a manual toggle is later needed, set `data-theme="dark"` on `document.documentElement` from `uiStore` — one line of code, no context needed.

### Anti-Pattern 4: Merging QuickOpenPalette into SearchModal

**What people do:** Add a `mode` prop to `SearchModal` to handle both Ctrl+F full-text and Ctrl+K quick-open.

**Why it's wrong:** The two have different UX: SearchModal shows bullet results only, needs debounce, fires on 2+ chars. QuickOpenPalette shows docs instantly (no debounce, no minimum chars), then bullets below. Sharing the component creates branching logic that grows complex.

**Do this instead:** Two separate components. They share `FilteredBulletList` as a display primitive. They both use `useSearch` but with different conditions. Keep them decoupled.

---

## Integration Points

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| AppPage ↔ Sidebar | `sidebarOpen` via uiStore | No props needed — store is shared |
| HamburgerButton ↔ uiStore | Direct `setSidebarOpen(true)` call | Simple, no event bus |
| useGlobalKeyboard ↔ QuickOpenPalette | `quickOpenOpen` in uiStore | Same pattern as `searchOpen` |
| QuickOpenPalette ↔ useDocuments | React Query cache — no new fetch | Documents already in cache from AppPage |
| QuickOpenPalette ↔ useSearch | Same hook SearchModal uses | Reuse existing hook |
| CSS tokens ↔ all components | CSS custom properties via `var()` | Components need inline styles → CSS class migration |

### Key Constraint: FocusToolbar is Inside DndContext

FocusToolbar is mounted inside `BulletTree` (inside `DndContext`). Modifying its icon library does not affect this constraint. Adding dark mode `var()` references to its inline styles is straightforward. No structural change needed.

---

## Suggested Build Order (with Rationale)

```
Step 1: CSS Token Foundation (index.css)
  - Define --color-* custom properties (light + dark)
  - Define .app-layout, .app-sidebar, .app-content skeleton classes
  - Rationale: Everything else depends on tokens existing first

Step 2: Responsive Layout (AppPage + Sidebar)
  - Apply CSS classes to AppPage flex layout
  - Apply CSS class to Sidebar <aside>
  - Add data-sidebar-open attribute wiring
  - Add HamburgerButton (can be a simple inline button for now)
  - Verify: sidebar slides on mobile, stays persistent on desktop, Ctrl+E works
  - Rationale: Layout is the frame; all other UI sits inside it

Step 3: Dark Mode — Component Pass
  - Convert inline hex colors in Sidebar, AppPage, DocumentView to var(--color-*)
  - Convert FocusToolbar, DocumentToolbar, BulletNode, BulletContent
  - Verify: system dark mode switches correctly, WCAG AA contrast
  - Rationale: After layout is settled, color migration is pure find-and-replace work

Step 4: Icon Library + Fonts (lucide-react)
  - npm install lucide-react
  - Add font links to index.html
  - Update font-family in index.css
  - Replace FocusToolbar unicode chars with Lucide icons
  - Replace Sidebar header chars, DocumentToolbar chars, BulletNode chars
  - Verify: all icons render, no missing glyphs, FocusToolbar remains functional
  - Rationale: Icons + fonts are purely additive; no logic changes

Step 5: PWA Manifest
  - Create public/manifest.json
  - Create/source icon-192.png, icon-512.png
  - Add <link rel="manifest"> and theme-color to index.html
  - Verify: browser installs PWA, shows app name + icon on home screen
  - Rationale: Isolated change, no component dependencies

Step 6: Quick-Open Palette
  - Add quickOpenOpen to uiStore
  - Add Ctrl+K to useGlobalKeyboard
  - Build QuickOpenPalette component (fuzzy doc filter + useSearch bullets)
  - Mount in AppPage
  - Verify: Ctrl+K opens palette, arrow key nav works, Enter navigates
  - Rationale: Builds on stable layout + icons; uses existing hooks; last because most new logic
```

### Dependency Graph

```
Step 1 (CSS Tokens)
  ↓ (blocking)
Step 2 (Layout)     Step 3 (Dark Mode)   — both depend on Step 1
  ↓                      ↓
Step 4 (Icons)           |               — depends on Step 2 (layout stable)
  ↓                      |
Step 5 (PWA)             |               — independent, can go anywhere after Step 1
  ↓
Step 6 (Quick-Open)      |               — can start after Step 2; does not depend on icons/fonts/dark mode
```

Steps 3, 4, 5 are independent of each other once Step 2 is done. Steps 3-5 can be done in any order or in parallel.

---

## Sources

- Source code directly read: `AppPage.tsx`, `Sidebar.tsx`, `uiStore.ts`, `index.css`, `index.html`, `useUndo.ts` (useGlobalKeyboard), `SearchModal.tsx`, `FocusToolbar.tsx`, `gestures.ts`, `BulletTree.tsx`, `DocumentView.tsx`, `package.json` — HIGH confidence on all integration points
- PWA Web App Manifest spec: https://developer.mozilla.org/en-US/docs/Web/Manifest — standard, stable
- CSS custom properties + prefers-color-scheme: standard CSS, browser support universal in 2026
- Lucide React: https://lucide.dev — tree-shakeable, actively maintained, replaces heroicons/feather

---
*Architecture research for: v1.1 Mobile & UI Polish integration*
*Researched: 2026-03-10*
