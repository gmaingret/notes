# Pitfalls Research

**Domain:** Adding mobile layout, dark mode, PWA manifest, and quick-open palette to an existing React outliner with gestures and drag-and-drop
**Researched:** 2026-03-10
**Confidence:** HIGH (codebase inspected directly; all pitfalls traced to specific files, line numbers, and patterns in the v1.0 codebase)

---

## Critical Pitfalls

### Pitfall 1: FocusToolbar becomes invisible in dark mode due to hardcoded inline style colors

**What goes wrong:**
`FocusToolbar.tsx` uses inline `style` props with literal hex colors (`background: '#fff'`, `color: '#444'`, `color: '#e55'`, `borderTop: '1px solid #e5e7eb'`). CSS-variable-based dark mode — applied via `[data-theme="dark"]` on `<html>` — cannot override inline style attributes. The toolbar will remain white-on-white or lose contrast entirely in dark mode because inline styles have the highest cascade specificity. The same problem exists in `btnStyle` objects in `FocusToolbar.tsx` and in `BulletContent.tsx`.

**Why it happens:**
Inline styles were the fast path during v1.0 development for correctness and component isolation. When a theming layer is added later, it relies on CSS custom properties cascading from a root selector — but inline styles sit above the cascade and block that inheritance.

**How to avoid:**
Before wiring the `data-theme` attribute or any dark mode toggle, run a migration pass: convert every hardcoded color literal in `.tsx` files to a `className` + CSS stylesheet pattern using custom properties. Grep for `style=\{\{` with hex color strings across all TSX files. Treat this migration as the first task in the Dark Mode phase — it is a prerequisite, not an optional cleanup.

**Warning signs:**
- Any `style={{ background: '#fff' }}` or `style={{ color: '#444' }}` in a React component will silently fail in dark mode.
- The FocusToolbar appears but is white-on-white — easy to miss in a desktop dark mode test if you do not scroll a document on mobile.

**Phase to address:** Phase 2 (Dark Mode) — first task within that phase, before adding any `data-theme` wiring.

---

### Pitfall 2: Dark mode FOUC — white flash before React mounts

**What goes wrong:**
If dark mode is implemented by reading `localStorage` or `window.matchMedia` inside a React `useEffect` and setting state, there is always one frame where the component renders with the default light theme before the effect fires. Users who prefer dark mode see a white flash on every hard refresh or page load.

**Why it happens:**
React effects run after the browser has painted the initial frame. Any theme state initialized to `'light'` (or derived from an effect) will be visible momentarily. This is inherent to the React lifecycle — not a bug but a timing issue.

**How to avoid:**
Since this app is a Vite SPA (no SSR, no hydration), the correct fix is an inline `<script>` block in `client/index.html`, placed before the `<script type="module">` that loads the React bundle. The script reads `localStorage.getItem('theme')` (or `window.matchMedia('(prefers-color-scheme: dark)').matches`) and immediately calls `document.documentElement.setAttribute('data-theme', 'dark')`. This runs synchronously before the first paint. The React theme context then reads the already-applied attribute to initialize its state, with no flash. Note: because this is a Vite SPA, SSR hydration mismatches are not a concern here — the FOUC is the only problem to solve.

**Warning signs:**
- White flash is visible when system OS is set to dark mode and the page is hard-refreshed.
- Flash only appears on initial load, not on client-side navigation — confirms it is an initialization-timing problem, not a component re-render issue.

**Phase to address:** Phase 2 (Dark Mode) — the inline `<script>` in `index.html` must be the very first thing implemented in the dark mode phase.

---

### Pitfall 3: dnd-kit PointerSensor fires drag on horizontal swipe, killing existing swipe gestures

**What goes wrong:**
The existing swipe gestures (`gestures.ts`: swipe right = complete, swipe left = delete) are implemented as raw `touchstart`/`touchmove`/`touchend` handlers on bullet rows. dnd-kit's `PointerSensor` with `activationConstraint: { distance: 5 }` intercepts pointer events the moment any touch moves 5px in any direction — including a horizontal swipe. When a user swipes right to complete a bullet, dnd-kit activates the drag before the swipe handler crosses its 40% threshold, consuming the pointer events and preventing the swipe action from ever completing.

