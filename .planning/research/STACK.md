# Stack Research

**Domain:** Self-hosted multi-user outliner / PKM web app (Dynalist/Workflowy clone)
**Researched:** 2026-03-09 (v1.0) | Updated: 2026-03-10 (v1.1 additions)
**Confidence:** HIGH (all new package versions verified against npm registry 2026-03-10)

---

## v1.1 Additions: Mobile & UI Polish

This section documents the NEW libraries needed for the v1.1 milestone only.
The v1.0 foundation stack (Express, Drizzle, React, TanStack Query, Zustand, dnd-kit) is validated and unchanged.

### What Already Exists (Do Not Re-Add)

- React 19.2.0 + Vite 7.3.1 + TypeScript 5.9.3
- @tanstack/react-query 5.x, zustand 5.x, react-router-dom 6.x
- @dnd-kit/core 6.x + @dnd-kit/sortable 8.x (drag-and-drop)
- dompurify, marked, pdfjs-dist
- Custom `gestures.ts` — pure-function swipe handlers, no animation library
- **No icon library** — Unicode characters used today (▶, ▾, ×, etc.)
- **No dark mode** — single light theme, system font stack in `index.css`
- **No font library** — `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`
- **No PWA manifest**
- **No command palette**

### New Technologies for v1.1

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| lucide-react | ^0.577.0 | Replace Unicode icon chars with SVG components | Tree-shakable by default — only imported icons land in the bundle; 1,400+ icons; MIT; consistent stroke-based style; each icon is a typed React component (`<ChevronRight size={16} />`); largest React icon library by npm dependents (10,675+ packages) in 2025 |
| CSS custom properties (built-in) | n/a | Dark mode token system | Zero dependency; define `--bg-primary`, `--text-primary`, etc. on `:root`, override inside `@media (prefers-color-scheme: dark)`; add `color-scheme: light dark` on `<html>` so browser chrome (scrollbars, form inputs) also themes; WCAG AA requires 4.5:1 contrast ratio for normal text — achievable with CSS tokens |
| vite-plugin-pwa | ^1.2.0 | Generate PWA manifest + optional service worker | Zero-config Vite plugin; v1.0.1+ required for Vite 7 (project uses Vite 7.3.1); v1.2.0 is current; generates `manifest.webmanifest`, injects `<link rel="manifest">`, handles icon array; Workbox under the hood for optional precaching |
| cmdk | ^1.1.1 | Quick-open Ctrl+K command palette | Headless and unstyled — styles are 100% yours, matches project's existing plain-CSS approach; built-in fuzzy search via command-score library; ARIA-compliant out of the box (role="combobox", aria-activedescendant, focus trap); React 19 compatible; used by Vercel, Linear, Raycast; actively maintained (kbar alternative last updated 2022) |
| @fontsource-variable/inter | ^5.2.8 | Body/UI font, self-hosted | Variable font = single file covers all weights (400–900); self-hosting eliminates Google Fonts network request — correct for a privacy-first self-hosted app; Fontsource recommends variable over static packages for multi-weight use |
| @fontsource-variable/jetbrains-mono | ^5.2.8 | Monospace font for code/tags/chips | Self-hosted variable font; single file for all weights; used for inline code spans, tag chips (`#tag`), and `@mention` chips to distinguish semantic content visually |

### Optional: Swipe Animation Polish

| Library | Version | Purpose | When to Add |
|---------|---------|---------|-------------|
| motion | ^12.35.2 | Spring-physics swipe animations | Only add if CSS `transition` + `transform` is insufficient for iOS-quality swipe-reveal feel; adds ~35KB gzipped; `motion` is the rebranded `framer-motion` package (same code, same maintainer); React 19 compatible |

Recommendation: Attempt CSS-only swipe polish first. The existing `gestures.ts` already tracks touch delta — translating that to CSS `transform: translateX()` with `transition` covers 80% of the feel. Add `motion` only if spring-back physics are required after testing.

---

## Implementation Details

### Dark Mode Strategy

Use CSS custom properties on `:root` with a `@media (prefers-color-scheme: dark)` override block. This requires no JavaScript, no library, and no user toggle (which is out of scope for v1.1).

```css
/* index.css — root tokens */
:root {
  color-scheme: light dark; /* tells browser chrome to theme itself */
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f5;
  --text-primary: #0d0d0d;
  --text-secondary: #555555;
  --border: #e0e0e0;
  --accent: #2563eb;
  /* ... */
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg-primary: #0d0d0d;
    --bg-secondary: #1a1a1a;
    --text-primary: #f0f0f0;
    --text-secondary: #a0a0a0;
    --border: #333333;
    --accent: #60a5fa;
  }
}
```

