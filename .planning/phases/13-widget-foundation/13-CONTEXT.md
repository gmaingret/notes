# Phase 13: Widget Foundation - Context

**Gathered:** 2026-03-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can place a Notes widget on their Android home screen, pick a document via a config activity, and see its root-level bullets rendered as a scrollable flat list with correct display states (loading, error, empty, content). No background sync, no add/delete actions — those come in Phases 14-15.

</domain>

<decisions>
## Implementation Decisions

### Widget Visual Layout
- Rounded card shape (Material 3 Glance card)
- No header, no title — pure bullet list filling the widget
- No branding (no app icon, no accent border) — clean card only
- Follow system dark/light mode automatically (consistent with app's system-preference approach)
- Tapping anywhere on the widget opens the document in the full Notes app
- Default size: 4x2 minimum, user-resizable

### Document Picker
- Full-screen Activity with scrollable list of document names (name only, no metadata)
- Tap a document to instantly confirm and close — no separate confirm button
- Uses app's NotesTheme (Material 3, #2563EB seed, dark mode support)
- If user isn't logged in: show login form inline (email/password + Google SSO) before document list
- Reconfigurable via Android long-press widget menu (re-opens picker to change document)

### Bullet Row Display
- Each row: bullet dot + text content only
- Completed bullets: strikethrough + reduced opacity (matching app behavior)
- Long text: single line, truncated with ellipsis
- Basic formatting: render bold and strikethrough (Glance SpannableString). Skip links, chips, italic
- Plain text for markdown syntax that can't render (strip #tags, @mentions, !!dates syntax)

### Widget Sizing
- SizeMode.Responsive with breakpoints (already decided in research)
- Larger widget = more visible rows; font size and padding stay constant across all sizes

### Scroll Behavior
- Bullet list is scrollable within the widget via Glance LazyColumn

### Empty State
- Centered "No bullets yet" text inside the widget card

### Loading State
- 3-4 placeholder shimmer rows mimicking bullet rows (dots + gray rectangles)

### Error State
- "Couldn't load — tap to retry" centered in widget. Tapping triggers refresh

### Deleted Document State
- "Document not found" + reconfigure prompt (tap to re-open document picker)

### Session Expired State
- "Session expired" + tap to re-login (opens full app or config activity to re-authenticate)

### Claude's Discretion
- Exact shimmer animation implementation in Glance
- Responsive breakpoint DpSize values
- Exact card corner radius, padding, row spacing
- Font size and text styling details
- Bullet dot size and color
- Strikethrough + opacity values for completed bullets
- SpannableString implementation for bold/strikethrough rendering
- Login form layout in config activity
- How reconfigure re-opens the picker (appwidget-provider reconfigurable flag)

</decisions>

<specifics>
## Specific Ideas

- No header at all — user explicitly wants maximum space for bullets, no title row
- Widget is a clean, minimal bullet list card — the user knows which document they picked
- The widget should feel like a "glanceable list" — grocery list / quick tasks use case
- Tap-to-open-app on any part of the widget provides the escape hatch to the full editor

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NotesTheme` (Theme.kt): Material 3 theme with seed #2563EB, system dark mode — reuse for config activity
- `GetDocumentsUseCase`: Fetches document list — reuse in config activity
- `DocumentRepository` / `DocumentApi`: Retrofit API for document list — widget data fetching
- `BulletApi` / `BulletRepository`: Existing bullet fetch endpoints — widget reads root bullets
- `EncryptedDataStoreFactory`: Tink AES256-GCM encryption — WidgetStateStore will use same pattern
- `TokenStore` / `DataStoreCookieJar`: Auth token persistence — widget auth reads from same store
- `AuthInterceptor` / `TokenAuthenticator`: OkHttp auth chain — config activity can reuse

### Established Patterns
- Clean Architecture: data/domain/presentation with use cases as `operator fun invoke()`
- Hilt DI: NetworkModule (Retrofit), DataModule (repositories) — widget uses @EntryPoint pattern instead of @AndroidEntryPoint
- StateFlow in ViewModels, collected with `collectAsState()` — config activity follows same pattern
- Kotlin `Result<T>` for all repository/use case error handling

### Integration Points
- `NotesApplication.kt`: Must add `Configuration.Provider` + `HiltWorkerFactory` injection (for Phase 14, but setup needed now)
- `AndroidManifest.xml`: Register widget receiver, config activity, appwidget-provider XML
- `BulletApi.kt`: GET /api/documents/:id/bullets endpoint for root bullet fetch
- `DocumentApi.kt`: GET /api/documents endpoint for config activity document list
- Widget DataStore: new WidgetStateStore singleton (custom, not Glance PreferencesGlanceStateDefinition)

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 13-widget-foundation*
*Context gathered: 2026-03-14*
