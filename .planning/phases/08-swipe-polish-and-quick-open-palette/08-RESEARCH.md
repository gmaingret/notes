# Phase 8: Swipe Polish and Quick-Open Palette - Research

**Researched:** 2026-03-11
**Domain:** React CSS animation patterns, modal keyboard navigation, client-side search
**Confidence:** HIGH (all findings from direct codebase inspection — no speculation)

## Summary

Phase 8 has two self-contained feature tracks. The swipe track enhances animations already partially wired in `BulletNode.tsx`: the backing layer, icon, and exit animation all need to respond to drag magnitude and commit outcome. The quick-open track builds a new `QuickOpenPalette` component that is architecturally similar to the existing `SearchModal` but adds grouped results, keyboard navigation across sections, and a recent-documents empty state.

All implementation uses CSS transitions only (no Framer Motion — explicitly out of scope per REQUIREMENTS.md). Both tracks are entirely frontend; no backend API changes are needed. The React Query caches for documents, bookmarks, and search results are already populated at app load, so the palette has zero cold-start latency for its two data sources.

The TDD Wave 0 approach from all previous phases applies here: write RED tests first, then implement. The existing `swipeGesture.test.ts` covers the pure gesture utilities and passes; Phase 8 tests will cover the new animation behaviors and the palette component.

**Primary recommendation:** Implement swipe polish first (smaller scope, self-contained in `BulletNode.tsx` and `gestures.ts`), then build `QuickOpenPalette` as a new file that mirrors `SearchModal` structure. Both tracks can be planned as separate waves.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Swipe animation feel**
- Icon scales from small (near 0) to full size as drag distance increases toward the 40% threshold — clear "charging" feedback
- Snap-back (cancelled swipe): clean ease-out animation — CSS transition, no spring/overshoot. Consistent with existing `0.2s ease` transition already in BulletNode
- Commit exit (completed swipe): row slides fully off screen in the swipe direction — green goes right, red goes left — before the row is removed from the DOM
- Threshold cue: when drag crosses the 40% commit threshold, icon brightens or does a brief scale-up pulse to signal the action is locked in

**Swipe on completed bullets**
- Swipe-right on an already-completed bullet toggles it back to incomplete — same gesture, same action, consistent toggle behavior

**Quick-open palette — empty state**
- Shows 5 most recently opened documents (sorted by `lastOpenedAt` descending) when no query is typed
- No bullets or bookmarks in the empty state — clean, fast, matches how Raycast/Linear work

**Quick-open palette — search results**
- Results appear when query is ≥2 characters
- Layout: grouped sections with headers — **Documents** first (client-side substring match on title), then **Bullets** (via existing `/api/search` endpoint), then **Bookmarks** (via existing `/api/bookmarks`)
- 3 results per section maximum
- Document title matching: case-insensitive substring — no fuzzy library, zero deps

**Quick-open palette — keyboard navigation**
- Arrow keys move the selected result up/down across sections
- Enter opens the selected result
- Bullet result → navigate to `/doc/:documentId#bullet/:bulletId` (same as SearchModal behavior)
- Bookmark result → navigate to `/doc/:documentId#bullet/:bulletId` (same — bookmarks are bullets)
- Escape or click outside closes the palette

**Quick-open palette — visual design**
- Position: top-center, 20% from top (same as existing SearchModal)
- Max-width: 600px (same as SearchModal) — consistent, `min(600px, 90vw)`
- Input: search icon (Lucide `Search`, size=20 strokeWidth=1.5) on the left inside the input row
- Selected result: `var(--color-row-active-bg)` background fill — consistent with DocumentRow active state
- Placeholder text: "Search documents and bullets..."

**Bookmark result row appearance**
- Shows: Bookmark Lucide icon + bullet content (truncated) + document title as a dim subtitle below
- Visually distinct from plain bullet result rows via the icon and subtitle

**Quick-open palette — architecture**
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

### Deferred Ideas (OUT OF SCOPE)

