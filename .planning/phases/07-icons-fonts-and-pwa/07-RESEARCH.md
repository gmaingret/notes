# Phase 7: Icons, Fonts, and PWA - Research

**Researched:** 2026-03-10
**Domain:** React SVG icon library, variable font self-hosting, PWA manifest
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Icon scope:**
- Replace Unicode/emoji used as interactive icons only — buttons, actions, affordances
- Leave decorative characters unchanged: `›` breadcrumb separator, `•` bullet dot, `…` ellipsis, `⋯` text ellipsis in non-button contexts

**Icon library and defaults:**
- Library: Lucide React (`lucide-react` npm package)
- Default size: 20px across the app
- Default strokeWidth: 1.5 (Lucide default)

**Icon mapping:**
| Current | Location | Lucide replacement |
|---------|----------|--------------------|
| ☰ hamburger | Breadcrumb bar (mobile) | `<Menu />` |
| ✕ close | Sidebar X button (mobile) | `<X />` |
| + new doc | Sidebar header | `<Plus />` |
| ⋯ overflow | Sidebar header, DocumentRow | `<MoreHorizontal />` |
| ★ / ☆ bookmark | FilteredBulletList bullet row | `<Star />` (filled) / `<Star />` (outline via CSS) |
| 📎 attachment | AttachmentRow | `<Paperclip />` |
| ✅ swipe complete | BulletNode swipe icon | `<Check />` |
| 🗑️ swipe delete | BulletNode swipe icon | `<Trash2 />` |
| 🔖 bookmark tab | Sidebar tab | `<Star />` (icon-only tab) |
| ▶ collapse chevron | BulletNode | `<ChevronRight />` rotating 0°→90° on expand |
| filtered-nav-icon | FilteredBulletList | `<ArrowRight />` |

**Sidebar tabs:**
- Switch to icons-only (no text labels): Docs tab: `<FileText />`, Tags tab: `<Tag />`, Bookmarks tab: `<Star />`
- Add `title` attribute for accessibility tooltips

**Font loading:**
- Method: fontsource variable npm packages — bundled by Vite, served from the app server
- Inter: `@fontsource-variable/inter` — import in `main.tsx`
- JetBrains Mono: `@fontsource-variable/jetbrains-mono` — import in `main.tsx`
- `index.css` body font: Replace `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif` with `'Inter Variable', sans-serif`
- No Google Fonts — all font requests stay on the self-hosted app server

**JetBrains Mono application scope:**
Apply `font-family: 'JetBrains Mono Variable', monospace` to:
- Inline code spans (backtick markdown inside bullets)
- Tag chips (`#tags`, `@mentions`, `!!dates`)
- Search input field
- Comment body text

**PWA manifest:**
- Display name: "Notes"
- Short name: "Notes"
- Display mode: "standalone"
- Orientation: "any"
- Theme color: `#ffffff` (matches `--color-bg-base` light mode value)
- Background color: same as theme color
- Start URL: "/"
- Icons: 192x192 and 512x512 PNG
- No service worker — static manifest only

**PWA icon design:**
- Style: simple letter mark — "N" in Inter font centered on a solid background
- Background color: Claude's discretion (must look clean at 192px and 512px)

**Favicon:**
- Replace `vite.svg` with a new SVG letter mark — "N" matching the PWA icon style
- Reference in `index.html` as `<link rel="icon" type="image/svg+xml" href="/favicon.svg">`

**Tab title:** Keep "Notes" (static)