WCAG AA minimums: 4.5:1 for normal text, 3:1 for large text (18px+ or 14px+ bold). `#f0f0f0` on `#0d0d0d` is approximately 16.5:1 — comfortably AA and AAA.

Note: The CSS `light-dark()` function (87% browser support, May 2024+) is viable for progressive enhancement but not the primary mechanism. The `@media` approach has universal browser support.

### Icon Migration Pattern

Replace Unicode chars with Lucide components at the call site:

| Current | Replace With |
|---------|-------------|
| `▶` (collapsed) | `<ChevronRight size={14} strokeWidth={2} />` |
| `▾` (expanded) | `<ChevronDown size={14} strokeWidth={2} />` |
| `×` / close | `<X size={16} />` |
| `...` / menu | `<MoreHorizontal size={16} />` |
| `+` / new | `<Plus size={16} />` |
| hamburger | `<Menu size={20} />` |
| search | `<Search size={16} />` |
| bookmark | `<Bookmark size={16} />` / `<BookmarkCheck size={16} />` |
| tag | `<Tag size={14} />` |
| attach | `<Paperclip size={16} />` |
| comment | `<MessageSquare size={16} />` |

Import only what you use — Lucide is tree-shaken at import:
```typescript
import { ChevronRight, ChevronDown, X } from 'lucide-react';
```

### Font Loading Pattern

Import variable fonts at the top of `main.tsx` (or `index.css`). Vite bundles the CSS; the woff2 files are served from the same origin.

```typescript
// main.tsx
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
```

```css
/* index.css */
body {
  font-family: 'Inter Variable', -apple-system, BlinkMacSystemFont, sans-serif;
}

code, .tag-chip, .mention-chip {
  font-family: 'JetBrains Mono Variable', 'Courier New', monospace;
}
```

The `-apple-system` fallback chain remains — fonts load progressively and the fallback covers the flash before fonts render.

### PWA Config Pattern

Add to `vite.config.ts`. For v1.1 scope (manifest + home screen install only, no offline caching):

```typescript
import { VitePWA } from 'vite-plugin-pwa';

plugins: [
  react(),
  VitePWA({
    registerType: 'autoUpdate',
    workbox: {
      globPatterns: [], // no precaching — offline is out of scope
    },
    manifest: {
      name: 'Notes',
      short_name: 'Notes',
      description: 'Personal outliner',
      theme_color: '#2563eb',
      background_color: '#ffffff',
      display: 'standalone',
      start_url: '/',
      icons: [
        { src: '/pwa-192.png', sizes: '192x192', type: 'image/png' },
        { src: '/pwa-512.png', sizes: '512x512', type: 'image/png' },
        { src: '/pwa-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    },
  }),
]
```

Two PNG icons (192×192 and 512×512) must be placed in `client/public/`. Generate from a single source SVG.

### cmdk Integration Pattern

cmdk is fully unstyled. Render it as a modal overlay triggered by `Ctrl+K` / `Cmd+K`:

```typescript
import { Command } from 'cmdk';

// Wrap in a dialog/overlay with backdrop
// Command.Dialog or Command + custom modal shell both work
// Command.Input — the search field (fuzzy filtering is automatic)
// Command.List + Command.Group + Command.Item — the results
// Command.Empty — shown when no results match
```

Key wiring: listen for `Ctrl+K` at the document level (avoid conflict with browser default — `Ctrl+K` focuses address bar in Firefox; use `e.preventDefault()` to override). Close on `Escape` (cmdk handles this).

---

## Installation (v1.1 Additions)

Run from `client/` directory:

```bash
# Icons
npm install lucide-react

# Fonts (self-hosted variable)
npm install @fontsource-variable/inter @fontsource-variable/jetbrains-mono

# Command palette
npm install cmdk

# PWA plugin (dev/build time only)
npm install -D vite-plugin-pwa

# Animation — defer until swipe polish phase; add only if CSS transitions are insufficient
npm install motion
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| lucide-react | @phosphor-icons/react | Prefer Phosphor if per-icon weight variants (thin/light/regular/bold/fill/duotone) are needed; heavier package; no strong reason to diverge from Lucide for an outliner's functional icons |
| lucide-react | @heroicons/react | Heroicons has only ~292 icons vs Lucide's 1,400+; coupled to Tailwind's design language; insufficient icon range for this app |
| CSS custom properties | Tailwind `dark:` variant | Only if Tailwind already in the project — it is not; adding Tailwind solely for dark mode is unjustified complexity |
| CSS custom properties | next-themes | Designed for Next.js; not usable in Vite projects |
| CSS custom properties | styled-components ThemeProvider | CSS-in-JS adds a runtime dependency; CSS variables handle this natively with zero overhead |
| @fontsource-variable/* | Google Fonts CDN `<link>` | Google Fonts is simpler but introduces a third-party network request — wrong for a privacy-first self-hosted app where all assets should be self-served |
| @fontsource-variable/* | @fontsource/* (static) | Static packages ship separate files per weight (400, 500, 600, 700…); variable package is a single file covering the full weight range — smaller total payload |
| cmdk | kbar | kbar provides a batteries-included action registry with breadcrumb navigation; last published 2022 (maintenance concern); cmdk is actively maintained and headless is exactly what this project needs |
| cmdk | Custom implementation | Not worth the accessibility engineering; cmdk's ARIA implementation (combobox role, focus trap, activedescendant) would need to be re-implemented from scratch |
| vite-plugin-pwa | Manual `manifest.json` in `public/` | Manual manifest works for installability; use it only if the 10-line vite.config.ts change is somehow undesirable; vite-plugin-pwa also handles icon injection and future service worker |
| CSS transitions | motion (Framer Motion) | Prefer CSS first; motion only justified if spring-physics drag-while-animate is required |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| react-icons | Bundles entire icon libraries unless using deep path imports (`react-icons/fi/...`); easy footgun; no consistent TypeScript prop types | lucide-react (tree-shaken at named import) |
| Google Fonts `<link>` tag | Third-party DNS + TLS handshake per page load; Google can log font requests — violates privacy-first self-hosted design philosophy | @fontsource-variable/* |
| @fontsource/inter (non-variable) | Ships one CSS+woff2 per weight; 4+ weights = 4+ files vs one variable file | @fontsource-variable/inter |
| next-themes | Next.js specific; will not work in Vite | CSS `@media (prefers-color-scheme)` + optional Zustand toggle |
| kbar | Last meaningful update was 2022; maintenance status unclear; cmdk is the active equivalent | cmdk |
| framer-motion (old package name) | Rebranded to `motion`; the old `framer-motion` npm name still works but points to the same code — use the canonical `motion` package name | motion |

---

## Stack Patterns by Variant

**For dark mode v1.1 (system-only, no toggle):**
- Use `@media (prefers-color-scheme: dark)` on `:root` in `index.css`
- Add `color-scheme: light dark` to `<html>` element
- No JavaScript, no user toggle, no dependency
- Result: app follows OS setting automatically

**For dark mode future (user toggle):**
- Add `data-theme` attribute on `<html>` set by a Zustand store
- CSS: `[data-theme="dark"] { --bg-primary: #0d0d0d; ... }`
- Persist to `localStorage`; initialize from `matchMedia('(prefers-color-scheme: dark)')` if no stored preference
- The `light-dark()` CSS function is an alternative but at 87% support it needs a fallback anyway

**For PWA v1.1 (manifest + install only, no offline):**
- Set `globPatterns: []` in workbox config to skip precaching
- Result: users get "Add to Home Screen" prompt; app still requires network
- Offline caching is explicitly out of scope (see PROJECT.md)

**For icons — migration order:**
- Start with high-frequency components: BulletNode chevrons, Sidebar menu trigger, toolbar buttons
- Replace inline Unicode with Lucide components; keep `size` and `strokeWidth` consistent per component type (toolbar = 16px, primary nav = 20px)

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| vite-plugin-pwa@1.2.0 | vite@7.3.1 | Vite 7 support added in v1.0.1; v1.2.0 is current as of March 2026 |
| vite-plugin-pwa@1.2.0 | @vitejs/plugin-react@5.1.1 | No conflicts; both are Vite 7 ecosystem plugins |
| cmdk@1.1.1 | react@19.2.0 | cmdk 1.x targets React 18+; confirmed compatible with React 19 |
| lucide-react@0.577.0 | react@19.2.0 | Each icon is a forwardRef component; React 19 compatible |
| motion@12.35.2 | react@19.2.0 | Framer Motion 12.x (`motion` package); React 19 compatible |
| @fontsource-variable/*@5.2.8 | vite@7.3.1 | Plain CSS import with woff2 assets; no bundler-specific integration |

---

## Sources

- https://www.npmjs.com/package/lucide-react — version 0.577.0 verified 2026-03-10 (HIGH)
- https://lucide.dev/guide/packages/lucide-react — official Lucide React docs, tree-shaking confirmed (HIGH)
- https://www.npmjs.com/package/vite-plugin-pwa — version 1.2.0 verified 2026-03-10 (HIGH)
- https://github.com/vite-pwa/vite-plugin-pwa/releases — Vite 7 support from v1.0.1 confirmed in release notes (HIGH)
- https://www.npmjs.com/package/cmdk — version 1.1.1 verified 2026-03-10 (HIGH)
- https://www.npmjs.com/package/motion — version 12.35.2 verified 2026-03-10 (HIGH)
- https://www.npmjs.com/package/@fontsource-variable/inter — version 5.2.8 verified 2026-03-10 (HIGH)
- https://www.npmjs.com/package/@fontsource-variable/jetbrains-mono — version 5.2.8 verified 2026-03-10 (HIGH)
- https://fontsource.org/docs/getting-started/variable — variable fonts preferred over static for multi-weight (MEDIUM)
- https://caniuse.com/mdn-css_types_color_light-dark — light-dark() at 87% browser support as of 2025 (HIGH)
- https://developer.mozilla.org/en-US/docs/Web/CSS/color-scheme — color-scheme property for browser chrome theming (HIGH)
- WebSearch: cmdk vs kbar comparison 2025 — kbar maintenance status, cmdk ARIA compliance (MEDIUM)

---

## v1.0 Foundation Stack (Validated, Unchanged)

The original v1.0 stack research below remains valid. No changes to the backend, database, auth, editor, or drag-and-drop layers are needed for v1.1.

---

## Recommended Stack (v1.0 Foundation)

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Node.js | 22 LTS | Backend runtime | LTS through 2027; Express 5 requires ≥ v18; use 22 for longevity |
| Express.js | 5.2.x | HTTP framework | v5 is now stable (April 2025); async error handling built in; no need for express-async-errors wrapper |
| PostgreSQL | 16 or 17 | Primary database | Adjacency list + WITH RECURSIVE is first-class in PG; ltree extension available for path queries |
| Drizzle ORM | 0.40.0 (pinned) | DB access layer | Code-first TypeScript schema; pinned at 0.40.0 — 0.45.x has a broken npm package (missing index.cjs); do not upgrade until resolved |
| React | 19.2.x | Frontend UI | Largest ecosystem; TanStack Query 5.x integrates cleanly; React 19 concurrent features help with complex tree renders |
| Vite | 7.3.x | Frontend build | De facto standard since CRA deprecation; instant HMR |
| TypeScript | 5.9.x | Type safety | Applied to both server and client |

### Auth Libraries

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| passport | 0.7.x | Auth middleware orchestrator | De facto Node.js auth; pluggable strategies |
| passport-local | 1.0.x | Email/password strategy | Standard, stable, battle-tested |
| passport-google-oauth20 | 2.0.x | Google OAuth 2.0 strategy | Official Jared Hanson package; well-maintained |
| passport-jwt | 4.0.x | JWT verification strategy | Verifies Bearer tokens on protected routes |
| jsonwebtoken | 9.0.x | JWT sign/verify | 18M+ weekly downloads; stable |
| bcryptjs | 5.x | Password hashing | Pure JS bcrypt (no native bindings = no compile step in Docker); use cost factor 12 |

### Data Fetching & State

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @tanstack/react-query | 5.x | Server state, mutations, caching | Built-in optimistic updates via onMutate/onError rollback |
| Zustand | 5.x | Local UI state | Lightweight; manages expand/collapse state, focused bullet, undo stack UI state |

### Drag-and-Drop

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @dnd-kit/core | 6.x | Drag-and-drop primitives | Accessible, pointer/touch/keyboard; better mobile support than react-beautiful-dnd (deprecated) |
| @dnd-kit/sortable | 8.x | Sortable lists | SortableContext for bullet reordering within a parent |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| zod | 3.x | Runtime validation | Validate all request bodies; share schemas with client |
| multer | 1.4.x | File upload middleware | Multipart form-data parsing to Docker volume |
| dompurify | 3.x | HTML sanitization | Sanitize markdown output before rendering |
| marked | 17.x | Markdown rendering | Parse bullet content markdown to HTML |

### Key Architecture Decisions (Validated in Production)

| Decision | Outcome |
|----------|---------|
| Adjacency list + FLOAT8 fractional position | Locked in schema — no migrations needed; correct choice |
| Single flat SortableContext over whole tree | Cross-level drag works; nested SortableContexts blocked it |
| AccessToken in React context only (not localStorage) | XSS protection maintained |
| Plain contenteditable per bullet (not ProseMirror) | Tree model conflicts with ProseMirror document model; simpler is better |
| gestures.ts uses closure-based state (not useRef) | Pure functions, unit testable without React |
| Drizzle _journal.json must list ALL migrations | migrate() uses journal for discovery; missing entries = silently skipped SQL |

---

*Stack research for: self-hosted multi-user outliner (Dynalist/Workflowy clone)*
*v1.0 researched: 2026-03-09 | v1.1 additions researched: 2026-03-10*