**Why it happens:**
`distance: 5` is very low. A deliberate horizontal swipe crosses 5px almost instantly. Both the swipe handler and dnd-kit compete for the same `pointermove` events. On mobile, dnd-kit calls `event.preventDefault()` on `pointermove` once drag activates, which blocks the touch events the swipe handler depends on.

**How to avoid:**
Option A (recommended): Change dnd-kit activation to a delay constraint instead of distance — `{ delay: 250, tolerance: 5 }`. A 250ms press-and-hold is a natural distinction from a fast horizontal swipe. Option B: Restrict the drag handle to a dedicated element (e.g., the bullet dot icon) and set `touch-action: none` only on that handle element. The swipe handler operates on the full row without interference. Do NOT use both `PointerSensor` and `TouchSensor` simultaneously — dnd-kit documentation explicitly warns this causes event conflicts.

**Warning signs:**
- Swipe right or left produces a drag overlay instead of completing/deleting the bullet.
- Long-press context menu still works correctly (500ms delay is longer than any drag activation — no conflict there).
- The problem only manifests on touch devices, not on desktop mouse. Chrome DevTools touch emulation may not reproduce it accurately — test on a physical iPhone.

**Phase to address:** Phase 1 (Mobile Layout — swipe gesture polish task). Verify on physical iPhone before merging.

---

### Pitfall 4: iOS Safari auto-zoom when tapping bullet contenteditable (font-size < 16px)

**What goes wrong:**
iOS Safari automatically zooms the viewport whenever a user focuses any `input` or `contenteditable` element whose computed `font-size` is below 16px. If the new font pairing (Inter + JetBrains Mono) is applied at a size like 14px for mobile bullet text, every tap-to-edit triggers a jarring full-viewport zoom. After the keyboard is dismissed, the viewport may not cleanly return to 1x scale, leaving the layout shifted. The bullet being edited may be partially hidden under the browser chrome.

**Why it happens:**
Apple's design intent: text smaller than 16px is assumed to be unreadable at default zoom, so Safari zooms in to assist. This fires on `focus`, before any typing. `contenteditable` elements are treated identically to `<input>` for this purpose.

**How to avoid:**
Add `font-size: max(16px, 1em)` to all `[contenteditable]` and `<input>` elements in the global stylesheet (`client/src/index.css`). If the design calls for visually smaller text, use `transform: scale(0.875)` with compensating negative margins rather than a sub-16px font-size. Never use `user-scalable=no` in the viewport `<meta>` tag — it is an accessibility violation, and iOS 10+ ignores it anyway.

**Warning signs:**
- Viewport jumps when tapping any bullet on a physical iPhone.
- Chrome DevTools mobile emulation does NOT reproduce this bug — requires a real device or a cloud testing service.
- The font pairing change (Inter + JetBrains Mono) is the trigger that can introduce this — measure actual computed font sizes after the typography refactor.

**Phase to address:** Phase 1 (Mobile Layout) — include as a verification criterion in the font pairing task. Check computed font size on mobile with DevTools before closing the phase.

---

### Pitfall 5: iOS Safari 100dvh — sidebar overlay and full-height layouts clip on mobile

**What goes wrong:**
If the mobile sidebar overlay uses `height: 100vh`, it will be taller than the visible area on iOS Safari. Safari calculates `100vh` as the total viewport including the address bar and bottom navigation bar. When those bars are visible (which they are when the page first loads), the sidebar extends below the visible screen and the bottom is clipped. Users cannot tap sidebar items near the bottom.

**Why it happens:**
Apple chose not to resize the viewport when the address bar collapses on scroll. This behavior has been unchanged since 2016. The CSS dynamic viewport units (`dvh`, `svh`, `lvh`) were introduced in Safari 15.4 specifically to address this and are now well-supported.

