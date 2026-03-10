# Phase 5: Mobile Layout Foundation - Context

**Gathered:** 2026-03-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Responsive layout where the sidebar is hidden on mobile by default — opened via a hamburger button in the breadcrumb bar, closed by tapping outside or the X button — with all key interactive elements having ≥44×44px touch targets, correct 100dvh viewport height, and desktop Ctrl+E sidebar toggle. No visual redesign, no dark mode, no new navigation capabilities.

</domain>

<decisions>
## Implementation Decisions

### Sidebar drawer pattern
- **Overlay/drawer**: sidebar slides OVER the content on mobile (not push) — content stays full-width underneath
- **Width**: fixed 240px on mobile (same as desktop) — content peeks on the right
- **Animation**: `translateX(-100%)` when closed → `translateX(0)` when open — CSS transform, GPU-accelerated
- **Easing/duration**: 250ms ease-out
- **Backdrop**: semi-transparent overlay fades in together with the sidebar slide (opacity transition, same toggle)
- **Close triggers**: tap outside (backdrop tap) and X button only — no swipe-to-close in Phase 5 (defer to Phase 8 to avoid conflict with bullet swipe gestures)
- **Position**: sidebar `position: fixed` with `100dvh` height on mobile — doesn't scroll with page content

### Hamburger button
- **Location**: left side of the Breadcrumb bar in DocumentView — slots in left of the breadcrumb text
- **Sticky**: Breadcrumb bar made sticky (`position: sticky, top: 0`) so hamburger is always accessible while scrolling
- **Visibility**: mobile-only (hidden when viewport > 768px) — desktop uses Ctrl+E only, no hamburger shown
- **Icon**: classic ☰ three-line hamburger — updated to Lucide icon in Phase 7

### Sidebar X close button
- **Position**: top-right of the sidebar header, next to the existing `...` menu button
  Layout: `| Notes   [...]  [X] |`
- **Visibility**: mobile-only (hidden when viewport > 768px)

### Desktop sidebar collapse (Ctrl+E)
- **Behavior**: full hide — sidebar completely disappears, content area fills 100% width
- **No visual toggle button** — keyboard shortcut only (Ctrl+E / Cmd+E); no chevron button at sidebar edge
- **Breadcrumb bar**: remains visible and goes full-width when sidebar is hidden
- **Persistence**: uses existing `sidebarOpen` in uiStore (already persisted via Zustand persist) — collapsed state survives page reload

### Sidebar initial state per device
- **Mobile always starts closed**: regardless of persisted `sidebarOpen` value, viewport ≤768px always initializes with sidebar closed
- **Detection**: `window.matchMedia('(max-width: 768px)')` at React mount — consistent with existing CSS breakpoint
- **Rotation**: sidebar state controlled by user interaction only — no auto-open on landscape rotation

### Touch targets (MOBL-05)
- **Approach**: targeted audit of key interactive elements (not a global CSS rule)
- **Elements to fix**: hamburger button, sidebar header buttons (create doc, export, logout), bullet dot drag handles, toolbar icon buttons, breadcrumb navigation links
- **Bullet dot**: expand tap/drag area with padding or pseudo-element to 44×44px — keep visual dot size unchanged

### dnd-kit drag sensor (mobile)
- **Activation**: switch to delay-based activation — 250ms hold before drag initiates on mobile
- **Mobile-only**: TouchSensor with 250ms delay on mobile; PointerSensor (immediate) on desktop
- **Rationale**: prevents drag sensor from intercepting horizontal swipe gestures in Phase 8; matches native iOS/Android drag behavior

### Viewport height
- Change `height: 100vh` in AppPage.tsx to `height: 100dvh` — correct height on mobile browsers accounting for address bar and home indicator

### Claude's Discretion
- Exact touch area expansion technique for bullet dots (padding vs pseudo-element vs min-width/height)
- Precise z-index layering for sidebar overlay, backdrop, and FocusToolbar
- iOS 26 visualViewport defensive clamp for FocusToolbar (noted in STATE.md — handle defensively)
- CSS implementation details for media query breakpoints

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `uiStore.ts`: `sidebarOpen: boolean` + `setSidebarOpen()` already exist and are persisted — sidebar state infrastructure ready; add mobile detection logic on top
- `Sidebar.tsx`: already has mobile overlay div (z-index 9, `display: none` via CSS, shown at 768px via `.mobile-overlay` class) + overlay `onClick={() => setSidebarOpen(false)}` — backdrop tap-to-close already wired; sidebar `<aside>` needs `translateX` transform added
- `AppPage.tsx`: main layout at line with `height: 100vh` → change to `100dvh`; sidebar always rendered (correct — always-mounted decision)
- `DocumentView.tsx`: `<Breadcrumb>` component at top — hamburger button slots left of it; make Breadcrumb bar sticky
- `index.css`: 768px breakpoint already established for `.mobile-overlay`

### Established Patterns
- Zustand uiStore for transient UI state — `sidebarOpen` already in the store
- CSS media queries at 768px — existing breakpoint to use consistently
- Sidebar always-mounted (conditional unmount evicts React Query caches for DocumentList, TagBrowser)

### Integration Points
- `AppPage.tsx` → change `100vh` to `100dvh`; sidebar always rendered (no conditional)
- `Sidebar.tsx` → add `translateX` CSS transform based on `sidebarOpen`; add X close button (mobile-only); backdrop already wired
- `DocumentView.tsx` / `Breadcrumb.tsx` → add hamburger button left of breadcrumb text; make bar sticky
- `BulletTree.tsx` → switch dnd-kit to TouchSensor (250ms delay) on mobile + PointerSensor on desktop
- `index.css` → existing 768px breakpoint; add sticky breadcrumb bar styles + mobile sidebar transforms

</code_context>

<specifics>
## Specific Ideas

- Sidebar layout on mobile (decided): 240px fixed-width drawer overlaying content, 250ms ease-out slide, backdrop fades in simultaneously
- Header layout inside open sidebar: `| Notes   [...]  [X] |` — X button top-right, mobile-only
- Hamburger in breadcrumb bar: `| [☰] Home > Doc Title |` — sticky, mobile-only, updates to Lucide in Phase 7
- Desktop Ctrl+E: full-width content (no icon rail), keyboard-only (no visual button), persists across reloads

</specifics>

<deferred>
## Deferred Ideas

- Swipe-left to close sidebar — Phase 8 (after swipe infrastructure settled)
- Sidebar collapse to icon rail on desktop — not planned; full-hide is sufficient
- Small toggle chevron button at sidebar edge for mouse users — deferred, keyboard-first

</deferred>

---

*Phase: 05-mobile-layout-foundation*
*Context gathered: 2026-03-10*
