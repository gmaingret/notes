# Project Research Summary

**Project:** Notes v1.1 — Mobile & UI Polish
**Domain:** Self-hosted multi-user outliner (Dynalist/Workflowy clone) — mobile experience, dark mode, PWA, quick-open palette
**Researched:** 2026-03-10
**Confidence:** HIGH

## Executive Summary

The v1.1 milestone is a UI polish and mobile hardening layer on top of an already-working v1.0 outliner. The core stack (React 19, Vite 7, Express 5, Drizzle, TanStack Query, Zustand, dnd-kit) is locked and validated in production. The six new feature areas — responsive mobile layout with hamburger sidebar, system dark mode, Lucide icon library, self-hosted font pairing, PWA manifest, and a Ctrl+K quick-open palette — are all well-researched with clear implementation paths. No new architectural patterns are needed; the work is additive CSS refactoring plus two new components (`HamburgerButton` and `QuickOpenPalette`). All five new npm packages (`lucide-react`, `@fontsource-variable/inter`, `@fontsource-variable/jetbrains-mono`, `cmdk`, `vite-plugin-pwa`) have been verified against the npm registry as of 2026-03-10 and confirmed compatible with the existing React 19 / Vite 7 stack.

The recommended approach is to build in strict dependency order: CSS color tokens first (foundation for all other features), then responsive layout (foundation for swipe gesture polish and correct sidebar behavior on mobile), then dark mode component migration (large surface area but mechanical find-and-replace), then the parallel tracks of icons/fonts, PWA manifest, and swipe gesture polish, and finally the quick-open palette (which reuses existing data hooks and the established modal pattern from SearchModal). This order avoids the single most expensive rework scenario: building components with hardcoded inline hex colors before the token system exists, which would require a second migration pass.

The critical risks are all known and avoidable. The most dangerous is dnd-kit's PointerSensor with its low 5px activation distance intercepting horizontal swipe gestures before they reach the custom gesture handler — this must be fixed by switching to a 250ms delay-based activation before any swipe polish work begins, and must be validated on a physical iPhone (Chrome DevTools emulation does not reproduce the conflict). The second highest risk is the dark mode migration: the existing codebase uses inline styles almost exclusively, and CSS variables cannot override inline style attributes. Every color literal must be migrated before the `@media (prefers-color-scheme: dark)` override can take effect. Both risks have specific file locations documented and clear prevention strategies.

## Key Findings

### Recommended Stack

The v1.0 foundation stack is validated and unchanged. For v1.1, five additions are needed in the client only. All package versions were verified against the npm registry on 2026-03-10. Dark mode requires no new library — CSS custom properties with `@media (prefers-color-scheme: dark)` handles it natively with zero dependency and universal browser support. The `motion` (Framer Motion) animation library is explicitly optional and should be deferred until CSS transitions are proven insufficient for swipe snap-back physics on a physical device.

**Core v1.1 technologies:**
- `lucide-react ^0.577.0`: SVG icon components replacing Unicode/emoji — tree-shakable at named import, 1,400+ icons, React 19 compatible
- `@fontsource-variable/inter ^5.2.8`: Self-hosted UI font — variable font (single file for all weights), eliminates Google Fonts third-party network dependency
- `@fontsource-variable/jetbrains-mono ^5.2.8`: Self-hosted monospace font for code/tags — x-height 0.73 matches Inter's 0.72, visually harmonious pairing
- `cmdk ^1.1.1`: Headless command palette — ARIA-compliant (combobox role, focus trap, activedescendant), built-in fuzzy search, React 19 compatible, actively maintained (kbar alternative last updated 2022)
- `vite-plugin-pwa ^1.2.0`: PWA manifest generation — Vite 7 support added in v1.0.1, confirmed compatible with project's Vite 7.3.1
- CSS custom properties (built-in): Dark mode token system — zero dependency, `@media (prefers-color-scheme: dark)` on `:root`, universal browser support
- `motion ^12.35.2` (optional): Spring-physics swipe snap-back — defer until CSS `transition: transform 300ms cubic-bezier(...)` is tested on physical device and found insufficient

**Critical existing constraint:** Drizzle ORM is pinned at 0.40.0 (not 0.45.x — 0.45.x has a broken npm package missing `index.cjs`). This is a v1.0 constraint with no v1.1 impact; do not upgrade.

### Expected Features

