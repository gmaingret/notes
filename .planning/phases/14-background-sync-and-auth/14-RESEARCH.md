# Phase 14: Background Sync and Auth - Research

**Researched:** 2026-03-14
**Domain:** Android WorkManager, CoroutineWorker, Hilt DI for workers, in-app widget trigger pattern
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Refresh widget on every bullet mutation: create, edit, delete, complete, indent, outdent, undo, redo
- Only trigger when the changed document matches the widget's pinned document
- Single widget instance only
- Keep old data visible, swap silently when new data arrives (no loading shimmer on mutation-triggered refresh)
- Also refresh on app open (cold start / resume from background)
- Refresh on logout → show "Session expired" state immediately
- Refresh on login → auto-recover and fetch fresh content immediately
- Delete pinned document → show "Document not found" state (triggered by mutation refresh detecting 404)
- 15-min periodic sync via WorkManager — no manual refresh button
- No freshness indicator at all — always show cached data silently
- On sync failure: keep cached data, retry on next 15-min WorkManager cycle (silent)
- Error state only when no cached data exists (first load failure)
- WorkManager requires network connectivity constraint (don't waste cycles offline)
- Tap "Session expired" → opens full Notes app (reuses existing auth flow)
- WorkManager sync worker detects auth expiry (401 + refresh failure) and writes "session_expired" state to WidgetStateStore
- Worker keeps running on 15-min schedule even when auth is expired (no cancel/re-enqueue complexity)
- After re-login in app, widget immediately re-fetches content (login triggers widget refresh)
- WorkManager handles reboot automatically — no BOOT_COMPLETED receiver needed
- Widget shows cached data from WidgetStateStore immediately after reboot (before first sync)
- No battery exemption request
- 15-minute WorkManager interval (minimum allowed)
- Network constraint prevents wasted cycles when offline
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

### Deferred Ideas (OUT OF SCOPE)
- Manual refresh button in widget header — WIDG-02 in future requirements
- Configurable sync interval (15/30/60 min) in app settings — potential future enhancement
- Multiple widget instances pointing to different documents — WIDG-01 in future requirements
- Staleness indicator / "Updated X min ago" — explicitly rejected for now
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SYNC-01 | Widget refreshes automatically when bullets are changed in the Android app | In-app trigger pattern: call `NotesWidget().updateAll(context)` from ViewModel after each mutation; Glance re-invokes provideGlance which re-fetches via entryPoint |
| SYNC-02 | Widget refreshes periodically in the background via WorkManager (15-min interval) | CoroutineWorker + enqueueUniquePeriodicWork with NetworkType.CONNECTED constraint; minimum 15-min interval is exactly what Doze allows; KEEP policy avoids cancelling running worker |
| SYNC-03 | Widget authenticates independently using the persisted refresh token | DataStoreCookieJar persists refresh cookie across process death; worker calls authApi.refresh() → saves new token to TokenStore; same OkHttpClient handles cookie injection automatically |
</phase_requirements>

---

## Summary

Phase 14 adds three concerns to the widget: (1) in-app sync where any bullet mutation in the app triggers a silent widget refresh, (2) periodic background sync via WorkManager every 15 minutes, and (3) independent widget authentication using the persisted refresh token without requiring the user to be in the app.

The codebase already has all the building blocks in place. `DataStoreCookieJar` persists the refresh token cookie across process death — this is the critical auth mechanism for the worker. The shared `OkHttpClient` (provided by Hilt's `SingletonComponent`) already has `AuthInterceptor`, `TokenAuthenticator`, and `DataStoreCookieJar` wired in. A `CoroutineWorker` injected via Hilt can receive the same `OkHttpClient` singleton and call `AuthApi.refresh()` + `BulletApi.getBullets()` exactly as the app does, without any process boundary issues (WorkManager runs in the app's own process).

The in-app trigger is straightforward: after any successful bullet mutation in `BulletTreeViewModel`, check if the open document matches the widget's pinned document (read from `WidgetStateStore`) and call `NotesWidget().updateAll(context)` in a `viewModelScope.launch` coroutine. This is the simplest correct approach — direct coroutine call, no broadcast bus needed.

**Primary recommendation:** Use the shared Hilt-provided `OkHttpClient` in the worker (no worker-scoped client needed); wire mutation triggers at the ViewModel layer for simplicity; use `ExistingPeriodicWorkPolicy.KEEP` for enqueue-on-placement and `cancelUniqueWork` on removal.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.work:work-runtime-ktx | 2.11.1 | WorkManager with Kotlin coroutine support | Decided in STATE.md; KTX adds CoroutineWorker and suspend-friendly builders |
| androidx.hilt:hilt-work | 1.3.0 | @HiltWorker + HiltWorkerFactory for DI into workers | Decided in STATE.md; only Jetpack-official way to inject Hilt singletons into WorkManager |

### Supporting (already in project)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| com.google.dagger:hilt-android | 2.56.1 | Hilt DI (already present) | @HiltWorker requires the same hilt version as the app |
| androidx.datastore:datastore-preferences | 1.2.1 | WidgetStateStore persistence (already present) | Storing bullet cache + display state in worker |
| com.google.crypto.tink:tink-android | 1.8.0 | Encryption (already present) | WidgetStateStore must match existing encryption pattern |

**Installation (additions only):**
```toml
# gradle/libs.versions.toml
workManager = "2.11.1"
hiltWork = "1.3.0"

work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
hilt-work-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.work.runtime.ktx)
implementation(libs.hilt.work)
ksp(libs.hilt.work.compiler)
testImplementation("androidx.work:work-testing:2.11.1")
```

---

## Architecture Patterns

### Recommended Project Structure (additions to existing widget package)
```
widget/
├── NotesWidget.kt          # existing — add updateAll() call helpers
├── NotesWidgetReceiver.kt  # existing — add cancelUniqueWork on onDeleted
├── WidgetStateStore.kt     # existing — add bullet cache + display state keys
├── WidgetUiState.kt        # existing — no changes needed
├── WidgetEntryPoint.kt     # existing — already exposes needed repos
├── sync/
│   └── WidgetSyncWorker.kt  # NEW: CoroutineWorker for periodic sync
di/
├── WidgetModule.kt         # existing — add WorkManager enqueue helper
NotesApplication.kt         # existing — add Configuration.Provider
AndroidManifest.xml         # existing — add tools:node="remove" for WorkManager init
```

### Pattern 1: HiltWorker Setup

**What:** Annotate the worker with `@HiltWorker` and use `@AssistedInject` for the constructor. The two `@Assisted` parameters (`Context`, `WorkerParameters`) come from WorkManager at runtime; all other parameters are Hilt singletons injected at construction time.

**When to use:** Always — this is the only supported pattern for Hilt + WorkManager.

```kotlin
// Source: https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bulletRepository: BulletRepository,
    private val documentRepository: DocumentRepository,
    private val widgetStateStore: WidgetStateStore,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val docId = widgetStateStore.getDocumentId(/* single instance: known widget id */)
            ?: return Result.success() // not configured — nothing to sync

        // Attempt refresh if no access token (post-reboot scenario)
        val hasToken = authRepository.getAccessToken() != null
        if (!hasToken) {
            val refreshResult = authRepository.refresh()
            if (refreshResult.isFailure) {
                widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
                NotesWidget().updateAll(applicationContext)
                return Result.success() // keep schedule running
            }
        }

        val bulletsResult = bulletRepository.getBullets(docId)
        bulletsResult.fold(
            onSuccess = { bullets ->
                val rootBullets = bullets.filter { it.parentId == null }.take(50)
                widgetStateStore.saveBullets(rootBullets)
                widgetStateStore.saveDisplayState(
                    if (rootBullets.isEmpty()) DisplayState.EMPTY else DisplayState.CONTENT
                )
                NotesWidget().updateAll(applicationContext)
                Result.success()
            },
            onFailure = { e ->
                if (isAuthError(e)) {
                    widgetStateStore.saveDisplayState(DisplayState.SESSION_EXPIRED)
                    NotesWidget().updateAll(applicationContext)
                } // else: keep cached data, don't update state — silent failure
                Result.success() // always success to keep schedule
            }
        )
        return Result.success()
    }
}
```

### Pattern 2: NotesApplication Configuration.Provider

**What:** Implement `Configuration.Provider` on the Application class to provide `HiltWorkerFactory`. Disable the default WorkManager auto-initializer in AndroidManifest so WorkManager uses the Hilt-provided factory.

**When to use:** Required exactly once — on `NotesApplication`.

```kotlin
// Source: https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager
@HiltAndroidApp
class NotesApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var okHttpClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: Context): ImageLoader { /* unchanged */ }
}
```

AndroidManifest.xml — add inside `<application>`:
```xml
<!-- Disable default WorkManager initializer so our Configuration.Provider is used -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

### Pattern 3: Enqueue on Widget Placement, Cancel on Removal

**What:** Enqueue the periodic sync when the user selects a document in `WidgetConfigActivity`. Cancel the unique work by name when `onDeleted` fires.

**When to use:** The CONTEXT.md decision is: enqueue on placement, cancel on removal.

```kotlin
// In WidgetConfigActivity (after user confirms document selection):
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "widget_sync",
    ExistingPeriodicWorkPolicy.KEEP,  // don't interrupt running sync
    PeriodicWorkRequestBuilder<WidgetSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
)

