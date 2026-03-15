# Project Research Summary

**Project:** Notes v2.1 — Android Home Screen Widget
**Domain:** Jetpack Glance home screen widget added to an existing Kotlin/Compose Android app with Clean Architecture, Hilt DI, DataStore+Tink, and Retrofit/OkHttp
**Researched:** 2026-03-14
**Confidence:** HIGH

## Executive Summary

The v2.1 milestone adds an Android home screen widget to a fully-built Notes Android app (12,200 LOC Kotlin, Clean Architecture). The widget is built on Jetpack Glance 1.1.1 — the only production-stable Jetpack widget framework. Glance composables render to `RemoteViews`, not a standard Compose surface, which means the entire set of standard Compose and Material 3 components is unavailable inside the widget. Developers must import exclusively from `androidx.glance.*` and keep a hard package boundary between widget code and app UI code. The widget reuses all existing infrastructure: the Hilt singleton graph, the Retrofit API interfaces, the DataStore+Tink `TokenStore`, and the existing `BulletRepository` and `DocumentRepository`. New infrastructure is limited to Glance+WorkManager+HiltWork dependencies, a `WidgetStateStore` DataStore singleton, and the `widget/` package containing four new components.

The recommended approach is a three-phase build ordered by dependency: foundation first (DI wiring, widget registration, configuration activity, basic rendering), then background sync infrastructure (WorkManager, auth token strategy for widget context, multi-instance support), then interactive action handling (delete from widget, add via overlay Activity, in-app broadcast). This ordering is non-negotiable: the DI entry-point pattern must be correct before any feature work because errors there are silent at compile time but produce null fields at runtime. Auth strategy is the second critical dependency because the existing app stores the access token in-memory only — a gap the widget process cannot bridge without an explicit refresh-cookie strategy in the worker.

The most significant risk is the in-memory access token gap: the existing `TokenStore` keeps the short-lived access token in a `StateFlow` that is null when the widget receiver wakes in a fresh process. The widget must use the persisted refresh cookie from encrypted DataStore to obtain a fresh token independently. A secondary risk is the `GlanceAppWidget` Hilt injection pattern: native `@AndroidEntryPoint` is not supported on `GlanceAppWidget` (open issue since 2021), and developers who assume it works will get silent null-field failures. The workaround is well-documented and verified against the official Glance issue tracker: use `@EntryPoint` + `EntryPointAccessors.fromApplication()` inside `provideGlance()`.

## Key Findings

### Recommended Stack

The existing Android stack (Kotlin 2.1.20, Compose BOM 2025.02.00, Material 3, Hilt 2.56.1, Retrofit 3.0.0 + OkHttp 4.12.0, DataStore 1.2.1 + Tink 1.8.0, Navigation3 1.0.1, AGP 8.9.1, compileSdk 36, minSdk 26) is validated and unchanged. Three dependency groups are added for the widget.

**New dependencies for v2.1 (add to `android/gradle/libs.versions.toml`):**

- **Glance AppWidget 1.1.1** (`androidx.glance:glance-appwidget`): Widget rendering framework — the only stable Jetpack option; 1.1.1 includes a protobuf security fix (CVE-2024-7254); do not add the `glance` core artifact separately (pulled in transitively)
- **Glance Material 3 1.1.1** (`androidx.glance:glance-material3`): Maps the app's M3 color tokens into widget surfaces via `GlanceMaterialTheme`; without it the widget ignores the app's theme
- **WorkManager KTX 2.11.1** (`androidx.work:work-runtime-ktx`): Periodic 15-minute background sync; `CoroutineWorker` for Kotlin-native suspend logic; minSdk raised to 23 in 2.11.0 — no conflict with project minSdk 26
- **Hilt Work 1.3.0** (`androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler`): `@HiltWorker` + `@AssistedInject` for injecting repositories into the WorkManager worker; must use `ksp(...)` not `kapt`