### Claude's Discretion
- Exact hex values for PWA icon background color
- SVG favicon exact dimensions and viewBox
- PNG icon generation method (inline script, sharp, or canvas API)
- Any Lucide `size` override needed for toolbar icons that are denser than the 20px default
- CSS class names for JetBrains Mono application

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| VISL-01 | All Unicode/emoji icons replaced with Lucide React SVG components | Lucide React 0.577.0 confirmed; all 11 icon names verified to exist in package |
| VISL-02 | UI text uses self-hosted Inter variable font (no Google Fonts) | `@fontsource-variable/inter` 5.2.8 — single import, Vite bundles to static assets |
| VISL-03 | Inline code and tag chips use self-hosted JetBrains Mono variable font | `@fontsource-variable/jetbrains-mono` 5.2.8 — same pattern as Inter |
| PWA-01 | App has a valid PWA manifest enabling Add to Home Screen | Static manifest sufficient for Chrome Android; iOS Safari uses Share menu flow (no service worker required) |
| PWA-02 | App has 192x192 and 512x512 PNG icons for home screen | sharp npm package generates both from a single SVG source |
| PWA-03 | App opens in standalone mode when launched from home screen | `"display": "standalone"` in manifest is the only required field |
</phase_requirements>

---

## Summary

Phase 7 is a purely additive, low-risk polish phase. No architectural changes are needed — it replaces Unicode chars with typed React components, swaps system fonts for self-hosted variable fonts via npm, and drops a JSON manifest file into `client/public/`. All three concerns are well-understood and have stable, widely-used npm solutions.

The decisions are fully locked. Research confirms the chosen tools (Lucide React, fontsource-variable, PWA static manifest) are the correct standard stack for this exact problem. No alternatives need evaluation.

The one nuance is iOS Safari: it does not show an automatic install prompt but does support "Add to Home Screen" via the Share menu without requiring a service worker. The user's decision to skip service workers is safe and correct.

**Primary recommendation:** Implement in three sequential waves — Wave 0 (test scaffold), Wave 1 (icons), Wave 2 (fonts), Wave 3 (PWA manifest + assets). Each wave is independently verifiable.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `lucide-react` | 0.577.0 | SVG icon components for React | De-facto standard; tree-shakeable named exports, consistent 24px grid, `currentColor` by default |
| `@fontsource-variable/inter` | 5.2.8 | Self-hosted Inter variable font | Single npm import; Vite copies woff2 to dist, zero CDN dependency |
| `@fontsource-variable/jetbrains-mono` | 5.2.8 | Self-hosted JetBrains Mono variable font | Same mechanism as Inter; one import covers all weights |
| `sharp` | latest | Generate PNG icon assets at build time | Handles SVG-to-PNG with resize; no headless browser required |

### File Assets (not npm packages)

| Asset | Location | Description |
|-------|----------|-------------|
| `favicon.svg` | `client/public/favicon.svg` | SVG letter-mark "N", replaces `vite.svg` |
| `manifest.webmanifest` | `client/public/manifest.webmanifest` | PWA manifest JSON |
| `icon-192.png` | `client/public/icon-192.png` | Home screen icon, 192x192 |
| `icon-512.png` | `client/public/icon-512.png` | Home screen icon, 512x512 |

### Installation

```bash
# In client/ directory
npm install lucide-react @fontsource-variable/inter @fontsource-variable/jetbrains-mono

# sharp is a devDependency — only needed to run the icon generation script
npm install --save-dev sharp
```

---

## Architecture Patterns

### Recommended Project Structure (additions only)

```
client/
├── public/
│   ├── favicon.svg          # NEW — letter-mark "N" SVG
│   ├── manifest.webmanifest # NEW — PWA manifest
│   ├── icon-192.png         # NEW — generated from favicon.svg
│   └── icon-512.png         # NEW — generated from favicon.svg
├── scripts/
│   └── generate-icons.mjs   # NEW — one-time Node script using sharp
└── src/
    ├── main.tsx             # EDIT — add fontsource imports
    ├── index.css            # EDIT — body font-family, add mono classes
    ├── index.html           # EDIT — swap favicon href, add manifest link
    └── components/
        └── ...              # EDIT — replace Unicode with <LucideIcon />
```

### Pattern 1: Named Icon Import (Lucide React)

**What:** Import only the icons you use — Lucide exports each icon as a named React component. Tree-shaking eliminates unused icons from the bundle.

**When to use:** Every interactive icon replacement.