// In NotesWidgetReceiver.onDeleted():
WorkManager.getInstance(context).cancelUniqueWork("widget_sync")
```

**Why KEEP (not UPDATE or REPLACE):**
- KEEP: If work already exists with this name, do nothing. Preserves existing schedule. Avoids accidental restart of an in-flight sync.
- UPDATE (added in WorkManager 2.8): Preferred when you need to update constraints/interval. For this phase we never change constraints after enqueue — KEEP is correct.
- REPLACE/CANCEL_AND_REENQUEUE: Cancels running work — wrong for our case.

### Pattern 4: In-App Mutation Trigger at ViewModel Layer

**What:** After each successful bullet mutation in `BulletTreeViewModel`, call `NotesWidget().updateAll(context)` in a fire-and-forget coroutine. Guard with a check that the open document matches the widget's pinned document.

**When to use:** Every bullet mutation operation (create, patch, delete, indent, outdent, move, undo, redo, complete/uncomplete).

**Implementation decision (Claude's discretion):** Wire at the ViewModel layer, not the repository layer, because:
1. ViewModels already know the current `docId` (open document).
2. The check "does widget pin this doc?" belongs at call site, not deep in the data layer.
3. Avoids threading the check through all repository methods.
4. Repository layer has no Application context for `updateAll(context)`.

```kotlin
// In BulletTreeViewModel — inject Application context (already AndroidViewModel):
private fun triggerWidgetRefreshIfNeeded() {
    viewModelScope.launch {
        val pinnedDocId = widgetStateStore.getDocumentId(/* single widget id */)
        if (pinnedDocId != null && pinnedDocId == currentDocId) {
            NotesWidget().updateAll(getApplication())
        }
    }
}
// Called after: createBullet, patchBullet, deleteBullet, indent, outdent,
//               move, undo, redo — whenever the mutation succeeds
```

**Getting the widget's pinned docId:** `WidgetStateStore` stores per-widget docId keyed by `appWidgetId`. Since there is exactly one widget instance, the ViewModel needs the `appWidgetId`. The cleanest approach: `GlanceAppWidgetManager(context).getGlanceIds(NotesWidget::class.java)` returns all placed instances. If non-empty, read `WidgetStateStore.getDocumentId(appWidgetIds.first())`.

**Alternative without appWidgetId:** Add a `getAnyDocumentId(): String?` method to `WidgetStateStore` that reads the first stored docId (since there is only one widget). This is simpler for this phase.

### Pattern 5: Login/Logout Widget Refresh

**What:** After successful login (in `AuthRepositoryImpl.login()` / `loginWithGoogle()`) and after logout, call `NotesWidget().updateAll(context)` to immediately reflect the new auth state.

**Where to wire:** `MainViewModel.logout()` already has access to `Application` context via its ViewModel scope. For login, `AuthViewModel` (or the splash path in `SplashViewModel`) should trigger the refresh after auth succeeds. Use Application context passed through the use case, or call from the ViewModel using a provided `applicationContext`.

**Simpler alternative:** Use `LifecycleObserver` / `onResume` in `MainActivity` — every time the app comes to foreground, call `NotesWidget().updateAll(context)`. This handles: cold start, resume from background, post-login (login completes → app returns to main → onResume fires). This covers all the CONTEXT.md cases with a single hook.

### Anti-Patterns to Avoid

- **Starting a new OkHttpClient in the worker:** The existing Hilt singleton `OkHttpClient` already has `AuthInterceptor`, `TokenAuthenticator`, and `DataStoreCookieJar` — reuse it. A worker-scoped client would not share the cookie jar and would fail auth independently.
- **Using `ExistingPeriodicWorkPolicy.REPLACE`:** This was deprecated in WorkManager 2.8 and is now renamed `CANCEL_AND_REENQUEUE`. Don't use it — it cancels an in-flight sync.
- **Calling `updateAll()` inside `provideContent{}` lambda:** `updateAll()` is a suspend function that must be called from outside the composable lambda. Call it from the worker's `doWork()` or from ViewModel scope.
- **Using `runBlocking` in `CoroutineWorker.doWork()`:** doWork() is already a `suspend fun` — use `withContext(Dispatchers.IO)` for network calls, not `runBlocking`.
- **Storing plain text bullets in WidgetStateStore:** Must use Tink encryption consistent with the existing `EncryptedDataStoreFactory` pattern — same keyset as other widget data.
- **Cancelling the WorkManager schedule when auth expires:** CONTEXT.md explicitly says keep the schedule running — the worker writes `SESSION_EXPIRED` state and returns `Result.success()`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Persist work across reboots | Custom BroadcastReceiver + restart logic | WorkManager PeriodicWorkRequest | WorkManager persists periodic work in its own database, survives reboots automatically |
| Inject singletons into Worker | Manually pass dependencies through WorkerParameters input data | `@HiltWorker` + `HiltWorkerFactory` | Input data is String/primitive only; full objects need Hilt injection |
| Schedule minimum interval < 15 min | Custom AlarmManager scheduler | WorkManager 15-min floor | WorkManager's 15-min is OS-enforced minimum; AlarmManager would drain battery and be killed by Doze |
| Network-aware scheduling | Manual connectivity check in doWork() before network call | `NetworkType.CONNECTED` constraint | WorkManager won't even start the worker without network — no wasted cycles |

**Key insight:** WorkManager exists precisely because scheduling background work correctly on Android (Doze, App Standby, OEM battery killers) is extremely hard to get right. The 15-minute periodic sync with a network constraint is the idiomatic solution and requires virtually no custom scheduling logic.

---

## Common Pitfalls

### Pitfall 1: Missing tools:node="remove" in AndroidManifest
**What goes wrong:** WorkManager initializes with its default `WorkerFactory` before the Application's `Configuration.Provider` is consulted. The `@HiltWorker` cannot be constructed because `HiltWorkerFactory` is never used. Results in `NoSuchMethodException` at runtime when the worker tries to instantiate.
**Why it happens:** WorkManager uses the `androidx.startup` `InitializationProvider` to auto-initialize on app start before Application.onCreate() runs.
**How to avoid:** Add the `tools:node="remove"` metadata inside `InitializationProvider` in AndroidManifest.xml (see Pattern 2 above). This is the **critical** setup step — everything else is correct but this breaks the DI chain silently.
**Warning signs:** Worker never runs; logs show `NoSuchMethodException` for worker class constructor.

### Pitfall 2: Worker Uses Stale Access Token After Process Death
**What goes wrong:** After device reboot, the in-memory access token is null. If the worker calls a protected endpoint without first refreshing, it gets a 401. The `TokenAuthenticator` will attempt to refresh, but only if the request had an Authorization header. Without a token, `AuthInterceptor` skips the header — so `TokenAuthenticator` is never invoked.
**Why it happens:** `AuthInterceptor.intercept()` does: `if (token != null) add header else skip`. After reboot, `TokenStore.getAccessToken()` returns null (token was in memory; not persisted — wait, actually TokenStore IS encrypted DataStore). Re-check: `TokenStore` uses encrypted DataStore which IS persistent. So after reboot, the access token should be available if it was saved before the previous process death.
**Actual scenario:** JWT access tokens have short lifetimes (typically 15 min or less). After overnight Doze, the persisted token will be expired. The first worker run after reboot will get a 401 with an Authorization header → `TokenAuthenticator` fires → calls `authApi.refresh()` using the `DataStoreCookieJar` refresh cookie → gets new token. This should work automatically with the shared `OkHttpClient`. No extra code needed in the worker for this case.
**How to avoid:** Trust the existing OkHttp auth chain. Document the flow clearly. If the refresh cookie is also expired (> 7 days), `TokenAuthenticator` clears tokens and the worker's `getBullets()` call fails with 401 — catch this and write `SESSION_EXPIRED`.
**Warning signs:** Widget shows "Session expired" on first sync after reboot when user only slept the device overnight (not a multi-week gap).

### Pitfall 3: WidgetStateStore appWidgetId Gap
**What goes wrong:** `WidgetStateStore.getDocumentId(id)` requires knowing the `appWidgetId`. The worker runs without a widget ID — it needs to know which document to fetch.
**Why it happens:** The worker is enqueued globally, not per-widget-id.
**How to avoid:** Add a `getFirstDocumentId(): String?` helper to `WidgetStateStore` that iterates DataStore preferences and returns the first non-null docId. Since there is exactly one widget, this is safe for Phase 14. Alternatively, store the current appWidgetId in a separate DataStore key alongside the document ID when the user selects a document.
**Warning signs:** Worker always returns early because `getDocumentId()` can't find a doc.

### Pitfall 4: updateAll() In Glance Composition Context
**What goes wrong:** Calling `NotesWidget().updateAll(context)` from inside `provideContent{}` creates a recursive re-render loop.
**Why it happens:** `updateAll()` schedules re-invocation of `provideGlance()`, which runs `provideContent{}` again.
**How to avoid:** Only call `updateAll()` from outside Glance's composition context: from ViewModel coroutines, from WorkManager's `doWork()`, from `LoginUseCase`, or from `NotesWidgetReceiver.onDeleted()`. Never from within `provideContent{}`.

### Pitfall 5: Mutation Trigger Skips Undo/Redo
**What goes wrong:** After undo/redo, the widget still shows old content because the trigger was only added to create/patch/delete but not undo/redo.
**Why it happens:** Undo/redo go through a separate code path in `BulletTreeViewModel` (`.undoUseCase()` / `.redoUseCase()`).
**How to avoid:** Add `triggerWidgetRefreshIfNeeded()` calls after both `undoUseCase()` and `redoUseCase()` success paths.

### Pitfall 6: REPLACE Policy Deprecated
**What goes wrong:** Using `ExistingPeriodicWorkPolicy.REPLACE` produces a deprecation warning (it was renamed `CANCEL_AND_REENQUEUE` in WorkManager 2.8+).
**How to avoid:** Use `ExistingPeriodicWorkPolicy.KEEP` for first-time enqueue (which is the correct semantic anyway — don't interrupt a running sync).

---

## Code Examples

### WidgetStateStore Extensions for Bullet Cache

The existing `WidgetStateStore` only stores docId (one `String` key per widget). Phase 14 needs to additionally cache the bullet list and display state. Since this is a single-widget design, add global keys (not per-widget-id keys):

```kotlin
// Additions to WidgetStateStore
private val DISPLAY_STATE_KEY = stringPreferencesKey("display_state")
private val BULLETS_KEY = stringPreferencesKey("bullets_json")