**Must have (table stakes — mobile users expect these):**
- Sidebar hidden on mobile (≤768px), full-width content — a visible 240px sidebar on a 375px screen is unusable
- Hamburger button (min 44×44px) in app header to open sidebar — universal drawer disclosure pattern; instinctive
- Sidebar auto-close on outside tap + explicit X close button — both required; tap-outside alone fails discoverability
- System-preference dark mode (`prefers-color-scheme`) — macOS, iOS, Android all have OS dark mode; apps that ignore it feel broken
- WCAG AA contrast in dark mode — text ≥4.5:1 ratio, UI controls ≥3:1 (failing creates an accessibility regression worse than no dark mode)
- Touch targets ≥44×44px on all interactive elements — WCAG 2.5.5 and Apple HIG
- Safe area insets via `env(safe-area-inset-*)` with `viewport-fit=cover` — iPhones clip bottom content under home indicator without this
- PWA installable (Add to Home Screen) — Dynalist and Workflowy both support this; users expect installable tools
- App icon for home screen — 192×192 and 512×512 PNG; blank icon destroys trust when installed

**Should have (differentiators — competitive advantage for v1.1):**
- Quick-open palette (Ctrl+K) — navigation across documents, bullets, and bookmarks; power users navigate 50+ documents; Obsidian, Notesnook, Notion all have this
- Swipe gesture color/icon reveal feedback — bare swipes without visual backing feel broken; Todoist/Things pattern (colored backing layer revealed as user drags)
- Swipe snap-back animation on cancelled swipe — CSS `transition`, no library needed; aborted swipes feel jarring without it
- Lucide icon library replacing Unicode characters — visual coherence; emoji render at system font size and ignore CSS color tokens (dark mode incompatible)
- Inter + JetBrains Mono font pairing — 88/100 pairing score; legible, modern; Inter is the de facto 2025 React UI font
- Ctrl/Cmd+E desktop sidebar toggle — already implemented in `useGlobalKeyboard`; just needs the CSS layout wired to respond to it
- Recent documents shown when palette is opened with no query — reduces keystrokes for the most common case

**Defer to v1.2:**
- Manual dark/light toggle — creates a three-state complexity (system/light/dark) requiring persistence, settings UI, and sync; follow OS only for v1.1
- Full offline mode — explicitly out of scope per PROJECT.md; service worker cache invalidation conflicts with server-side undo/redo model
- Palette action commands — navigation-only is sufficient; VS Code command depth not warranted at this app's scale (~15 real actions)
- Screenshots in PWA manifest — nice for Android richer install dialog; not blocking home screen installation

### Architecture Approach

The codebase uses a flat `src/` component tree with inline styles dominating throughout. Architecture research was conducted by directly reading source files, giving HIGH confidence on all integration points. Two key findings from source inspection: (1) `sidebarOpen` already exists in `uiStore` and `Ctrl+E` is already wired in `useGlobalKeyboard` — the sidebar simply never visually responds because no CSS applies an off-canvas transform based on that state; the layout fix is purely CSS. (2) `SearchModal.tsx` already demonstrates the exact overlay pattern — backdrop, centered box, debounced input, result list — that the `QuickOpenPalette` needs. It is a template, not a competing concern, and the two should remain separate components with different behavior rather than being merged.

**Major components and their v1.1 roles:**
1. `index.css` — expanded from ~13 lines to the CSS token system foundation; all `--color-*` variables and layout classes live here; no component works correctly in dark mode until this exists
2. `AppPage.tsx` — gains CSS layout class + `data-sidebar-open` attribute wiring; mounts `<QuickOpenPalette>`
3. `Sidebar.tsx` — gains CSS class on `<aside>` for off-canvas transform; must remain always-mounted (conditional unmounting evicts React Query caches for DocumentList, TagBrowser — causes flicker and unnecessary refetches)
4. `HamburgerButton` — new, small; calls `setSidebarOpen(true)`; hidden on desktop via CSS media query
5. `QuickOpenPalette.tsx` — new component; fuzzy-filters `useDocuments()` cache client-side for instant document results; reuses `useSearch` hook for bullet content search (same hook SearchModal uses); wired via `quickOpenOpen: boolean` in `uiStore` (not persisted — transient)
6. `FocusToolbar.tsx` — highest-priority target for dark mode migration; densest concentration of hardcoded hex inline styles (`background: '#fff'`, `color: '#444'`, `color: '#e55'`, `borderTop: '1px solid #e5e7eb'`) that will silently fail in dark mode
7. `useGlobalKeyboard` in `useUndo.ts` — gains `Ctrl+K` handler; all global shortcuts must be registered here, never in individual components