One mandatory configuration change accompanies `hilt-work`: `NotesApplication` must implement `Configuration.Provider` and inject `HiltWorkerFactory`. The default WorkManager auto-initializer must be disabled in `AndroidManifest.xml` via the `tools:node="remove"` meta-data entry, or WorkManager crashes on init.

**What not to add:** `androidx.glance:glance` core (transitive), `glance-material` Material 2 variant, `work-multiprocess` (same process app), FCM/push (out of scope per PROJECT.md), `glance-wear-tiles` (Wear OS only).

### Expected Features

**Must have — v2.1 launch (P1):**

- Config activity: document picker launched on widget placement; handles Back press with `RESULT_CANCELED`; reconfigurable on long-press (Android 12+)
- Scrollable `LazyColumn` of root-level bullet text (no nested bullets — fundamental widget constraint)
- Loading / Empty / Error / Unauthenticated states with user-readable messages using a sealed state class
- Manual refresh button in widget header
- Delete bullet from widget via per-row delete button using `ActionParameters` (not lambda closures — not serializable across RemoteViews process boundaries)
- Add new bullet via transparent dialog-themed overlay `Activity` (not `SYSTEM_ALERT_WINDOW`)
- WorkManager periodic sync (15-minute interval, `setRequiredNetworkType(CONNECTED)`, `enqueueUniquePeriodicWork(KEEP)`)
- In-app broadcast: call `updateAll()` from `BulletTreeViewModel` in a fire-and-forget `viewModelScope.launch` after every successful mutation
- Per-instance document binding: `appWidgetId → documentId` stored in `WidgetStateStore`
- Unauthenticated state: "Open app to sign in" with `actionStartActivity` tap target

**Should have — v2.1 if time permits (P2):**

- Tap row → deep-link opens the app at the specific document
- Widget reconfiguration flag (`android:widgetFeatures="reconfigurable"`)

**Defer to v2.1.x / v2.2+:**

- Completed-bullet strikethrough in widget
- Mark-complete action from widget (extends delete pattern but adds visual state complexity)
- Widget theming (defer until app theming is finalized)
- Error state retry button (one-liner add once error state is shipped)

**Hard anti-features (platform constraints — never implement):**

- Inline text editing in widget — `EditText` is not supported by RemoteViews or Glance; no workaround exists
- Sub-15-minute background refresh — OS silently coerces shorter intervals to 15 minutes
- Drag-to-reorder in widget — no drag-and-drop mechanism in RemoteViews or Glance
- Nested bullet tree in widget — widget screen real estate; root-level only is the declared product goal
- Auth flow inside widget — widget process cannot reliably host OAuth or credential flows

### Architecture Approach

The widget layer is a clean addition to the existing Clean Architecture. Seven components are affected: four new (in a `widget/` package), two minor modifications to existing classes, and zero changes to data/domain/network layers. The new `WidgetStateStore` DataStore singleton is the coordination hub — every widget component reads/writes through it. Glance's built-in `PreferencesGlanceStateDefinition` is explicitly avoided because it is scoped to the rendering session and is inaccessible from `WidgetConfigActivity` (which must write before the widget has rendered) or from `NotesWidgetReceiver.onDeleted()`.

**Build order (dependency-ordered):**

