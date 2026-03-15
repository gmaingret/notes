# Phase 15: Interactive Actions - Context

**Gathered:** 2026-03-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can add and delete bullets directly from the home screen widget without opening the app. A "+" button in the widget header opens a lightweight overlay dialog to create a new root bullet at the top of the list. A delete icon on each bullet row removes that bullet instantly. No drag-reorder, no nesting, no editing from widget — those require the full app.

</domain>

<decisions>
## Implementation Decisions

### Widget Layout (supersedes Phase 13 "no header" decision)
- Header row: document title (left-aligned, single-line truncated with ellipsis) + [+] icon (right-aligned)
- Thin divider line between header and bullet list
- Same background throughout header and list (no distinct header shade)
- Tapping the document title opens the document in the full Notes app
- Bullet rows: dot + text + bare × icon (right-aligned)
- Delete icon only appears on actual bullet rows (not empty/loading/error states)
- Empty state: centered "No bullets yet" text (no add prompt — header [+] is sufficient)

### Add Bullet UX
- Tapping [+] opens a transparent overlay Activity (dialog-themed) over the home screen
- Uses NotesTheme (Material 3, #2563EB seed, dark mode follows system)
- No dialog title — just a single-line text field with placeholder + Cancel button below
- Keyboard auto-shows, text field pre-focused
- Enter key confirms: creates bullet and closes dialog
- No [Add] button — Enter is the only way to confirm
- Tapping outside the dialog (dimmed background) dismisses it (same as Cancel)
- Empty submit ignored — pressing Enter with nothing typed does nothing
- New bullet created as root-level (parentId=null), inserted at top of list (first position)
- Widget auto-scrolls to top after adding to show the new bullet
- On failure: dialog stays open with typed text preserved, toast error message

### Delete Bullet UX
- Always-visible bare × icon on every bullet row (no circular background)
- × icon color matches bullet text color (same gray/white depending on theme)
- One tap deletes — no confirmation dialog
- Completed bullets treated identically to active bullets for deletion
- Instant removal (no fade-out animation)
- Tapping bullet text/dot area opens document in full app (document level, not specific bullet)

### Optimistic Updates
- Add: dialog closes immediately, new bullet appears optimistically in widget at top
- Delete: bullet row vanishes instantly from widget
- On add failure: dialog stays open (text preserved), toast "Couldn't add bullet"
- On delete failure: bullet reappears in original position, toast "Couldn't delete"
- No visual highlight on rollback — just toast

### Error Handling
- Toast messages for all action failures (network error, auth expired)
- Add dialog stays open on failure for retry
- Delete failure: bullet reappears + toast
- Auth expiry detected naturally via failed API call (no upfront auth check)
- [+] button always enabled regardless of widget state (loading/error/expired)
- Session expired after action failure: toast "Session expired", widget transitions to expired state

### Claude's Discretion
- Exact overlay Activity implementation (Theme.Translucent, windowIsFloating, etc.)
- Cancel button styling and positioning
- Text field outline/underline styling
- Dimmed background opacity
- ActionCallback implementation for add/delete in Glance
- How optimistic state is managed in WidgetStateStore
- Rollback timing and mechanism
- Toast message exact wording
- How to trigger widget updateAll() after action completes
- Whether add action goes through ActionCallback → Activity or direct Activity launch

</decisions>

<specifics>
## Specific Ideas

- The widget with header now looks like: title + [+] in header, divider, bullet list with × on each row
- The add dialog is ultra-minimal: no title, just text field + Cancel. Enter confirms. Keyboard auto-shows.
- "Let it fail naturally" philosophy for auth — don't check upfront, let the API call surface the error
- Optimistic updates for snappy feel, but preserve the user's typed text on add failure so they can retry without retyping
- Root-level only from widget — nesting, editing, and reordering require the full app

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CreateBulletUseCase`: `operator fun invoke(request: CreateBulletRequest): Result<Bullet>` — direct reuse for add action
- `DeleteBulletUseCase`: `operator fun invoke(id: String): Result<Unit>` — direct reuse for delete action
- `CreateBulletRequest(documentId, parentId=null, afterId=null, content)` — parentId=null for root, afterId=null for first position
- `BulletApi.createBullet()` / `BulletApi.deleteBullet()` — Retrofit endpoints ready
- `BulletRepository` / `BulletRepositoryImpl` — data layer with Result<T> error handling
- `NotesTheme` (Theme.kt) — Material 3 theme with #2563EB seed, system dark mode
- `TokenStore` — Tink-encrypted DataStore, provides `getLastDocId()` for pinned document
- `AuthInterceptor` / `TokenAuthenticator` — OkHttp auth chain handles 401 refresh transparently
- `EncryptedDataStoreFactory` — AES256-GCM encryption for WidgetStateStore

### Established Patterns
- Clean Architecture: data/domain/presentation with use cases as `operator fun invoke()`
- Hilt DI: widget uses @EntryPoint pattern (not @AndroidEntryPoint) to access singleton graph
- Kotlin `Result<T>` for all repository/use case error handling
- Optimistic updates with rollback pattern used in BulletTreeScreen (Phase 12)

### Integration Points
- Widget receiver/provider (Phase 13) — add [+] to header, add × to bullet rows
- WidgetStateStore (Phase 13) — manage optimistic state for add/delete
- Widget refresh mechanism (Phase 14) — trigger updateAll() after action success/failure
- AndroidManifest.xml — register overlay Activity for add dialog
- No existing overlay Activity pattern — this is the first dialog-themed Activity in the app

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 15-interactive-actions*
*Context gathered: 2026-03-14*