**Key patterns to follow:**
- CSS custom properties for theming — not React context, not ThemeProvider, not styled-components
- Off-canvas sidebar via CSS `transform: translateX(-100%)` — sidebar always in DOM, never conditionally unmounted
- Client-side fuzzy filter for quick-open documents (`useDocuments()` cache is warm from session start); server `useSearch` for bullet content (debounced, ≥2 chars)
- All global keyboard shortcuts registered in one centralized `useGlobalKeyboard` handler in `useUndo.ts`

### Critical Pitfalls

1. **FocusToolbar invisible in dark mode (inline styles override CSS variables)** — CSS custom properties cannot override inline `style` attribute values (inline styles have highest cascade specificity). Run a complete migration pass — convert every hardcoded hex color literal in TSX files to CSS class + `var(--color-*)` references — before wiring the `@media (prefers-color-scheme: dark)` override. Grep for `style=\{\{.*#` across all TSX files; result must be zero before dark mode is wired.

2. **dnd-kit PointerSensor intercepts horizontal swipe gestures** — `activationConstraint: { distance: 5 }` in `BulletTree.tsx` fires after any 5px movement, including fast horizontal swipes. Change to `{ delay: 250, tolerance: 5 }` — a 250ms press naturally distinguishes vertical drag-to-reorder from fast horizontal swipe-to-complete/delete. Test on a physical iPhone before closing this pitfall; Chrome DevTools emulation does not reproduce the conflict.

3. **Dark mode FOUC — white flash before React mounts** — React effects fire after the first browser paint; any theme state initialized in `useEffect` produces a visible white flash on hard refresh for users with dark OS preference. Prevention: add a synchronous inline `<script>` block in `client/index.html` before the `<script type="module">` that loads the React bundle; it reads `window.matchMedia('(prefers-color-scheme: dark)').matches` and calls `document.documentElement.setAttribute('data-theme', 'dark')` synchronously before any paint.

4. **iOS Safari 100dvh clips sidebar bottom** — `height: 100vh` on the sidebar overlay or full-height layout elements is calculated as total viewport including the collapsed address bar on iOS Safari. Use `height: 100dvh` (dynamic viewport height, Safari 15.4+) with `height: 100vh` as a fallback. Add `padding-bottom: env(safe-area-inset-bottom)` to the sidebar scroll container for home-indicator clearance on iPhone X+. Test on a physical device — desktop testing shows no problem.

5. **PWA service worker caches `/api/*` responses — stale data after deploys** — default Workbox configuration caches API responses with `cache-first` or `stale-while-revalidate`. After any server restart or deploy, installed PWA users see old bullet data. Configure `runtimeCaching` with `NetworkOnly` for all `/api/*` routes, or skip the service worker entirely (Chrome 135+ allows home screen installation without one — HTTPS + manifest is sufficient).

6. **iOS Safari auto-zoom on `contenteditable` elements with font-size < 16px** — Safari zooms the viewport on focus for any `input` or `contenteditable` with computed font-size below 16px. The new Inter font applied at a design size below 16px will trigger this on every bullet tap. Add `font-size: max(16px, 1em)` globally to all `[contenteditable]` and `<input>` in `index.css`. Cannot be reproduced in DevTools; requires a physical iPhone.

7. **Ctrl+K keyboard shortcut must live in the centralized handler** — adding a separate `window.addEventListener('keydown', ...)` inside the palette component creates a second global listener with unpredictable firing order. On Firefox, `Ctrl+K` is a native browser shortcut (focuses address bar); the app handler must call `e.preventDefault()` reliably. Add `Ctrl+K` directly as a new branch in `useGlobalKeyboard` in `useUndo.ts`, following the same pattern as the existing `Ctrl+F`, `Ctrl+E`, `Ctrl+Z` handlers.

## Implications for Roadmap

Based on the dependency graph documented in ARCHITECTURE.md and the feature prioritization in FEATURES.md, a 4-phase structure is recommended. This matches the build order validated by direct source inspection.

