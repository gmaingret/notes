# Feature Research

**Domain:** Mobile UX polish + dark mode + quick-open palette (v1.1 milestone)
**Researched:** 2026-03-10
**Confidence:** HIGH (hamburger UX, dark mode WCAG, PWA manifest fields), MEDIUM (gesture feedback patterns, palette scope conventions)

---

## Scope Note

This file covers only the **six new feature areas for v1.1**. The existing v1.0 core (infinite outliner, bullet CRUD, swipe gestures, search, tags, undo/redo, bookmarks, attachments, comments) is already shipped and is not re-researched here. The existing FEATURES.md content from 2026-03-09 remains the authoritative record for v1.0 table stakes.

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist in any modern mobile web app. Missing these means the app feels unfinished on mobile and in dark mode.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Sidebar hidden on mobile, full-width content | Every mobile app does this; visible sidebar on 375px screen is unusable | MEDIUM | CSS media query at ≤768px breakpoint; sidebar becomes a drawer overlay |
| Hamburger button to open sidebar | Universal drawer disclosure pattern; users look for this instinctively | LOW | Fixed top-left position; min 44×44px touch target (WCAG 2.5.5) |
| Sidebar auto-closes on outside tap | Users tap content to dismiss overlays — muscle memory from every mobile OS | LOW | Backdrop overlay with pointerdown listener; must not interfere with bullet drag |
| Sidebar visible close button (X) | Outside-tap alone fails discoverability; users look for explicit close affordance | LOW | Top-right of drawer panel; do not rely only on backdrop dismiss |
| System-preference dark mode | macOS/iOS/Android all have OS dark mode; users expect apps to follow it automatically | MEDIUM | `@media (prefers-color-scheme: dark)` + CSS custom property token system |
| WCAG AA contrast in dark mode | A dark mode that fails contrast creates an accessibility regression worse than no dark mode | MEDIUM | Text ≥4.5:1; large text ≥3:1; UI controls/icons ≥3:1 (WCAG 1.4.3 and 1.4.11) |
| Touch targets ≥44×44px on all interactive elements | WCAG 2.5.5 + Apple HIG; small targets = fat-finger errors and user frustration | LOW | Applies to: hamburger, sidebar items, bullet action icons, palette results |
| Safe area insets respected (notch + home indicator) | iPhones with home indicator clip bottom content without CSS env() padding | LOW | Requires `viewport-fit=cover` in meta viewport + `padding-bottom: env(safe-area-inset-bottom)` on fixed elements |
| PWA installable (Add to Home Screen prompt) | Users expect "app-like" tools to be installable; Dynalist and Workflowy both support this | LOW | Manifest with name, short_name, icons (192px + 512px), start_url, display: standalone; HTTPS already met |
| App icon for home screen | Blank or generic icon destroys trust when installed | LOW | 192×192 PNG + 512×512 PNG minimum; maskable variant for Android adaptive icons |

### Differentiators (Competitive Advantage)

