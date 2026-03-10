# Phase 5: Mobile Layout Foundation - Research

**Researched:** 2026-03-10
**Domain:** Responsive CSS layout, off-canvas sidebar, touch sensors, mobile viewport
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Sidebar drawer pattern**
- Overlay/drawer: sidebar slides OVER the content on mobile (not push) — content stays full-width underneath
- Width: fixed 240px on mobile (same as desktop) — content peeks on the right
- Animation: `translateX(-100%)` when closed → `translateX(0)` when open — CSS transform, GPU-accelerated
- Easing/duration: 250ms ease-out
- Backdrop: semi-transparent overlay fades in together with the sidebar slide (opacity transition, same toggle)
- Close triggers: tap outside (backdrop tap) and X button only — no swipe-to-close in Phase 5
- Position: sidebar `position: fixed` with `100dvh` height on mobile — doesn't scroll with page content

**Hamburger button**
- Location: left side of the Breadcrumb bar in DocumentView — slots in left of the breadcrumb text
- Sticky: Breadcrumb bar made sticky (`position: sticky, top: 0`) so hamburger is always accessible while scrolling
- Visibility: mobile-only (hidden when viewport > 768px) — desktop uses Ctrl+E only, no hamburger shown
- Icon: classic ☰ three-line hamburger — updated to Lucide icon in Phase 7

**Sidebar X close button**
- Position: top-right of the sidebar header, next to the existing `...` menu button
  Layout: `| Notes   [...]  [X] |`
- Visibility: mobile-only (hidden when viewport > 768px)

**Desktop sidebar collapse (Ctrl+E)**
- Behavior: full hide — sidebar completely disappears, content area fills 100% width
- No visual toggle button — keyboard shortcut only (Ctrl+E / Cmd+E); no chevron button at sidebar edge
- Breadcrumb bar: remains visible and goes full-width when sidebar is hidden
- Persistence: uses existing `sidebarOpen` in uiStore (already persisted via Zustand persist) — collapsed state survives page reload

**Sidebar initial state per device**
- Mobile always starts closed: regardless of persisted `sidebarOpen` value, viewport ≤768px always initializes with sidebar closed
- Detection: `window.matchMedia('(max-width: 768px)')` at React mount — consistent with existing CSS breakpoint
- Rotation: sidebar state controlled by user interaction only — no auto-open on landscape rotation

**Touch targets (MOBL-05)**
- Approach: targeted audit of key interactive elements (not a global CSS rule)
- Elements to fix: hamburger button, sidebar header buttons (create doc, export, logout), bullet dot drag handles, toolbar icon buttons, breadcrumb navigation links
- Bullet dot: expand tap/drag area with padding or pseudo-element to 44×44px — keep visual dot size unchanged

**dnd-kit drag sensor (mobile)**
- Activation: switch to delay-based activation — 250ms hold before drag initiates on mobile
- Mobile-only: TouchSensor with 250ms delay on mobile; PointerSensor (immediate) on desktop
- Rationale: prevents drag sensor from intercepting horizontal swipe gestures in Phase 8; matches native iOS/Android drag behavior

**Viewport height**
- Change `height: 100vh` in AppPage.tsx to `height: 100dvh` — correct height on mobile browsers accounting for address bar and home indicator

### Claude's Discretion
- Exact touch area expansion technique for bullet dots (padding vs pseudo-element vs min-width/height)
- Precise z-index layering for sidebar overlay, backdrop, and FocusToolbar
- iOS 26 visualViewport defensive clamp for FocusToolbar (noted in STATE.md — handle defensively)
- CSS implementation details for media query breakpoints