### Phase 1: Mobile Layout Foundation
**Rationale:** Everything in v1.1 depends on CSS tokens existing and the sidebar being correctly responsive. Layout is the frame; colors, icons, and the palette all sit inside it. The dnd-kit swipe/drag conflict must be fixed here before swipe gesture polish begins in Phase 4 — it is a prerequisite, not an afterthought.
**Delivers:** CSS token foundation in `index.css` (light values only — dark values added in Phase 2), responsive sidebar with hamburger open/close, backdrop overlay, off-canvas slide animation (250ms ease-out), sidebar auto-close on outside tap and Escape key, Ctrl/Cmd+E desktop toggle (already wired — just needs CSS), safe area insets, 100dvh fix, iOS font-zoom fix (`font-size: max(16px, 1em)` on `[contenteditable]`), defensive FocusToolbar clamp for iOS 26 visualViewport regression.
**Addresses features:** Sidebar hidden on mobile, hamburger button, auto-close, explicit X button, safe area insets, touch targets ≥44px, Ctrl/Cmd+E toggle.
**Avoids pitfalls:** dnd-kit sensor conflict (change activation constraint first in `BulletTree.tsx`), iOS 100dvh clip, iOS contenteditable auto-zoom, FocusToolbar stuck mid-screen on iOS 26 keyboard dismiss.
**Research flag:** Standard patterns, no additional research needed. All integration points read directly from source with HIGH confidence.

### Phase 2: Dark Mode
**Rationale:** Dark mode is the largest surface-area change in v1.1 — it touches nearly every component file. It must happen as a single coherent pass; partial migration leaves the app broken in dark mode with some elements themed and others displaying hardcoded light colors. Sequenced after layout (Phase 1) to avoid re-converting colors in rearranged layout elements. The FOUC-prevention inline script must be the absolute first step within this phase.
**Delivers:** Complete CSS custom property token system (`--color-*`) with `@media (prefers-color-scheme: dark)` override, FOUC-prevention synchronous inline script in `index.html`, full migration of all inline hex color literals to CSS `var()` references across all component files, WCAG AA contrast compliance verified, `color-scheme: light dark` on `<html>` so browser chrome (scrollbars, form inputs) also themes.
**Uses:** CSS custom properties only — no new libraries.
**Implements:** Token system that swipe backing layer colors (`--color-swipe-delete`, `--color-swipe-complete`) and palette backdrop/surface colors depend on.
**Avoids pitfalls:** FocusToolbar invisible in dark mode (complete inline style migration as the first task of this phase), FOUC on hard refresh (inline script before any other dark mode wiring).
**Research flag:** Standard patterns — CSS custom properties and `prefers-color-scheme` are universal and well-documented. Validate WCAG contrast ratios with a contrast checker tool (e.g., WebAIM Contrast Checker) during implementation for the specific dark palette values.

### Phase 3: Icons, Fonts, and PWA Manifest
**Rationale:** These three features are independent of each other once the token system (Phase 2) exists — icon colors can reference `var(--color-*)` rather than hardcoded hex, fonts are a CSS `font-family` change, and the manifest is static file addition. Grouped into one phase because they are all additive, low-risk, no-logic changes that can be validated in a single deployment. PWA manifest is the highest ratio of perceived user value to implementation effort in the entire milestone.
**Delivers:** Lucide icon library replacing all Unicode characters and emoji in FocusToolbar (11 buttons), Sidebar, DocumentToolbar, and BulletNode; Inter variable font as UI font; JetBrains Mono variable font for inline code and tag chips; PWA manifest (`manifest.json` with 192px + 512px icons, `display: standalone`) enabling "Add to Home Screen" on iOS and Android.
**Uses:** `lucide-react`, `@fontsource-variable/inter`, `@fontsource-variable/jetbrains-mono`, static `manifest.json` in `public/` (preferred over `vite-plugin-pwa` given offline mode is out of scope).
**Avoids pitfalls:** PWA service worker caching API responses (use static manifest without service worker — Chrome 135+ allows installation without one), icon wildcard import bloating bundle (named imports only: `import { Trash2, Check } from 'lucide-react'`).
**Research flag:** Standard patterns for icons and fonts. Verify PWA installability with Lighthouse PWA audit after deployment — confirm install criteria are met on both iOS Safari (manual Add to Home Screen) and Chrome Android (install prompt).