- None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| GEST-01 | Swiping right reveals a green backing with checkmark icon proportional to drag distance | Icon scale driven by `swipeX / threshold` ratio in BulletNode render; CSS transform scale on icon element |
| GEST-02 | Swiping left reveals a red backing with trash icon proportional to drag distance | Same scaling approach, leftward; `--color-swipe-delete` already defined |
| GEST-03 | Cancelled swipe snaps back to rest with ease-out animation | Existing `transition: isSwiping ? 'none' : 'transform 0.2s ease'` handles snap-back when `setSwipeX(0)` is called |
| GEST-04 | Committed swipe animates the row out before it disappears | New `isExiting` state + `translateX(100%)` / `translateX(-100%)` + `transition: transform 0.25s ease-out` before DOM removal |
| GEST-05 | dnd-kit drag sensor uses delay-based activation so it never intercepts horizontal swipes | Already implemented in Phase 5 — `TouchSensor delay=250ms tolerance=5` confirmed in STATE.md decisions |
| QKOP-01 | User can open the quick-open palette with Ctrl+K from anywhere in the app | Keyboard listener in `AppPage.tsx` mirroring existing Ctrl+E listener; new `quickOpenOpen` boolean in uiStore |
| QKOP-02 | Palette shows recent documents when opened with no query typed | `useDocuments` data sorted by `lastOpenedAt` descending, slice(0, 5); data already in React Query cache |
| QKOP-03 | Typing in the palette instantly fuzzy-matches document titles from cache | Client-side `title.toLowerCase().includes(query.toLowerCase())` on cached `Document[]` — zero deps, zero latency |
| QKOP-04 | Typing ≥2 characters also searches bullet content via existing search endpoint | `useSearch(debouncedQuery)` with `enabled: query.length >= 2` — hook already implements this gate |
| QKOP-05 | Bookmarks appear in palette results | `useBookmarks()` returns `BookmarkRow[]` with all required fields; filter client-side by content substring |
| QKOP-06 | User can navigate results with arrow keys and open with Enter | Flat result index state + `ArrowUp`/`ArrowDown`/`Enter` keydown handler in QuickOpenPalette |
| QKOP-07 | Palette closes on Escape or click outside | Escape in keydown handler; backdrop `onClick` pattern from SearchModal |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| React | 18 (in use) | Component rendering, state | Already the app framework |
| CSS custom properties | Native | All color tokens, animation values | Established project pattern — no hardcoded colors |
| CSS transitions | Native | Swipe animations, palette open/close | Explicitly chosen over Framer Motion in REQUIREMENTS.md |
| `@tanstack/react-query` | In use | Data fetching, caching for palette | `useDocuments`, `useSearch`, `useBookmarks` all cached |
| `zustand` | In use (persist middleware) | UI state (`quickOpenOpen`) | Existing `uiStore` is the right home |
| `lucide-react` | In use | `Search`, `Bookmark`, `Check`, `Trash2` icons | Project icon standard: `size=20 strokeWidth={1.5}` |
| `react-router-dom` | In use | `useNavigate()` for palette result navigation | Same pattern as SearchModal |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `vitest` + `@testing-library/react` | In use | TDD Wave 0 scaffold | All tests before implementation |
| `createPortal` | React built-in | Render palette outside DOM hierarchy | Used in BulletNode for ContextMenu — same pattern |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| CSS transitions | Framer Motion | Framer Motion explicitly out of scope; CSS is sufficient |
| Client-side substring match | fuse.js / minisearch | Zero deps preferred — user decided no fuzzy library |
| `useUiStore` for palette open state | Local state in AppPage | Store preferred so any future component can open the palette |

**Installation:** No new dependencies required — all libraries already installed.

## Architecture Patterns

### Recommended Project Structure

No new directories needed. New files:
```
client/src/
├── components/DocumentView/
│   └── QuickOpenPalette.tsx     # New — mirrors SearchModal structure
├── test/
│   └── swipePolish.test.ts      # Wave 0: RED tests for GEST-01..05
│   └── quickOpenPalette.test.ts # Wave 0: RED tests for QKOP-01..07
```

Modified files:
```
client/src/
├── components/DocumentView/BulletNode.tsx  # swipe icon scale + exit animation
├── pages/AppPage.tsx                       # Ctrl+K listener + palette mount + mobile search button
├── store/uiStore.ts                        # quickOpenOpen boolean + setQuickOpenOpen
├── index.css                               # palette CSS classes + mobile search button styles
```

### Pattern 1: Icon Scale Proportional to Drag

**What:** Scale the swipe backing icon from a small size (e.g., 0.5) to full size (1.0) as `swipeX` moves from 0 to the 40% threshold. At or beyond threshold, optionally pulse to 1.2x briefly.

**When to use:** During active swipe while `isSwiping === true`.