```tsx
// Source: https://lucide.dev/guide/packages/lucide-react
import { Menu, X, Plus, MoreHorizontal, Star, Paperclip,
         Check, Trash2, ChevronRight, FileText, Tag, ArrowRight } from 'lucide-react';

// Default props for consistent sizing across the app
// size=20, strokeWidth=1.5 per locked decisions
<Menu size={20} strokeWidth={1.5} />

// Color always inherits from CSS — never hardcode color prop
// Let var(--color-text-*) tokens propagate via CSS `color`
<Star size={20} strokeWidth={1.5} className="bookmark-star" />
```

### Pattern 2: Fontsource Variable Font Import

**What:** Import the CSS file once at the app entry point. Vite processes the import, copies woff2 files to dist/, and generates correct `@font-face` declarations.

```tsx
// Source: https://fontsource.org/fonts/inter/install
// In client/src/main.tsx — add at top, before './index.css'
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
```

```css
/* In client/src/index.css — line 155 */
/* BEFORE: */
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }

/* AFTER: */
body { margin: 0; font-family: 'Inter Variable', sans-serif; }
```

**Font name reference:** Fontsource variable packages use the name pattern `'{Font Name} Variable'` in CSS. Confirmed names:
- Inter: `'Inter Variable'`
- JetBrains Mono: `'JetBrains Mono Variable'`

### Pattern 3: JetBrains Mono CSS Classes

```css
/* In index.css — new classes for mono font application */
.font-mono-code,
.chip,          /* existing chip class — chips already have their own class */
.search-input,
.comment-body {
  font-family: 'JetBrains Mono Variable', monospace;
}
```

Note: Chip elements already use the `.chip` class (defined at line 141 of index.css). Adding `font-family` there applies JetBrains Mono to all tag/mention/date chips without new markup.

### Pattern 4: PWA Manifest (Static, No Service Worker)

```json
{
  "name": "Notes",
  "short_name": "Notes",
  "start_url": "/",
  "display": "standalone",
  "orientation": "any",
  "theme_color": "#ffffff",
  "background_color": "#ffffff",
  "icons": [
    {
      "src": "/icon-192.png",
      "type": "image/png",
      "sizes": "192x192"
    },
    {
      "src": "/icon-512.png",
      "type": "image/png",
      "sizes": "512x512"
    }
  ]
}
```

```html
<!-- In client/index.html <head> — alongside existing tags -->
<link rel="manifest" href="/manifest.webmanifest" />
<link rel="icon" type="image/svg+xml" href="/favicon.svg" />
<!-- Remove: <link rel="icon" type="image/svg+xml" href="/vite.svg" /> -->
```

### Pattern 5: PNG Icon Generation Script

```js
// client/scripts/generate-icons.mjs
// Run once: node scripts/generate-icons.mjs
import sharp from 'sharp';
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const svgPath = resolve(__dirname, '../public/favicon.svg');
const svgBuffer = readFileSync(svgPath);

await sharp(svgBuffer).resize(192).png().toFile(resolve(__dirname, '../public/icon-192.png'));
await sharp(svgBuffer).resize(512).png().toFile(resolve(__dirname, '../public/icon-512.png'));
console.log('Icons generated: icon-192.png, icon-512.png');
```

**Note on sharp and embedded fonts:** sharp uses librsvg which does not support embedded web fonts in SVG. The favicon SVG letter-mark must use geometric paths (not `<text>` elements with font references) to render correctly at PNG export time. Use a path-traced "N" or render the text to paths.

### Pattern 6: Rotating Chevron (existing CSS transition preserved)

The collapsed bullet `▶` is replaced with `<ChevronRight />`. The existing CSS `transform: rotate(90deg)` transition is already in place — just swap the element. No CSS changes needed for the rotation behavior.

### Anti-Patterns to Avoid

