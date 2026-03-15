# Architecture Research

**Domain:** Jetpack Glance home screen widget integrated into existing Clean Architecture Android app
**Researched:** 2026-03-14
**Confidence:** HIGH (official Android docs + official Glance codelab + verified Hilt docs)

---

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        HOME SCREEN PROCESS                           │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  NotesGlanceWidget (GlanceAppWidget)                          │   │
│  │    provideGlance() → EntryPointAccessors → repos              │   │
│  │    → reads WidgetStateStore → fetches bullets                 │   │
│  │    → provideContent { LazyColumn of root bullets }            │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
         ↑ Android OS renders RemoteViews; calls provideGlance on update
         │
┌────────────────────────────────────────────────────────────────────────┐
│                         APP PROCESS (existing + new widget layer)       │
│                                                                         │
│  ┌──────────────────────┐   ┌─────────────────────────────────────┐    │
│  │  WIDGET LAYER (NEW)  │   │  PRESENTATION LAYER (existing)       │    │
│  │                      │   │                                      │    │
│  │  NotesWidgetReceiver │   │  BulletTreeViewModel ← MODIFIED      │    │
│  │  (@AndroidEntryPoint)│   │    after successful mutation:        │    │
│  │  @Inject repo for    │   │    viewModelScope.launch {           │    │
│  │  onDeleted cleanup   │   │      NotesGlanceWidget()             │    │
│  │                      │   │        .updateAll(context)           │    │
│  │  WidgetConfigActivity│   │    }                                 │    │
│  │  (doc picker)        │   │                                      │    │
│  │                      │   │  MainViewModel, AuthViewModel, etc.  │    │
│  │  WidgetSyncWorker    │   │  (unchanged)                         │    │
│  │  (@HiltWorker, 15m)  │   │                                      │    │
│  └──────────┬───────────┘   └──────────────────────────────────────┘   │
│             │ @Inject / EntryPointAccessors.fromApplication()           │
│  ┌──────────▼──────────────────────────────────────────────────────┐   │
│  │                    DOMAIN LAYER (existing, unchanged)            │   │
│  │                                                                  │   │
│  │  GetBulletsUseCase  CreateBulletUseCase  DeleteBulletUseCase     │   │
│  │  GetDocumentsUseCase                                             │   │
│  └──────────┬───────────────────────────────────────────────────────┘  │
│             │                                                            │
│  ┌──────────▼──────────────────────────────────────────────────────┐   │
│  │                    DATA LAYER (existing + minor addition)         │   │
│  │                                                                  │   │
│  │  BulletRepositoryImpl  DocumentRepositoryImpl  (unchanged)       │   │
│  │  TokenStore (DataStore/Tink — shared, unchanged)                 │   │
│  │  AuthInterceptor → OkHttpClient (shared, unchanged)              │   │
│  │                                                                  │   │
│  │  WidgetStateStore (NEW)                                          │   │
│  │    DataStore Preferences: appWidgetId → documentId mapping       │   │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | New or Modified | Responsibility |
|-----------|----------------|----------------|
| `NotesGlanceWidget` | NEW | Renders widget UI. Uses `EntryPointAccessors` to get repos from Hilt singleton graph. Reads selected `documentId` from `WidgetStateStore`. Fetches root bullets in `provideGlance()` before calling `provideContent {}`. |
| `NotesWidgetReceiver` | NEW | `GlanceAppWidgetReceiver` subclass. Annotated `@AndroidEntryPoint` for lifecycle callbacks. Injects `WidgetStateStore` for `onDeleted` cleanup. Starts/stops `WidgetSyncWorker` in `onEnabled`/`onDisabled`. |
| `WidgetConfigActivity` | NEW | Standard Activity launched by Android on first widget placement. User picks a document. Saves `appWidgetId → documentId` to `WidgetStateStore`. Calls `NotesGlanceWidget().updateAll()`. Returns `RESULT_OK` to Android. |
| `WidgetStateStore` | NEW | DataStore Preferences singleton. Stores `appWidgetId → documentId` as string key-value pairs. Accessible from all three widget components (unlike Glance's built-in session state). |
| `WidgetSyncWorker` | NEW | `@HiltWorker` CoroutineWorker. Scheduled as `PeriodicWorkRequest` with 15-minute minimum interval. Injects `BulletRepository` and `DocumentRepository`. Calls `NotesGlanceWidget().updateAll(context)` which triggers `provideGlance()` for each placed instance. |
| `BulletTreeViewModel` | MODIFIED (minor) | After each successful bullet mutation (create, delete, patch), launch a fire-and-forget coroutine that calls `NotesGlanceWidget().updateAll(applicationContext)`. |
| `NotesApplication` | MODIFIED (minor) | Implement `Configuration.Provider` to supply `HiltWorkerFactory`. Required for `@HiltWorker` to work. Two additions: `@Inject lateinit var workerFactory: HiltWorkerFactory` and `override val workManagerConfiguration`. |
| `BulletRepositoryImpl` | UNCHANGED | Already provides suspend API the widget can consume. |
| `DocumentRepositoryImpl` | UNCHANGED | `WidgetConfigActivity` and `WidgetSyncWorker` use existing interface. |
| `TokenStore` | UNCHANGED | Widget HTTP requests flow through the same `AuthInterceptor → OkHttpClient` path. No separate token mechanism. |
| `NetworkModule` | UNCHANGED | Singleton `OkHttpClient` already configured with `AuthInterceptor` and `TokenAuthenticator`. |
| `DataModule` | UNCHANGED | All existing `@Binds` repository bindings remain. |

---

## Recommended Project Structure

```
android/app/src/main/java/com/gmaingret/notes/
├── di/
│   ├── DataModule.kt               # unchanged
│   └── NetworkModule.kt            # unchanged
├── data/
│   ├── local/
│   │   ├── TokenStore.kt           # unchanged
│   │   ├── EncryptedDataStoreFactory.kt  # unchanged
│   │   └── WidgetStateStore.kt     # NEW — appWidgetId → docId mapping
│   └── repository/                 # unchanged
├── domain/                         # unchanged
├── presentation/
│   └── bullet/
│       └── BulletTreeViewModel.kt  # MODIFIED — add updateAll after mutations
└── widget/                         # NEW package
    ├── NotesGlanceWidget.kt        # GlanceAppWidget subclass with EntryPoint
    ├── NotesWidgetReceiver.kt      # GlanceAppWidgetReceiver + @AndroidEntryPoint
    ├── WidgetConfigActivity.kt     # Document picker, saves to WidgetStateStore
    └── WidgetSyncWorker.kt         # @HiltWorker periodic 15-min sync

android/app/src/main/res/xml/
└── notes_widget_info.xml           # NEW — appwidget-provider metadata

android/app/src/main/AndroidManifest.xml
    # ADD: <receiver> for NotesWidgetReceiver
    # ADD: <activity> for WidgetConfigActivity
```

---

## Architectural Patterns

### Pattern 1: EntryPointAccessors for GlanceAppWidget Dependencies

`GlanceAppWidget.provideGlance()` is not an Android component. `@AndroidEntryPoint` cannot annotate it. The correct pattern is a `@EntryPoint` interface scoped to `SingletonComponent`, accessed via `EntryPointAccessors.fromApplication()` inside `provideGlance()`.

**When to use:** Inside `GlanceAppWidget.provideGlance()` — the only Glance class that cannot use `@AndroidEntryPoint`.

**Trade-offs:** More boilerplate than field injection, but the only correct approach. Verified by official Glance codelab (SociaLite sample) and Dagger docs.

```kotlin
class NotesGlanceWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun bulletRepository(): BulletRepository
        fun documentRepository(): DocumentRepository
        fun widgetStateStore(): WidgetStateStore
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val docId = ep.widgetStateStore().getDocumentId(appWidgetId)
        val bullets = if (docId != null) {
            withContext(Dispatchers.IO) {
                ep.bulletRepository().getRootBullets(docId)
            }
        } else emptyList()

        provideContent {
            NotesWidgetContent(docId, bullets, appWidgetId)
        }
    }
}
```

**Critical detail:** Data fetching must happen in `provideGlance()` using `withContext(Dispatchers.IO)`, not inside the `@Composable` lambda passed to `provideContent {}`. Blocking the composition thread in the lambda causes rendering failures.

### Pattern 2: @AndroidEntryPoint on GlanceAppWidgetReceiver

`GlanceAppWidgetReceiver` is a `BroadcastReceiver` subclass, so `@AndroidEntryPoint` works directly. Use it for lifecycle callbacks where injected dependencies are needed.

**When to use:** `GlanceAppWidgetReceiver` only — never on `GlanceAppWidget`.

```kotlin
@AndroidEntryPoint
class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesGlanceWidget()

    @Inject
    lateinit var widgetStateStore: WidgetStateStore

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { widgetStateStore.removeDocumentId(it) }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSyncWorker.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSyncWorker.cancel(context)
    }
}
```

### Pattern 3: @HiltWorker for WidgetSyncWorker

WorkManager workers support Hilt injection via `@HiltWorker` + `@AssistedInject`. This is the documented pattern in `hilt-work` (`androidx.hilt:hilt-work`). It avoids manual `EntryPointAccessors` boilerplate inside the worker.

**When to use:** Any `CoroutineWorker` needing Hilt-provided singletons.

**Prerequisite:** `NotesApplication` must implement `Configuration.Provider` supplying `HiltWorkerFactory`. This is the one required change to the existing `NotesApplication`.

```kotlin
@HiltWorker
class WidgetSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bulletRepository: BulletRepository,
    private val widgetStateStore: WidgetStateStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            NotesGlanceWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "notes_widget_sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
```

The `NotesApplication` additions required:

```kotlin
@HiltAndroidApp
class NotesApplication : Application(), SingletonImageLoader.Factory,
    Configuration.Provider {   // ADD

    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var workerFactory: HiltWorkerFactory  // ADD

    override val workManagerConfiguration: Configuration   // ADD
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // newImageLoader() unchanged
}
```

### Pattern 4: WidgetStateStore — Shared DataStore for Per-Instance Document Selection

Each widget instance needs its own document. The mapping (`appWidgetId → documentId`) lives in a regular DataStore Preferences singleton — not in Glance's built-in session state.

**Why not Glance's built-in `PreferencesGlanceStateDefinition`:** Glance's internal state lives inside the rendering session. It is not accessible from `WidgetConfigActivity` (which must write the selection before the widget has rendered) or from `NotesWidgetReceiver.onDeleted()`. A shared DataStore singleton is accessible from all three call sites.

```kotlin
private val Context.widgetDataStore by preferencesDataStore(name = "widget_state")

@Singleton
class WidgetStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getDocumentId(appWidgetId: Int): String? =
        context.widgetDataStore.data.firstOrNull()
            ?.get(stringPreferencesKey("doc_$appWidgetId"))

    suspend fun saveDocumentId(appWidgetId: Int, docId: String) {
        context.widgetDataStore.edit { prefs ->
            prefs[stringPreferencesKey("doc_$appWidgetId")] = docId
        }
    }

    suspend fun removeDocumentId(appWidgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("doc_$appWidgetId"))
        }
    }
}
```

The `preferencesDataStore` delegate requires a top-level `val` in a file (not inside the class). Follow the same pattern as the existing `Context.authTokenDataStore` in `TokenStore.kt`.

---

## Data Flow

### Flow 1: Widget First Placement (Configuration)

```
User long-presses home screen → selects Notes widget
    ↓
Android OS launches WidgetConfigActivity with EXTRA_APPWIDGET_ID intent extra
    ↓
WidgetConfigActivity (@AndroidEntryPoint, @Inject GetDocumentsUseCase + WidgetStateStore)
    → shows document list
    → user taps a document
    → widgetStateStore.saveDocumentId(appWidgetId, docId)
    → NotesGlanceWidget().updateAll(context)
    → setResult(RESULT_OK, Intent().putExtra(EXTRA_APPWIDGET_ID, appWidgetId))
    → finish()
    ↓
Android OS places widget on home screen
    ↓
NotesGlanceWidget.provideGlance(context, glanceId)
    → EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
    → widgetStateStore.getDocumentId(appWidgetId)   // returns docId just saved
    → withContext(Dispatchers.IO) { bulletRepository.getRootBullets(docId) }
        → same OkHttpClient → AuthInterceptor → TokenStore → API call
    → provideContent { NotesWidgetContent(bullets) }
```

### Flow 2: In-App Bullet Mutation Triggers Widget Refresh

```
User creates/deletes/patches bullet in BulletTreeScreen
    ↓
BulletTreeViewModel.createBullet() / deleteBullet() / patchBullet()
    → use case executes (optimistic update already applied)
    → on API success:
        viewModelScope.launch {
            NotesGlanceWidget().updateAll(applicationContext)
        }
    ↓
Android OS calls provideGlance() for each placed widget instance
    → fresh bullet fetch from API
    → widget recomposes
```

`updateAll()` is a suspend function. Launch in a fire-and-forget `viewModelScope.launch` block — do not await it on the mutation path. The widget update is best-effort; mutation success is independent of widget refresh success.

**Why not a custom BroadcastReceiver:** `NotesGlanceWidget().updateAll()` is the idiomatic Glance mechanism. A custom broadcast adds indirection with no benefit in a single-process app. Custom broadcasts are only necessary when the update source runs in a different process (e.g., a remote push).

### Flow 3: Periodic Background Sync (WorkManager)

```
WorkManager fires WidgetSyncWorker every 15 minutes (when network available)
    ↓
WidgetSyncWorker.doWork()
    → NotesGlanceWidget().updateAll(applicationContext)
    ↓
For each placed widget instance:
    provideGlance() runs → widgetStateStore.getDocumentId() → bulletRepository.getRootBullets()
    → fresh network fetch via shared OkHttpClient
    → provideContent { ... }
```

The 15-minute interval is an Android OS minimum, not a product choice. Manual pull-to-refresh in the widget (button that calls `updateAll`) handles on-demand sync.

### Flow 4: Auth Token (Unchanged — Zero Widget-Specific Work)

```
Widget HTTP request (bulletRepository → Retrofit → OkHttpClient)
    → AuthInterceptor.intercept()
        → runBlocking { tokenStore.getAccessToken() }   // same Tink-encrypted DataStore
        → adds Authorization: Bearer header
    → If 401: TokenAuthenticator refreshes token (existing logic)
    → If refresh fails: clearAll() → widget receives empty/error response
        → widget UI shows "Please log in to the app" empty state
```

The widget shares the same `TokenStore`, `OkHttpClient`, and `Retrofit` singletons as the main app. No separate credential storage or auth flow is needed.

### Widget UI State Machine

```
Not configured (no docId in WidgetStateStore)
    → WidgetConfigActivity shown automatically by Android (android:configure in metadata)
    ↓ user picks document + saveDocumentId() + updateAll()
Configured, loading
    → provideGlance() fetching bullets
    ↓ success
Showing bullets (root-level flat list)
    → user taps "+" → launches WidgetAddBulletActivity (thin overlay Activity)
    → user taps delete icon on bullet → actionRunCallback<DeleteBulletCallback>()
        (DeleteBulletCallback: GlanceAppWidgetReceiver action callback, injects repo via EntryPoint)
    ↓ mutation complete
Back to "Configured, loading" → fresh fetch → rerender
    ↓ WorkManager tick OR in-app mutation
Same loop
```

---

## Integration Points

### New vs Modified Components

| Build Order | Component | Status | Integrates With |
|-------------|-----------|--------|-----------------|
| 1 | `WidgetStateStore` | NEW | Nothing new — standalone DataStore singleton; follows existing `TokenStore` pattern |
| 2 | `NotesGlanceWidget` | NEW | `WidgetStateStore`, `BulletRepository`, `DocumentRepository` via `EntryPointAccessors` |
| 3 | `NotesWidgetReceiver` | NEW | `NotesGlanceWidget`, `WidgetStateStore`, `WidgetSyncWorker` |
| 4 | `WidgetConfigActivity` | NEW | `GetDocumentsUseCase`, `WidgetStateStore`, `NotesGlanceWidget.updateAll()` |
| 5 | `WidgetSyncWorker` | NEW | `BulletRepository`, `WidgetStateStore`, `NotesGlanceWidget.updateAll()` |
| 6 | `BulletTreeViewModel` | MODIFIED | Adds `NotesGlanceWidget().updateAll()` call after successful mutations |
| 7 | `NotesApplication` | MODIFIED | Adds `Configuration.Provider` + `HiltWorkerFactory` injection |

### New Gradle Dependencies

```toml
# libs.versions.toml additions
glance = "1.1.1"
workManager = "2.10.0"
hiltWork = "1.2.0"

[libraries]
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
workmanager-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
hilt-work-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }
```

`hilt-work-compiler` must be added as a `ksp(...)` dependency (not `implementation`) in `app/build.gradle.kts`, alongside the existing `ksp(libs.hilt.android.compiler)`.

### AndroidManifest.xml Additions

```xml
<!-- Widget receiver -->
<receiver
    android:name=".widget.NotesWidgetReceiver"
    android:exported="true"
    android:label="Notes Widget">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/notes_widget_info" />
</receiver>

<!-- Widget configuration activity -->
<activity
    android:name=".widget.WidgetConfigActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

### res/xml/notes_widget_info.xml

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:targetCellWidth="3"
    android:targetCellHeight="3"
    android:minWidth="180dp"
    android:minHeight="180dp"
    android:minResizeWidth="140dp"
    android:minResizeHeight="140dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:configure="com.gmaingret.notes.widget.WidgetConfigActivity"
    android:widgetFeatures="configuration_optional|reconfigurable"
    android:initialLayout="@layout/glance_default_loading_layout">
</appwidget-provider>
```

`updatePeriodMillis="0"` because WorkManager owns the refresh schedule. Setting this to non-zero causes redundant Android OS wake-ups alongside WorkManager.

---

## Anti-Patterns

### Anti-Pattern 1: @AndroidEntryPoint on GlanceAppWidget

**What people try:** Annotating `GlanceAppWidget` with `@AndroidEntryPoint` or declaring `@Inject lateinit var` fields inside it.

**Why it fails:** `GlanceAppWidget` is not an Android component. Hilt's `@AndroidEntryPoint` only applies to Activity, Fragment, Service, BroadcastReceiver, and View. The annotation will compile; the injection will never run. Fields will be null at runtime.

**Do this instead:** Use `EntryPointAccessors.fromApplication()` inside `provideGlance()` with a `@InstallIn(SingletonComponent::class)` entry point interface (Pattern 1 above).

### Anti-Pattern 2: Storing Selected Document in Glance Built-In State

**What people try:** Using `updateAppWidgetState()` and `currentState<Preferences>()` to store the selected `documentId` as Glance's internal per-widget state.

**Why it's wrong:** Glance's built-in state is scoped to the rendering session and is not accessible outside `provideGlance()`. `WidgetConfigActivity` cannot write to it before the widget has first rendered. `NotesWidgetReceiver.onDeleted()` cannot read it to clean up. This forces an impossible initialization order.

**Do this instead:** Use a plain DataStore singleton (`WidgetStateStore`) keyed by `appWidgetId`. Accessible from any app component.

### Anti-Pattern 3: Fetching Data Inside the provideContent Composable Lambda

**What people try:** `provideContent { val bullets = runBlocking { repo.getBullets() } ... }` or using `LaunchedEffect` inside the Glance composable lambda.

**Why it's wrong:** The composable lambda passed to `provideContent {}` runs on the composition thread. Blocking it hangs rendering. `LaunchedEffect` is not available in Glance composables (Glance uses a restricted Compose runtime subset).

**Do this instead:** Fetch all data in `provideGlance()` before calling `provideContent {}`, using `withContext(Dispatchers.IO)`. Pass fetched data as parameters into the composable.

### Anti-Pattern 4: Custom BroadcastReceiver to Signal Widget Update from App

**What people try:** Sending a custom broadcast from `BulletTreeViewModel` and handling it in `onReceive()` to trigger a widget refresh.

**Why it's unnecessary:** The app and widget receiver are in the same process. `NotesGlanceWidget().updateAll(context)` called directly from `viewModelScope.launch` is simpler, type-safe, and coroutine-native. Custom broadcasts are only needed when the update source is a separate process.

**Do this instead:** Call `NotesGlanceWidget().updateAll(applicationContext)` in a fire-and-forget `viewModelScope.launch` after successful mutations in `BulletTreeViewModel`.

### Anti-Pattern 5: Missing HiltWorkerFactory Setup

**What people try:** Adding `@HiltWorker` to a worker without updating `NotesApplication` to implement `Configuration.Provider`.

**Why it fails:** Without `HiltWorkerFactory` registered as WorkManager's factory, Hilt-injected workers fall back to the default no-arg constructor. Any `@Inject` field in the worker will be missing. WorkManager will either crash at enqueue time or produce a worker with null dependencies.

**Do this instead:** Implement `Configuration.Provider` in `NotesApplication` and inject `HiltWorkerFactory` (see Pattern 3 above). This is a one-time setup and enables `@HiltWorker` for all future workers.

---

## Scaling Considerations

This is a personal self-hosted tool. Scaling is not a concern. The widget fetches root-level bullets for one document per instance — a bounded, small payload in all realistic scenarios. WorkManager's 15-minute minimum is an OS constraint, not a product limitation.

The only realistic "scale" concern is multiple widget instances on the same device pointing to different documents: `updateAll()` triggers `provideGlance()` for every placed instance. With 5-10 instances this is still trivially fast (parallel small API calls through the shared OkHttpClient).

---

## Sources

- [Create an app widget with Glance — Android Developers (official codelab)](https://developer.android.com/codelabs/glance) — HIGH confidence — covers @AndroidEntryPoint on receiver, EntryPoint pattern for widget class, config activity pattern
- [Manage and update GlanceAppWidget — Android Developers](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) — HIGH confidence — covers updateAll(), WorkManager CoroutineWorker pattern, when to update
- [Create an app widget with Glance — official docs](https://developer.android.com/develop/ui/compose/glance/create-app-widget) — HIGH confidence — GlanceAppWidget, GlanceAppWidgetReceiver, provideGlance lifecycle
- [Use Hilt with other Jetpack libraries — Android Developers](https://developer.android.com/training/dependency-injection/hilt-jetpack) — HIGH confidence — @HiltWorker, HiltWorkerFactory, Configuration.Provider
- [Dagger Hilt Entry Points documentation](https://dagger.dev/hilt/entry-points.html) — HIGH confidence — EntryPointAccessors.fromApplication() pattern
- [Glance release notes — version 1.1.1 stable, 1.2.0-rc01 in development](https://developer.android.com/jetpack/androidx/releases/glance) — HIGH confidence — version confirmation
- [Android Widgets with Glance: what's new with Google I/O 2024](https://medium.com/@ssharyk/android-widgets-with-glance-whats-new-with-google-i-o-2024-08b85b7ce676) — MEDIUM confidence
- Direct inspection: existing `TokenStore.kt`, `NotesApplication.kt`, `NetworkModule.kt`, `DataModule.kt`, `libs.versions.toml` — HIGH confidence for integration surface

---

*Architecture research for: Jetpack Glance widget integration with existing Clean Architecture Android app (v2.1 milestone)*
*Researched: 2026-03-14*