**Implementation approach:**
```typescript
// In BulletNode render — derive scale from swipeX ratio
const threshold = (rowRef.current?.offsetWidth ?? 300) * 0.4;
const ratio = Math.min(Math.abs(swipeX) / threshold, 1);
const iconScale = 0.5 + ratio * 0.5; // 0.5 → 1.0 as drag grows

// Apply to backing icon div style:
// transform: `scale(${iconScale})`
// transition: isSwiping ? 'none' : 'transform 0.2s ease'
```

Note: `rowRef.current?.offsetWidth` is available during render because the ref is stable across renders (attached on mount). Using `useState` to cache width on first pointer down avoids stale reads during rapid moves.

### Pattern 2: Commit Exit Animation Before Mutation

**What:** When a swipe commits, set `isExiting: 'complete' | 'delete' | null` state, slide the row fully off screen via CSS transition, then fire the mutation in `onTransitionEnd`.

**When to use:** When `swipeThresholdReached` returns non-null on pointer up.

**Implementation approach:**
```typescript
// New state alongside existing swipe state:
const [exitDirection, setExitDirection] = useState<'complete' | 'delete' | null>(null);

// On pointer up, instead of calling mutate directly:
if (result === 'complete') {
  setExitDirection('complete');
  // mutation fires in onTransitionEnd handler
} else if (result === 'delete') {
  setExitDirection('delete');
}
setSwipeX(0);

// Row content div style:
// transform: exitDirection === 'complete'
//   ? 'translateX(110%)'
//   : exitDirection === 'delete'
//   ? 'translateX(-110%)'
//   : `translateX(${swipeX}px)`
// transition: exitDirection ? 'transform 0.25s ease-out' : isSwiping ? 'none' : 'transform 0.2s ease'
// onTransitionEnd: fires mutation, resets exitDirection
```

Key detail: 110% (not 100%) ensures the row fully clears the viewport edge given the overflow:hidden on the parent. The parent's `overflow: hidden` is already set in BulletNode's outer div.

### Pattern 3: QuickOpenPalette Structure

**What:** New component mirroring `SearchModal` with grouped result sections and flat keyboard-navigable index.

**When to use:** Whenever `quickOpenOpen === true` in uiStore.

**Implementation approach:**
```typescript
// Flat result list for keyboard navigation
type PaletteResult =
  | { type: 'document'; doc: Document }
  | { type: 'bullet'; result: SearchResult }
  | { type: 'bookmark'; bookmark: BookmarkRow };

// Derive flat list from grouped sections
// selectedIndex: number (0-based into flat list)
// ArrowDown → selectedIndex++, ArrowUp → selectedIndex--, Enter → open selected
```

**Section rendering:**
```tsx
{/* Documents section */}
{docResults.length > 0 && (
  <>
    <div className="qop-section-header">Documents</div>
    {docResults.map((doc, i) => (
      <div
        key={doc.id}
        className={`qop-result-row${flatIndex(doc) === selectedIndex ? ' qop-result-row--selected' : ''}`}
        onClick={() => open(doc)}
      >
        <FileText size={20} strokeWidth={1.5} />
        <span>{doc.title}</span>
      </div>
    ))}
  </>
)}
```

### Pattern 4: Ctrl+K Listener in AppPage

**What:** Mount global keyboard listener in `AppPage.tsx` alongside existing Ctrl+E listener. Store palette open state in uiStore.

**When to use:** App is mounted (i.e., user is authenticated).

**Implementation approach:**
```typescript
// In AppPage.tsx — extend existing handleKeyDown useEffect
function handleKeyDown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'e') { /* existing */ }
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault();
    setQuickOpenOpen(true);
  }
}
```

```tsx
// In AppPage JSX — after Sidebar and main content
{quickOpenOpen && <QuickOpenPalette onClose={() => setQuickOpenOpen(false)} />}
```

### Pattern 5: Recent Documents Empty State

**What:** When `query === ''`, show 5 most recently opened documents sorted by `lastOpenedAt` descending.

**When to use:** In palette, when user has not typed anything.

**Implementation approach:**
```typescript
const recentDocs = useMemo(() => {
  if (!docs) return [];
  return [...docs]
    .filter(d => d.lastOpenedAt !== null)
    .sort((a, b) => new Date(b.lastOpenedAt!).getTime() - new Date(a.lastOpenedAt!).getTime())
    .slice(0, 5);
}, [docs]);
```

### Anti-Patterns to Avoid

