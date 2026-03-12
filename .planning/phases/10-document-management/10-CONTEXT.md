# Phase 10: Document Management - Context

**Gathered:** 2026-03-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can manage their document list in a native Android ModalNavigationDrawer and navigate between documents. Full CRUD (create, rename, delete), drag reorder, last-opened persistence, and cold start flow. No backend changes — all APIs exist. Content area is a placeholder (bullet tree editing is Phase 11).

</domain>

<decisions>
## Implementation Decisions

### Drawer Type & Behavior
- ModalNavigationDrawer (slides over content with scrim overlay)
- Opens via hamburger icon tap only — left-edge swipe gesture DISABLED (avoids conflict with Phase 12 bullet swipe gestures)
- Auto-closes when user taps a document
- Header: "Notes" title
- Footer: "+ New document" button, pinned at bottom (does not scroll with list)
- Logout stays in TopAppBar overflow menu (not in drawer)

### Document Row
- Title only — no dates, no bullet counts
- Selected document highlighted with filled background tint (Material 3 NavigationDrawerItem primaryContainer style)
- Trailing ⋮ icon on each row opens context menu (Rename / Delete)
- Document list is scrollable independently of header/footer

### TopAppBar
- Title shows the currently selected document name (not always "Notes")
- Tapping the title does nothing (title is display-only, not editable)
- Hamburger icon on the left, overflow ⋮ on the right (same as Phase 9 but functional)

### Create Document
- Tap "+ New document" in drawer footer
- New document appears at end of list with active inline TextField showing "Untitled" (pre-selected)
- User types name and presses done on keyboard
- Matches web app's inline rename pattern on create

### Rename Document
- Tap ⋮ on document row → context menu → "Rename"
- Title text becomes an editable inline TextField
- User edits and presses done on keyboard

### Delete Document
- Tap ⋮ on document row → context menu → "Delete"
- Confirmation AlertDialog: "Delete [doc name]? This will delete all bullets in this document." with Cancel / Delete buttons
- If deleting the currently open document: auto-open next document in list (or previous if last). If no docs remain, show empty state

### Drag Reorder
- Long-press anywhere on a document row to initiate drag (lifts the item)
- Haptic feedback (HapticFeedbackConstants.LONG_PRESS) fires when item lifts
- Visual feedback: elevation shadow + 1.02x scale on dragged item; other items animate to make room
- Auto-scroll at edges when dragging near top/bottom of visible list
- Any drop position commits (no cancel gesture)
- Optimistic update: UI reorders immediately, API call (PATCH /:id/position with afterId) fires in background. Revert + Snackbar on failure
- Backend uses FLOAT8 midpoint positioning — Android sends afterId (UUID of doc above, or null for first position)

### Empty & Loading States
- **No documents**: Centered "No documents yet" text with "+ Create document" button
- **Loading**: Skeleton shimmer rows (3-4 placeholder rows) in the drawer while documents load; content area shows CircularProgressIndicator
- **Error**: "Couldn't load documents" message with Retry button in the drawer

### Content Area Placeholder
- Selected document shows: document title as heading + subtle "content area" below
- Phase 11 will replace this with the bullet tree editor

### Document List Refresh
- Fetch fresh document list from API every time the drawer opens
- No caching, no pull-to-refresh — the list is always current on open

### Cold Start Flow
1. Splash screen (Phase 9 — icon on brand color)
2. Token refresh in background
3. Read lastDocId from DataStore
4. Navigate to MainScreen
5. Fetch document list + auto-open lastDocId
6. TopAppBar shows document name

### Last-Opened Persistence
- Save last-opened document ID to DataStore (survives app kills)
- On cold start, auto-open that document
- If last-opened doc was deleted (404): fall back to first document in the list. If no documents exist, show empty state
- Call POST /:id/open on document select (tracks lastOpenedAt server-side)

### Claude's Discretion
- Exact spacing, padding, and typography in drawer
- Skeleton shimmer implementation details
- ViewModel state management structure (single vs split ViewModels)
- Drag-and-drop library choice or custom implementation
- Exact animation curves and durations
- How inline rename TextField handles keyboard dismiss vs confirm

</decisions>

<specifics>
## Specific Ideas

- Drawer matches the mockup: "Notes" header, scrollable doc list with selection highlight, pinned "+ New document" at bottom
- Document row interaction model: tap = open doc, long-press = drag, trailing ⋮ = context menu (Rename/Delete)
- Create flow matches the web app: new doc appears with inline editable name

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `MainScreen.kt`: Already has Scaffold + TopAppBar + hamburger icon placeholder — will be evolved into the real drawer host
- `MainViewModel.kt`: Has logout logic + userEmail state — will be extended with document list state
- `AuthInterceptor.kt` + `TokenAuthenticator.kt`: Bearer token injection and refresh already working — all API calls will use the same OkHttp client
- `TokenStore.kt` + `DataStore`: Can store lastDocId alongside existing token storage
- `NavRoutes.kt`: Navigation3 with `AuthRoute` / `MainRoute` — no new routes needed, MainRoute stays

### Established Patterns
- Clean Architecture: data/domain/presentation layers with use cases as separate classes with `operator fun invoke()`
- Hilt DI: NetworkModule provides Retrofit/OkHttp, DataModule provides DataStore — new DocumentApi + DocumentRepository follow same pattern
- Kotlin `Result<T>` for error handling (not custom sealed classes)
- StateFlow in ViewModels, collected in Composables

### Integration Points
- Backend API: `GET /api/documents`, `POST /api/documents`, `PATCH /api/documents/:id`, `PATCH /api/documents/:id/position`, `POST /api/documents/:id/open`, `DELETE /api/documents/:id` — all existing, no backend changes
- MainScreen evolves from placeholder to real drawer + content host
- TopAppBar title becomes dynamic (document name instead of "Notes")

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 10-document-management*
*Context gathered: 2026-03-12*
