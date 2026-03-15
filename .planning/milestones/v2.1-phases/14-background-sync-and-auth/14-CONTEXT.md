# Phase 14: Background Sync and Auth - Context

**Gathered:** 2026-03-14
**Status:** Ready for planning

<domain>
## Phase Boundary

The widget stays current across device sessions, app restarts, and process death without requiring the user to manually intervene. This phase adds WorkManager periodic sync (15-min), in-app broadcast updates on bullet mutations, and independent widget auth via the persisted refresh token. No interactive actions (add/delete) — those come in Phase 15.

</domain>

<decisions>
## Implementation Decisions

### In-App Update Trigger
- Refresh widget on every bullet mutation: create, edit, delete, complete, indent, outdent, undo, redo
- Only trigger when the changed document matches the widget's pinned document
- Single widget instance only — no multi-widget support
- Keep old data visible, swap silently when new data arrives (no loading shimmer on mutation-triggered refresh)
- Also refresh on app open (cold start / resume from background)
- Refresh on logout → show "Session expired" state immediately
- Refresh on login → auto-recover and fetch fresh content immediately
- Delete pinned document → show "Document not found" state (Phase 13 defined, triggered by mutation refresh detecting 404)
- 15-min periodic sync is sufficient for cross-device changes — no manual refresh button

### Stale Data Indicator
- No freshness indicator at all — always show cached data silently
- No visual hint for stale data, regardless of time since last sync
- Consistent with Phase 13's "no header, no title" minimal aesthetic

### Background Sync Failure
- On sync failure: keep cached data, retry on next 15-min WorkManager cycle (silent)
- Error state only when no cached data exists (first load failure)
- WorkManager requires network connectivity constraint (don't waste cycles offline)

### Auth Expiry Recovery
- Tap "Session expired" → opens full Notes app (reuses existing auth flow)
- WorkManager sync worker detects auth expiry (401 + refresh failure) and writes "session_expired" state to WidgetStateStore
- Worker keeps running on 15-min schedule even when auth is expired (no cancel/re-enqueue complexity)
- After re-login in app, widget immediately re-fetches content (login triggers widget refresh)

### Device Reboot Behavior
- WorkManager handles reboot automatically (persists periodic work across reboots)
- No BOOT_COMPLETED receiver needed
- Widget shows cached data from WidgetStateStore immediately after reboot (before first sync)

### Battery Optimization
- No battery exemption request — accept that Doze may delay syncs beyond 15 min
- 15-minute WorkManager interval (minimum allowed)
- Network constraint prevents wasted cycles when offline

### Widget Data Persistence
- Cache in WidgetStateStore: root bullet list (content, isComplete) + display state (content/loading/error/expired/not_found) + pinned docId
- No timestamps, no document title, no metadata
- Encrypted with Tink AES256-GCM (same EncryptedDataStoreFactory pattern as TokenStore)
- Clear WidgetStateStore on widget removal (onDeleted callback)
- Cancel WorkManager periodic sync on widget removal; re-enqueue on new placement

### Claude's Discretion
- Whether to use shared OkHttpClient or worker-scoped client for CoroutineWorker auth
- Broadcast mechanism implementation (LocalBroadcast, direct updateAll() call from repository, or content observer)
- WorkManager unique work naming and policy (KEEP vs REPLACE)
- Exact CoroutineWorker implementation details
- How to wire mutation detection (repository layer, ViewModel layer, or use case layer)
- HiltWorkerFactory and Configuration.Provider setup details

</decisions>

<specifics>
## Specific Ideas

- Widget is a "glanceable list" — grocery list / quick tasks use case. Background sync should be invisible and reliable, never intrusive
- The user explicitly wants no visual clutter: no timestamps, no staleness hints, no loading flicker on updates
- Single widget instance simplifies the sync logic — no need to track multiple glanceIds or document mappings
- Privacy-first: bullet data in WidgetStateStore must be encrypted (consistent with TokenStore/DataStoreCookieJar)

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TokenStore` (data/local/TokenStore.kt): Tink-encrypted DataStore for access tokens — widget reads from same store
- `DataStoreCookieJar` (data/local/DataStoreCookieJar.kt): Persists refresh token cookie — widget worker uses this for independent auth
- `EncryptedDataStoreFactory` (data/local/EncryptedDataStoreFactory.kt): AES256-GCM encryption helpers — reuse for WidgetStateStore encryption
- `AuthInterceptor` / `TokenAuthenticator` (data/api/): OkHttp auth chain — worker may reuse or need worker-scoped instance
- `AuthApi.refresh()`: POST /api/auth/refresh — widget worker calls this independently
- `BulletApi.getBullets()`: GET /api/bullets/documents/{docId}/bullets — widget sync endpoint
- `BulletRepository` / `DocumentRepository`: Existing data layer — mutation detection point for in-app broadcasts

### Established Patterns
- Clean Architecture: data/domain/presentation with use cases as `operator fun invoke()`
- Hilt DI: @Module + @InstallIn(SingletonComponent::class) — widget uses @EntryPoint pattern
- Kotlin `Result<T>` for all repository/use case error handling
- Tink encryption via EncryptedDataStoreFactory for all persisted sensitive data

### Integration Points
- `NotesApplication.kt`: Must add `Configuration.Provider` + inject `HiltWorkerFactory` for WorkManager
- `AndroidManifest.xml`: Disable default WorkManager auto-initializer (tools:node="remove")
- `gradle/libs.versions.toml`: Add work-runtime-ktx 2.11.1 + hilt-work 1.3.0
- `BulletRepository` / relevant ViewModels: Hook mutation detection to trigger widget updateAll()
- `NotesWidgetReceiver.onDeleted()`: Clear WidgetStateStore + cancel WorkManager periodic job
- Widget config activity (Phase 13): Enqueue WorkManager periodic sync on document selection

</code_context>

<deferred>
## Deferred Ideas

- Manual refresh button in widget header — WIDG-02 in future requirements
- Configurable sync interval (15/30/60 min) in app settings — potential future enhancement
- Multiple widget instances pointing to different documents — WIDG-01 in future requirements
- Staleness indicator / "Updated X min ago" — explicitly rejected for now, could revisit

</deferred>

---

*Phase: 14-background-sync-and-auth*
*Context gathered: 2026-03-14*