### Phase 4: Swipe Gesture Polish and Quick-Open Palette
**Rationale:** Swipe gesture polish is additive CSS on top of already-working gesture logic (`gestures.ts` threshold callbacks already fire at 40% swipe). The quick-open palette is the most new-logic item in the milestone but reuses existing data hooks. Both are sequenced last because they benefit from stable layout (Phase 1 — swipe gestures should not fire when sidebar overlay is open), dark mode tokens for backing layer colors and palette surface (Phase 2), and Lucide icons for swipe action icons (Phase 3 — trash and checkmark icons in the backing layer).
**Delivers:** Swipe color/icon reveal (red backing for delete-left, green for complete-right), icon fades in after 25–30% threshold, scale-up at commit zone (~50%), row animates out on commit, snap-back with ease-out on cancelled swipe, optional Android haptic feedback via `navigator.vibrate()` (gracefully skipped on iOS Safari which does not support Vibration API). Quick-open palette triggered by Ctrl+K — instant fuzzy document title matching from warm `useDocuments()` cache, bullet content search via existing `/api/search` endpoint (debounced 150ms, ≥2 chars), bookmark matching, keyboard navigation (arrow keys + Enter + Escape), recent documents shown on empty query (last 5–10 opened).
**Uses:** `cmdk` for palette UI rendering and built-in fuzzy search (command-score); existing `useDocuments()`, `useSearch()`, `useBookmarks()` hooks — no new API endpoints.
**Implements:** `QuickOpenPalette.tsx` (new component), `quickOpenOpen: boolean` state in `uiStore`, `Ctrl+K` handler added to `useGlobalKeyboard` in `useUndo.ts`.
**Avoids pitfalls:** Ctrl+K registered in centralized `useGlobalKeyboard` (never inside the palette component or a separate `window.addEventListener`), palette kept separate from `SearchModal` (different UX: instant client-side doc results vs debounced full-text bullet search), `isContentEditable` guard bypassed for `Ctrl+K` so palette opens even when a bullet is being edited.
**Research flag:** Standard patterns throughout. cmdk API is well-documented. No additional research phase needed.

### Phase Ordering Rationale

- CSS tokens must precede all components — components referencing `var(--color-*)` before tokens exist in `index.css` produce `transparent` values silently, not errors.
- Layout must precede swipe gesture polish — swipe gestures should not fire when the sidebar overlay is open, and the layout changes in Phase 1 establish this constraint correctly.
- Dark mode is sequenced before icons and PWA — icon SVG colors must reference `var(--color-*)` tokens (available after Phase 2), and the FOUC-prevention script in `index.html` is cleaner to add before the manifest `<link>` tag is present.
- Icons, fonts, and PWA are parallel-capable once the token system exists; grouping them avoids three separate deployments for low-risk additive changes.
- The quick-open palette is last — it is the only feature with meaningful new component logic and benefits from all prior phases being stable and validated.

### Research Flags

Phases likely needing deeper research during planning:
- None. All four phases have well-documented implementation paths. Architecture research was conducted against actual source files with HIGH confidence on every integration point.