Features that go beyond expected — these make the mobile experience genuinely good rather than merely functional, or add power-user capabilities not present in Dynalist/Workflowy.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Quick-open palette (Ctrl+K) — documents + bullets | Power users navigate 50+ documents by keyboard; faster than sidebar scroll; Obsidian, Notesnook, Notion all have this | MEDIUM | Fuzzy search over document titles + bullet text; keyboard navigation (arrows + Enter + Escape) |
| Palette shows recent documents when query is empty | Reduces keystrokes for the most common case: reopening a recent document | LOW | Piggybacks on existing document open tracking; no new persistence needed |
| Swipe gesture color + icon reveal feedback | Bare swipes without color cues feel broken; Todoist/Things show red/green backing with icon appearing as you drag | MEDIUM | Translate bullet row on swipe; reveal colored backing layer (red=delete, green=complete); icon fades in after 30% threshold |
| Swipe snap-back animation on cancelled swipe | Without a spring snap-back, aborted swipes feel jarring | LOW | CSS `transition: transform 300ms cubic-bezier(0.25, 0.46, 0.45, 0.94)` on touch-end when threshold not met |
| Lucide icon library (consistent, tree-shakable) | Coherent icon set elevates perceived quality; Lucide is the de facto standard for React apps in 2025 | LOW | 1000+ icons; import individually for tree-shaking; aria-hidden by default; add aria-label on standalone icon controls |
| Inter + JetBrains Mono font pairing | Legible, modern typography pair; Inter is the de facto UI font; JetBrains Mono for code/inline-code; x-height harmony (0.72 vs 0.73 ratio) | LOW | Load via Google Fonts or self-host; font-display: swap to prevent FOIT; min 16px body, 1.5 line-height |
| Ctrl/Cmd+E desktop sidebar toggle | Keyboard-first users expect sidebar toggle; VS Code pattern is well-known | LOW | Single keydown listener; no conflict with Ctrl+Z/Y/F/B/I/Enter existing shortcuts |
| Haptic feedback at swipe commit threshold | Android: the moment a swipe commits should feel distinct from passive dragging | LOW | `navigator.vibrate(10)` at threshold crossing; check API availability — iOS Safari does NOT support Vibration API |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Manual dark/light mode toggle button | Users want override control | Creates a third UI state (user preference vs OS preference) that must be persisted, synced, and surfaced in settings; doubles theming work; can get permanently out-of-sync with OS | Follow OS preference only via `prefers-color-scheme`; add manual toggle in v1.2 settings if users request it |
| Full offline mode (service worker + cache) | Users want the app to work on planes | Service worker + cache invalidation conflicts with server-side undo/redo model; sync conflicts on reconnect; explicitly out of scope per PROJECT.md | PWA manifest for home screen install without service worker; fast server is the offline mitigation |
| Animated sidebar with spring physics (Framer Motion) | "Premium" feel | Animation libraries like Framer Motion add 30KB+ to bundle; CSS `transition: transform 250ms ease-out` achieves 90% of the feel with zero dependencies | CSS transitions only; no animation library |
| Full command palette (all app actions) | VS Code / Linear pattern | VS Code has hundreds of commands; this app has ~15 real actions; a full command layer adds discovery complexity without proportional value | Scope palette to navigation only (documents + bullets + bookmarks); action shortcuts remain keyboard bindings |
| Per-document custom themes | Power user personalization | Multiplies token system complexity; every new theme must be WCAG-audited; creates visual inconsistency across docs | Single coherent light/dark pair; token system enables future expansion without implementing it now |
| Bottom tab bar replacing hamburger | More thumb-friendly for primary nav | App has one primary workspace (the open document) plus secondary content (sidebar, search); a bottom tab bar implies multiple co-equal top-level sections that don't exist | Hamburger drawer matches Dynalist/Workflowy conventions users already know |
| Animated hamburger icon (lines → X morphing) | Looks polished | CSS-only morphing hamburger animations often have layout reflow issues on older Android; unclear UX benefit over a static hamburger + a separate X inside the drawer | Static hamburger icon that opens drawer; explicit X button inside drawer to close |

---

## Feature Details by Category

### 1. Hamburger Sidebar UX

**Pattern:** Drawer overlay (not push-layout). Document content stays fixed; sidebar slides over it from the left.

**Behavior checklist:**
- Sidebar hidden by default on viewport width ≤768px
- Hamburger button in app header, top-left; min 44×44px
- Tap hamburger → sidebar translates from `translateX(-100%)` to `translateX(0)`; transition 250ms ease-out
- Semi-transparent backdrop covers content behind sidebar (rgba(0,0,0,0.4))
- Tap backdrop → sidebar closes (translateX(-100%)); transition 200ms ease-in
- Tap any document in sidebar → navigate + auto-close sidebar
- Escape key → close sidebar (mirrors standard modal behavior)
- Sidebar contains visible X button top-right (not relying solely on backdrop)
- On desktop (≥768px): sidebar always visible; hamburger hidden; Ctrl/Cmd+E toggles it
- Sidebar width: 280–320px (Notion: 280px, Dynalist: ~240px; 280px is the standard)

**Thumb reach note:** Hamburger at top-left is outside the natural thumb zone on large phones. This is acceptable because it is used at most once per session (switching documents), not on every interaction. High-frequency actions (new bullet, complete, search) should remain in the thumb-friendly bottom 40% of screen.

**Desktop Ctrl/Cmd+E behavior:**
- Toggle sidebar visibility (same behavior as sidebar being collapsed)
- Store collapse state in localStorage so it persists across page refresh
- No conflict with existing shortcuts: Ctrl+E is not currently bound

### 2. Dark Mode Token System

