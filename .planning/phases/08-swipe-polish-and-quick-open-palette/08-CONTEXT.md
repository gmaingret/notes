# Phase 8: Swipe Polish and Quick-Open Palette - Context

**Gathered:** 2026-03-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Two distinct features: (1) Polish swipe gesture animations so they feel intentional and satisfying on mobile, and (2) Build a global Ctrl+K quick-open palette for navigating to documents, bullets, and bookmarks. Creating/editing bullets and the underlying touch gesture threshold are out of scope — both already work.

</domain>

<decisions>
## Implementation Decisions

### Swipe animation feel

- Icon scales from small (near 0) to full size as drag distance increases toward the 40% threshold — clear "charging" feedback
- Snap-back (cancelled swipe): clean ease-out animation — CSS transition, no spring/overshoot. Consistent with existing `0.2s ease` transition already in BulletNode
- Commit exit (completed swipe): row slides fully off screen in the swipe direction — green goes right, red goes left — before the row is removed from the DOM
- Threshold cue: when drag crosses the 40% commit threshold, icon brightens or does a brief scale-up pulse to signal the action is locked in

### Swipe on completed bullets

- Swipe-right on an already-completed bullet toggles it back to incomplete — same gesture, same action, consistent toggle behavior

### Quick-open palette — empty state

- Shows 5 most recently opened documents (sorted by `lastOpenedAt` descending) when no query is typed
- No bullets or bookmarks in the empty state — clean, fast, matches how Raycast/Linear work

### Quick-open palette — search results

- Results appear when query is ≥2 characters
- Layout: grouped sections with headers — **Documents** first (client-side substring match on title), then **Bullets** (via existing `/api/search` endpoint), then **Bookmarks** (via existing `/api/bookmarks`)
- 3 results per section maximum
- Document title matching: case-insensitive substring — no fuzzy library, zero deps

### Quick-open palette — keyboard navigation

- Arrow keys move the selected result up/down across sections
- Enter opens the selected result
- Bullet result → navigate to `/doc/:documentId#bullet/:bulletId` (same as SearchModal behavior)
- Bookmark result → navigate to `/doc/:documentId#bullet/:bulletId` (same — bookmarks are bullets)
- Escape or click outside closes the palette

### Quick-open palette — visual design

- Position: top-center, 20% from top (same as existing SearchModal)
- Max-width: 600px (same as SearchModal) — consistent, `min(600px, 90vw)`
- Input: search icon (Lucide `Search`, size=20 strokeWidth=1.5) on the left inside the input row
- Selected result: `var(--color-row-active-bg)` background fill — consistent with DocumentRow active state
- Placeholder text: "Search documents and bullets..."

### Bookmark result row appearance

- Shows: Bookmark Lucide icon + bullet content (truncated) + document title as a dim subtitle below
- Visually distinct from plain bullet result rows via the icon and subtitle

### Quick-open palette — architecture

- New component `QuickOpenPalette` — separate from the existing `SearchModal`
- `SearchModal` (Ctrl+F, in-document bullet search only) continues to exist unchanged
- Ctrl+K listener mounts at the App/AppPage level — palette is accessible from anywhere (document list, open document, bookmarks screen)
- Mobile: Search icon button in the app-level header (next to hamburger) opens the palette — visible from all screens

### Claude's Discretion

- Exact icon size at rest vs at threshold (e.g., scale from 0.5x → 1.0x → 1.2x burst)
- Commit-exit slide animation duration and easing curve
- Section header typography and spacing in the palette
- How to handle empty sections (hide section header if no results for that type)
- Debounce timing for the bullet search query (≥2 chars triggers the API call)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets

- `gestures.ts` — `swipeThresholdReached()` and `createLongPressHandler()` — pure closure-based, no React. Used directly in BulletNode. Swipe animations should integrate here or alongside these utilities.
- `BulletNode.tsx` — `swipeX` and `isSwiping` state already present. Backing div with Check/Trash2 icons already rendered. `transition: isSwiping ? 'none' : 'transform 0.2s ease'` already handles snap-back baseline.
- `SearchModal.tsx` — Modal structure (backdrop + centered box + input + FilteredBulletList) is a reference pattern for the new QuickOpenPalette. Do NOT modify SearchModal — it stays as-is.
- `useDocuments` — returns `Document[]` with `lastOpenedAt: string | null` — sort descending to get recent docs. Data already in React Query cache at app load.
- `useBookmarks` — returns `BookmarkRow[]` with `content`, `documentId`, `documentTitle` — all fields needed for palette bookmark rows.
- `useSearch` — `enabled: query.length >= 2` — already gated correctly. Returns `SearchResult[]` with `content`, `documentId`, `documentTitle`.
- Lucide icons: `Search`, `Bookmark`, `Check`, `Trash2` already in use. Standard: `size=20 strokeWidth={1.5}`.
- CSS token `--color-row-active-bg` already defined for both light/dark — use for selected palette row.
- CSS token `--color-swipe-complete` and `--color-swipe-delete` already defined for swipe backing.

### Established Patterns

- CSS transitions for animations (not Framer Motion) — confirmed in REQUIREMENTS.md out-of-scope
- Dark mode via CSS custom properties — all new UI must use `var(--color-*)` tokens, no hardcoded colors
- Modal backdrop pattern: `position: fixed; inset: 0; background: var(--color-bg-overlay); zIndex: 1000` with modal box at `zIndex: 1001`
- TDD Wave 0 expected: write RED tests before any production code — planner should include a Wave 0 test scaffold plan

### Integration Points

- App-level keyboard listener for Ctrl+K → mount in `AppPage.tsx` (where Ctrl+E sidebar toggle already lives)
- Mobile search button → add to the app-level header in `AppPage.tsx` alongside the existing hamburger button
- BulletNode swipe animation changes are self-contained — no API or routing changes needed
- QuickOpenPalette navigates via `useNavigate()` from react-router-dom — same as SearchModal

</code_context>

<specifics>
## Specific Ideas

- Swipe commit exit should feel like the row is "flying off" in the direction of the action — satisfying, directional
- The palette should feel consistent with SearchModal visually (same positioning, same width) — not a completely foreign UI
- The mobile search button in the header next to the hamburger is the trigger for palette on mobile — similar to how apps like Bear or Craft put search in their top bar

</specifics>

<deferred>
## Deferred Ideas

- None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-swipe-polish-and-quick-open-palette*
*Context gathered: 2026-03-11*