- **Hardcoding `color` prop on Lucide icons:** All icon colors must come from CSS `color` property via `var(--color-text-*)` tokens. Setting `color="#111"` bypasses dark mode.
- **Using `@fontsource/inter` (non-variable):** The non-variable package requires importing individual weight files. The `-variable` package covers all weights with a single import at a smaller total size.
- **SVG favicon with `<text>` element:** Browsers render it fine, but sharp/librsvg cannot resolve the font at PNG generation time. Use a path-based letterform in the SVG.
- **`<link rel="apple-touch-icon">` meta tags:** MDN notes these are deprecated — modern iOS respects the manifest `icons` array. Do not add legacy Apple meta tags.
- **File extension `.json` for manifest:** Use `.webmanifest` extension. Vite serves both, but `.webmanifest` is the MIME-typed standard (`application/manifest+json`).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SVG icons | Inline SVG strings or Unicode chars | `lucide-react` named imports | Accessibility, consistent sizing, tree-shaking, `currentColor` |
| Font hosting | Copy woff2 files manually, write @font-face | `@fontsource-variable/*` npm packages | Vite handles asset pipeline; subsetting, CORS headers, cache busting all automatic |
| Image resizing | Canvas API resizing, custom Node canvas | `sharp` | Production-quality resampling, handles SVG input, 2-line API |
| PWA manifest validation | Custom checklist | Browser DevTools "Application" panel | Chrome DevTools PWA audit shows exactly what's missing |

---

## Common Pitfalls

### Pitfall 1: Lucide Default Size Is 24, Not 20

**What goes wrong:** Importing `<Menu />` without explicit `size` prop renders at 24px (Lucide default), not 20px (phase decision).
**Why it happens:** Props default to Lucide's own defaults, not the project's.
**How to avoid:** Always pass `size={20} strokeWidth={1.5}` explicitly. If this gets repetitive, create a thin wrapper component or use a Lucide `<IconContext.Provider>` with defaults:

```tsx
// Option A: LucideContext (cleaner for a large icon count)
// Source: https://lucide.dev/guide/packages/lucide-react
import { createLucideIcon } from 'lucide-react'; // not needed for context

// At app root or just inline per icon:
<Menu size={20} strokeWidth={1.5} />

// Option B: Wrap with defaults — only worth it if >20 icon usages
```

Given 11 icon replacements across 6 files, inline props are fine. No context provider needed.

### Pitfall 2: Font Name Typo

**What goes wrong:** CSS `font-family: 'Inter', sans-serif` does not match `'Inter Variable'` — falls back to system font silently.
**Why it happens:** fontsource-variable registers the font under the name with "Variable" suffix.
**How to avoid:** Always use the full name: `'Inter Variable'` and `'JetBrains Mono Variable'`.

### Pitfall 3: iOS Safari Add-to-Home-Screen Behavior

**What goes wrong:** Expecting an automatic install prompt banner on iOS like Chrome Android.
**Why it happens:** iOS Safari does not show automatic install prompts — it never has. Users must use Share > Add to Home Screen manually.
**How to avoid:** The manifest alone is sufficient. Test by opening in Safari on iOS, tapping Share, and verifying "Add to Home Screen" appears and launches in standalone mode. This satisfies PWA-01 and PWA-03 requirements.
**Warning signs:** If the standalone mode does not activate, check that `"display": "standalone"` is present and the manifest link is in `<head>`.

### Pitfall 4: Manifest Not Served by Vite Dev Server

**What goes wrong:** Files in `client/public/` are served as-is in production builds, but during Vite dev server the manifest might 404 if the path is wrong.
**Why it happens:** Vite serves `public/` at the root URL (`/manifest.webmanifest`). The `href` in `index.html` must match exactly.
**How to avoid:** Use `href="/manifest.webmanifest"` (absolute path from root). Vite dev server and production both serve this correctly.

### Pitfall 5: Star Icon Fill State for Bookmarks

**What goes wrong:** The bookmarked state `★` is expected to be filled gold, but `<Star />` renders only an outline.
**Why it happens:** Lucide icons render as stroked outlines by default. A "filled" star requires CSS: `fill: currentColor` or a separate filled variant.
**How to avoid:** Use CSS class to control fill state:
```css
.star-filled { fill: var(--color-accent-amber); color: var(--color-accent-amber); }
.star-outline { fill: none; color: var(--color-text-muted); }
```
Apply `.star-filled` when `isBookmarked === true`, `.star-outline` otherwise.