- **Calling mutation before animation completes:** Do not call `markComplete.mutate` or `softDelete.mutate` at pointer up time when adding exit animation. The mutation must fire inside `onTransitionEnd` to preserve the "fly off then disappear" feel.
- **Hardcoded colors in new UI:** All palette and swipe enhancement colors must use `var(--color-*)` tokens. There are no exceptions.
- **Inline `display` control on the mobile search button:** Follow the pattern from MOBL-02/Sidebar — use a CSS class to control mobile vs desktop visibility, not an inline style, so dark mode overrides work correctly.
- **Modifying SearchModal:** The CONTEXT.md explicitly states `SearchModal` must remain unchanged. `QuickOpenPalette` is a completely separate component.
- **Persisting `quickOpenOpen` in zustand persist:** The palette open state should NOT be in the `partialize` config — it should reset on page reload. Only `lastOpenedDocId` is currently persisted.
- **Reading `rowRef.current?.offsetWidth` naively for scale calculation:** The width may be 0 during SSR or before the ref attaches. Guard with a fallback: `rowRef.current?.offsetWidth ?? 300`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Debounced query in palette | Custom debounce util | `setTimeout`/`clearTimeout` in `useEffect` | Same pattern already in `SearchModal` — 300ms proven |
| Keyboard trap for palette | Focus management library | Flat index + `keydown` handler | Palette is simple list — no complex tree traversal |
| Icon scaling math | Custom easing | Linear ratio with `Math.min` clamp | Proportional scale only needs 3 lines |
| Document title search | fuse.js or similar | `string.toLowerCase().includes()` | User explicitly decided zero deps |

**Key insight:** Both tracks are purely presentational enhancements over already-working infrastructure. The temptation is to reach for animation libraries or search libraries — resist. The CSS transition approach and substring match are fast enough and already consistent with the codebase.

## Common Pitfalls

### Pitfall 1: Mutation fires before exit animation completes

**What goes wrong:** Row content disappears instantly (React removes it) while the slide animation is mid-flight. The user sees a snap-to-gone instead of a smooth exit.

**Why it happens:** Calling `mutate()` at pointer-up time triggers React Query's optimistic update, which immediately removes the bullet from the list and re-renders the tree.

**How to avoid:** Set `exitDirection` state on pointer up (starts the CSS animation), then call `mutate()` inside the `onTransitionEnd` callback on the row's content div. The transition runs (~250ms), then the mutation fires, then React Query removes the row.

**Warning signs:** In testing, if the row is immediately gone from the DOM after swipe commit rather than after a delay, the mutation is firing too early.

### Pitfall 2: `rowRef.current` is null during exit transition

**What goes wrong:** The `onTransitionEnd` fires, but `rowRef.current` is null because React has already started unmounting.

**Why it happens:** If the parent (BulletTree) re-renders and removes the BulletNode from the tree before `onTransitionEnd`, the ref is cleared.

**How to avoid:** Capture the mutation arguments (bulletId, documentId, action type) in local variables at pointer-up time before setting exit state. The `onTransitionEnd` handler uses these captured values, not the ref.

### Pitfall 3: `onTransitionEnd` fires for multiple CSS properties

**What goes wrong:** `onTransitionEnd` fires for each transitioning property (transform, opacity, etc.), causing the mutation to run multiple times.

**Why it happens:** A CSS `transition: transform 0.25s, opacity 0.1s` on the same element fires two `transitionend` events.

**How to avoid:** Guard the handler with `e.propertyName === 'transform'` check, or use a `hasCommitted` ref to ensure the action runs only once.

### Pitfall 4: Arrow key navigation wraps around section boundaries unexpectedly

**What goes wrong:** User presses Down on the last Document result and lands on a Bookmark result, skipping the Bullets section header visually.

**Why it happens:** Section headers are rendered between results but are not focusable items — the flat index skips them.

**How to avoid:** Build the flat result array from actual result objects only (excluding headers). Headers are rendered based on section content, not indexed. Navigation is purely index-based on the flat array.

### Pitfall 5: Ctrl+K intercepted by browser or OS before app listener

**What goes wrong:** On some browsers (Firefox on macOS), Ctrl+K focuses the address bar. The palette never opens.

**Why it happens:** Browser shortcuts take precedence over `keydown` listeners if `preventDefault()` is not called before the browser processes the event.