### Deferred Ideas (OUT OF SCOPE)
- Swipe-left to close sidebar — Phase 8 (after swipe infrastructure settled)
- Sidebar collapse to icon rail on desktop — not planned; full-hide is sufficient
- Small toggle chevron button at sidebar edge for mouse users — deferred, keyboard-first
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| MOBL-01 | User can open the sidebar on mobile via a hamburger button in the header | Hamburger button in sticky Breadcrumb bar; `setSidebarOpen(true)` wired to existing uiStore |
| MOBL-02 | Sidebar auto-closes when user taps outside it on mobile | Backdrop overlay already wired in Sidebar.tsx; needs to be always rendered (not conditional on open state) for opacity-fade pattern |
| MOBL-03 | Sidebar has an explicit X close button on mobile | New button in sidebar header area, mobile-only via media query class |
| MOBL-04 | Sidebar slides in/out with a smooth off-canvas transition | CSS `translateX(-100%)` → `translateX(0)` with `transition: transform 250ms ease-out` on the `<aside>` |
| MOBL-05 | All interactive elements have touch targets ≥44×44px | Targeted padding/min-height on identified elements; bullet dot expanded via padding with `overflow:hidden` on outer div |
| MOBL-06 | App layout uses 100dvh (not 100vh) for correct height on mobile browsers | One-line change in AppPage.tsx; also update `height: 100vh` on sidebar `<aside>` |
| MOBL-07 | User can toggle sidebar on desktop with Ctrl/Cmd+E | `keydown` listener in AppPage or App; `setSidebarOpen(!sidebarOpen)`; event on `document` |
</phase_requirements>

---

## Summary

Phase 5 is almost entirely a CSS + React wiring task. The app already has the correct Zustand state (`sidebarOpen`/`setSidebarOpen`) and even a partially-wired mobile overlay div. The work is: (1) converting the sidebar from a statically-positioned flex child to a `position: fixed` off-canvas drawer with CSS transform, (2) adding the hamburger and X buttons with media-query visibility, (3) making the breadcrumb bar sticky, (4) switching dnd-kit to `TouchSensor` on mobile, and (5) doing a targeted touch-target audit on key interactive elements.

The only non-trivial technical concern is z-index layering: the sidebar overlay sits above main content but must not obscure the FocusToolbar (currently `zIndex: 1000`). The existing sidebar backdrop is at `zIndex: 9` — the sidebar `<aside>` will need `zIndex: 10` on mobile, and FocusToolbar must remain at or above that. The iOS 26 `visualViewport.offsetTop` regression in FocusToolbar is already defensively clamped via `Math.max(0, ...)` in `computeKeyboardOffset`; no additional work is needed unless physical device testing reveals a new failure mode.

`100dvh` is universally supported in all target browsers (Chrome 108+, Safari 15.4+, Firefox 101+, Edge 94+). There is no need for a fallback given the project's current user base.

**Primary recommendation:** Do all sidebar transformation with CSS classes toggled by `sidebarOpen` state rather than inline style objects, keeping the media-query override pattern consistent with the existing `.mobile-overlay` approach in `index.css`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| CSS custom properties + media queries | N/A (browser native) | Responsive breakpoints, transform transitions | No runtime cost; GPU-accelerated transforms |
| Zustand `useUiStore` | ^5.0.11 (already in project) | `sidebarOpen` state, `setSidebarOpen` | Already wired and persisted; no new state needed |
| `@dnd-kit/core` | ^6.3.1 (already in project) | `TouchSensor` for mobile drag activation | Same library already used for PointerSensor |
| `window.matchMedia` | Browser native | Detect mobile viewport at React mount | Synchronous, no library needed |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `window.visualViewport` | Browser native | FocusToolbar keyboard offset | Already implemented; defensive clamp for iOS 26 |
| CSS `100dvh` | Browser native | Full viewport height excluding browser chrome | Replace all `100vh` in app-level layout |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Pure CSS transitions | Framer Motion | Project decision: CSS sufficient; Framer Motion explicitly out of scope per REQUIREMENTS.md |
| CSS media query class toggling | JS-driven inline styles | Inline styles already used throughout app; either approach works, but CSS classes are more maintainable for responsive overrides |
| `window.matchMedia` at mount | CSS-only responsive show/hide | JS check needed to override persisted `sidebarOpen` state on mobile; CSS handles visibility but not state reset |

