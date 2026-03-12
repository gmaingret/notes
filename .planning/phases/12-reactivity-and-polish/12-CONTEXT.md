# Phase 12: Reactivity and Polish - Context

**Gathered:** 2026-03-12
**Status:** Ready for planning

<domain>
## Phase Boundary

The app feels fast and native — swipe gestures for complete/delete, full-text search across documents and bullets, undo/redo via server-side stack (toolbar already wired in Phase 11), pull-to-refresh, loading/error states on all screens, bookmarks screen, attachment viewing/downloading, Material 3 dark theme, and animations. No new backend features — all APIs exist. Upload attachments deferred to v2.1.

</domain>

<decisions>
## Implementation Decisions

### Swipe Gestures
- Proportional reveal: icon + color background grow as finger moves
- Swipe right = complete (green background + checkmark icon); swipe left = delete (red background + trash icon)
- On threshold reached: row slides off screen in swipe direction (~200ms), then mutation fires
- Complete: row reappears with strikethrough + 50% opacity after slide-off
- Delete: row removed, subtree deleted (matches server DELETE behavior). No confirmation dialog — undo is sufficient
- Swipe right toggles: swiping right on a completed bullet un-completes it
- Swipe disabled while a bullet is focused (keyboard open) to prevent accidental swipes during editing
- Haptic feedback on threshold reached

### Search UX
- TopAppBar search icon (magnifying glass) — tapping opens inline search bar replacing the title area (Material 3 SearchBar pattern)
- Back arrow to close search, X button to clear query
- 300ms debounce on input before firing GET /api/search?q=...
- Results grouped by document name — each result shows bullet content with query term highlighted
- Tapping a result: closes search, opens target document, scrolls to matching bullet, brief highlight flash (~1s)
- Tapping a chip (#tag, @mention, !!date) in unfocused bullet opens search pre-filled with that chip's text

### Bookmarks Screen
- Accessed via "Bookmarks" item in the navigation drawer, positioned above the document list with a separator
- Dedicated screen with list of bookmarked bullets
- Each row shows: bullet content as primary text, document name as secondary text below
- Tapping a bookmarked bullet navigates to it in its document (same behavior as search result tap)
- Bookmark a bullet via long-press context menu on any bullet row → "Bookmark" / "Remove bookmark"
- Long-press context menu (stationary hold, no movement) also includes: "Attachments", "Delete"
- Bookmarked bullets show a small filled star icon next to the bullet dot in the tree view

### Attachments View
- Images render inline as thumbnails below the bullet content
- Non-image files show as file-type icon + tappable filename + file size below the bullet
- Tapping any attachment triggers system download via Android DownloadManager → open with system file handler (Downloads folder)
- Paperclip icon indicator on bullets that have attachments (similar to StickyNote2 note indicator pattern)
- Long-press context menu "Attachments" item expands/collapses the attachment list inline below the bullet (toggle)
- Image thumbnails lazy-loaded with Coil library, gray placeholder while loading
- Upload deferred to v2.1

### UI Size
- Increase overall UI size slightly — larger text and touch targets across the app

### Claude's Discretion
- Dark theme implementation (Material 3 system preference, seed color #2563EB — already decided in Phase 9)
- Loading/error states (skeleton shimmer + error-with-retry — pattern from Phases 10-11, apply consistently)
- Pull-to-refresh (Material 3 PullToRefreshBox on document list and bullet tree)
- Animations (AnimatedVisibility, animateItemPlacement, Crossfade — standard Material 3 motion)
- Exact swipe threshold distance and animation curves
- Search bar transition animation details
- Bookmark star icon exact size and positioning
- Coil configuration (cache size, placeholder style)
- Attachment thumbnail dimensions
- Context menu styling and positioning
- Exact UI size increases (font scale, padding adjustments, touch target sizing)

</decisions>

<specifics>
## Specific Ideas

- Swipe pattern matches the web app's exitDirection + slide-off approach — same mental model across platforms
- Search grouped by document matches web app's quick-open palette grouped results
- Long-press context menu serves triple duty: bookmark, attachments, delete — keeps the UI clean without extra toolbar buttons
- Chip-to-search link makes tags/mentions/dates actually useful for navigation on Android
- Bookmarks in drawer rather than bottom nav keeps the app's single-screen-with-drawer architecture intact

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BulletRow.kt`: Direct modification point for swipe gestures (wrap in SwipeToDismissBox), attachment display, bookmark indicator, and context menu
- `BulletTreeViewModel.kt`: Already has `deleteBullet()`, undo/redo, operation queue — add `toggleComplete()`, search state
- `BulletTreeScreen.kt`: LazyColumn + Reorderable — add SwipeToDismissBox per item, pull-to-refresh wrapper
- `MainScreen.kt`: TopAppBar modification point for search icon, drawer content for bookmarks item
- `DocumentDrawerContent.kt`: Add bookmarks entry above document list
- `BulletApi.kt`: Already has patch (for isComplete toggle), delete — need search and bookmark endpoints added
- `InlineChip` in `BulletRow.kt`: Currently non-tappable — add clickable modifier to trigger search
- `NoteField.kt`: Pattern for inline expandable content below bullet — same approach for attachment list

### Established Patterns
- Clean Architecture: data/domain/presentation with use cases as `operator fun invoke()`
- Optimistic update + snackbar + revert (Phases 10-11) — apply to swipe complete/delete
- StateFlow in ViewModels, collected with `collectAsState()` in Composables
- Hilt DI: NetworkModule provides Retrofit — add SearchApi, BookmarkApi, AttachmentApi
- `Response<Unit>` for 204 endpoints (bookmarks DELETE returns 204)
- Operation queue (Channel<suspend () -> Unit>) serializes server calls
- Content/note debounce pattern (MutableSharedFlow + debounce) — reuse for search input

### Integration Points
- Backend APIs (all existing, no changes):
  - `GET /api/search?q=...` — full-text search across user's bullets
  - `GET /api/bookmarks` — list user's bookmarks
  - `POST /api/bookmarks` — add bookmark (body: { bulletId })
  - `DELETE /api/bookmarks/:bulletId` — remove bookmark (returns 204)
  - `GET /api/attachments/bullets/:bulletId` — get attachments for a bullet
  - `GET /api/attachments/:id/download` — download attachment file
  - `PATCH /api/bullets/:id` with `{ isComplete: true/false }` — toggle complete
  - `POST /api/undo`, `POST /api/redo`, `GET /api/undo/status` — already wired
- Navigation: may need new route for BookmarksScreen, or render inline in MainScreen content area
- Theme.kt: Dark theme colors auto-generated from seed #2563EB via Material 3 dynamic color

</code_context>

<deferred>
## Deferred Ideas

- Upload attachments from Android — v2.1
- Tag browser sidebar — v2.1
- Inline image rendering in bullet content (markdown images) — v2.1
- Physical keyboard shortcuts (Tab, Ctrl+arrows) — v2.1
- Export document(s) as Markdown — v2.1

</deferred>

---

*Phase: 12-reactivity-and-polish*
*Context gathered: 2026-03-12*