### Pitfall 6: sharp Binary Not Present on Docker Server

**What goes wrong:** `scripts/generate-icons.mjs` is run on the server, but sharp requires native binaries compiled for the server OS.
**Why it happens:** sharp uses libvips native binaries, platform-specific.
**How to avoid:** Run the icon generation script LOCALLY before committing. Commit the generated `icon-192.png` and `icon-512.png` to git. The server never needs sharp — it just serves static files.

---

## Code Examples

### Replacing a Unicode Icon (Before/After)

```tsx
// BEFORE — Sidebar.tsx
<button onClick={() => setShowSidebarMenu(v => !v)} className="sidebar-icon-btn" title="More options">⋯</button>

// AFTER
import { MoreHorizontal } from 'lucide-react';
<button onClick={() => setShowSidebarMenu(v => !v)} className="sidebar-icon-btn" title="More options">
  <MoreHorizontal size={20} strokeWidth={1.5} />
</button>
```

### Icon-Only Sidebar Tabs

```tsx
// BEFORE
{tab === 'docs' ? 'Docs' : tab === 'tags' ? 'Tags' : '🔖'}

// AFTER
import { FileText, Tag, Star } from 'lucide-react';
const tabIcon = { docs: <FileText size={20} strokeWidth={1.5} />, tags: <Tag size={20} strokeWidth={1.5} />, bookmarks: <Star size={20} strokeWidth={1.5} /> };
// In tab button:
<button ... title={tab === 'docs' ? 'Documents' : tab === 'tags' ? 'Tags' : 'Bookmarks'}>
  {tabIcon[tab]}
</button>
```

### Fontsource Imports in main.tsx

```tsx
// Source: https://fontsource.org/fonts/inter/install
// Add at the very top of client/src/main.tsx, before './index.css'
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
import React from 'react';
// ... rest of imports
```

### Complete manifest.webmanifest

```json
{
  "name": "Notes",
  "short_name": "Notes",
  "start_url": "/",
  "display": "standalone",
  "orientation": "any",
  "theme_color": "#ffffff",
  "background_color": "#ffffff",
  "icons": [
    {
      "src": "/icon-192.png",
      "type": "image/png",
      "sizes": "192x192"
    },
    {
      "src": "/icon-512.png",
      "type": "image/png",
      "sizes": "512x512"
    }
  ]
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@fontsource/inter` (static weights) | `@fontsource-variable/inter` (variable font) | ~2022 | Single import vs. multiple weight imports; smaller total payload |
| Manual woff2 files in public/ | fontsource npm packages | 2020+ | Asset pipeline, cache busting, and CORS handled automatically |
| Emoji/Unicode as icons | SVG icon library (Lucide) | Industry norm by ~2021 | Consistent rendering across OS, scalable, theme-aware |
| `manifest.json` extension | `manifest.webmanifest` extension | W3C spec update | Correct MIME type; browsers prefer it |
| `apple-mobile-web-app-capable` meta tags | Web app manifest `display` field | iOS 16.4+ | Legacy tags deprecated; manifest-only approach now recommended |

**Deprecated/outdated:**
- `@fontsource/inter` (non-variable): Still works but requires separate imports per weight; replaced by `-variable` packages
- `<meta name="apple-mobile-web-app-capable">`: Deprecated per MDN; manifest `display: standalone` is the correct mechanism
- Emoji icons: Render differently per OS, not scalable, no dark mode adaptation

---

## Open Questions