**Installation:** No new packages required.

---

## Architecture Patterns

### Recommended Project Structure

No new files needed for this phase. Changes are localized to:

```
client/src/
├── pages/AppPage.tsx              # 100dvh fix, Ctrl+E listener
├── components/Sidebar/Sidebar.tsx # fixed-position drawer, X button, backdrop always-rendered
├── components/DocumentView/
│   ├── DocumentView.tsx           # wrap Breadcrumb bar in sticky container
│   ├── Breadcrumb.tsx             # hamburger button slot left of content
│   └── BulletTree.tsx             # useSensors: TouchSensor+PointerSensor
└── index.css                      # mobile breakpoint rules for sidebar transform, touch targets
```

### Pattern 1: Off-Canvas Sidebar with CSS Transform

**What:** Sidebar is always-mounted in the DOM. On mobile, it uses `position: fixed` and slides via `translateX`. Open/closed is driven by the `sidebarOpen` boolean from uiStore.

**When to use:** When sidebar must not unmount (would evict React Query caches for DocumentList/TagBrowser).

**Example:**
```css
/* index.css */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    height: 100dvh;
    z-index: 10;
    transform: translateX(-100%);
    transition: transform 250ms ease-out;
  }
  .sidebar.open {
    transform: translateX(0);
  }
  .sidebar-backdrop {
    display: block;
    opacity: 0;
    pointer-events: none;
    transition: opacity 250ms ease-out;
  }
  .sidebar-backdrop.open {
    opacity: 1;
    pointer-events: auto;
  }
}
```

```tsx
// Sidebar.tsx — aside element gets className based on sidebarOpen
<aside className={`sidebar${sidebarOpen ? ' open' : ''}`} style={{ width: 240, ... }}>
```

### Pattern 2: Mobile-Only Elements via CSS Class

**What:** Elements that are only relevant on mobile (hamburger, X button) rendered in JSX but hidden on desktop via CSS. Avoids conditional rendering complexity.

**When to use:** When the element is cheap to render and its presence/absence doesn't affect layout on desktop.

**Example:**
```css
@media (min-width: 769px) {
  .mobile-only { display: none !important; }
}
```

```tsx
<button className="mobile-only hamburger-btn" onClick={() => setSidebarOpen(true)}>
  &#9776;
</button>
```

### Pattern 3: dnd-kit Multi-Sensor (Mobile vs Desktop)

**What:** Use `TouchSensor` with 250ms delay on mobile, `PointerSensor` with distance constraint on desktop. Detect at component render time with `window.matchMedia`.

**When to use:** When touch drag must not intercept horizontal swipe gestures (required before Phase 8 swipe implementation).

**Example:**
```tsx
// Source: https://dndkit.com/api-documentation/sensors/touch
import { PointerSensor, TouchSensor, useSensor, useSensors } from '@dnd-kit/core';

const isMobile = window.matchMedia('(max-width: 768px)').matches;

const sensors = useSensors(
  useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  ...(isMobile
    ? [useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } })]
    : [])
);
```

Note: `window.matchMedia` at render time is acceptable here because the sensor configuration is stable per-mount. If orientation changes are a concern, a `useEffect` with `addEventListener('change', ...)` can update the sensors ref, but CONTEXT.md explicitly defers rotation handling.

### Pattern 4: Sticky Breadcrumb Bar

**What:** The container wrapping the Breadcrumb (and the new hamburger button) is given `position: sticky; top: 0; z-index: 5; background: #fff`.

**When to use:** DocumentView content area scrolls; the breadcrumb bar must remain accessible.