1. `WidgetStateStore` (NEW) — standalone DataStore singleton; follows existing `TokenStore.kt` pattern; no dependencies on new widget code
2. `NotesGlanceWidget` (NEW) — reads `WidgetStateStore` + repositories via `EntryPointAccessors.fromApplication()`; fetches data in `provideGlance()` before calling `provideContent {}`
3. `NotesWidgetReceiver` (NEW) — `@AndroidEntryPoint`; manages `WidgetSyncWorker` lifecycle in `onEnabled`/`onDisabled`; cleans up `WidgetStateStore` keys in `onDeleted()`
4. `WidgetConfigActivity` (NEW) — document picker; writes `appWidgetId → documentId` to `WidgetStateStore`; calls `NotesGlanceWidget().updateAll()`; returns `setResult(RESULT_OK)` before `finish()`
5. `WidgetSyncWorker` (NEW) — `@HiltWorker` `CoroutineWorker`; calls `NotesGlanceWidget().updateAll(context)` every 15 minutes when network is connected
6. `BulletTreeViewModel` (MODIFIED) — add `viewModelScope.launch { NotesGlanceWidget().updateAll(applicationContext) }` after successful mutations
7. `NotesApplication` (MODIFIED) — add `Configuration.Provider` + inject `HiltWorkerFactory`

**Non-negotiable patterns:**

- `EntryPointAccessors.fromApplication()` with a `@InstallIn(SingletonComponent::class)` `@EntryPoint` interface — the only correct Hilt path into `GlanceAppWidget`
- All I/O inside `provideGlance()` using `withContext(Dispatchers.IO)` — never inside `provideContent {}` composable lambda
- `updateAll()` everywhere except inside `ActionCallback.onAction()` — never hard-code a single `glanceId`
- Write to DataStore first, then call `update()` — `provideGlance()` re-reads DataStore on every recompose
- `android:updatePeriodMillis="0"` in `appwidget-provider` XML — WorkManager exclusively owns the schedule
- `SizeMode.Responsive` with 2-3 predefined `DpSize` breakpoints — not `SizeMode.Exact`

### Critical Pitfalls

1. **Hilt cannot inject into `GlanceAppWidget`** — `@AndroidEntryPoint` compiles but never runs on `GlanceAppWidget`; injected fields are null at runtime. Use `@EntryPoint` + `EntryPointAccessors.fromApplication()` inside `provideGlance()`. `@AndroidEntryPoint` is valid on `GlanceAppWidgetReceiver` only. Phase: foundation.

2. **In-memory access token is null in widget context** — the app stores the access token in a `StateFlow` that is null when the widget receiver wakes in a fresh process. The widget must read the persisted refresh cookie from encrypted DataStore and call `/auth/refresh` independently. Do not assume the singleton `OkHttpClient`'s in-memory cookie jar is populated. Phase: sync/auth.

3. **Glance composables are not Jetpack Compose composables** — import exclusively from `androidx.glance.*`. No `AnimatedVisibility`, no `MaterialTheme`, no `LazyListState`, no `Modifier` (use `GlanceModifier`). Custom fonts (`Inter Variable`) are not renderable in Glance — design with system fonts from the start. Phase: foundation.

4. **Config activity must call `setResult(RESULT_OK)` with widget ID extra** — omitting this causes the launcher to silently discard the widget placement. Set `RESULT_CANCELED` at activity start; only switch to `RESULT_OK` on user confirmation. Phase: foundation.

5. **Race condition between action and state update** — concurrent `update()` calls from the 15-min sync and an `ActionCallback` can race; Glance has an internal update lock period that silently drops requests during an in-progress update. Write new state to DataStore first, then call `update()`. Use `WorkManager.enqueueUniqueWork(REPLACE)` for on-demand refreshes. Phase: action handling.

6. **`updatePeriodMillis` is unreliable on OEM devices** — OEM battery management (Xiaomi, Samsung, Huawei) suppresses broadcasts; the Android OS silently rounds 15-minute values up to 30 minutes. Set `updatePeriodMillis="0"` and use WorkManager exclusively. Phase: sync setup.

7. **`update()` misses secondary widget instances** — calling `update()` with a hard-coded `glanceId` silently skips all other placed instances. Use `updateAll()` everywhere except inside `ActionCallback.onAction()` where the specific instance is known. Phase: sync setup.

## Implications for Roadmap

Research strongly suggests three phases ordered by strict technical dependency. No phase can begin until the previous one is verified because each builds on infrastructure the prior phase establishes.