**Required WCAG AA contrast ratios (WCAG 2.1):**
- Normal text on background: ≥4.5:1 (WCAG 1.4.3)
- Large text (≥18pt or 14pt bold) on background: ≥3:1 (WCAG 1.4.3)
- UI controls, borders, icons, focus rings vs adjacent color: ≥3:1 (WCAG 1.4.11)

**Common dark mode contrast failures to avoid:**
- Pure black (#000000) background with white text: harsh, causes halation
- Pure white on dark: eye strain at night
- Low-contrast disabled states (gray-on-gray): fails 3:1 on dark backgrounds
- Focus rings disappearing in dark mode (focus ring color must contrast with dark background)

**Recommended token structure:**
```css
:root {
  /* Backgrounds */
  --color-bg-base: #ffffff;
  --color-bg-surface: #f8f9fa;
  --color-bg-elevated: #ffffff;

  /* Text */
  --color-text-primary: #1a1a1a;
  --color-text-secondary: #6b7280;
  --color-text-muted: #9ca3af;

  /* Interactive */
  --color-accent: #3b82f6;
  --color-accent-hover: #2563eb;

  /* Borders / Dividers */
  --color-border: #e5e7eb;

  /* Status */
  --color-success: #10b981;
  --color-danger: #ef4444;

  /* Focus */
  --color-focus-ring: #3b82f6;

  /* Swipe feedback */
  --color-swipe-delete: #ef4444;
  --color-swipe-complete: #10b981;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-bg-base: #0f1115;
    --color-bg-surface: #1a1d23;
    --color-bg-elevated: #22262e;
    --color-text-primary: #e9ecf1;
    --color-text-secondary: #9ca3af;
    --color-text-muted: #6b7280;
    --color-accent: #58a6ff;
    --color-accent-hover: #79b8ff;
    --color-border: #30363d;
    --color-success: #3fb950;
    --color-danger: #f85149;
    --color-focus-ring: #58a6ff;
    --color-swipe-delete: #f85149;
    --color-swipe-complete: #3fb950;
  }
}
```

**Naming discipline:** tokens describe purpose, not style (`--color-text-primary` not `--color-dark-gray`). This makes future palette changes non-breaking.

**Additional dark mode adjustments:**
- Shadows: reduce opacity in dark mode or eliminate; dark mode shadows look wrong
- Images with white backgrounds: `filter: brightness(0.85)` to reduce glare
- Markdown code blocks: surface token elevates them above base background

### 3. Quick-Open Palette (Ctrl+K)

**User expectations from Obsidian, Notesnook, Notion, Capacities:**

| Behavior | Expected |
|----------|----------|
| Keyboard shortcut | Ctrl+K (Windows/Linux), Cmd+K (Mac); also Cmd+P accepted by power users |
| Trigger from mobile | Search icon tap — Ctrl+K is keyboard-only, mobile needs an icon entry point |
| Opening position | Centered modal overlay with backdrop; 600px wide max; responsive |
| Auto-focus | Input field receives focus immediately on open |
| Results when empty | "Recent documents" list (last 5-10 opened) |
| Results when typing | Fuzzy match: documents by title, bullets by text, bookmarks by name |
| Update speed | Debounced ~150ms; document title matches are instant (client-side); bullet matches need server call |
| Keyboard navigation | ArrowUp/ArrowDown to move focus through results; Enter to select |
| Dismiss | Escape key or click outside backdrop |
| Result groups | Documents / Bullets / Bookmarks (clearly separated, labeled) |
| Fuzzy tolerance | "inb" matches "Inbox"; "mtg nts" matches "Meeting Notes"; partial and out-of-order chars |

**Scope for v1.1 (navigation only — no action commands):**
- Fuzzy search documents by title (client-side, instant)
- Fuzzy search bullets by text (server-side, debounced — reuse existing `/search` endpoint)
- Fuzzy search bookmarks (client-side, instant)
- Show recently opened documents when query is empty
- Keyboard navigation through results

**Scope NOT in v1.1:**
- Action commands ("bold selection", "export document", "create new doc") — these stay as keyboard shortcuts
- Tag navigation via palette
- Date-based search within palette
- Mobile bottom sheet variant

**Fuzzy library options:**
- Fuse.js: 16KB, excellent fuzzy matching, battle-tested, good for document/bookmark titles (client-side)
- microfuzz: ~2KB, faster, from Nozbe (makers of Nozbe productivity app), good for simple string matching
- Recommendation: microfuzz for document title matching (smaller); fall back to server full-text search for bullet content

**Interaction with existing search:** The existing Ctrl+F search searches within the current document only. Ctrl+K palette searches across all documents. These are complementary and should not conflict.

### 4. PWA Manifest — Required Fields

**Minimum for Chrome/Edge install prompt (no service worker required as of Chrome 135+):**

```json
{
  "name": "Notes",
  "short_name": "Notes",
  "description": "Self-hosted infinite outliner for personal knowledge management",
  "start_url": "/",
  "display": "standalone",
  "orientation": "portrait",
  "background_color": "#ffffff",
  "theme_color": "#3b82f6",
  "icons": [
    {
      "src": "/icons/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/icons/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    },
    {
      "src": "/icons/icon-512-maskable.png",
      "sizes": "512x512",
      "type": "image/png",
      "purpose": "maskable"
    }
  ]
}
```

**Additional recommended (Android richer install dialog):**
```json
{
  "screenshots": [
    { "src": "/screenshots/narrow-1.png", "sizes": "390x844", "type": "image/png", "form_factor": "narrow" },
    { "src": "/screenshots/narrow-2.png", "sizes": "390x844", "type": "image/png", "form_factor": "narrow" }
  ]
}
```

**theme_color in dark mode:** The `<meta name="theme-color">` tag supports a `media` attribute:
```html
<meta name="theme-color" content="#ffffff" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#0f1115" media="(prefers-color-scheme: dark)">
```

**HTML link:** `<link rel="manifest" href="/manifest.json">` in the `<head>`.

**Service worker:** NOT needed. Offline mode is explicitly out of scope. Do not add a service worker to meet install criteria — Chrome 135+ no longer requires it.

**HTTPS:** Already satisfied by Nginx + Let's Encrypt.

**Install criteria summary:**
1. Manifest present with required fields (name/short_name, icons 192+512, start_url, display)
2. Served over HTTPS
3. User has interacted with page (one click + 30 seconds minimum — automatic with normal use)

### 5. Swipe Gesture Animation Polish

**Current state:** Swipe right = complete, swipe left = delete, long-press = context menu. Already functional. v1.1 adds visual feedback quality.

**Expected behavior (Todoist/Things/iOS Mail pattern):**
1. User begins swipe → bullet row `translateX()` follows touch delta in real-time (no transition during active touch)
2. Colored backing layer revealed behind the row: red for left (delete), green for right (complete)
3. Icon (trash / checkmark from Lucide) on the backing layer fades in after 25–30% swipe threshold
4. At ~50% threshold: icon scale-up (transform: scale(1.1)) signals "commit zone"
5. On touch end:
   - Threshold met: action executes; row animates out (translateX to ±100% + opacity 0)
   - Threshold not met: row snaps back with ease-out transition (300ms)

**CSS approach (no animation library needed):**
```css
.bullet-row {
  position: relative;
  transition: transform 300ms cubic-bezier(0.25, 0.46, 0.45, 0.94),
              opacity 200ms ease;
}
.bullet-row.is-swiping {
  transition: none; /* disable transition during active touch — follows finger */
}
.swipe-backing-left,
.swipe-backing-right {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  opacity: 0;
  transition: opacity 150ms ease;
}
.swipe-backing-left { background: var(--color-swipe-delete); justify-content: flex-end; padding-right: 20px; }
.swipe-backing-right { background: var(--color-swipe-complete); justify-content: flex-start; padding-left: 20px; }
.bullet-row.swipe-threshold-reached .swipe-backing-left,
.bullet-row.swipe-threshold-reached .swipe-backing-right {
  opacity: 1;
}
```

**Haptic feedback (Android only — iOS Safari does not support Vibration API):**
- Check: `if ('vibrate' in navigator)` before calling
- On threshold cross: `navigator.vibrate(10)` — single short pulse
- On action commit: `navigator.vibrate(20)` — slightly longer pulse
- Do NOT vibrate on every pixel of swipe movement — only at threshold events
- Centralize in a helper: `function haptic(ms) { if ('vibrate' in navigator) navigator.vibrate(ms); }`

### 6. Icon Library and Font Pairing

**Lucide React — recommended icon library:**
- 1,500+ icons; consistent 24×24 design grid; community-maintained fork of Feather Icons
- Tree-shakable (ES modules): `import { Trash2, Check, ChevronRight } from 'lucide-react'` — only imported icons appear in bundle
- First-class TypeScript support
- All icons accept `size`, `color`, `strokeWidth` props and any SVG attribute
- `aria-hidden="true"` by default; add `aria-label` when icon is the sole label for a control
- Weekly npm downloads: 200,000+; 14,000+ GitHub stars (HIGH confidence, current)

**Installation:** `npm install lucide-react`

**Anti-pattern to avoid:** `import * as Icons from 'lucide-react'` — imports all 1,500 icons into bundle. Always use named imports.

**Inter + JetBrains Mono font pairing:**
- Inter: de facto standard for UI text; designed for screen readability; variable font available; x-height 0.72
- JetBrains Mono: designed for code readability; ligatures; x-height 0.73 (matches Inter visually); free + open source
- Pairing score: 88/100 (fontalternatives.com analysis); x-height harmony means same font-size looks visually consistent
- Use Inter for: all UI text, sidebar, bullets, toolbar, palette results
- Use JetBrains Mono for: inline code spans (backtick-delimited), code block content
- Load strategy: Google Fonts CDN or self-host; `font-display: swap` to prevent invisible text during load

**Font size minimums:**
- Body/bullet text: 16px minimum
- Secondary labels: 14px minimum (ensure contrast still passes WCAG at smaller size)
- Code: 14px with JetBrains Mono is standard for inline code

---

## Feature Dependencies

```
Dark Mode Token System
    └──required-by──> ALL UI components in v1.1
    └──must-implement-first──> (every other change should use tokens from day one)

Responsive Layout (sidebar hidden on mobile)
    └──required-by──> Hamburger button (needs hidden state to disclose)
    └──required-by──> Swipe gesture polish (gestures should not fire when sidebar overlay is open)
    └──bundled-with──> Safe area insets (single CSS refactor covers both)

Lucide Icon Library
    └──enhances──> Swipe gesture backing layer icons (trash, checkmark)
    └──enhances──> Hamburger and sidebar close icons
    └──enhances──> Quick-open palette icons (document, bullet, bookmark)

Dark Mode Tokens
    └──required-by──> Swipe backing colors (--color-swipe-delete, --color-swipe-complete)
    └──required-by──> Palette backdrop and surface colors

Quick-Open Palette
    └──reuses──> Existing document list (sidebar state — no new data fetch for doc titles)
    └──reuses──> Existing /search endpoint (bullet text search)
    └──requires──> Modal/overlay pattern (same as sidebar backdrop)

PWA Manifest
    └──independent──> (standalone feature; no deps on other v1.1 features)
    └──requires──> Icon assets (192px + 512px PNG) to be created first

Font Pairing
    └──independent──> (CSS font-family swap; no structural dependencies)
    └──enhances──> Overall visual quality of dark mode (readable at small sizes in dim light)
```

### Dependency Notes

- **Implement dark mode tokens first** — if any component is built before tokens exist, it will use hardcoded colors that must be retrofitted. Token system is the foundation all other v1.1 features rest on.
- **Responsive layout and safe area insets are one refactor** — the same CSS pass that adds media queries should also add `env(safe-area-inset-*)` padding. Doing them separately doubles the CSS churn.
- **Palette does not need new search infrastructure** — the existing `/search` endpoint handles bullet full-text search; document titles are already in sidebar state. The palette is a new UI over existing data.
- **Gesture polish is additive CSS** — the existing touch handler in `gestures.ts` already has threshold detection callbacks. The v1.1 work adds CSS classes to the row and backing layers at each threshold, not new gesture logic.
- **Lucide icons are a drop-in replacement** — existing ad-hoc SVGs can be replaced icon by icon; no architectural change required.

---

## MVP Definition

### v1.1 Launch (all features are in scope per PROJECT.md)

Order matters — implement in this sequence to avoid rework:

- [ ] Dark mode token system — CSS custom properties; every other component uses tokens
- [ ] Responsive mobile layout + safe area insets — CSS refactor; sidebar becomes drawer on mobile
- [ ] Hamburger + backdrop + sidebar close button + Ctrl/Cmd+E desktop toggle
- [ ] Lucide icon library — replace existing SVGs; bundled naturally with layout refactor
- [ ] Inter + JetBrains Mono font pairing — CSS font-family changes
- [ ] PWA manifest + icon assets — manifest.json + HTML link tag
- [ ] Swipe gesture color/icon feedback — CSS backing layers + threshold classes
- [ ] Quick-open palette (Ctrl+K) — modal overlay + fuzzy doc/bullet/bookmark search

### Defer to v1.2

- [ ] Manual dark/light override toggle — add to settings if user-requested
- [ ] Offline support / service worker — explicitly out of scope
- [ ] Palette action commands — navigation-only is sufficient for v1.1
- [ ] Screenshots in PWA manifest — nice for Android install dialog; not blocking install

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Dark mode token system | HIGH | MEDIUM | P1 — foundation; implement first |
| Responsive mobile layout | HIGH | MEDIUM | P1 — nothing else works on mobile without this |
| Safe area insets | MEDIUM | LOW | P1 — bundle with responsive layout refactor |
| Hamburger + auto-close sidebar | HIGH | LOW | P1 — without it mobile users can't navigate |
| Sidebar explicit close button | MEDIUM | LOW | P1 — accessibility + discoverability |
| Ctrl/Cmd+E desktop toggle | LOW | LOW | P2 — power user quality-of-life |
| PWA manifest + icons | HIGH | LOW | P1 — low effort, high perceived value |
| Lucide icon library | MEDIUM | LOW | P1 — visual coherence; bundle with layout work |
| Inter + JetBrains Mono fonts | MEDIUM | LOW | P1 — CSS change; immediate quality uplift |
| Swipe gesture color/icon reveal | MEDIUM | MEDIUM | P2 — existing gestures work; this is polish |
| Swipe snap-back animation | LOW | LOW | P2 — bundle with swipe color work |
| Haptic swipe feedback | LOW | LOW | P3 — Android only; graceful skip on iOS |
| Quick-open palette (docs + bookmarks) | HIGH | MEDIUM | P2 — high value; needs token + layout done first |
| Quick-open palette (bullet search) | HIGH | MEDIUM | P2 — extension of palette; server call |
| Mobile entry point for palette | MEDIUM | LOW | P2 — search icon on toolbar; Ctrl+K is keyboard-only |

**Priority key:**
- P1: Must have for v1.1 launch — without these the milestone fails
- P2: Should have in v1.1 — high value, bundle with surrounding work
- P3: Nice to have — include only if no complexity risk

---

## Competitor Feature Analysis

| Feature | Dynalist | Workflowy | Notion | Our v1.1 Approach |
|---------|----------|-----------|--------|-------------------|
| Mobile hamburger sidebar | Drawer overlay | Drawer overlay | Bottom sheet / sidebar | Drawer overlay (matches Dynalist pattern) |
| Dark mode | System-follow | System-follow | System + manual toggle | System-follow only (no manual toggle in v1.1) |
| Quick-open palette | Ctrl+K (docs only) | Ctrl+K (docs) | Ctrl+K (full command palette) | Ctrl+K (docs + bullets + bookmarks; no action commands) |
| PWA installable | Yes | Yes | Yes | Yes via manifest (no service worker needed) |
| Icon library | Custom SVGs | Minimal | Lucide-based | Lucide |
| Typography | System font | System font | Custom (Notion font) | Inter (UI) + JetBrains Mono (code) |
| Swipe gestures with feedback | Color reveal | Minimal | No swipe | Color reveal + icon + snap-back |
| Haptic feedback | Not on web | Not on web | Not on web | Android only via Vibration API |
| Safe area insets | Yes (iOS app) | Yes (iOS app) | Yes | Yes via CSS env() |

---

## Mobile-Specific UX Patterns

### Thumb Reach Zones

On a typical 6-inch phone held one-handed:

| Zone | Location | Suitable For |
|------|----------|--------------|
| Easy (natural) | Bottom 40%, center | High-frequency: new bullet, complete, search input |
| Reachable | Middle 30% | Moderate-frequency: toolbar buttons, sidebar items |
| Stretch (hard) | Top 30%, far corners | Low-frequency: hamburger (once/session), settings |

**Implications for v1.1:**
- Hamburger at top-left is acceptable — it is used once per session to switch documents
- If a floating action button (FAB) for "new document" is considered in a future milestone, bottom-right is the correct thumb-friendly placement
- Sidebar document items should be ≥48px tall (48px is Material Design's minimum; 44px is Apple HIG minimum)
- Quick-open palette results: 48px row height; finger-sized tap targets

### Safe Area Insets — Implementation Pattern

```css
/* Requires in HTML: <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"> */

.app-header {
  padding-top: max(env(safe-area-inset-top), 16px);
}

.sidebar {
  padding-bottom: env(safe-area-inset-bottom);
}

/* Any fixed bottom bars */
.bottom-toolbar {
  padding-bottom: max(env(safe-area-inset-bottom), 8px);
}
```

Without `viewport-fit=cover`, `env(safe-area-inset-*)` returns 0 — the meta viewport tag change is required.

### Touch Target Minimums

| Element | Minimum Size | Standard |
|---------|--------------|----------|
| Hamburger button | 44×44px | WCAG 2.5.5, Apple HIG |
| Sidebar document items | 44px height, full width | WCAG 2.5.5 |
| Bullet expand/collapse caret | 44×44px tap area (visual can be smaller) | WCAG 2.5.5 |
| Palette result rows | 48px height | Material Design |
| Swipe action icon area | Visual only — swipe gesture initiates action, not icon tap | N/A |
| Close (X) button | 44×44px | WCAG 2.5.5 |

---

## Sources

- [Mobile Navigation UX Best Practices (2026) — designstudiouiux.com](https://www.designstudiouiux.com/blog/mobile-navigation-ux/)
- [Mobile UX Thumb Zones 2025 — elaris.software](https://elaris.software/blog/mobile-ux-thumb-zones-2025/)
- [Command Palette UX Patterns — Mobbin glossary](https://mobbin.com/glossary/command-palette)
- [Command Palette — UX Patterns for Developers](https://uxpatterns.dev/patterns/advanced/command-palette)
- [Maggie Appleton on Command K Bars](https://maggieappleton.com/command-bar)
- [Notesnook 3.0.27 Command Palette Launch — AlternativeTo, Feb 2025](https://alternativeto.net/news/2025/2/notesnook-3-0-27-introduces-a-command-palette-support-for-markdown-pasting-and-more/)
- [PWA Install Criteria — web.dev](https://web.dev/articles/install-criteria)
- [PWA Web App Manifest — web.dev/learn](https://web.dev/learn/pwa/web-app-manifest)
- [Making PWAs Installable — MDN](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable)
- [PWA 2025 Web Almanac — HTTP Archive](https://almanac.httparchive.org/en/2025/pwa)
- [Dark Mode Accessibility Guide (WCAG 2.1 AA) — blog.greeden.me, Feb 2026](https://blog.greeden.me/en/2026/02/23/complete-accessibility-guide-for-dark-mode-and-high-contrast-color-design-contrast-validation-respecting-os-settings-icons-images-and-focus-visibility-wcag-2-1-aa/)
- [Color Contrast WCAG Guide 2025 — allaccessible.org](https://www.allaccessible.org/blog/color-contrast-accessibility-wcag-guide-2025)
- [CSS env() Safe Area Insets — MDN](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Values/env)
- [Chrome Android Edge-to-Edge Migration Guide — developer.chrome.com, Feb 2025](https://developer.chrome.com/docs/css-ui/edge-to-edge)
- [Lucide React — lucide.dev](https://lucide.dev/guide/packages/lucide-react)
- [Inter + JetBrains Mono Pairing Score 88/100 — fontalternatives.com](https://fontalternatives.com/pairings/inter-and-jetbrains-mono/)
- [JetBrains Mono — jetbrains.com](https://www.jetbrains.com/lp/mono/)
- [Best Fonts for Web Design 2025 — shakuro.com](https://shakuro.com/blog/best-fonts-for-web-design)
- [WebHaptics for React — cssscript.com](https://www.cssscript.com/haptic-feedback-web/)
- [2025 Haptics Guide — saropa-contacts.medium.com](https://saropa-contacts.medium.com/2025-guide-to-haptics-enhancing-mobile-ux-with-tactile-feedback-676dd5937774)
- [Motion for React Gestures — motion.dev](https://motion.dev/docs/react-gestures)
- [microfuzz fuzzy library — github.com/Nozbe/microfuzz](https://github.com/Nozbe/microfuzz)

---

*Feature research for: Notes v1.1 Mobile & UI Polish milestone*
*Researched: 2026-03-10*