**Pitfall:** `position: sticky` requires the scrolling ancestor to NOT have `overflow: hidden`. The current `<main>` in AppPage.tsx has `overflow: auto` — this is compatible.

**Example:**
```tsx
// DocumentView.tsx
<div style={{ position: 'sticky', top: 0, zIndex: 5, background: '#fff', display: 'flex', alignItems: 'center', gap: 8, padding: '0.5rem 0' }}>
  <button className="mobile-only hamburger-btn" onClick={() => setSidebarOpen(true)}>
    &#9776;
  </button>
  {zoomedBulletId ? (
    <Breadcrumb ... />
  ) : (
    <h1 ...>{document.title}</h1>
  )}
</div>
```

### Pattern 5: Mobile `sidebarOpen` Override at Mount

**What:** On component mount, if viewport matches mobile, force `sidebarOpen` to `false` regardless of persisted value. This overrides the Zustand-persisted state.

**When to use:** MOBL-01 requirement that mobile always starts with sidebar closed.

**Example:**
```tsx
// AppPage.tsx
useEffect(() => {
  if (window.matchMedia('(max-width: 768px)').matches) {
    setSidebarOpen(false);
  }
}, []); // empty deps — run once at mount only
```

### Anti-Patterns to Avoid

- **Conditional rendering of the sidebar** (`{sidebarOpen && <Sidebar />}`): This unmounts the component and evicts React Query cache for DocumentList and TagBrowser, causing a loading flicker on reopen. The sidebar must always be rendered; use CSS transform to hide it visually.
- **Using `display: none` for sidebar close animation**: `display: none` cannot be transitioned. The `translateX` approach allows the CSS transition to run.
- **Setting `overflow: hidden` on a parent of the sticky breadcrumb bar**: Breaks sticky positioning. The current `<main>` uses `overflow: auto` which is correct.
- **Global `min-height: 44px; min-width: 44px` on all buttons**: The CONTEXT.md decision is a targeted audit, not a global rule. A global rule would break toolbar and tab bar layouts.
- **Using `window.innerHeight` alone for FocusToolbar offset**: Already handled with `visualViewport` — but iOS 26 regression means `offsetTop` may not reset to 0 after keyboard dismiss. The existing `Math.max(0, ...)` clamp in `computeKeyboardOffset` already handles this defensively.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Drag activation delay | Custom `setTimeout` + pointer event tracking | `TouchSensor` with `activationConstraint.delay` | dnd-kit handles pointer capture, cancel on move beyond tolerance, and cleanup |
| Touch target expansion on bullet dot | Invisible sibling element or wrapper div | CSS `padding` + `overflow: hidden` on the outer div, or `::after` pseudo-element | Pseudo-element avoids DOM changes; padding is simpler but must be offset with negative margin to avoid layout shift |
| Viewport height | JavaScript `window.innerHeight - chrome` calculation | `100dvh` CSS unit | Native CSS, no JS, no layout thrash |
| Backdrop | Custom `useEffect` event listener | Existing backdrop div in Sidebar.tsx with `onClick={() => setSidebarOpen(false)}` | Already implemented — only needs to move outside the `{sidebarOpen && ...}` conditional |

**Key insight:** The project's existing code already implements ~60% of this phase. The key transforms are CSS class additions and restructuring existing elements, not new systems.

---

## Common Pitfalls

### Pitfall 1: Backdrop Rendered Inside Conditional `{sidebarOpen && ...}`

**What goes wrong:** The current `Sidebar.tsx` wraps the backdrop in `{sidebarOpen && ...}`. This means the fade-out transition never plays — the element is immediately removed from the DOM when `sidebarOpen` becomes false.

**Why it happens:** Logical placement: "show backdrop only when open." But transitions require the element to exist in both states.

**How to avoid:** Always render the backdrop div. Control visibility via CSS opacity + `pointer-events: none` when closed, not conditional rendering.

**Warning signs:** Backdrop disappears instantly (no fade) when tapping outside.