### Phase 1: Widget Foundation

**Rationale:** DI wiring, widget registration, sizing, and the configuration activity contract must be correct before any rendering or sync work. The wrong Hilt pattern produces silent null-field failures that are extremely hard to diagnose after feature code is layered on top. The `RESULT_OK` contract failure causes silent widget discard with nothing in logcat on physical devices. Get the structural bones right first.

**Delivers:** A placeable widget that shows the selected document's root bullets after manual placement. No background sync, no interactive actions — static rendering with correct states (Loading, Empty, Error, Unauthenticated).

**Addresses features:** Config activity (P1), scrollable bullet list (P1), loading/empty/error/unauthenticated states (P1), per-instance document binding (P1)

**Avoids pitfalls:** Hilt entry-point anti-pattern (Pitfall 1), Glance/Compose import confusion (Pitfall 3), config activity `RESULT_OK` failure (Pitfall 4/7), `SizeMode.Exact` layout jank (Pitfall 6)

**Research flag:** Not needed. All patterns are verified against the official Glance codelab and Android docs. ARCHITECTURE.md contains production-ready code samples for every component in this phase.

### Phase 2: Background Sync and Auth

**Rationale:** WorkManager and auth strategy are the next dependency layer. The widget must function when the app is force-stopped (the 401 gap from in-memory tokens), and it must update all placed instances (not just one). These are correctness requirements that block interactive features in Phase 3 — delete and add actions make real API calls that require valid auth.

**Delivers:** Widget that stays current without user action. WorkManager periodic sync at 15-minute intervals. Correct auth token handling from widget process context (read refresh cookie → call `/auth/refresh` → use token for API call). Multi-instance support verified (`updateAll()` throughout). Manual refresh button.

**Addresses features:** WorkManager periodic sync (P1), in-app broadcast refresh from `BulletTreeViewModel` (P1), manual refresh button (P1), unauthenticated state flow (P1)

**Avoids pitfalls:** In-memory token gap (Pitfall 2), `updatePeriodMillis` OEM unreliability (Pitfall 6), single-instance `update()` vs `updateAll()` bug (Pitfall 9), missing `HiltWorkerFactory` setup (architecture Anti-Pattern 5)

**Research flag:** The auth token strategy for widget worker context deserves explicit design before Phase 2 begins. Validate whether the shared `OkHttpClient` with `AuthInterceptor` handles the widget worker context correctly, or whether a worker-scoped client is needed. Confirm the actual `/auth/refresh` endpoint behavior when called from a `CoroutineWorker` with a DataStore-read cookie.

### Phase 3: Interactive Actions

**Rationale:** Action handling (delete, add) depends on the correct update loop from Phase 2. The persist-before-update ordering and race condition mitigations only make sense once the update path is established and verified. The transparent overlay Activity for adding bullets also requires valid auth — hence Phase 2 first.

**Delivers:** Full interactive widget. Delete bullet from widget via per-row `ActionCallback` + `ActionParameters`. Add bullet via transparent dialog-themed overlay `Activity`. Tap row → deep-link to document in the app.

**Addresses features:** Delete from widget (P1), add via overlay Activity (P1), tap row deep-link (P2)

**Avoids pitfalls:** Persist-before-update ordering failure (Pitfall 8), race condition on concurrent updates (Pitfall 4), Activity launch from broadcast receiver on Android 12+ (use `actionStartActivity` not lambda), `onAction()` not deprecated `onRun()` in `ActionCallback`

**Research flag:** Not needed. The `ActionParameters` pattern, `ActionCallback` API, and transparent overlay `Activity` pattern are all verified against official docs with clear code samples in FEATURES.md and ARCHITECTURE.md.

### Phase Ordering Rationale

