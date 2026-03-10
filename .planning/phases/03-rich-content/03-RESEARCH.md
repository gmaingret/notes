# Phase 3: Rich Content - Research

**Researched:** 2026-03-09
**Domain:** Markdown rendering in contenteditable, chip syntax parsing, PostgreSQL full-text search, React UI patterns (sidebar tabs, modal search, bookmarks)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Per-bullet contenteditable model (locked from Phase 2 — no ProseMirror)
- Raw markdown shows while the cursor is in that bullet; renders on blur/Enter/Esc (BULL-10, Dynalist-style)
- **#tag chip click** → switch to Tags sidebar tab, show filtered bullet list for that tag
- **@mention chip click** → switch to Tags sidebar tab, show filtered bullet list for that mention
- **!!date chip click** → open date picker to edit the date
- **!! while typing** → opens native browser date input (no custom calendar library — zero dependencies)
- Tab bar at the top of the sidebar: **[Docs] [Tags]** (2 tabs, compact strip)
- Tags tab layout: grouped by type with headers — Tags (#), Mentions (@), Dates (!!)
- Within each group: items sorted by bullet count descending
- Filter input at the top of the Tags tab to narrow the list as you type
- **Clicking a tag/mention/date in the browser** → replaces the main canvas with a filtered bullet list
- Filtered view shows: bullet text + document name per row (e.g., `• Finish report    [Inbox]`)
- Clicking a bullet in filtered view → zoom to that bullet in its document
- **Return to document** → click any document in the Docs tab (no explicit close button in the filtered view)
- **Ctrl+F** (overrides KB-07 which said Ctrl+P)
- Centered modal overlay (Spotlight-style): input + results list below
- Empty state: just the search input — no recent searches, no suggestions
- Results show: bullet text (with matched terms highlighted) + document name
- Clicking a result: automatically switches document if needed and zooms to that bullet
- Search icon in the document toolbar (no-focus state) to trigger the modal
- Bookmarks triggered by: toolbar icon + Ctrl+* keyboard shortcut
- Bookmarks display: replaces the main canvas (same pattern as tag-filtered view)
- Row format: bullet text + document name (no bookmark date)
- Unbookmark from this view: click the filled bookmark icon on the row to toggle off
- Return to document: click any document in the Docs tab (same as tag-filtered view)

### Claude's Discretion
- Markdown rendering library choice and XSS sanitization approach
- Exact visual styling of chips (#tag = blue, @mention = purple, !!date = orange, or Claude decides)
- Chip parsing regex / detection while typing
- How the tab bar is styled (underline indicator, background highlight, etc.)
- Exact transition / animation when switching between Docs and Tags tabs
- Search debounce timing and min-char threshold
- How KB-06 (Ctrl+B = bold, Ctrl+I = italic) inserts markdown syntax into contenteditable

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within Phase 3 scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| BULL-09 | Bullet text supports inline markdown: **bold**, *italic*, ~~strikethrough~~, [links](url), ![image](url) | marked library parses these; dangerouslySetInnerHTML with DOMPurify or React element rendering |
| BULL-10 | Markdown renders on blur/Enter/Esc; raw markdown shows while editing (Dynalist-style live toggle) | focus/blur state drives conditional render in BulletContent; cursor preservation via save/restore Selection API |
| TAG-01 | `#tag` syntax creates a tag, renders as a clickable chip in bullet text | regex `/(?<!\w)#(\w+)/g` detected post-blur; spans rendered inside innerHTML; click handlers dispatch to sidebar |
| TAG-02 | `@mention` syntax renders as a clickable chip (personal label) | same pattern as TAG-01, regex `/(?<!\w)@(\w+)/g` |
| TAG-03 | `!!` syntax opens a date/time picker; renders as a 📅 date chip | `<input type="date">` programmatically clicked; result inserted as `!![YYYY-MM-DD]` text; chip rendered on blur |
| TAG-04 | Sidebar Tab 2: Tag Browser lists all unique #tags, @mentions, !!dates with bullet counts | GET /api/tags — SQL regex extraction or content scan; grouped response; new TagBrowser component |
| TAG-05 | Clicking a tag in the Tag Browser opens a filtered view of all matching bullets | GET /api/tags/:tag/bullets — returns bullets + doc names; canvas-replace view state in uiStore |
| SRCH-01 | User can search across all personal documents (full-text) | PostgreSQL ILIKE or websearch_to_tsquery; GET /api/search?q= |
| SRCH-02 | Search supports #tag, @mention, !!date query syntax | query-string parsing on server; strip # @ !! prefix then match in content |
| SRCH-03 | Search results show matching bullets; clicking a result opens it in zoomed focus view | results include bulletId + documentId; navigate to /doc/:docId#bullet/:bulletId |
| SRCH-04 | Search accessible via no-focus toolbar button and Ctrl+F keyboard shortcut | DocumentToolbar adds search icon; GlobalKeyboard handles Ctrl+F; modal state in uiStore |
| BM-01 | User can bookmark a bullet (via focus toolbar or context menu) | POST /api/bookmarks { bulletId }; new bookmarks table; useBookmarks hook |
| BM-02 | No-focus toolbar "Bookmarks" button shows a dedicated screen of all bookmarked bullets | GET /api/bookmarks; canvas-replace view in uiStore; BookmarksView component |
| BM-03 | Clicking a bookmark opens the bullet in zoomed focus view | same navigate pattern as search results |
</phase_requirements>

---

## Summary

Phase 3 adds rich content on top of the existing plain-text bullet tree. The three distinct problem domains are: (1) in-place markdown rendering inside a per-bullet contenteditable, (2) chip syntax parsing and the Tag Browser sidebar tab, and (3) full-text search and bookmarks backed by new server endpoints and a new `bookmarks` DB table.

The critical challenge is markdown rendering inside a contenteditable div. The existing `BulletContent.tsx` manages the DOM directly via `el.textContent`. To render markdown, the blur state must switch to a non-editable display element showing parsed HTML, while the edit state reverts to raw textContent. This is a **display mode swap**, not in-place contenteditable HTML editing — no cursor-in-HTML-nodes complexity.

For search, the database is already PostgreSQL. Using `ILIKE '%query%'` across all user bullets is acceptable for a personal self-hosted app (hundreds to thousands of bullets, not millions). The tag/mention/date query syntax (`#tag`, `@mention`, `!!date`) is handled by stripping prefixes server-side and matching content. No full-text search index is needed for this scale.

**Primary recommendation:** Use `marked` (v14+, ~20KB minified) with `DOMPurify` for markdown HTML generation rendered into a non-editable span on blur. Swap back to contenteditable div on focus. Keep chip rendering in the same blur-rendered HTML. Use PostgreSQL ILIKE for search. Add a `bookmarks` table with a migration.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| marked | ^14.x | Parse markdown string → HTML string | Smallest full-featured parser (~20KB min); simpler API than markdown-it; already in npm ecosystem |
| DOMPurify | ^3.x | Sanitize HTML before dangerouslySetInnerHTML | Industry standard XSS sanitizer; tiny; works in browser |
| (No new server deps) | - | Search via raw SQL + ILIKE | drizzle's `sql` template + ILIKE covers all search cases at this scale |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (none new) | - | All other features use existing stack | React Query, Zustand, Tailwind patterns already established |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| marked + DOMPurify | react-markdown | react-markdown renders React components, not HTML string — cannot be used inside a contenteditable's adjacent span without re-implementing the toggle; marked + DOMPurify is simpler for this specific pattern |
| marked + DOMPurify | markdown-it | markdown-it is 768KB vs marked ~20KB minified; no advantage at this feature level |
| ILIKE search | PostgreSQL tsvector / GIN index | tsvector excels at millions of rows and multi-word relevance ranking; personal outliner with thousands of bullets doesn't need it; ILIKE is simpler, no migration complexity |

**Installation:**
```bash
# In client/
npm install marked dompurify
npm install --save-dev @types/dompurify

# Server: no new dependencies needed
```

---

## Architecture Patterns

### Recommended Project Structure (new files only)

```
client/src/
├── components/
│   ├── DocumentView/
│   │   ├── BulletContent.tsx        # MODIFY: add markdown toggle + chip rendering
│   │   ├── DocumentToolbar.tsx      # MODIFY: add search icon + bookmarks icon
│   │   ├── DocumentView.tsx         # MODIFY: add canvas-replace view states
│   │   ├── FilteredBulletList.tsx   # NEW: shared list component (tags, bookmarks, search results)
│   │   └── SearchModal.tsx          # NEW: Ctrl+F modal overlay
│   └── Sidebar/
│       ├── Sidebar.tsx              # MODIFY: add tab bar + conditional render
│       └── TagBrowser.tsx           # NEW: Tags tab content
├── hooks/
│   ├── useBookmarks.ts              # NEW: React Query hooks for bookmarks
│   ├── useTags.ts                   # NEW: React Query hooks for tag browser + filtered bullets
│   └── useSearch.ts                 # NEW: React Query hook for search
└── store/
    └── uiStore.ts                   # MODIFY: add sidebarTab, canvasView, searchOpen

server/src/
├── services/
│   ├── bookmarkService.ts           # NEW: CRUD for bookmarks
│   ├── tagService.ts                # NEW: tag extraction queries
│   └── searchService.ts             # NEW: ILIKE search query
└── routes/
    ├── bookmarks.ts                 # NEW: /api/bookmarks routes
    ├── tags.ts                      # NEW: /api/tags routes
    └── search.ts                    # NEW: /api/search route

server/db/
└── migrations/
    └── 0001_bookmarks.sql           # NEW: bookmarks table
```

### Pattern 1: Markdown Toggle in BulletContent (BULL-09, BULL-10)

**What:** Swap between a raw `contenteditable` div (edit mode) and a `dangerouslySetInnerHTML` span (view mode) based on focus state.

**When to use:** Every bullet — markdown renders on blur, raw on focus.

**Key insight:** Do NOT try to render markdown inside the contenteditable itself. That breaks cursor position handling (the codebase already manages `textContent` directly). Instead, render a sibling element in view mode and hide the contenteditable, then swap on focus.

**Example:**
```tsx
// Source: verified pattern from existing BulletContent.tsx + marked/DOMPurify docs
import { marked } from 'marked';
import DOMPurify from 'dompurify';
import { useState, useRef } from 'react';

// Configure marked once (module level)
marked.setOptions({ breaks: false, gfm: true });

function BulletContent({ bullet, ... }) {
  const [isEditing, setIsEditing] = useState(false);
  const divRef = useRef<HTMLDivElement>(null);

  // Rendered HTML: parse markdown + sanitize
  const renderedHtml = DOMPurify.sanitize(
    marked.parse(bullet.content) as string,
    { ALLOWED_TAGS: ['strong', 'em', 'del', 'a', 'img', 'span', 'p', 'code'] }
  );

  function handleFocus() {
    setIsEditing(true);
    // Must set textContent after switching to editable mode
    setTimeout(() => {
      if (divRef.current) {
        divRef.current.textContent = localContent;
        divRef.current.focus();
      }
    }, 0);
  }

  function handleBlur() {
    // Save content first, then switch to view mode
    const content = divRef.current?.textContent ?? '';
    saveContent(content);
    setIsEditing(false);
  }

  if (!isEditing) {
    return (
      <span
        dangerouslySetInnerHTML={{ __html: renderedHtml }}
        onClick={() => setIsEditing(true)}
        style={{ flex: 1, fontSize: '0.9375rem', cursor: 'text', lineHeight: 1.6 }}
      />
    );
  }

  return (
    <div
      ref={divRef}
      contentEditable
      suppressContentEditableWarning
      onBlur={handleBlur}
      // ...existing keyboard handlers unchanged
    />
  );
}
```

**Critical detail:** The existing `BulletContent` already has `localContent` state and `divRef`. The `isEditing` flag maps to whether `document.activeElement === divRef.current`. On Enter/Esc keydown, call `divRef.current.blur()` to trigger the existing save flow and switch to view mode.

### Pattern 2: Chip Rendering (TAG-01, TAG-02, TAG-03)

**What:** During the markdown parse step (blur state), detect `#tag`, `@mention`, and `!![date]` tokens and replace them with styled `<span>` elements that have click handlers.

**When to use:** Part of the view-mode render pipeline — happens alongside markdown parsing.

**Implementation approach:** After `marked.parse()` produces HTML, run a second pass with regex replacement to wrap chip tokens in styled spans. React event delegation handles clicks (since `dangerouslySetInnerHTML` elements don't get React event handlers directly, use `onClick` on the wrapper span and check `event.target.dataset`).

```tsx
// Source: standard DOM event delegation pattern
function renderWithChips(markdownHtml: string): string {
  // These replacements run on the markdown-parsed HTML string
  // Order: replace inside text nodes only (after HTML is generated)
  return markdownHtml
    // #tag — word boundary, not inside href attributes
    .replace(/(?<![="])#([a-zA-Z0-9_]+)/g,
      '<span class="chip chip-tag" data-chip-type="tag" data-chip-value="$1">#$1</span>')
    // @mention
    .replace(/(?<![="])@([a-zA-Z0-9_]+)/g,
      '<span class="chip chip-mention" data-chip-type="mention" data-chip-value="$1">@$1</span>')
    // !![YYYY-MM-DD] — already stored as !![date] after date picker
    .replace(/!!\[(\d{4}-\d{2}-\d{2})\]/g,
      '<span class="chip chip-date" data-chip-type="date" data-chip-value="$1">📅 $1</span>');
}

// On the wrapper span — event delegation
function handleRenderedClick(e: React.MouseEvent<HTMLSpanElement>) {
  const target = e.target as HTMLElement;
  const chipType = target.dataset.chipType;
  const chipValue = target.dataset.chipValue;
  if (!chipType || !chipValue) return;

  if (chipType === 'tag' || chipType === 'mention') {
    // Switch sidebar to Tags tab and show filtered view
    uiStore.setSidebarTab('tags');
    uiStore.setCanvasView({ type: 'filtered', chipType, chipValue });
  } else if (chipType === 'date') {
    // Open date picker to edit
    openDatePickerForBullet(bullet.id, chipValue);
  }
}
```

**!! typing trigger (TAG-03):** In `handleInput`, detect when `el.textContent` ends with `!!` (or has `!!` at cursor). Show a hidden `<input type="date">` and programmatically `.click()` it. On change, replace `!!` in content with `!![YYYY-MM-DD]`. Native browser date input — no library dependency.

```tsx
// In handleInput — detect !! trigger
if (content.includes('!!') && !content.includes('!![')) {
  // Find position of !! in text, show date input
  triggerDatePicker(bullet.id);
}

function triggerDatePicker(bulletId: string) {
  const input = document.createElement('input');
  input.type = 'date';
  input.style.cssText = 'position:fixed;opacity:0;pointer-events:none;';
  document.body.appendChild(input);
  input.addEventListener('change', () => {
    const date = input.value; // YYYY-MM-DD
    const el = divRef.current;
    if (el && date) {
      el.textContent = (el.textContent ?? '').replace('!!', `!![${date}]`);
      setLocalContent(el.textContent);
      scheduleSave(el.textContent);
    }
    document.body.removeChild(input);
  });
  input.addEventListener('blur', () => document.body.removeChild(input));
  input.click();
}
```

### Pattern 3: Sidebar Tab Bar (TAG-04)

**What:** Add a `[Docs] [Tags]` tab strip at the top of the existing Sidebar. Conditionally render DocumentList vs TagBrowser based on active tab.

**When to use:** Always rendered — the tab bar replaces the header section.

**State:** Add `sidebarTab: 'docs' | 'tags'` to uiStore. Non-persisted (always starts on Docs).

```tsx
// In Sidebar.tsx
const { sidebarTab, setSidebarTab } = useUiStore();

// Tab bar (replaces nothing — inserts above existing content)
<div style={{ display: 'flex', borderBottom: '1px solid #e0e0e0' }}>
  {(['docs', 'tags'] as const).map(tab => (
    <button
      key={tab}
      onClick={() => setSidebarTab(tab)}
      style={{
        flex: 1,
        padding: '0.5rem',
        border: 'none',
        background: 'none',
        cursor: 'pointer',
        fontSize: '0.8rem',
        fontWeight: sidebarTab === tab ? 600 : 400,
        borderBottom: sidebarTab === tab ? '2px solid #333' : '2px solid transparent',
        color: sidebarTab === tab ? '#111' : '#666',
      }}
    >
      {tab === 'docs' ? 'Docs' : 'Tags'}
    </button>
  ))}
</div>

{sidebarTab === 'docs' ? <DocumentList ... /> : <TagBrowser />}
```

### Pattern 4: Canvas-Replace Views (TAG-05, BM-01, BM-02)

**What:** Tag-filtered view and Bookmarks view both replace the main canvas. This is a view state, not a route change — no URL needed.

**When to use:** When user clicks a tag in the Tag Browser or the Bookmarks toolbar icon.

**State:** Add `canvasView: { type: 'document' } | { type: 'filtered'; chipType: string; chipValue: string } | { type: 'bookmarks' }` to uiStore.

**Returning to document:** Setting canvasView back to `{ type: 'document' }` happens when user clicks any doc in the Docs tab — the existing DocumentList click handler already navigates; just also reset canvasView.

**Shared list row component:** All three surfaces (tag-filtered, bookmarks, search results) use the same row format. Extract as `FilteredBulletRow`:

```tsx
type FilteredBulletRow = {
  bulletId: string;
  bulletContent: string;  // raw markdown text (not rendered)
  documentId: string;
  documentTitle: string;
  isBookmarked?: boolean;
  highlightText?: string;  // for search
};
```

### Pattern 5: Search Modal (SRCH-01 to SRCH-04)

**What:** Ctrl+F opens a centered modal overlay. Input with debounced search. Results list below.

**When to use:** User presses Ctrl+F or clicks search icon in DocumentToolbar.

**State:** Add `searchOpen: boolean` to uiStore.

**Ctrl+F interception:** In GlobalKeyboard handler (`useUndo.ts`), add the Ctrl+F case. Note: browsers intercept Ctrl+F natively — `e.preventDefault()` in a keydown handler DOES block the native find bar in Chrome/Firefox.

**Debounce:** 300ms after last keystroke, minimum 2 characters. This is well-established; at 300ms users don't perceive lag on typical network.

**Search highlighting:** Bold/mark the matched substring in results. Simple approach: `content.replace(new RegExp(escapeRegex(query), 'gi'), '<mark>$&</mark>')` — render via dangerouslySetInnerHTML in the result row (safe because it's a generated internal string, not user-controlled HTML).

### Anti-Patterns to Avoid

- **Rendering markdown inside contenteditable:** Setting innerHTML on a contenteditable div breaks cursor position (the codebase uses textContent-only). Always swap to a non-editable element for view mode.
- **Storing tag/mention data in a separate table:** Tags don't need a `bullet_tags` table. Extract them at query time from `content` using PostgreSQL `regexp_matches`. Avoids denormalization and sync complexity.
- **Using ProseMirror or slate.js for chip autocomplete:** The contenteditable model is locked. Chips are view-only decorations rendered on blur, not in-editor nodes.
- **Using Ctrl+P for search:** CONTEXT.md locks Ctrl+F (overrides the KB-07 Ctrl+P that was spec'd in REQUIREMENTS.md).
- **Chip click handlers in dangerouslySetInnerHTML spans:** React event handlers attached to `dangerouslySetInnerHTML` elements don't fire. Use event delegation on the wrapper element.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Markdown → HTML | Custom regex parser for bold/italic/links | `marked` npm package | Nested markdown, edge cases in links, and GFM strikethrough are extremely hard to get right; marked handles ~100 edge cases |
| HTML sanitization | Custom allowlist filter | `DOMPurify` | XSS attack surface in markdown is enormous; DOMPurify is battle-tested against polyglot attacks |
| Date picker UI | Custom calendar component | Native `<input type="date">` | Zero dependency, mobile-friendly, works on iOS Safari; user decision locks this |
| Search tokenization | Custom word splitter | PostgreSQL ILIKE with `%term%` | Correct Unicode handling, case-insensitivity, accent handling all built in |

**Key insight:** Chip rendering is intentionally view-layer only (no schema changes). Tags are parsed from `content` on every query — this works perfectly at personal-outliner scale and avoids a sync problem where the tag table drifts from content.

---

## Common Pitfalls

### Pitfall 1: Cursor position lost when switching edit/view modes
**What goes wrong:** After `setIsEditing(false)` then `setIsEditing(true)`, the cursor is at position 0 or lost entirely.
**Why it happens:** The contenteditable div is unmounted and remounted; it starts with empty textContent before the useEffect runs.
**How to avoid:** On entering edit mode, set `divRef.current.textContent = localContent` in the same synchronous flow (or in a `useLayoutEffect`) before calling `.focus()`. Use a 0ms `setTimeout` trick only as last resort; `useLayoutEffect` is reliable.
**Warning signs:** Cursor jumps to start of text when clicking on a rendered bullet.

### Pitfall 2: Chip regex matches inside HTML attribute values
**What goes wrong:** A link like `[click #here](https://example.com/#anchor)` produces a chip inside the href attribute value.
**Why it happens:** The regex runs on the full HTML string including attributes.
**How to avoid:** Run chip replacement only inside text nodes. Use a lookahead to skip matches preceded by `="` or inside `href=`. The negative lookbehind `(?<![="/#])#(\w+)` covers most cases. Or parse the HTML with DOMPurify first, then walk text nodes.
**Warning signs:** Links break or chip spans appear inside href values.

### Pitfall 3: `marked.parse()` wraps inline text in `<p>` tags
**What goes wrong:** Bullet content `**hello**` becomes `<p><strong>hello</strong></p>` — the `<p>` causes a line break in an inline context.
**Why it happens:** `marked.parse()` treats input as a block-level document by default.
**How to avoid:** Use `marked.parseInline()` for single-line bullet content. It returns inline HTML without wrapping `<p>` tags. Only use `marked.parse()` if multi-paragraph bullets are supported (they are not in this design).
**Warning signs:** Extra vertical space or paragraph breaks inside bullets.

### Pitfall 4: Ctrl+F blocked by browser before keydown fires
**What goes wrong:** On some browsers, the native find bar opens before the keydown handler can call `e.preventDefault()`.
**Why it happens:** Chrome and Firefox both intercept Ctrl+F at a lower level than DOM events in some contexts.
**How to avoid:** `e.preventDefault()` in a `keydown` handler reliably blocks the browser find bar in Chrome/Firefox/Safari. However, this only works when focus is inside the app (not in a native UI element). Test explicitly. The existing `GlobalKeyboard` hook uses `window.addEventListener('keydown', ...)` which runs before browser defaults.
**Warning signs:** Browser find bar opens at the same time as the app search modal.

### Pitfall 5: Tag extraction performance with LIKE on large content
**What goes wrong:** `SELECT content FROM bullets WHERE user_id = $1` then client-side regex is slow for users with thousands of bullets.
**Why it happens:** All bullet content fetched to extract tags.
**How to avoid:** Use PostgreSQL `regexp_matches(content, '#(\w+)', 'g')` in a server-side query. This keeps the work in the DB and avoids transferring content. For the tag browser aggregate query, use a CTE or subquery.
**Warning signs:** Tag Browser tab loads slowly for power users.

### Pitfall 6: `bookmarks` table not yet created
**What goes wrong:** Phase 3 ships code that calls `/api/bookmarks` but the migration hasn't run.
**Why it happens:** The schema.ts and migration files are separate; the DB migration must run on the server before the code works.
**How to avoid:** Wave 0 (first plan) must include the Drizzle schema addition and migration file for `bookmarks`. The migration runs automatically on server start via `drizzle-kit migrate`.
**Warning signs:** 500 errors on all bookmark endpoints.

---

## Code Examples

Verified patterns from official sources and existing codebase:

### Drizzle ILIKE search across bullets

```typescript
// Source: Drizzle ORM docs + existing bulletService.ts pattern
import { sql, ilike, and, eq, isNull, or } from 'drizzle-orm';
import { db } from '../../db/index.js';
import { bullets, documents } from '../../db/schema.js';

export async function searchBullets(userId: string, query: string) {
  // Strip chip prefixes for raw content matching
  const normalized = query.replace(/^[#@!]+/, '').trim();
  const pattern = `%${normalized}%`;

  return db
    .select({
      id: bullets.id,
      content: bullets.content,
      documentId: bullets.documentId,
      documentTitle: documents.title,
    })
    .from(bullets)
    .innerJoin(documents, eq(bullets.documentId, documents.id))
    .where(
      and(
        eq(bullets.userId, userId),
        isNull(bullets.deletedAt),
        ilike(bullets.content, pattern)
      )
    )
    .limit(50);
}
```

### PostgreSQL tag extraction (aggregate for Tag Browser)

```typescript
// Source: Drizzle sql`` template + PostgreSQL regexp_matches
export async function getTagCounts(userId: string) {
  // Extract all #tags with counts
  const result = await db.execute(sql`
    SELECT
      regexp_matches(content, '(?<![="])#([a-zA-Z0-9_]+)', 'g') AS tag,
      COUNT(*) AS bullet_count
    FROM bullets
    WHERE user_id = ${userId}
      AND deleted_at IS NULL
    GROUP BY tag
    ORDER BY bullet_count DESC
  `);
  return result.rows;
}
```

Note: `regexp_matches` with the `g` flag in a `SELECT` returns multiple rows per bullet (one per match). Wrap in a subquery or use `regexp_matches` in a lateral join for accuracy.

### Bookmarks schema addition (Drizzle)

```typescript
// Source: existing schema.ts pattern
export const bookmarks = pgTable('bookmarks', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  bulletId: uuid('bullet_id').notNull().references(() => bullets.id, { onDelete: 'cascade' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('bookmarks_user_id_idx').on(t.userId),
  // Prevent duplicate bookmarks
  // uniqueIndex('bookmarks_user_bullet_idx').on(t.userId, t.bulletId),
]);
```

### uiStore additions

```typescript
// Source: existing uiStore.ts pattern
type UiStore = {
  // ... existing fields ...
  sidebarTab: 'docs' | 'tags';
  setSidebarTab: (tab: 'docs' | 'tags') => void;
  canvasView: CanvasView;
  setCanvasView: (view: CanvasView) => void;
  searchOpen: boolean;
  setSearchOpen: (open: boolean) => void;
};

type CanvasView =
  | { type: 'document' }
  | { type: 'filtered'; chipType: 'tag' | 'mention' | 'date'; chipValue: string }
  | { type: 'bookmarks' };

// In the persist store:
sidebarTab: 'docs',
canvasView: { type: 'document' },
searchOpen: false,
```

Note: `sidebarTab` and `canvasView` should NOT be persisted (reset to document/docs on reload). `searchOpen` definitely not persisted. Remove them from the persist middleware name or use `partialize`.

### marked.parseInline usage

```typescript
// Source: marked npm docs
import { marked } from 'marked';
import DOMPurify from 'dompurify';

// One-time config at module level
marked.use({ breaks: false, gfm: true });

export function renderBulletMarkdown(content: string): string {
  // parseInline avoids wrapping <p> tags — correct for single-line bullets
  const html = marked.parseInline(content) as string;
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['strong', 'em', 'del', 'a', 'img', 'code', 'span'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'data-chip-type', 'data-chip-value', 'target'],
  });
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| marked sanitize option | marked dropped sanitize in v5; use DOMPurify separately | marked v5 (2023) | Must use separate DOMPurify; `marked.parse(str, { sanitize: true })` no longer valid |
| `marked.parse()` for inline | `marked.parseInline()` for inline content | marked v5+ | Avoids unwanted `<p>` wrapping on single-line inputs |
| PostgreSQL text search via LIKE | ILIKE for case-insensitive; tsvector for large scale | Always available | For personal scale (<10K bullets) ILIKE is sufficient and avoids GIN index complexity |

**Deprecated/outdated:**
- `marked({ sanitize: true })` option: removed in marked v5. Use DOMPurify.
- `marked.setOptions()` global config: still works but `marked.use()` is preferred in v14.
- `drizzle-orm 0.45.x` npm package: has missing index.cjs (locked decision from Phase 1 — must use 0.40.0).

---

## Open Questions

1. **Tag extraction via SQL regex vs. client-side parse**
   - What we know: PostgreSQL `regexp_matches` can extract tags server-side; alternatively the server could load all bullet content and run JS regex
   - What's unclear: Whether `regexp_matches` with `g` flag behaves correctly with the Drizzle `sql` template for the aggregate query
   - Recommendation: Use server-side SQL regex for the Tag Browser aggregate (efficiency); test in Wave 0 with a direct DB query before wrapping in service

2. **Unique constraint on bookmarks**
   - What we know: A user bookmarking the same bullet twice should be idempotent
   - What's unclear: Whether to use a UNIQUE constraint (clean schema) or upsert logic in the service
   - Recommendation: Add `UNIQUE (user_id, bullet_id)` constraint + use `INSERT ... ON CONFLICT DO NOTHING` in the service

3. **Ctrl+F interception in contenteditable context**
   - What we know: `window.addEventListener('keydown', handler)` fires before browser defaults; existing GlobalKeyboard uses this pattern
   - What's unclear: Edge case where cursor is in a contenteditable and browser intercepts before window keydown
   - Recommendation: Test on Chrome and Firefox explicitly; the existing `BulletContent.tsx` already handles Ctrl+Z in keydown which confirms the pattern works

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | vitest (server: ^3.0.8, client: ^4.0.18) |
| Config file | server/vitest.config.ts, client/vite.config.ts (test property) |
| Quick run command (server) | `ssh root@192.168.1.50 "cd /root/notes/server && .venv/bin/pytest --tb=short -q"` — Note: server uses vitest not pytest; correct cmd: `cd /root/notes/server && npx vitest run --reporter=dot` |
| Full suite command | client: `cd client && npx vitest run`; server: `npx vitest run` from server/ |

**Note on server test runner:** MEMORY.md shows pytest path but server package.json uses vitest. The server tests use vitest with supertest for HTTP route tests. Use `npx vitest run` in the server directory.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BULL-09 | `renderBulletMarkdown('**bold**')` returns HTML with `<strong>` | unit | `npx vitest run src/test/markdown.test.ts` | ❌ Wave 0 |
| BULL-10 | BulletContent renders view span on blur, contenteditable on focus | unit (pure logic) | `npx vitest run src/test/bulletContent.test.ts` | ❌ Wave 0 |
| TAG-01 | `renderWithChips` wraps `#tag` in chip span with data attrs | unit | `npx vitest run src/test/chips.test.ts` | ❌ Wave 0 |
| TAG-02 | `renderWithChips` wraps `@mention` in chip span | unit | same | ❌ Wave 0 |
| TAG-03 | `!!` in content triggers date picker flow; `!![date]` stored | manual | manual - browser interaction | manual only |
| TAG-04 | GET /api/tags returns grouped tags with counts | route | `npx vitest run tests/routes/tags.test.ts` (server) | ❌ Wave 0 |
| TAG-05 | GET /api/tags/:value/bullets returns filtered bullets | route | same | ❌ Wave 0 |
| SRCH-01 | GET /api/search?q=term returns matching bullets | route | `npx vitest run tests/routes/search.test.ts` (server) | ❌ Wave 0 |
| SRCH-02 | GET /api/search?q=#tag strips # and matches content | route | same | ❌ Wave 0 |
| SRCH-03 | Clicking result navigates to /doc/:id#bullet/:id | manual | manual - browser navigation | manual only |
| SRCH-04 | Ctrl+F opens search modal; search icon in toolbar opens it | manual | manual - keyboard + visual | manual only |
| BM-01 | POST /api/bookmarks creates bookmark; DELETE removes | route | `npx vitest run tests/routes/bookmarks.test.ts` (server) | ❌ Wave 0 |
| BM-02 | GET /api/bookmarks returns user's bookmarks with doc titles | route | same | ❌ Wave 0 |
| BM-03 | Clicking bookmark navigates to bullet in zoomed view | manual | manual - browser navigation | manual only |

### Sampling Rate
- **Per task commit:** Run the specific new test file (e.g., `npx vitest run tests/routes/bookmarks.test.ts`)
- **Per wave merge:** Full server suite + client suite
- **Phase gate:** All automated tests green + manual verification of SRCH-03, SRCH-04, BM-03, TAG-03 on production

### Wave 0 Gaps

- [ ] `client/src/test/markdown.test.ts` — covers BULL-09: `renderBulletMarkdown` unit tests
- [ ] `client/src/test/chips.test.ts` — covers TAG-01, TAG-02: `renderWithChips` unit tests
- [ ] `server/tests/routes/tags.test.ts` — covers TAG-04, TAG-05: tag extraction route tests
- [ ] `server/tests/routes/search.test.ts` — covers SRCH-01, SRCH-02: search route tests
- [ ] `server/tests/routes/bookmarks.test.ts` — covers BM-01, BM-02: bookmark CRUD route tests
- [ ] DB migration: `server/db/migrations/0001_bookmarks.sql` — must exist before any bookmark test can run
- [ ] Schema update: add `bookmarks` table to `server/db/schema.ts`
- [ ] Install dependencies: `cd client && npm install marked dompurify @types/dompurify`

---

## Sources

### Primary (HIGH confidence)
- Drizzle ORM official docs (orm.drizzle.team/docs/guides/postgresql-full-text-search) — ILIKE and sql template patterns verified
- Existing codebase (`BulletContent.tsx`, `uiStore.ts`, `bulletService.ts`, `schema.ts`) — integration points verified by direct inspection
- marked npm docs (marked.js.org) — `marked.parseInline()` and `marked.use()` API verified

### Secondary (MEDIUM confidence)
- WebSearch: marked ~20KB min vs markdown-it 768KB (via bundlephobia.com comparison, March 2026)
- WebSearch: DOMPurify as standard XSS sanitizer for marked output (multiple security sources agree)
- WebSearch: `useLayoutEffect` for cursor restoration after dangerouslySetInnerHTML mutation (multiple sources including React GitHub issues)
- WebSearch: PostgreSQL GIN indexes for tsvector — confirmed as standard approach but not needed at this scale

### Tertiary (LOW confidence)
- WebSearch: Exact behavior of `regexp_matches` with Drizzle `sql` template for tag extraction aggregate — needs empirical testing in Wave 0

---

## Metadata

**Confidence breakdown:**
- Standard stack (marked + DOMPurify): HIGH — bundle size confirmed via bundlephobia, API verified via docs
- Architecture (markdown toggle, chip delegation): HIGH — based on direct inspection of existing BulletContent.tsx and established DOM event patterns
- Pitfalls: HIGH for cursor/marked issues (known issues from React GitHub + marked changelogs); MEDIUM for chip regex edge cases
- Search implementation: HIGH — ILIKE with Drizzle sql template verified from official Drizzle docs
- Tag extraction SQL: MEDIUM — pattern confirmed from PostgreSQL docs but Drizzle sql template usage for this specific query needs validation

**Research date:** 2026-03-09
**Valid until:** 2026-06-09 (stable libraries; marked and DOMPurify APIs rarely break)