### Pitfall 2: Sidebar `<aside>` Uses Inline Styles That Fight CSS Media Query Overrides

**What goes wrong:** The current `<aside>` uses `style={{ width: 240, height: '100vh', ... }}`. Inline styles have higher specificity than class-based styles, so CSS media query overrides may fail.

**Why it happens:** The app uses inline styles throughout (consistent pattern), but responsive overrides need to win.

**How to avoid:** For properties that need media query overrides (position, height, transform, z-index), use className-based CSS rules rather than inline styles for those specific properties. Keep non-responsive properties as inline styles.

**Warning signs:** Mobile sidebar still takes up space in the layout (flex-child behavior not overridden to fixed).

### Pitfall 3: Sticky Breadcrumb Bar Background Shows Through

**What goes wrong:** Without an explicit `background` on the sticky container, content scrolls behind the transparent breadcrumb bar.

**Why it happens:** `position: sticky` still paints behind scrolled content if no background is set.

**How to avoid:** Always set `background: #fff` (or the page background color) on the sticky element.

### Pitfall 4: dnd-kit `useSensors` Called Conditionally

**What goes wrong:** React hooks cannot be called conditionally. Attempting `if (isMobile) useSensor(TouchSensor, ...)` inside `useSensors` call or with early return violates Rules of Hooks.

**Why it happens:** The mobile check is a boolean at render time — it's tempting to inline it.

**How to avoid:** Always declare both sensors; for desktop, simply do not include `TouchSensor` in the array passed to `useSensors`. The pattern in Pattern 3 above (spread into array) is compliant.

### Pitfall 5: `window.matchMedia` Called Outside React (Not in Effect/Render)

**What goes wrong:** `window.matchMedia` is not available during SSR. This project is CSR-only (Vite SPA), so this is not an issue here — but calling it at module scope rather than inside a component/effect would break any future SSR consideration and may cause test failures in jsdom if `window` is not available.

**Why it happens:** Convenience — storing the result in a module constant.

**How to avoid:** Call `window.matchMedia` inside the component function body or a `useEffect`. For the sensor pattern, calling it in the render function is fine for a CSR-only app.

### Pitfall 6: iOS 26 `visualViewport.offsetTop` Regression

**What goes wrong:** On iOS 26 (Safari 26), after the soft keyboard is dismissed, `visualViewport.offsetTop` does not reset to 0. The FocusToolbar's `bottom: keyboardOffset` stays elevated above the page bottom.

**Why it happens:** Confirmed WebKit bug filed on Apple Developer Forums (thread/800125). `vvOffsetTop` remains non-zero when it should be 0.

**How to avoid:** The existing `computeKeyboardOffset` already uses `Math.max(0, ...)`. Verify the formula is `Math.max(0, windowInnerHeight - vvOffsetTop - vvHeight)`. If `vvHeight` grows back to `windowInnerHeight` when keyboard closes, the result clamps to 0 correctly even if `offsetTop` is wrong.

**Warning signs:** FocusToolbar visually stuck above the bottom of the screen after keyboard dismissal on iOS 26 physical device.

---

## Code Examples

Verified patterns from official sources and existing codebase:

### TouchSensor with Delay (dnd-kit 6.x)
```typescript
// Source: https://dndkit.com/api-documentation/sensors/touch
import { PointerSensor, TouchSensor, useSensor, useSensors } from '@dnd-kit/core';

// In BulletTree.tsx, replace current useSensors call:
const isMobile = window.matchMedia('(max-width: 768px)').matches;
const sensors = useSensors(
  useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  ...(isMobile
    ? [useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } })]
    : [])
);
```