suspend fun saveDisplayState(state: DisplayState) {
    val encrypted = EncryptedDataStoreFactory.encrypt(aead, state.name, "display_state")
    context.widgetStateDataStore.edit { prefs ->
        prefs[DISPLAY_STATE_KEY] = encrypted
    }
}

suspend fun getDisplayState(): DisplayState {
    val encrypted = context.widgetStateDataStore.data.firstOrNull()
        ?.get(DISPLAY_STATE_KEY) ?: return DisplayState.NOT_CONFIGURED
    val name = EncryptedDataStoreFactory.decrypt(aead, encrypted, "display_state")
        ?: return DisplayState.NOT_CONFIGURED
    return DisplayState.valueOf(name)
}

// Bullets stored as JSON string (reuse Gson already in DataStoreCookieJar)
suspend fun saveBullets(bullets: List<WidgetBullet>) {
    val json = gson.toJson(bullets)
    val encrypted = EncryptedDataStoreFactory.encrypt(aead, json, "bullets")
    context.widgetStateDataStore.edit { prefs -> prefs[BULLETS_KEY] = encrypted }
}

suspend fun getBullets(): List<WidgetBullet> {
    val encrypted = context.widgetStateDataStore.data.firstOrNull()
        ?.get(BULLETS_KEY) ?: return emptyList()
    val json = EncryptedDataStoreFactory.decrypt(aead, encrypted, "bullets")
        ?: return emptyList()
    return gson.fromJson(json, Array<WidgetBullet>::class.java).toList()
}
```

**DisplayState enum (new):**
```kotlin
enum class DisplayState { NOT_CONFIGURED, LOADING, CONTENT, EMPTY, ERROR, SESSION_EXPIRED, DOCUMENT_NOT_FOUND }
```

### NotesWidget provideGlance — Reading from Cache

The widget's `provideGlance()` must be updated to read from `WidgetStateStore` (the cached bullets + display state) instead of always fetching from the network. The WorkManager worker fetches and writes to the cache; `provideGlance` reads from it:

```kotlin
override suspend fun provideGlance(context: Context, id: GlanceId) {
    val store = WidgetStateStore.getInstance(context)
    val displayState = withContext(Dispatchers.IO) { store.getDisplayState() }
    val cachedBullets = withContext(Dispatchers.IO) { store.getBullets() }
    val docId = withContext(Dispatchers.IO) {
        // single widget: get first stored docId
        store.getFirstDocumentId()
    }

    val uiState = when (displayState) {
        DisplayState.CONTENT -> WidgetUiState.Content(docId ?: "", "", cachedBullets)
        DisplayState.EMPTY -> WidgetUiState.Empty
        DisplayState.SESSION_EXPIRED -> WidgetUiState.SessionExpired
        DisplayState.DOCUMENT_NOT_FOUND -> WidgetUiState.DocumentNotFound
        DisplayState.ERROR -> WidgetUiState.Error("Sync failed")
        else -> WidgetUiState.Loading
    }

    provideContent {
        GlanceTheme(colors = NotesWidgetColorScheme.colors) {
            WidgetContent(uiState = uiState, context = context)
        }
    }
}
```

**Note:** This is a significant architectural shift from Phase 13's `provideGlance()` which fetched data on every call. Phase 14 makes the worker the single data-fetching authority; `provideGlance` becomes a pure render function reading from cache.

### Enqueue Pattern

```kotlin
// Source: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
fun enqueueWidgetSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "widget_sync",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