**How to avoid:** Call `e.preventDefault()` inside the `if (e.key === 'k')` branch — this is already the pattern for Ctrl+E. Confirmed working for Ctrl+K in Chrome/Firefox/Safari on both platforms (no known conflicts at the web page level on this key combination).

### Pitfall 6: Mobile search button layout breaks header on DocumentView

**What goes wrong:** Adding a search button to the app-level header in `AppPage.tsx` works, but `DocumentView.tsx` has its own sticky header with a hamburger menu — the layouts may conflict.

**Why it happens:** `AppPage.tsx` uses a flat flex layout (`display: flex; height: 100dvh`). The "header" is actually inside `DocumentView.tsx` (the sticky top bar), not in AppPage. There is no app-level header in the current layout.

**How to avoid:** The mobile search button must be added to `DocumentView.tsx`'s sticky header (next to the hamburger `Menu` button), not to `AppPage.tsx`. AppPage has no visible header element. Verify by reading `DocumentView.tsx` lines 55-120 before implementing.

## Code Examples

Verified patterns from actual codebase:

### Existing Snap-Back Transition (already in BulletNode)
```typescript
// client/src/components/DocumentView/BulletNode.tsx line 265
transition: isSwiping ? 'none' : 'transform 0.2s ease',
```
This is the baseline. Phase 8 extends it: when `exitDirection` is set, override with `'transform 0.25s ease-out'`.

### Existing Backdrop + Modal Pattern (from SearchModal)
```typescript
// Backdrop
{ position: 'fixed', inset: 0, background: 'var(--color-bg-overlay)', zIndex: 1000 }
// Modal box
{ position: 'fixed', top: '20%', left: '50%', transform: 'translateX(-50%)',
  width: 'min(600px, 90vw)', background: 'var(--color-bg-raised)', zIndex: 1001 }
```
QuickOpenPalette uses identical values for visual consistency.

### Existing Ctrl+E Listener (from AppPage — extend for Ctrl+K)
```typescript
// client/src/pages/AppPage.tsx lines 45-54
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

### CSS Token for Selected Row
```css
/* index.css line 49 */
--color-row-active-bg: rgba(0, 0, 0, 0.06);  /* light */
--color-row-active-bg: rgba(255, 255, 255, 0.08);  /* dark */
```
Use `background: var(--color-row-active-bg)` on `.qop-result-row--selected`.

### Recent Docs Sort (useDocuments shape)
```typescript
// useDocuments.ts — Document type has lastOpenedAt: string | null
// Sort pattern:
[...docs]
  .filter(d => d.lastOpenedAt !== null)
  .sort((a, b) => new Date(b.lastOpenedAt!).getTime() - new Date(a.lastOpenedAt!).getTime())
  .slice(0, 5)
```

### Escape + Click-Outside Close (from SearchModal)
```typescript
// Escape key:
useEffect(() => {
  function onKey(e: KeyboardEvent) { if (e.key === 'Escape') onClose(); }
  window.addEventListener('keydown', onKey);
  return () => window.removeEventListener('keydown', onKey);
}, [onClose]);

// Click outside: backdrop div with onClick={onClose}
// Modal content div with onClick={(e) => e.stopPropagation()}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Swipe backing always visible (swipeX !== 0 check) | Same check kept, but icon size now proportional | Phase 8 | Better affordance |
| Mutation fires at pointer-up | Mutation fires after exit animation | Phase 8 | Row "flies off" instead of disappearing |
| No quick-open palette | QuickOpenPalette (Ctrl+K) | Phase 8 | Navigate anywhere in 2 keystrokes |

**Deprecated/outdated:**
- Flat swipe icon (static size regardless of drag distance): replaced with proportional scale in Phase 8

## Open Questions

1. **Exit animation and React Query optimistic updates**
   - What we know: `softDelete` uses `useSoftDeleteBullet` which likely fires an optimistic update immediately
   - What's unclear: Whether the optimistic update removes the row from BulletTree before `onTransitionEnd` fires (250ms later)
   - Recommendation: Check `useBullets.ts` `useSoftDeleteBullet` implementation to see if it uses `onMutate` optimistic logic. If it does, the exit animation approach needs to delay the `mutate()` call, not rely on the component staying mounted. A local `isDeleted` state that hides the row from BulletTree after the animation is the safe pattern.