Phases with standard patterns (skip research-phase during roadmap execution):
- **Phase 1 (Mobile Layout):** CSS off-canvas sidebar transform and dnd-kit sensor configuration are documented patterns. All integration points verified in source.
- **Phase 2 (Dark Mode):** CSS custom properties + `prefers-color-scheme` is universal; WCAG contrast ratios are normative standard. The only implementation task is mechanical find-and-replace of inline hex values.
- **Phase 3 (Icons/Fonts/PWA):** npm package installations + static file additions. Lighthouse PWA audit provides automated verification of install criteria.
- **Phase 4 (Swipe/Palette):** cmdk API is simple and well-documented. Palette reuses existing `useDocuments()` and `useSearch()` hooks with no new endpoints.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All 5 new package versions verified against npm registry 2026-03-10; Vite 7 compatibility for `vite-plugin-pwa` confirmed in release notes; React 19 compatibility confirmed for all packages |
| Features | HIGH | Feature list cross-validated against Dynalist, Workflowy, Notion, and Obsidian behavior; WCAG 2.1 contrast ratios are normative standard (1.4.3, 1.4.11, 2.5.5); competitor feature matrix documented |
| Architecture | HIGH | Integration points read directly from actual source files: `AppPage.tsx`, `Sidebar.tsx`, `uiStore.ts`, `useUndo.ts`, `gestures.ts`, `BulletTree.tsx`, `index.css`, `FocusToolbar.tsx`, `SearchModal.tsx` |
| Pitfalls | HIGH | All pitfalls traced to specific files and line numbers in the v1.0 codebase; iOS-specific bugs sourced from Apple Developer Forums (thread #800125) and WebKit bug tracker (bug #237851); dnd-kit conflict sourced from GitHub issues #435 and #791 |

**Overall confidence:** HIGH

### Gaps to Address

- **iOS 26 visualViewport regression (WebKit bug #237851):** The defensive clamp in `computeKeyboardOffset` is documented as the mitigation (return 0 if computed offset exceeds 60% of `window.innerHeight`). Full validation requires a physical device running iOS 26 stable when it ships. Add "FocusToolbar stays at screen bottom after keyboard dismiss on iOS 26" as an acceptance criterion for Phase 1, flagged as requiring future validation.
- **PWA install prompt on Android vs iOS:** iOS Safari does not fire the standard `beforeinstallprompt` event and uses its own "Add to Home Screen" UX flow. A service worker is not required for this flow. Chrome Android's `beforeinstallprompt` (richer install dialog) does require a service worker. If the Android install prompt (not just manual Add to Home Screen) is a hard requirement, a minimal service worker with `NetworkOnly` for `/api/*` is needed. Resolution: the static manifest-only approach (no service worker) is recommended for v1.1; document the Android limitation in release notes.
- **`motion` animation library decision:** Research recommends attempting CSS `transition: transform 300ms cubic-bezier(0.25, 0.46, 0.45, 0.94)` first for swipe snap-back. The decision to add `motion` (+35KB gzipped) should be made during Phase 4 implementation after testing on a physical iOS device — not during planning.
- **Manual dark mode override:** System-preference-only is correct for v1.1 scope per PROJECT.md. The token system is designed to accept a `data-theme="dark"` attribute on `<html>` set by a `uiStore` field — adding the toggle in v1.2 is a one-line state addition plus a settings UI button. Not a gap in architecture; just a deferred feature.

## Sources

### Primary (HIGH confidence)
- Direct source inspection (2026-03-10): `client/src/pages/AppPage.tsx`, `Sidebar.tsx`, `store/uiStore.ts`, `hooks/useUndo.ts`, `gestures.ts`, `BulletTree.tsx`, `index.css`, `FocusToolbar.tsx`, `SearchModal.tsx`, `package.json`
- https://www.npmjs.com/package/lucide-react — version 0.577.0 verified 2026-03-10
- https://www.npmjs.com/package/vite-plugin-pwa — version 1.2.0; Vite 7 support from v1.0.1 confirmed in release notes
- https://www.npmjs.com/package/cmdk — version 1.1.1 verified 2026-03-10; kbar last published 2022
- https://www.npmjs.com/package/@fontsource-variable/inter — version 5.2.8 verified 2026-03-10
- https://www.npmjs.com/package/@fontsource-variable/jetbrains-mono — version 5.2.8 verified 2026-03-10
- https://bugs.webkit.org/show_bug.cgi?id=237851 — iOS 26 visualViewport regression documented
- https://developer.apple.com/forums/thread/800125 — Apple Developer Forums iOS 26 keyboard regression
- https://web.dev/articles/install-criteria — PWA install criteria; Chrome 135+ no service worker required
- WCAG 2.1 SC 1.4.3, 1.4.11, 2.5.5 — contrast and touch target normative requirements
- dnd-kit GitHub issues #435, #791 — PointerSensor mobile touch conflict documented

### Secondary (MEDIUM confidence)
- https://lucide.dev/guide/packages/lucide-react — tree-shaking via named imports confirmed
- https://fontalternatives.com/pairings/inter-and-jetbrains-mono/ — Inter + JetBrains Mono pairing score 88/100; x-height harmony analysis
- https://css-tricks.com/16px-or-larger-text-prevents-ios-form-zoom/ — iOS contenteditable zoom threshold documented
- https://www.joshwcomeau.com/react/dark-mode/ — dark mode FOUC prevention via inline script
- https://notanumber.in/blog/fixing-react-dark-mode-flickering — React SPA dark mode FOUC solution
- Competitor analysis: Dynalist, Workflowy, Notion, Obsidian behavior observed for dark mode, hamburger sidebar, and Ctrl+K palette patterns

### Tertiary (LOW confidence)
- `motion` bundle size estimate (~35KB gzipped) — from library documentation; not independently measured for this specific Vite 7 build configuration
- microfuzz (https://github.com/Nozbe/microfuzz) — lightweight alternative to Fuse.js for client-side fuzzy matching; viable but cmdk's built-in command-score is sufficient and reduces dependencies

---
*Research completed: 2026-03-10*
*Ready for roadmap: yes*
