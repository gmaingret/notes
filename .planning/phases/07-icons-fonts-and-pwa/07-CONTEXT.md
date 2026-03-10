# Phase 7: Icons, Fonts, and PWA - Context

**Gathered:** 2026-03-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace all Unicode/emoji used as interactive icons with Lucide React SVG components. Self-host Inter (UI font) and JetBrains Mono (code/chips/search/comments) via fontsource npm packages. Add a valid PWA manifest with app icons enabling Add to Home Screen. No service worker — static manifest only.

</domain>

<decisions>
## Implementation Decisions

### Icon scope
- Replace Unicode/emoji used as **interactive icons only** — buttons, actions, affordances
- Leave decorative characters unchanged: `›` breadcrumb separator, `•` bullet dot, `…` ellipsis, `⋯` text ellipsis in non-button contexts

### Icon library and defaults
- Library: **Lucide React** (`lucide-react` npm package)
- Default size: **20px** across the app
- Default strokeWidth: **1.5** (Lucide default)

### Icon mapping
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
| ▶ collapse chevron | BulletNode | `<ChevronRight />` rotating 0°→90° on expand (keep existing CSS transition) |
| filtered-nav-icon | FilteredBulletList | `<ArrowRight />` |

### Sidebar tabs
- Tabs switch to **icons-only** (no text labels):
  - Docs tab: `<FileText />`
  - Tags tab: `<Tag />`
  - Bookmarks tab: `<Star />`
- Add `title` attribute for accessibility tooltips

### Font loading
- **Method**: fontsource variable npm packages — bundled by Vite, served from the app server
- **Inter**: `@fontsource-variable/inter` — import subset weights 400–700 in `main.tsx`
- **JetBrains Mono**: `@fontsource-variable/jetbrains-mono` — import in `main.tsx`
- **`index.css` body font**: Replace `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif` with `'Inter Variable', sans-serif`
- **No Google Fonts** — all font requests stay on the self-hosted app server

### JetBrains Mono application scope
Apply `font-family: 'JetBrains Mono Variable', monospace` to:
- Inline code spans (backtick markdown inside bullets)
- Tag chips (`#tags`, `@mentions`, `!!dates`)
- Search input field
- Comment body text

### PWA manifest
- **Display name**: `"Notes"`
- **Short name**: `"Notes"`
- **Display mode**: `"standalone"` (opens without browser chrome)
- **Orientation**: `"any"` (no lock — allows rotation on tablets and landscape phones)
- **Theme color**: match light mode background (near-white/off-white, e.g. `#ffffff` or the `--color-bg-base` light value)
- **Background color**: same as theme color
- **Start URL**: `"/"`
- **Icons**: 192×192 and 512×512 PNG
- **No service worker** — static manifest only (no offline caching; avoids API cache-stale risk on deploys per STATE.md decision)

### PWA icon design
- Style: **simple letter mark** — "N" in Inter font centered on a solid background
- Background color: accent color or dark neutral (Claude's discretion — must look clean at 192px and 512px)
- Generate both sizes as PNG (can use a simple Node/Canvas script or SVG-to-PNG tool)

### Favicon
- Replace `vite.svg` with a new **SVG letter mark** — "N" matching the PWA icon style
- Reference in `index.html` as `<link rel="icon" type="image/svg+xml" href="/favicon.svg">`

### Tab title
- Keep `"Notes"` (static) — no dynamic document name in title

### Claude's Discretion
- Exact hex values for PWA icon background color (as long as it looks clean at small sizes)
- SVG favicon exact dimensions and viewBox
- PNG icon generation method (inline script, sharp, or canvas API)
- Any Lucide `size` override needed for toolbar icons that are denser than the 20px default
- CSS class names for JetBrains Mono application

</decisions>

<specifics>
## Specific Ideas

- Sidebar tabs go icon-only (FileText / Tag / Star) — user explicitly chose this; add `title` attributes for accessibility
- Star icon (not Bookmark icon) for both the bullet bookmark action and the sidebar bookmark tab — consistent iconography
- ChevronRight replaces the rotating ▶ triangle — keep the existing 0°→90° CSS rotation transition, just swap the element
- Fontsource variable packages: `@fontsource-variable/inter` + `@fontsource-variable/jetbrains-mono` — import once in `main.tsx`

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `client/src/index.css`: line 155 has `font-family: -apple-system, ...` on `body` — replace with Inter Variable
- `client/index.html`: has FOUC script and `color-scheme` meta from Phase 6; add `<link rel="manifest">` and swap favicon here
- Existing CSS token system from Phase 6: `--color-bg-base` is the light mode background — reuse for PWA theme color

### Current Unicode Icon Locations
| File | Icons to replace |
|------|-----------------|
| `client/src/components/DocumentView/BulletNode.tsx` | ▶ collapse, ✅/🗑️ swipe icons |
| `client/src/components/DocumentView/FilteredBulletList.tsx` | ★/☆ bookmark, filtered-nav-icon |
| `client/src/components/DocumentView/AttachmentRow.tsx` | 📎 paperclip |
| `client/src/components/Sidebar/Sidebar.tsx` | ⋯ overflow, ✕ X close, + new doc, 🔖 bookmark tab |
| `client/src/components/Sidebar/DocumentRow.tsx` | ⋯ overflow |
| `client/src/components/DocumentView/Breadcrumb.tsx` | (decorative only — leave) |

### Established Patterns
- Phase 5 decided: hamburger icon ☰ already exists — update to `<Menu />` from Lucide (Phase 5 context noted this explicitly)
- Phase 6 CSS token system: any icon color should use `var(--color-text-*)` tokens, not hardcoded hex
- No icon library currently installed — `lucide-react` needs to be added to `client/package.json`

### Integration Points
- `client/index.html` → add `<link rel="manifest" href="/manifest.webmanifest">`, swap favicon to `favicon.svg`
- `client/public/` → add `favicon.svg`, `manifest.webmanifest`, `icon-192.png`, `icon-512.png`
- `client/src/main.tsx` → add fontsource imports at top
- `client/src/index.css` → update body `font-family`, add JetBrains Mono classes
- All 6 component files above → swap Unicode chars for Lucide `<Component />` imports

</code_context>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-icons-fonts-and-pwa*
*Context gathered: 2026-03-10*