fun cancelWidgetSync(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("widget_sync")
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `ExistingPeriodicWorkPolicy.REPLACE` | Use `KEEP` (or `UPDATE` for changing constraints) | WorkManager 2.8 | REPLACE deprecated/renamed to CANCEL_AND_REENQUEUE; UPDATE added for mid-flight safe updates |
| `kapt` for hilt-work compiler | `ksp` | Hilt 2.48+ / KSP stable | Project already uses `ksp` for all Hilt annotations |
| Manual `Configuration.Builder` in `onCreate()` | Implement `Configuration.Provider` on Application class | WorkManager 2.6 | App Startup library handles initialization; Provider is invoked lazily before first use |

**Deprecated/outdated:**
- `WorkerFactory` manual subclass: replaced by `HiltWorkerFactory` which auto-discovers `@HiltWorker` classes.
- `ExistingPeriodicWorkPolicy.REPLACE`: renamed to `CANCEL_AND_REENQUEUE` in WorkManager 2.8; use `KEEP` for this phase.

---

## Open Questions

1. **provideGlance() architecture shift**
   - What we know: Phase 13's `provideGlance()` fetches data directly from repositories on every call. Phase 14 wants the worker to be the data authority.
   - What's unclear: Does changing `provideGlance()` to read from cache break the in-app mutation trigger? When BulletTreeViewModel calls `updateAll()`, Glance re-invokes `provideGlance()` — if it only reads cache, the cache must be updated BEFORE calling `updateAll()`.
   - Recommendation: The in-app trigger must be: (1) fetch fresh data from repository, (2) write to WidgetStateStore cache, (3) call `updateAll()`. This is safe. The worker does the same sequence. `provideGlance()` is always a pure cache reader.

2. **appWidgetId in single-widget design**
   - What we know: `WidgetStateStore` keys docId by `appWidgetId`. Worker doesn't have a widget ID.
   - Recommendation: Add `getFirstDocumentId(): String?` to `WidgetStateStore` that returns the first stored docId. Also write the current `appWidgetId` to a dedicated global key ("current_widget_id") when the user selects a document, so the worker can look it up.

3. **BulletTreeViewModel context for updateAll()**
   - What we know: `BulletTreeViewModel` extends `AndroidViewModel` — has Application context available.
   - Recommendation: Use `getApplication<Application>()` directly in `triggerWidgetRefreshIfNeeded()`. No additional injection needed.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + MockK 1.13.14 + Robolectric 4.16 (existing) |
| Config file | Robolectric via `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [28])` |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.widget.*" -x lint` |
| Full suite command | `./gradlew :app:testDebugUnitTest -x lint` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SYNC-01 | After bullet mutation matching widget docId, `updateAll()` is called | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncTriggerTest" -x lint` | ❌ Wave 0 |
| SYNC-01 | Widget NOT refreshed when mutation is for a different document | unit | same | ❌ Wave 0 |
| SYNC-02 | WidgetSyncWorker.doWork() with valid docId writes bullets to WidgetStateStore and returns success | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncWorkerTest" -x lint` | ❌ Wave 0 |
| SYNC-02 | WidgetSyncWorker.doWork() with no docId returns success (no-op) | unit | same | ❌ Wave 0 |
| SYNC-02 | WidgetSyncWorker.doWork() on network failure keeps cached state (no state overwrite) | unit | same | ❌ Wave 0 |
| SYNC-03 | Worker writes SESSION_EXPIRED to WidgetStateStore on 401 that survives refresh attempt | unit | same | ❌ Wave 0 |
| SYNC-03 | After logout, widget display state becomes SESSION_EXPIRED | unit | `./gradlew :app:testDebugUnitTest --tests "*.widget.sync.WidgetSyncTriggerTest"` | ❌ Wave 0 |
| SYNC-03 | After login, widget display state is refreshed (SUCCESS) | unit | same | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*.widget.*" -x lint`
- **Per wave merge:** `./gradlew :app:testDebugUnitTest -x lint`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/.../widget/sync/WidgetSyncWorkerTest.kt` — covers SYNC-02 and SYNC-03 auth; use `TestListenableWorkerBuilder` with a custom `WorkerFactory` that injects MockK mocks
- [ ] `app/src/test/.../widget/sync/WidgetSyncTriggerTest.kt` — covers SYNC-01; unit tests `BulletTreeViewModel` mutation paths + widget trigger
- [ ] `work-testing` dependency: `testImplementation("androidx.work:work-testing:2.11.1")`

---

## Sources

### Primary (HIGH confidence)
- Android Developers: [Use Hilt with WorkManager](https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager) — @HiltWorker pattern, Configuration.Provider, tools:node="remove"
- Android Developers: [Managing work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work) — enqueueUniquePeriodicWork, ExistingPeriodicWorkPolicy
- Android Developers: [Manage and update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) — updateAll() usage from coroutines
- Android Developers: [Testing Worker implementation](https://developer.android.com/develop/background-work/background-tasks/testing/persistent/worker-impl) — TestListenableWorkerBuilder pattern
- Codebase: `DataStoreCookieJar`, `TokenStore`, `TokenAuthenticator`, `AuthInterceptor` — confirmed refresh token is persisted; shared OkHttpClient has full auth chain
- Codebase: `WidgetStateStore`, `EncryptedDataStoreFactory` — confirmed encryption pattern for extension

### Secondary (MEDIUM confidence)
- [WorkManager periodicity deep dive](https://medium.com/androiddevelopers/workmanager-periodicity-ff35185ff006) — KEEP vs UPDATE vs REPLACE semantics
- [Updating unique periodic work with WorkManager](https://medium.com/@nicholas.rose/updating-unique-periodic-work-with-workmanager-583009486417) — UPDATE policy added in 2.8

### Tertiary (LOW confidence)
- None — all critical claims verified against official docs or codebase inspection.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions from STATE.md decisions, verified against libs.versions.toml
- Architecture: HIGH — patterns verified against official Android docs and existing codebase
- Pitfalls: HIGH — Pitfall 1 (missing tools:node) verified against official docs; others derived from codebase analysis
- Test patterns: MEDIUM — TestListenableWorkerBuilder verified against official docs; specific MockK integration requires validation in Wave 0

**Research date:** 2026-03-14
**Valid until:** 2026-06-14 (stable APIs; WorkManager and Hilt rarely break patterns between minor versions)