### CSS Off-Canvas Sidebar (Mobile)
```css
/* index.css — add to mobile section */
@media (max-width: 768px) {
  .sidebar {
    position: fixed !important;
    top: 0;
    left: 0;
    height: 100dvh !important;
    z-index: 10;
    transform: translateX(-100%);
    transition: transform 250ms ease-out;
  }
  .sidebar.sidebar-open {
    transform: translateX(0);
  }
  .sidebar-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.3);
    z-index: 9;
    opacity: 0;
    pointer-events: none;
    transition: opacity 250ms ease-out;
  }
  .sidebar-backdrop.sidebar-open {
    opacity: 1;
    pointer-events: auto;
  }
  .mobile-only { display: flex; }
}
@media (min-width: 769px) {
  .mobile-only { display: none !important; }
}
```

### Ctrl+E / Cmd+E Keyboard Shortcut
```typescript
// AppPage.tsx
useEffect(() => {
  function handleKeyDown(e: KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'e') {
      e.preventDefault();
      setSidebarOpen(!sidebarOpen);
    }
  }
  document.addEventListener('keydown', handleKeyDown);
  return () => document.removeEventListener('keydown', handleKeyDown);
}, [sidebarOpen, setSidebarOpen]);
```

### Touch Target Expansion (Bullet Dot — no layout change)
```css
/* Option A: padding + negative margin (no pseudo-element) */
.bullet-dot {
  padding: 14px 14px;       /* (44 - 16) / 2 = 14px each side */
  margin: -14px -14px;      /* cancel the layout expansion */
  box-sizing: content-box;
}

/* Option B: pseudo-element (zero layout impact) */
.bullet-dot {
  position: relative;
}
.bullet-dot::after {
  content: '';
  position: absolute;
  inset: -14px;
}
```

### 100dvh Replacement
```tsx
// AppPage.tsx line 34 — change:
<div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
// to:
<div style={{ display: 'flex', height: '100dvh', overflow: 'hidden' }}>

// Sidebar.tsx aside — also change height: '100vh' to '100dvh'
// (or handle via CSS class override shown above)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `100vh` (breaks on mobile) | `100dvh` (dynamic) | Chrome 108 / Safari 15.4 (2022-2023) | Correct height accounting for browser chrome |
| `overflow: hidden` + JS scroll lock when modal open | `overscroll-behavior: contain` | 2019+ | Simpler scroll locking; not needed here since sidebar is fixed-position |
| JS-calculated `window.innerHeight` workaround | `100dvh` CSS unit | 2023 | Eliminates the JS height hack entirely |
| dnd-kit `MouseSensor` for desktop | `PointerSensor` (unified pointer events) | dnd-kit v6 | PointerSensor handles mouse, stylus, and touch unified |

**Deprecated/outdated:**
- `-webkit-fill-available` height hack: replaced by `100dvh` (still needed as fallback for iOS < 15.4, but project targets modern browsers)
- `TouchSensor` without `tolerance`: always set tolerance ≥ 5px; zero tolerance cancels drag on any finger tremor

---

## Open Questions

1. **Sidebar `<aside>` inline vs class styling conflict**
   - What we know: The `<aside>` has `style={{ width: 240, height: '100vh', ... }}` inline. CSS media query overrides need `!important` to win over inline styles for `position`, `height`, and `transform`.
   - What's unclear: Whether using `!important` broadly in media queries will cause future maintenance issues.
   - Recommendation: Refactor the sidebar's `position`, `height`, and `transform` out of inline styles into a CSS class for the mobile override. Keep visual properties (background, border, padding) as inline styles.

2. **FocusToolbar z-index on mobile when sidebar is open**
   - What we know: FocusToolbar is `zIndex: 1000`, sidebar will be `zIndex: 10`, backdrop `zIndex: 9`. FocusToolbar should always be above the sidebar.
   - What's unclear: Whether the user expects the FocusToolbar to be dismissible or blocked when the sidebar is open.
   - Recommendation: No change to FocusToolbar z-index. When sidebar opens on mobile, the backdrop (z-index 9) will cover main content but FocusToolbar (z-index 1000) will remain visible. This is acceptable — tapping the backdrop closes the sidebar and restores normal flow.

3. **`window.matchMedia` reactivity on resize**
   - What we know: The mobile override (`setSidebarOpen(false)` at mount) runs once. If a user resizes from mobile to desktop breakpoint, the sidebar stays closed until they press Ctrl+E.
   - What's unclear: Is the "sidebar stays closed after rotation to landscape" behavior acceptable?
   - Recommendation: CONTEXT.md explicitly states "no auto-open on landscape rotation" — this is locked. No reactivity needed.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.x |
| Config file | `client/vite.config.ts` (test section) |
| Quick run command | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run --reporter=verbose src/test/` |
| Full suite command | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MOBL-01 | Hamburger click calls `setSidebarOpen(true)` | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ Wave 0 |
| MOBL-02 | Backdrop click calls `setSidebarOpen(false)` | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ Wave 0 |
| MOBL-03 | X button click calls `setSidebarOpen(false)` | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ Wave 0 |
| MOBL-04 | Sidebar has `.sidebar-open` class when open | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ Wave 0 |
| MOBL-05 | Touch targets ≥44×44px | manual | N/A — requires browser DevTools / physical device | manual-only |
| MOBL-06 | AppPage renders with `height: 100dvh` | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ Wave 0 |
| MOBL-07 | Ctrl+E toggles `sidebarOpen` | unit | `npx vitest run src/test/mobileLayout.test.tsx` | ❌ Wave 0 |