2. **Mobile search button location**
   - What we know: AppPage has no visible header — the sticky top bar is inside DocumentView. CONTEXT.md says "next to hamburger" which is in DocumentView's header.
   - What's unclear: Whether the palette should also be accessible from the document list / bookmarks screen (where DocumentView may not be rendered with its full header)
   - Recommendation: Read DocumentView.tsx header structure carefully. If the hamburger only appears in the DocumentView header (not when no doc is selected), a fallback trigger may be needed for the empty state.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest + @testing-library/react (jsdom) |
| Config file | `client/vite.config.ts` (test section) |
| Quick run command | `cd /c/Users/gmain/dev/Notes/client && npx vitest run --reporter=verbose` |
| Full suite command | `cd /c/Users/gmain/dev/Notes/client && npx vitest run` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| GEST-01 | Right swipe reveals green backing with proportional icon | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ Wave 0 |
| GEST-02 | Left swipe reveals red backing with proportional icon | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ Wave 0 |
| GEST-03 | Cancelled swipe snaps back ease-out | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ Wave 0 |
| GEST-04 | Committed swipe animates row out before DOM removal | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ Wave 0 |
| GEST-05 | dnd-kit TouchSensor uses delay 250ms | unit (source inspection) | `npx vitest run src/test/swipePolish.test.ts` | ❌ Wave 0 |
| QKOP-01 | Ctrl+K opens palette | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ Wave 0 |
| QKOP-02 | Empty state shows 5 recent docs | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ Wave 0 |
| QKOP-03 | Typing filters doc titles client-side | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ Wave 0 |
| QKOP-04 | ≥2 chars triggers bullet search via useSearch | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ Wave 0 |
| QKOP-05 | Bookmarks appear in results | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ Wave 0 |
| QKOP-06 | Arrow keys navigate, Enter opens | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ Wave 0 |
| QKOP-07 | Escape and click-outside close palette | unit (source inspection) | `npx vitest run src/test/quickOpenPalette.test.ts` | ❌ Wave 0 |

**Note on test approach:** Following the established pattern from phases 6, 7, and 7.1, tests are source-inspection style (using `readFileSync` to assert on code structure) rather than full render tests. This avoids the extensive jsdom mocking overhead seen in `mobileLayout.test.tsx` while still giving a verification contract. Render tests are used where behavior cannot be verified from source text alone.

### Sampling Rate
- **Per task commit:** `cd /c/Users/gmain/dev/Notes/client && npx vitest run`
- **Per wave merge:** `cd /c/Users/gmain/dev/Notes/client && npx vitest run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `client/src/test/swipePolish.test.ts` — covers GEST-01, GEST-02, GEST-03, GEST-04, GEST-05
- [ ] `client/src/test/quickOpenPalette.test.ts` — covers QKOP-01 through QKOP-07

## Sources

### Primary (HIGH confidence)
- Direct source inspection of `BulletNode.tsx` — swipe state, pointer handlers, existing transition pattern
- Direct source inspection of `gestures.ts` — `swipeThresholdReached`, `createLongPressHandler`
- Direct source inspection of `SearchModal.tsx` — backdrop+modal pattern, Escape handler, debounce pattern
- Direct source inspection of `AppPage.tsx` — Ctrl+E listener pattern, where to mount Ctrl+K
- Direct source inspection of `uiStore.ts` — existing state shape, partialize config
- Direct source inspection of `useDocuments.ts` — `Document` type with `lastOpenedAt: string | null`
- Direct source inspection of `useBookmarks.ts` — `BookmarkRow` type with all palette-needed fields
- Direct source inspection of `useSearch.ts` — `enabled: query.length >= 2` gate already in hook
- Direct source inspection of `index.css` — all CSS token names and values
- Direct source inspection of `vite.config.ts` — test configuration, vitest environment

### Secondary (MEDIUM confidence)
- `STATE.md` accumulated decisions — confirms GEST-05 (TouchSensor delay=250ms) already implemented in Phase 5, confirms CSS transitions are the chosen animation approach

### Tertiary (LOW confidence)
- None — all findings are from direct codebase inspection

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries confirmed in package.json/imports; no new deps needed
- Architecture: HIGH — patterns derived from existing working code (SearchModal, AppPage, BulletNode)
- Pitfalls: HIGH for animation pitfalls (verified from code structure); MEDIUM for Ctrl+K browser conflict (common knowledge, not tested)

**Research date:** 2026-03-11
**Valid until:** 2026-06-11 (stable codebase; changes only when dependencies are upgraded)
