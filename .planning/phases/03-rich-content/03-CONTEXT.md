# Phase 3: Rich Content - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Bullet text comes alive with inline markdown rendering, clickable #tag/@mention/!!date syntax chips, a Tag Browser sidebar tab, full-text search modal, and a bookmarks screen. No new structural features — all content enhancements on the existing bullet tree from Phase 2. Comments, attachments, and mobile touch gestures are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Markdown rendering
- Per-bullet contenteditable model (locked from Phase 2 — no ProseMirror)
- Raw markdown shows while the cursor is in that bullet; renders on blur/Enter/Esc (BULL-10, Dynalist-style)
- Claude handles library choice and XSS-safe rendering approach

### Chip syntax (#tag, @mention, !!date)
- Chips render in bullet text when not being edited (consistent with markdown toggle)
- **#tag chip click** → switch to Tags sidebar tab, show filtered bullet list for that tag
- **@mention chip click** → switch to Tags sidebar tab, show filtered bullet list for that mention
- **!!date chip click** → open date picker to edit the date
- **!! while typing** → opens native browser date input (no custom calendar library — zero dependencies)

### Sidebar tab system
- Tab bar at the top of the sidebar: **[Docs] [Tags]** (2 tabs, compact strip)
- Tags tab layout: grouped by type with headers — Tags (#), Mentions (@), Dates (!!)
- Within each group: items sorted by bullet count descending
- Filter input at the top of the Tags tab to narrow the list as you type
- **Clicking a tag/mention/date in the browser** → replaces the main canvas with a filtered bullet list
- Filtered view shows: bullet text + document name per row (e.g., `• Finish report    [Inbox]`)
- Clicking a bullet in filtered view → zoom to that bullet in its document
- **Return to document** → click any document in the Docs tab (no explicit close button in the filtered view)

### Search UI
- **Ctrl+F** (overrides KB-07 which said Ctrl+P — user decision)
- Centered modal overlay (Spotlight-style): input + results list below
- Empty state: just the search input — no recent searches, no suggestions
- Results show: bullet text (with matched terms highlighted) + document name
- Clicking a result: automatically switches document if needed and zooms to that bullet (no confirmation prompt)
- Search icon in the document toolbar (no-focus state) to trigger the modal

### Bookmarks screen
- Triggered by: toolbar icon + Ctrl+* keyboard shortcut (KB-07)
- Display: replaces the main canvas (same pattern as tag-filtered view — consistent)
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

</decisions>

<specifics>
## Specific Ideas

- Markdown toggle behavior explicitly matches Dynalist: raw while editing, rendered otherwise (BULL-09/10 requirements confirm this)
- !! date picker uses native browser date input — zero library dependency, works on mobile
- All list views (tag-filtered, bookmarks, search results) use the same row format: bullet text + [Document Name]
- This visual consistency across views is deliberate — one list component, three surfaces

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BulletContent.tsx`: Per-bullet contenteditable — this is where markdown toggle logic and chip rendering will hook in. Already handles cursor tracking, keyboard events, and split/merge operations.
- `DocumentToolbar.tsx`: Existing toolbar — search icon and bookmarks icon go here (no-focus state)
- `Sidebar.tsx`: Fixed 240px sidebar, no tabs yet — needs tab bar added at top
- `apiClient` from `src/api/client`: Established pattern for all server calls — search and bookmarks API hooks follow same shape
- `useDocuments.ts` / `useBullets.ts`: React Query pattern with optimistic updates — tag/bookmark hooks follow same pattern

### Established Patterns
- React Query for all server state (useQuery / useMutation + invalidateQueries)
- Optimistic updates on mutations (onMutate apply, onError rollback, onSettled revalidate)
- Zustand + persist for UI state (uiStore.ts) — sidebar tab active state can go here
- Plain contenteditable per bullet (not ProseMirror) — markdown must work within this constraint
- Dynalist/Workflowy minimal aesthetic — chips and rendered markdown should be subtle, not colorful/heavy

### Integration Points
- `Sidebar.tsx` → add tab bar + conditional render of DocumentList vs TagBrowser
- `BulletContent.tsx` → add markdown parse/render toggle on focus/blur + chip detection
- `DocumentView.tsx` → handle canvas-replace for tag-filtered view and bookmarks screen (new view states)
- Server: new routes needed for `/search`, `/bookmarks`, `/tags` (aggregate tag/mention/date counts)
- Schema: `bookmarks` table needed (userId, bulletId) — not yet in schema
- Schema: `bullet_tags` or tag extraction may be done at query time from bullet content (no schema change needed if parsed on read)

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 3 scope.

</deferred>

---

*Phase: 03-rich-content*
*Context gathered: 2026-03-09*