**MOBL-05 justification for manual-only:** `min-height`/`min-width` and touch target sizes are visual/physical properties. jsdom does not compute layout, so assertions on computed element dimensions are not meaningful in unit tests. Verification requires DevTools "Inspect Element" on a rendered page or physical device.

### Sampling Rate
- **Per task commit:** `cd /c/Users/gmain/Dev/Notes/client && npx vitest run src/test/mobileLayout.test.tsx`
- **Per wave merge:** `cd /c/Users/gmain/Dev/Notes/client && npx vitest run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `client/src/test/mobileLayout.test.tsx` — covers MOBL-01, MOBL-02, MOBL-03, MOBL-04, MOBL-06, MOBL-07
  - Tests: hamburger renders and triggers open, backdrop renders and triggers close, X button triggers close, sidebar className reflects open state, AppPage style prop contains `100dvh`, Ctrl+E keydown event toggles state
  - Pattern: follow `keyboardOffset.test.ts` — import component, mock dependencies (react-router-dom, TanStack Query), render with RTL, assert state/DOM

---

## Sources

### Primary (HIGH confidence)
- Official dnd-kit docs (`https://dndkit.com/api-documentation/sensors/touch`) — TouchSensor delay/tolerance API confirmed
- Existing project source files — AppPage.tsx, Sidebar.tsx, BulletTree.tsx, BulletNode.tsx, FocusToolbar.tsx, uiStore.ts, index.css — current state verified by direct read

### Secondary (MEDIUM confidence)
- web.dev blog (`https://web.dev/blog/viewport-units`) — `100dvh` support matrix (Chrome 108+, Safari 15.4+, Firefox 101+, Edge 94+)
- Apple Developer Forums (`https://developer.apple.com/forums/thread/800125`) — iOS 26 `visualViewport.offsetTop` regression confirmed; existing `Math.max(0, ...)` clamp in `computeKeyboardOffset` already mitigates it

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project, no new installs
- Architecture: HIGH — code verified by direct read; CSS patterns well-established
- Pitfalls: HIGH — inline style vs CSS specificity conflict identified by reading actual source; backdrop conditional rendering identified directly
- Touch targets: MEDIUM — verification requires browser/device; technique options (padding vs pseudo-element) both valid per CSS spec

**Research date:** 2026-03-10
**Valid until:** 2026-06-10 (stable CSS/browser APIs; dnd-kit 6.x API stable)
