# Phase 6: Dark Mode - Context

**Gathered:** 2026-03-10
**Status:** Ready for planning

<domain>
## Phase Boundary

System-preference dark theme — the app automatically switches to a dark interface when the OS is in dark mode (`prefers-color-scheme: dark`). All user-visible surfaces, components, and markdown content are dark-mode-aware. No white flash on hard refresh. Browser chrome adopts the active theme. No manual toggle (deferred to v1.2).

</domain>

<decisions>
## Implementation Decisions

### Color token strategy
- **Approach**: CSS custom properties on `:root` — semantic tokens named by role, not value
- **Naming convention**: role-based — `--color-text-primary/secondary/muted`, `--color-bg-base/surface/raised`, `--color-border-default/subtle`, `--color-accent-*`
- **Granularity**: ~15-20 tokens — semantic surfaces, not per-component
- **Interactive states**: base tokens only (normal states) — hover/active handled via alpha or brightness modifiers in component CSS, no extra tokens
- **Token swap mechanism**: `:root` defines light values; `@media (prefers-color-scheme: dark)` overrides them with dark values — pure CSS, no JS required for live switching
- **Dark palette direction**: Claude's discretion — standard dark gray backgrounds (not pure black), off-white text, muted borders. Target feel: GitHub Dark / Linear dark. WCAG AA guarantees minimum contrast.

### FOUC prevention
- **Mechanism**: inline `<script>` in `<head>` of `index.html` — runs synchronously before any CSS or React loads
- **What it does**: reads `window.matchMedia('(prefers-color-scheme: dark)').matches`, adds `class="dark"` to `<html>` if true
- **CSS relationship**: `@media (prefers-color-scheme: dark)` is the primary token swap mechanism; the `.dark` class on `<html>` provides the early-paint hook for FOUC prevention and future v1.2 manual toggle support
- **Live switching**: React effect (or pure CSS `@media`) handles OS theme changes mid-session — no page refresh needed

### Component color audit scope
- **Coverage**: ALL user-visible surfaces — sidebar, header/breadcrumb bar, document area, inputs, context menu, tag/mention/date chips, attachment rows, undo toast, lightbox, search modal, bookmark browser
- **Markdown content**: included — links, inline code, bold text inside bullets must use dark-mode-aware token values
- **Inline styles in TSX**: convert hardcoded `style={{color: ..., background: ...}}` to CSS classes that reference CSS custom properties — no inline style color values remain after this phase

### WCAG AA compliance
- **Who verifies**: planner calculates contrast ratios for every token pair and documents them — any pair missing 4.5:1 (normal text) or 3:1 (large text/icons) gets adjusted before implementation
- **Exemptions**: border and divider lines are decorative — just need to be visible, not AA-compliant. Everything with text or icon meaning must pass AA.
- **Chips**: tag/mention/date chips keep their color identity in dark mode (same hue family) — darkened backgrounds and lightened text adjusted to pass 4.5:1. Color distinction between chip types preserved.

### Claude's Discretion
- Exact hex values for dark palette (as long as WCAG AA is met and palette feel matches GitHub Dark / Linear dark)
- Specific CSS class names for component dark-mode conversions
- Implementation order of component migrations

</decisions>

<specifics>
## Specific Ideas

- FOUC script pattern (decided): `if (window.matchMedia('(prefers-color-scheme: dark)').matches) document.documentElement.classList.add('dark');`
- Inline `<script>` goes in `<head>` before any stylesheet links in `index.html`
- Token naming examples: `--color-bg-base` (main body), `--color-bg-surface` (sidebar/cards), `--color-bg-raised` (modals/dropdowns), `--color-text-primary` (main content), `--color-text-muted` (secondary/timestamps), `--color-border-default` (visible borders), `--color-border-subtle` (decorative dividers)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `index.css`: 61 lines, established 768px breakpoint — this is where the `:root` token block and `@media` dark override will live
- `uiStore.ts`: no theme state needed for phase 6 (system-preference only, no toggle) — uiStore unchanged
- `index.html` (Vite entry): receives the inline FOUC `<script>` in `<head>`

### Established Patterns
- CSS media query at 768px for mobile — same file, same pattern for `@media (prefers-color-scheme: dark)`
- No CSS custom properties yet — Phase 6 introduces the token system from scratch
- Colors currently hardcoded in `index.css` (3 values) and in ~15 TSX component files (inline `style={}`)

### Integration Points
- `index.html` → add inline `<script>` in `<head>` for FOUC prevention + `<meta name="color-scheme" content="light dark">` for browser chrome (DRKM-04)
- `index.css` → add `:root { --color-* }` token block + `@media (prefers-color-scheme: dark) { :root { --color-* } }` overrides; replace hardcoded hex values with `var(--color-*)`
- All TSX components with inline `style={{}}` colors → convert to CSS classes referencing tokens
- Component `.css` files (if any) → replace hardcoded hex values with `var(--color-*)`

</code_context>

<deferred>
## Deferred Ideas

- Manual dark/light mode toggle in settings — v1.2 (DRKM-05 in REQUIREMENTS.md)
- None — discussion stayed within phase scope

</deferred>

---

*Phase: 06-dark-mode*
*Context gathered: 2026-03-10*