- `WidgetStateStore` must exist and be populated by `WidgetConfigActivity` before WorkManager has a `documentId` to fetch — Phase 1 before Phase 2
- Auth must be solved before interactive API calls (delete/add) are possible — Phase 2 before Phase 3
- The "looks done but isn't" checklist items map cleanly to phase boundaries: Phase 1 verifies placement and basic rendering, Phase 2 verifies persistence across process death and multi-instance correctness, Phase 3 verifies mutations with optimistic state updates
- WorkManager unique job deduplication must be established in Phase 2 to prevent duplicate workers accumulating across widget add/remove/add cycles

### Research Flags

Phases needing explicit design review during planning:

- **Phase 2 (auth strategy):** Validate the refresh-cookie-to-access-token flow from a `CoroutineWorker` context. Determine whether to reuse the shared `OkHttpClient` (which has `AuthInterceptor` but an in-memory cookie jar that may be null) or create a worker-scoped client that explicitly reads the Tink-encrypted cookie from DataStore and injects it as a header.

Phases with standard, well-documented patterns (skip `/gsd:research-phase`):

- **Phase 1 (Widget Foundation):** Official Glance codelab + docs cover every required pattern. ARCHITECTURE.md provides production-ready code samples for all four new components.
- **Phase 3 (Action Handling):** `ActionCallback`, `ActionParameters`, transparent overlay `Activity` — all verified against official docs with clear examples in FEATURES.md and ARCHITECTURE.md.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All new dependency versions verified against official release notes and MVN Repository as of 2026-03-14. Version catalog TOML entries ready to copy in verbatim. |
| Features | HIGH | Official Android docs + direct comparison against Google Keep / Tasks widget baseline. Feature boundaries (especially anti-features) are grounded in platform constraints, not opinion. |
| Architecture | HIGH | Patterns verified against official Glance codelab (SociaLite sample), official Hilt docs, Dagger entry-point documentation, and direct inspection of existing app source files. |
| Pitfalls | HIGH | Critical pitfalls sourced from official docs and confirmed open issue tracker items (Glance + Hilt issue #218520083, open since 2021). OEM battery restriction pitfall supported by multiple practitioner sources and official WorkManager docs. |

**Overall confidence: HIGH**

### Gaps to Address

- **Auth token strategy for widget worker:** The in-memory token gap is identified and the solution direction is clear (read refresh cookie from DataStore, call `/auth/refresh`), but the exact implementation — specifically whether the shared `OkHttpClient`'s `AuthInterceptor` + `TokenAuthenticator` can be reused from a worker context or requires a worker-scoped client — must be validated against the actual backend in Phase 2 before action handling is built.

- **`LazyColumn` item count limits:** PITFALLS.md flags a risk with 30+ root-level bullets. Glance's `LazyColumn` is backed by `ListView` which has a soft cap on the number of `RemoteViews` children. Test with a document that has 30+ root bullets before shipping Phase 1.

- **OEM battery restriction testing:** WorkManager reliability on Xiaomi/Samsung/Huawei is best-effort by design. The manual refresh button mitigates this for users. Test on at least one OEM device (not just the Pixel emulator) during Phase 2 if hardware is available.

## Sources

### Primary (HIGH confidence)

- [Create an app widget with Glance — Android Developers codelab](https://developer.android.com/codelabs/glance) — EntryPoint pattern, receiver setup, config activity contract
- [Manage and update GlanceAppWidget — Android Developers](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) — `updateAll()`, WorkManager integration, update triggers
- [Build UI with Glance — Android Developers](https://developer.android.com/develop/ui/compose/glance/build-ui) — composable subset, `GlanceModifier`, `LazyColumn` backed by `ListView`
- [Create an app widget with Glance — Android Developers](https://developer.android.com/develop/ui/compose/glance/create-app-widget) — `GlanceAppWidget`, `GlanceAppWidgetReceiver`, `provideGlance` lifecycle
- [Enable users to configure app widgets — Android Developers](https://developer.android.com/develop/ui/views/appwidgets/configuration) — `RESULT_OK` contract, `EXTRA_APPWIDGET_ID`, Back press handling
- [Handle errors with Glance — Android Developers](https://developer.android.com/develop/ui/compose/glance/error-handling) — error state composable pattern, `glance_error_layout`
- [Handle user interaction — Glance — Android Developers](https://developer.android.com/develop/ui/compose/glance/user-interaction) — `ActionCallback`, `ActionParameters`, `actionStartActivity`
- [Use Hilt with other Jetpack libraries — Android Developers](https://developer.android.com/training/dependency-injection/hilt-jetpack) — `@HiltWorker`, `HiltWorkerFactory`, `Configuration.Provider`
- [Dagger Hilt Entry Points](https://dagger.dev/hilt/entry-points.html) — `EntryPointAccessors.fromApplication()` pattern, `@InstallIn(SingletonComponent::class)`
- [Glance release notes](https://developer.android.com/jetpack/androidx/releases/glance) — 1.1.1 stable confirmed; 1.2.0-rc01 latest RC
- [WorkManager release notes](https://developer.android.com/jetpack/androidx/releases/work) — 2.11.1 stable confirmed, minSdk 23
- [Hilt Jetpack releases](https://developer.android.com/jetpack/androidx/releases/hilt) — `androidx.hilt:hilt-work` 1.3.0 stable confirmed
- [Glance issue tracker #218520083](https://issuetracker.google.com/issues/218520083) — confirmed no native Hilt support on `GlanceAppWidget`; EntryPoint workaround is the official recommendation
- Direct code inspection: `data/local/TokenStore.kt`, `data/api/BulletApi.kt`, `data/api/DocumentApi.kt`, `app/NotesApplication.kt`, `di/NetworkModule.kt`, `di/DataModule.kt`, `gradle/libs.versions.toml`

### Secondary (MEDIUM confidence)

- [Demystifying Jetpack Glance for app widgets — Android Developers Medium](https://medium.com/androiddevelopers/demystifying-jetpack-glance-for-app-widgets-8fbc7041955c) — update patterns, common failure modes
- [Taming Glance Widgets: Fast & Reliable Widget Updates — Medium, Nov 2025](https://medium.com/@abdalmoniemalhifnawy/taming-glance-widgets-a-deep-dive-into-fast-reliable-widget-updates-ae44bfc4c75a) — race condition and update lock period analysis
- [Android: Jetpack Glance with Hilt — Medium](https://medium.com/@debuggingisfun/android-jetpack-glance-with-hilt-6dce38cc9ff6) — EntryPoint workaround practitioner example, consistent with issue tracker
- [Widgets with Glance: Beyond String States — ProAndroidDev](https://proandroiddev.com/widgets-with-glance-beyond-string-states-2dcc4db2f76c) — sealed state class pattern for widget UI states
- [Updating widgets with Jetpack WorkManager — DEV Community](https://dev.to/tkuenneth/updating-widgets-with-jetpack-workmanager-g0b) — WorkManager periodic sync implementation
- [How to reliably update widgets on Android — arkadiuszchmura.com](https://arkadiuszchmura.com/posts/how-to-reliably-update-widgets-on-android/) — `updatePeriodMillis` vs WorkManager with OEM battery restriction analysis
- [How to Reliably Refresh Your Widgets — Ackee blog](https://www.ackee.agency/blog/how-to-reliably-refresh-widgets) — vendor battery restriction patterns across OEM ROMs
- [Android Widgets with Glance: what's new with Google I/O 2024](https://medium.com/@ssharyk/android-widgets-with-glance-whats-new-with-google-i-o-2024-08b85b7ce676) — `SizeMode.Responsive` best practices
- [Goodbye EncryptedSharedPreferences: A 2026 Migration Guide — ProAndroidDev](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a) — confirms DataStore + Tink as the current encrypted storage path

---
*Research completed: 2026-03-14*
*Ready for roadmap: yes*