1. **PWA icon background color (Claude's discretion)**
   - What we know: Must look clean at 192px and 512px; accent blue `#4a90e2` and dark neutral `#1a1a1a` are both clean choices
   - What's unclear: User preference not specified
   - Recommendation: Use dark neutral `#1a1a1a` with white "N" — readable at small sizes in both light and dark contexts on any home screen wallpaper. Accent blue risks legibility on blue wallpapers.

2. **Favicon SVG "N" path vs `<text>` element**
   - What we know: `<text>` renders correctly in browsers but sharp cannot resolve fonts at PNG export time
   - What's unclear: Whether to use a geometric path or to generate PNGs via a different method
   - Recommendation: Use a geometric/path-based "N" in the SVG for maximum portability. Alternatively, generate a simple SVG with a colored rectangle and rasterize to PNG using sharp's built-in SVG rendering (sharp handles basic SVG shapes without font dependencies). Keep the favicon SVG using a `<text>` element since browsers handle it natively — only the PNG generation script needs the path-based variant.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest 4.0.18 + jsdom + @testing-library/react 16 |
| Config file | `client/vite.config.ts` (`test.environment: 'jsdom'`, `test.setupFiles: ['./src/test/setup.ts']`) |
| Quick run command | `cd /c/Users/gmain/Dev/Notes/client && npx vitest run --reporter=verbose 2>&1` |
| Full suite command | `ssh root@192.168.1.50 "cd /root/notes/client && npx vitest run"` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| VISL-01 | index.html references favicon.svg (not vite.svg) | unit (file read) | `npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts` | ❌ Wave 0 |
| VISL-01 | No raw Unicode icon chars remain in component source files | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| VISL-02 | index.css body font-family contains 'Inter Variable' | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| VISL-02 | main.tsx imports @fontsource-variable/inter | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| VISL-03 | main.tsx imports @fontsource-variable/jetbrains-mono | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| VISL-03 | index.css .chip rule contains 'JetBrains Mono Variable' | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| PWA-01 | index.html contains `<link rel="manifest"` | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| PWA-01 | manifest.webmanifest exists and contains required fields | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| PWA-02 | icon-192.png and icon-512.png exist in public/ | unit (file existence) | included in iconsAndFonts.test.ts | ❌ Wave 0 |
| PWA-03 | manifest.webmanifest display field is "standalone" | unit (file read) | included in iconsAndFonts.test.ts | ❌ Wave 0 |

**Note:** All VISL and PWA requirements are verifiable by reading file contents — no browser rendering required. This is consistent with the established `darkMode.test.tsx` pattern in the project (file-read assertions using `readFileSync`).

### Sampling Rate

- **Per task commit:** `cd /c/Users/gmain/Dev/Notes/client && npx vitest run --reporter=verbose src/test/iconsAndFonts.test.ts`
- **Per wave merge:** `cd /c/Users/gmain/Dev/Notes/client && npx vitest run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `client/src/test/iconsAndFonts.test.ts` — covers VISL-01, VISL-02, VISL-03, PWA-01, PWA-02, PWA-03 (all 6 requirements via file-read assertions, following darkMode.test.tsx pattern)

*(Existing setup.ts and test infrastructure are sufficient — no new framework installation needed)*

---

## Sources

### Primary (HIGH confidence)
- [lucide.dev/guide/packages/lucide-react](https://lucide.dev/guide/packages/lucide-react) — installation, API props, usage patterns
- [fontsource.org/fonts/inter/install](https://fontsource.org/fonts/inter/install) — exact import syntax, variable font support
- [MDN Making PWAs installable](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable) — minimum manifest fields, service worker not required for Chrome Android, iOS Safari behavior
- npm registry — confirmed versions: lucide-react@0.577.0, @fontsource-variable/inter@5.2.8, @fontsource-variable/jetbrains-mono@5.2.8

### Secondary (MEDIUM confidence)
- [techsparx.com sharp SVG-to-PNG guide](https://techsparx.com/nodejs/graphics/svg-to-png.html) — sharp API for SVG input; note on librsvg font limitation verified by multiple sources

### Tertiary (LOW confidence)
- None

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all three packages confirmed on npm with version numbers; Lucide and fontsource docs verified via official sites
- Architecture: HIGH — patterns derived directly from official documentation and existing codebase conventions
- Pitfalls: MEDIUM-HIGH — iOS Safari behavior confirmed via MDN; sharp font limitation confirmed by multiple sources; font naming confirmed via fontsource docs

**Research date:** 2026-03-10
**Valid until:** 2026-06-10 (stable ecosystem; fontsource and Lucide release frequently but APIs are stable)