**How to avoid:**
Replace `height: 100vh` on any full-height overlay or sidebar panel with:
```css
height: 100vh; /* fallback for old browsers */
height: 100dvh; /* correct for iOS Safari 15.4+ */
```
`dvh` (dynamic viewport height) tracks the visible viewport height including address bar collapse. Additionally, add `padding-bottom: env(safe-area-inset-bottom)` to the sidebar's scroll container so content is not clipped under the home indicator on notched iPhones (iPhone X and later).

**Warning signs:**
- Sidebar bottom is cut off on iPhone Safari — the last document in the list is partially or fully hidden.
- Desktop testing shows no problem (100vh is fine on desktop).
- The existing `@media (max-width: 768px)` and `.mobile-overlay` CSS in `index.css` will need this update.

**Phase to address:** Phase 1 (Mobile Layout) — apply alongside hamburger menu implementation.

---

### Pitfall 6: FocusToolbar stuck mid-screen after keyboard dismiss on iOS 26

**What goes wrong:**
`FocusToolbar` positions itself using `bottom: keyboardOffset` where `keyboardOffset` is computed from `visualViewport.offsetTop` and `visualViewport.height`. On iOS 26 (the next major iOS release, in beta as of early 2026), there is an active WebKit regression (bug #237851) where `visualViewport.offsetTop` does not reset to 0 after the keyboard is dismissed. The computed offset remains nonzero, leaving the toolbar floating in the middle of the screen rather than at the bottom.

**Why it happens:**
This is an Apple-introduced regression in iOS 26, confirmed on Apple's developer forums and WebKit bug tracker. The `computeKeyboardOffset` function in `FocusToolbar.tsx` correctly implements the visualViewport approach that worked through iOS 17 — the bug is in the browser, not in the app's code.

**How to avoid:**
Add a defensive clamp inside `computeKeyboardOffset`: if the computed offset exceeds a plausible threshold (e.g., more than 60% of `window.innerHeight`), return 0 instead. This prevents a stuck toolbar when `visualViewport.offsetTop` reports a stale nonzero value. Additionally, add `paddingBottom: 'env(safe-area-inset-bottom)'` to the toolbar's inline style — this handles the notch/home-indicator gap independently of the keyboard offset calculation.

Secondary mitigation: as an alternative positioning strategy, use `top: visualViewport.offsetTop + visualViewport.height - toolbarHeight` with a CSS `transform: translateY(-100%)` instead of relying on `bottom`. This framing is more explicit about what is being measured.

**Warning signs:**
- After dismissing the keyboard on iOS 26 beta, the FocusToolbar appears in the middle of the screen.
- The bug is not reproducible in Chrome DevTools device emulation — requires a physical device running iOS 26 beta.
- The existing `computeKeyboardOffset` unit test will pass (the function is correct) but the integration will be wrong.

**Phase to address:** Phase 1 (Mobile Layout) — add the defensive clamp as a precaution; the iOS 26 fix can be validated when iOS 26 stable ships.

---

### Pitfall 7: PWA service worker caches API responses, serving stale data after server restarts

**What goes wrong:**
Adding `vite-plugin-pwa` (or any service worker) with default workbox configuration causes API responses (`/api/bullets`, `/api/documents`, etc.) to be cached by the service worker using a `cache-first` or `stale-while-revalidate` strategy. After the server restarts, deploys new code, or runs migrations, users with the installed PWA continue seeing old bullet data from the cache. On iOS Safari in standalone (home screen) mode, service workers can remain active for days, making staleness persistent and hard to reproduce.

**Why it happens:**
PWA workbox plugins default to caching all fetch responses for offline support. The project explicitly deferred offline mode (PROJECT.md: "Offline mode — requires service worker complexity, defer"). The default config does not distinguish between static assets (which should be cached) and API routes (which must not be).

**How to avoid:**
For this milestone (PWA manifest for home screen installation, no offline support), configure the service worker to be minimal:
1. Cache only static assets: JS/CSS bundles, icons, fonts — using `cache-first`.
2. All `/api/*` routes: `NetworkOnly` — no caching whatsoever.
3. In `vite-plugin-pwa` config, add an explicit `runtimeCaching` entry: `{ urlPattern: /\/api\/.*/, handler: 'NetworkOnly' }`.

Alternatively, ship only a `manifest.webmanifest` with no service worker at all. Browsers (including Safari and Chrome) allow home screen installation from a manifest without a service worker, though Chrome's `beforeinstallprompt` event requires a service worker. If the install prompt is not a hard requirement, skip the service worker entirely for this milestone.

**Warning signs:**
- After deploying updated bullet logic, some users still see old data — especially users who installed the app to the home screen.
- Hard refresh or clearing site data resolves the problem (confirming service worker cache is the cause).
- iOS Safari installed PWAs are the most affected — they maintain service worker registrations aggressively.

**Phase to address:** Phase 3 (PWA Manifest) — include explicit `NetworkOnly` for `/api/*` as a phase entry criterion; verify with Network tab in an installed PWA session.

---

### Pitfall 8: Ctrl+K quick-open conflicts with existing global keyboard shortcuts

**What goes wrong:**
The existing `useGlobalKeyboard` hook in `useUndo.ts` owns `Ctrl+F` (search modal), `Ctrl+E` (sidebar toggle), `Ctrl+Z` (undo), `Ctrl+Y` (redo), and `Ctrl+*` (bookmarks) — all registered on `window`. If `Ctrl+K` for the quick-open palette is added via a separate `window.addEventListener('keydown', ...)` inside the palette component or a separate hook, there are now two independent listeners on `window`. Event listener firing order is not guaranteed across component mount/unmount cycles. On Firefox, `Ctrl+K` is a native browser shortcut that focuses the URL/search bar; the app handler must call `e.preventDefault()` reliably or the browser intercepts the keystroke first.

**Why it happens:**
The natural instinct is to add a keyboard listener inside the new quick-open component for self-containment. But global shortcuts do not belong to individual components — they belong to a centralized handler.

**How to avoid:**
Add `Ctrl+K` handling directly to `useGlobalKeyboard` in `useUndo.ts`, following the existing pattern. Add `setQuickOpenOpen(true)` as a new branch in the same `onKeyDown` function. Do NOT create a separate `window.addEventListener` for this shortcut. The quick-open modal's own `useEffect` should listen for `Escape` only (to close itself), scoped to the component's lifecycle — never on `window` globally. Note: unlike `Ctrl+Z` (which delegates to `BulletContent` when a contenteditable has focus), `Ctrl+K` should open the quick-open palette regardless of focus — remove the `isContentEditable` guard for this key.

**Warning signs:**
- Pressing `Ctrl+K` sometimes opens the search modal instead of the quick-open palette (both handlers fired).
- On Firefox, `Ctrl+K` focuses the browser URL bar instead of opening the palette (missing `preventDefault()`).
- Pressing `Escape` closes an unrelated modal unexpectedly (a global Escape listener in the wrong scope).

**Phase to address:** Phase 4 (Quick-Open Palette) — extend `useGlobalKeyboard` rather than adding a new listener.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Keep inline style colors during dark mode | Faster initial ship | Every inline color becomes a manual override; dark mode is incomplete and inconsistent | Never — inline colors and CSS-variable theming are fundamentally incompatible |
| Use only `prefers-color-scheme` media query (no JS toggle) | Zero JS, automatic | User cannot override system preference; acceptable for this milestone scope | Acceptable for v1.1 (system-preference only per PROJECT.md) |
| Add service worker with default workbox config | PWA install prompt works immediately | API responses cached; stale data in production after any deploy | Never for an app with mutable server-side data |
| Use `100vh` instead of `100dvh` for sidebar | Works on desktop | Bottom clipped on iOS Safari — affects every iPhone user | Never for full-height mobile overlays |
| Add global `keydown` listener per new feature | Simple, self-contained | Multiple listeners, unpredictable order, double-fires | Never for global shortcuts — always extend the centralized hook in `useUndo.ts` |
| Import all Lucide icons (`import * from 'lucide-react'`) | Convenient during development | Adds 200KB+ to bundle; slower FCP on mobile | Never in production — use named imports only |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| dnd-kit PointerSensor on mobile | `distance: 5` activation intercepts horizontal swipes | Use `delay: 250, tolerance: 5` or restrict drag to a handle element with `touch-action: none` |
| iOS visualViewport API | Trust `vv.offsetTop` as always correct | Clamp computed offset; add defensive fallback for iOS 26 regression where `offsetTop` does not reset after keyboard dismiss |
| `vite-plugin-pwa` workbox | Default config caches all routes including `/api/*` | Explicitly configure `NetworkOnly` for all `/api/*` in `runtimeCaching` |
| Dark mode initialization | Apply `data-theme` in React `useEffect` | Apply `data-theme` synchronously via inline `<script>` in `index.html` before the React bundle loads |
| Lucide React | `import { X, Plus, ... }` from wildcard | Individual named imports only — required for Vite tree-shaking to eliminate unused icons |
| cmdk (command palette library) | Register its own `Ctrl+K` `window` listener | Wire open/close state through `useGlobalKeyboard`; use cmdk only for the UI/filtering behavior, not for shortcut registration |
| iOS Safari contenteditable | Small font sizes (< 16px) in design mockup | Apply `font-size: max(16px, 1em)` globally to all `[contenteditable]` and `<input>` elements |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Quick-open palette queries all bullets on every keystroke | Visible lag on documents with 200+ bullets | Debounce input by 150ms; fuzzy-match against a pre-fetched document list (title + ID only) rather than all bullets | Documents with 200+ bullets visible |
| Icon library wildcard import | 200KB+ bundle size increase; slower FCP on mobile | Named imports only; verify with `npx vite-bundle-visualizer` after adding icons | Immediately on first production build |
| Dark mode CSS custom properties re-evaluated on every element | Sluggish theme-switch animation on low-end Android | Scope all tokens to `:root` or `[data-theme]` on `<html>` only; avoid per-component CSS variable overrides | Low-end Android devices on theme toggle |
| Service worker update cycle delays new code reaching users | Users run stale app code after a deploy | Set `skipWaiting: true` in workbox config; show a "New version available — reload" banner when a new service worker is detected | Every deploy to an installed PWA |
| FocusToolbar re-renders on every `visualViewport` resize event | 60 re-renders per second during keyboard animation | Acceptable — handler sets only one integer state value; React batches this efficiently. Not a concern at this app's scale | Not a concern |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Service worker intercepts authenticated API calls and caches 401/403 responses | Logged-out users see cached private data from a previous session | Configure `NetworkOnly` for all `/api/*` routes; never cache auth-sensitive responses |
| PWA `start_url` without explicit `scope` | Installed PWA navigates to unintended paths on the same origin | Set `scope: "/"` and `start_url: "/"` explicitly in the manifest |
| Quick-open palette exposes document titles without auth check | A cached document title from a previous session could be visible before token validation | Initialize quick-open document list from React Query (which requires a valid token) — do not pre-populate from `localStorage` |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Hamburger sidebar does not close on outside tap | Users tap the document area and feel trapped inside the sidebar | Render a transparent backdrop element behind the sidebar; close on `pointerdown` on the backdrop. Use `pointer-events: none` on the backdrop when sidebar is closed so it does not swallow document interaction |
| Swipe gesture has no visual affordance | Users discover swipe by accident; swipe threshold feels arbitrary | Show a color-coded background (green on right, red on left) that grows as the swipe progresses; spring-back animation if threshold not reached |
| Quick-open opens with empty state, no placeholder content | Users do not know what to type | Show the 5 most recently accessed documents immediately on open, before any query is typed |
| Dark mode toggle absent — system preference only | Users in bright outdoor environments with dark OS theme have no override | This is acceptable for v1.1 per PROJECT.md scope; document as a known limitation in release notes |
| Hamburger icon tap target too small on mobile | Mis-taps; frustrating UX | Minimum 44x44px tap target per Apple HIG; ensure the hamburger button meets this minimum |
| Icon refresh removes recognizable emoji icons without adequate icon labels | Users lose muscle memory for FocusToolbar buttons | Add `aria-label` and `title` to every Lucide-icon button; consider keeping text labels for critical actions (indent, outdent, delete) |

---

## "Looks Done But Isn't" Checklist

- [ ] **Dark mode:** FocusToolbar is readable in dark mode — no white background, no invisible icons. Grep for `style=\{\{.*#` in all TSX files should return zero hardcoded hex colors after the migration.
- [ ] **Dark mode:** Browser scrollbars, `<select>` elements, and native `<input>` borders respond to dark theme. Requires `color-scheme: dark` set on `:root` in addition to `data-theme` CSS variables.
- [ ] **Dark mode:** No white flash on hard refresh with OS set to dark mode — throttle to Slow 3G and hard-refresh to verify the inline `<script>` fires before the React bundle.
- [ ] **Mobile layout:** Sidebar bottom is not clipped on iPhone Safari — test on a physical device, not Chrome DevTools emulation.
- [ ] **Mobile layout:** FocusToolbar returns to the screen bottom after keyboard is dismissed — no mid-screen floating on iOS 26 beta.
- [ ] **Mobile layout:** Tapping any bullet on iPhone does not zoom the viewport — computed font-size must be ≥ 16px for all `contenteditable` elements.
- [ ] **Drag-and-drop after gesture polish:** Slow vertical drag activates drag-and-drop; fast horizontal swipe triggers complete/delete; no crossover between the two on a physical device.
- [ ] **PWA:** After server restart, a user with the app installed on home screen sees fresh data (not cached API responses) — verify with the Network tab in an installed-mode session.
- [ ] **PWA:** Lighthouse PWA audit passes — specifically the installability and manifest checks.
- [ ] **Quick-open:** `Ctrl+K` inside a focused contenteditable bullet opens the palette (not blocked by the `isContentEditable` guard in `useGlobalKeyboard`).
- [ ] **Quick-open:** `Ctrl+F` still opens the search modal (not hijacked by the quick-open handler).
- [ ] **Icon migration:** All emoji-based icons in `FocusToolbar.tsx` (currently `&#128206;`, `&#128172;`, `&#128278;`, etc.) are replaced with Lucide icon components — emoji render at system font size and ignore CSS color tokens.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Dark mode FOUC in production | LOW | Add the inline `<script>` to `client/index.html`, deploy; single-file change, no architectural rework |
| PWA caching stale API data | MEDIUM | Update service worker config with `NetworkOnly` for `/api/*`, bump `precacheVersion` to evict old caches, redeploy; users may need to wait up to 24h for old service worker to expire, or can manually clear site data |
| Swipe/drag conflict breaks both gestures | HIGH | Re-architect sensor config in `BulletTree.tsx` (switch to delay-based activation), re-test all gesture paths on physical device; expect 1-2 days of work |
| iOS input zoom shifts layout | LOW | Single CSS rule addition to `index.css`; deploy immediately; no rebuild needed for Docker image |
| Ctrl+K conflict with existing shortcut | LOW | Add to `useGlobalKeyboard` in `useUndo.ts` — one function change, 5 lines |
| FocusToolbar invisible in dark mode | MEDIUM | Color-token migration of all inline styles in `FocusToolbar.tsx` — approximately 2-4 hours, then re-test dark mode on all toolbar states |
| Service worker update not reaching users | LOW | Add `skipWaiting: true` + a reload prompt to the service worker registration code; deploy |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| dnd-kit sensor fires on horizontal swipe | Phase 1: Mobile Layout (swipe polish) | Fast swipe right = complete; slow vertical press = drag; no crossover on physical iPhone |
| iOS input auto-zoom on contenteditable | Phase 1: Mobile Layout (font pairing) | Tap any bullet on iPhone — no viewport zoom; computed font-size ≥ 16px |
| iOS 100dvh sidebar clip | Phase 1: Mobile Layout (hamburger) | Sidebar bottom visible on iPhone Safari; `height: 100dvh` in CSS |
| FocusToolbar safe-area on notch | Phase 1: Mobile Layout | Toolbar does not overlap home indicator on iPhone X+ |
| FocusToolbar mid-screen on iOS 26 keyboard dismiss | Phase 1: Mobile Layout | Defensive clamp in `computeKeyboardOffset`; validate on iOS 26 device when available |
| FocusToolbar invisible in dark mode (inline styles) | Phase 2: Dark Mode (first task: color token migration) | Zero hardcoded hex colors in TSX files; toolbar visible and correct-contrast in dark mode |
| Dark mode FOUC on page load | Phase 2: Dark Mode (first task: inline script in index.html) | Hard refresh with dark OS; no white flash even on Slow 3G throttle |
| PWA caching stale API responses | Phase 3: PWA Manifest | Network tab in installed PWA; `/api/*` shows 200 from server, not `(from service worker)` |
| PWA service worker caches auth errors | Phase 3: PWA Manifest | Log out, open installed PWA; no cached private data visible |
| Ctrl+K conflict with Ctrl+F | Phase 4: Quick-Open | Press Ctrl+K → quick-open opens; press Ctrl+F → search modal opens; never both simultaneously |
| Quick-open blocked in contenteditable focus | Phase 4: Quick-Open | Press Ctrl+K while editing a bullet → quick-open opens (not blocked by `isContentEditable` guard) |
| Icon emoji size/color in dark mode | Phase 2: Dark Mode (FocusToolbar icon migration) | All toolbar icons are Lucide SVG components; no emoji characters in rendered output |

---

## Sources

- dnd-kit PointerSensor mobile conflicts: [GitHub issue #435](https://github.com/clauderic/dnd-kit/issues/435), [dnd-kit Sensors documentation](https://docs.dndkit.com/api-documentation/sensors), [iOS haptic touch drag break #791](https://github.com/clauderic/dnd-kit/issues/791)
- iOS Safari 100vh: [DEV Community — 100vh problem with iOS Safari](https://dev.to/maciejtrzcinski/100vh-problem-with-ios-safari-3ge9), [Tailwind dvh discussion](https://github.com/tailwindlabs/tailwindcss/discussions/4515)
- iOS 26 visualViewport regression: [Apple Developer Forums thread #800125](https://developer.apple.com/forums/thread/800125), [WebKit bug #237851](https://bugs.webkit.org/show_bug.cgi?id=237851), [iifx.dev debugging iOS 26 fixed positioning](https://iifx.dev/en/articles/460201403/debugging-ios-26-how-to-correct-fixed-positioning-post-keyboard-interaction)
- iOS input zoom: [CSS-Tricks — 16px or Larger Text Prevents iOS Form Zoom](https://css-tricks.com/16px-or-larger-text-prevents-ios-form-zoom/), [Defensive CSS — Input zoom on iOS Safari](https://defensivecss.dev/tip/input-zoom-safari/)
- Dark mode FOUC: [Not A Number — Fixing Dark Mode Flickering](https://notanumber.in/blog/fixing-react-dark-mode-flickering), [Josh W. Comeau — The Quest for Perfect Dark Mode](https://www.joshwcomeau.com/react/dark-mode/)
- PWA caching pitfalls: [Infinity Interactive — Taming PWA Cache Behavior](https://iinteractive.com/resources/blog/taming-pwa-cache-behavior), [MDN — Caching in PWAs](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Caching)
- PWA manifest requirements: [web.dev — Install criteria](https://web.dev/articles/install-criteria), [Chrome Developers — installable-manifest Lighthouse check](https://developer.chrome.com/docs/lighthouse/pwa/installable-manifest)
- Command palette keyboard conflicts: [GitHub community discussion #15255](https://github.com/orgs/community/discussions/15255)
- Codebase inspection (HIGH confidence — direct source): `client/src/components/DocumentView/gestures.ts`, `FocusToolbar.tsx` (lines 148-165, inline styles), `BulletTree.tsx` (lines 86-88, sensor config), `useUndo.ts` (lines 24-70, global keyboard handler), `SearchModal.tsx`, `index.css`

---
*Pitfalls research for: v1.1 Mobile and UI Polish milestone — adding dark mode, hamburger layout, PWA manifest, and quick-open palette to an existing React outliner with gestures and drag-and-drop*
*Researched: 2026-03-10*
