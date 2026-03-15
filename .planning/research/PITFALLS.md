# Pitfalls Research

**Domain:** Jetpack Glance home screen widget — adding to existing Kotlin/Compose Android app
**Researched:** 2026-03-14
**Confidence:** HIGH (critical pitfalls verified against official Android docs and multiple practitioner sources)

---

## Critical Pitfalls

### Pitfall 1: Hilt Cannot Inject Into GlanceAppWidget or GlanceAppWidgetReceiver

**What goes wrong:**
Developers annotate `GlanceAppWidgetReceiver` with `@AndroidEntryPoint` expecting the same Hilt injection path used in Activities, Fragments, and ViewModels. This compiles but either silently injects nothing or crashes at runtime. `GlanceAppWidget.provideGlance()` has no injection point at all — it is not a Hilt-aware component.

**Why it happens:**
Hilt has first-class support for ViewModel, WorkManager, Navigation, and Compose, but not for Glance. This is an open feature request (Google Issue Tracker #218520083) that was still unresolved as of late 2025. Developers assume Glance receives the same treatment as WorkManager (`@HiltWorker` / `@AssistedInject`) and are surprised when it does not.

**How to avoid:**
Use the Hilt `@EntryPoint` pattern manually inside `provideGlance()`:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun notesRepository(): NotesRepository
    fun authRepository(): AuthRepository
}

// Inside GlanceAppWidget.provideGlance()
val entryPoint = EntryPointAccessors.fromApplication(
    context.applicationContext,
    WidgetEntryPoint::class.java
)
val repo = entryPoint.notesRepository()
```

`@AndroidEntryPoint` is still valid on `GlanceAppWidgetReceiver` itself (for the receiver's `onUpdate` / `onReceive` body), but `GlanceAppWidget` requires the `EntryPointAccessors` pattern. Only `@Singleton` or unscoped bindings are safe to retrieve from this context — do not request `@ActivityRetainedScoped` or `@ViewModelScoped` bindings.

**Warning signs:**
- `NullPointerException` on an injected field inside `provideGlance()`
- Hilt generates "Unsatisfied dependency" errors at compile time when scoped bindings are referenced
- Repository calls succeed in the app but NPE in the widget

**Phase to address:** Phase 1 — Widget Foundation. The DI wiring must be correct before any feature work begins.

---

### Pitfall 2: In-Memory Access Token Is Not Available in Widget Context

**What goes wrong:**
The existing app stores the access token in a Kotlin `StateFlow` inside an `AuthRepository` singleton that lives in the app process. When the widget receiver wakes up (often in a short-lived process separate from any running foreground activity), that singleton may be freshly constructed with a null token. API calls from the widget fail with 401.

**Why it happens:**
Android widget receivers extend `BroadcastReceiver`. The system can run them in a process that has no foreground components. Even if the singleton exists, `StateFlow` initial state is null — the in-memory token is only populated after a successful login flow that runs in the Activity/ViewModel layer. The widget has no way to trigger that flow.

This project deliberately stores the access token in-memory only (not in SharedPreferences). The right call for the main app creates a gap for widgets.

**How to avoid:**
The widget must obtain a fresh access token independently using the refresh cookie. The existing encrypted DataStore already stores the refresh cookie (via Tink). The widget should:

1. Read the refresh cookie from DataStore (accessible because it is persisted, not in-memory).
2. Call the `/auth/refresh` endpoint using a dedicated OkHttp client instance created inside the widget or WorkManager worker — do not assume the singleton `OkHttpClient` has a populated in-memory cookie jar.
3. Use the resulting short-lived access token for the single API call, then discard it.

The WorkManager `CoroutineWorker` is the correct location for this logic since it already has Hilt support via `@HiltWorker`.

**Warning signs:**
- Widget API calls return 401 intermittently (works when app is open, fails when app is backgrounded or killed)
- Logcat shows `IllegalStateException` or NPE accessing `authRepository.accessToken.value` from widget context
- Widget works immediately after user logs in, then breaks after phone restarts overnight

**Phase to address:** Phase 2 — Auth Integration. Must be solved before any real API call is attempted from the widget.

---

### Pitfall 3: Glance Composables Are Not Jetpack Compose Composables

**What goes wrong:**
Developers import `androidx.compose.foundation.*` or `androidx.compose.material3.*` inside `GlanceAppWidget.Content()`. The build fails or the composable silently renders nothing. Even when imports are correct, developers apply modifiers, animations, or theming from Compose that do not exist in Glance.

**Why it happens:**
Glance uses a Compose runtime but translates its composables into `RemoteViews`, not into a Compose surface. The available composable set is a strict subset: `Box`, `Column`, `Row`, `LazyColumn` (becomes `ListView`), `Text`, `Button`, `Image`, `CheckBox`, `Switch`. There are no `AnimatedVisibility`, no `SnackbarHost`, no `MaterialTheme`, no custom fonts. Glance uses its own `GlanceModifier`, not `Modifier`.

Key restriction: `LazyColumn` in Glance is backed by `ListView` — it does not support item keys, sticky headers, or `LazyListState`.

**How to avoid:**
- Import exclusively from `androidx.glance.*` inside any `GlanceAppWidget` subclass.
- Keep a hard package boundary: Glance files live in a `widget/` package, app Compose files live in `ui/`. Never cross-import.
- Glance does not support `@Preview` — use `GlancePreview` from `androidx.glance.appwidget.preview` or test on a device.
- Custom fonts (Inter Variable) are not renderable in Glance — widget text will use system fonts.

**Warning signs:**
- IDE shows no error but the widget renders blank or shows "Widget unavailable" on device
- `ClassCastException` at runtime mentioning `RemoteViews`
- Modifier compiler errors: `Unresolved reference: Modifier` (Glance uses `GlanceModifier`)

**Phase to address:** Phase 1 — Widget Foundation. A wrong import here produces silent failures that are hard to diagnose.

---

### Pitfall 4: Race Condition Between Widget Action and State Update

**What goes wrong:**
User taps "Delete bullet" in the widget. The `ActionCallback` fires, makes the API call, then calls `GlanceAppWidget().update(context, glanceId)`. Simultaneously, the 15-minute WorkManager sync fires and also calls `update()`. Both read the DataStore cache at the same time, one overwrites the other's result, and the widget shows stale data or the deleted bullet reappears.

Additionally, Glance has an internal update lock period — when a widget is already in the middle of updating, new update requests issued during that window are silently dropped.

**Why it happens:**
Glance's `update()` is a suspend function, but multiple callers can race without external coordination. There is no built-in locking mechanism across concurrent update triggers.

**How to avoid:**
- Funnel all widget state writes through a single `Mutex`-protected function in the repository layer.
- Use `WorkManager.enqueueUniqueWork(REPLACE)` for on-demand refreshes so concurrent triggers collapse into one.
- Write the new state to DataStore before calling `GlanceAppWidget().update()` — the update call re-reads DataStore, so the write must complete first (see Pitfall 8 below).
- For the delete action: optimistically update the DataStore cache immediately, then fire the API call. Do not wait for the API response before updating the widget or the UI will feel sluggish.

**Warning signs:**
- Deleted bullets occasionally reappear after a few seconds
- Widget shows loading state permanently after an action (update call was dropped during lock period)
- Rapid tap of "refresh" causes the widget to stop updating

**Phase to address:** Phase 3 — Action Handling. Applies to every interactive widget element.

---

### Pitfall 5: WorkManager Minimum Interval and Vendor Battery Restrictions

**What goes wrong:**
Developer sets `updatePeriodMillis` in `appwidget-provider` XML to 900000 (15 minutes) expecting reliable periodic updates. On many devices (especially Xiaomi, Huawei, Samsung with aggressive battery management), the widget stops refreshing entirely after a few cycles. On stock Android, the minimum honored value for `updatePeriodMillis` is 30 minutes — 15-minute values are silently rounded up to 30.

**Why it happens:**
`updatePeriodMillis` is unreliable by design. It is delivered as a broadcast that vendors can and do throttle or suppress under battery optimization. WorkManager itself runs in Doze mode at reduced frequency — on some OEM ROMs, periodic workers are deferred much longer than scheduled.

**How to avoid:**
- Set `android:updatePeriodMillis="0"` in the `appwidget-provider` XML. All periodic scheduling goes through WorkManager exclusively.
- Schedule as `PeriodicWorkRequest` with `repeatInterval = 15` minutes and `enqueueUniquePeriodicWork(KEEP)` so duplicate enqueues are ignored.
- Set `setRequiredNetworkType(NetworkType.CONNECTED)` — widget data is meaningless without a server connection.
- Do NOT set `setRequiresBatteryNotLow(true)` — overly restrictive for a user-visible widget.
- Accept that on heavily restricted OEM devices, 15-minute sync is best-effort. Make manual refresh a visible affordance in the widget.

**Warning signs:**
- Widget stops updating after the device sits idle overnight
- WorkManager shows tasks as `ENQUEUED` but never `RUNNING` in the WorkManager inspector
- Update frequency varies wildly across test devices

**Phase to address:** Phase 2 — WorkManager Sync Setup.

---

### Pitfall 6: Widget Size and SizeMode Misconfiguration

**What goes wrong:**
Developer uses `SizeMode.Exact` to get pixel-perfect control. On Android 11 and below, size calculation is unreliable across launchers — the reported available size can be significantly wrong. On all versions, `SizeMode.Exact` triggers a full widget recomposition every time the user resizes, causing visible UI jumps.

A separate mistake: `minWidth`/`minHeight` in `appwidget-provider` XML set too large so the widget cannot be placed on a standard 4x4 home screen grid. Or set too small causing layout overflow with no error.

**How to avoid:**
- Use `SizeMode.Responsive` with two or three well-chosen breakpoint sizes (e.g., `DpSize(180.dp, 110.dp)` for 2-cell, `DpSize(250.dp, 220.dp)` for 4-cell). This pre-renders layouts at fixed sizes; the best-fit is selected at runtime without recomposition on resize.
- If `SizeMode.Responsive` produces inconsistent launcher behavior, fall back to `SizeMode.Single` — simpler and more predictable.
- Avoid `SizeMode.Exact` unless the team has tested across at least three launcher implementations.
- Follow Android widget size guidelines: minimum cell size = approximately `(70 × n) - 30` dp. A 2×2 widget = `110 × 110` dp minimum.

**Warning signs:**
- Widget layout correct on Pixel but broken on Samsung or Xiaomi
- Widget placed but shows "Widget unavailable" when resized to a smaller size
- `LocalSize.current` returns `0.dp × 0.dp` inside `Content()`

**Phase to address:** Phase 1 — Widget Foundation. Get sizing right before any layout work.

---

### Pitfall 7: Widget Configuration Activity Doesn't Finish with RESULT_OK

**What goes wrong:**
User adds the widget to the home screen. The document picker configuration activity is shown. User selects a document and presses "Confirm." The widget is removed from the home screen immediately. The system treats the placement as cancelled.

**Why it happens:**
Android requires that a widget configuration `Activity` call `setResult(Activity.RESULT_OK, resultIntent)` where `resultIntent` carries `AppWidgetManager.EXTRA_APPWIDGET_ID`. If the activity finishes with `RESULT_CANCELED` (the default when `finish()` is called without `setResult`), or if the Intent is missing the widget ID extra, the launcher discards the widget placement silently.

**How to avoid:**
```kotlin
val appWidgetId = intent?.extras?.getInt(
    AppWidgetManager.EXTRA_APPWIDGET_ID,
    AppWidgetManager.INVALID_APPWIDGET_ID
) ?: AppWidgetManager.INVALID_APPWIDGET_ID

// Set CANCELED early so Back press is handled correctly
setResult(Activity.RESULT_CANCELED)

// When user confirms selection:
val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
setResult(Activity.RESULT_OK, resultValue)
finish()
```

**Warning signs:**
- Widget disappears from home screen immediately after configuration with no error in logcat (silent contract failure)
- Widget works without a configuration activity but fails when the activity is added
- Testing on emulator works but fails on physical device (some launchers are more strict)

**Phase to address:** Phase 1 — Widget Foundation.

---

### Pitfall 8: Calling update() Without First Persisting State to DataStore

**What goes wrong:**
`ActionCallback` modifies a local variable, calls `GlanceAppWidget().update(context, glanceId)`, and the widget re-renders with the old data. The action appears to do nothing.

**Why it happens:**
`GlanceAppWidget.Content()` is stateless by design. Every recomposition reads from `currentState()` (the Glance DataStore) or from an application-layer data source. Any in-memory state inside the widget composable is not guaranteed to survive across calls. `update()` triggers a new composition pass that re-reads whatever is persisted — if nothing was persisted, the old state is shown.

**How to avoid:**
Strict ordering: write to DataStore first, then trigger recomposition.

```kotlin
override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val result = repository.deleteBullet(bulletId)
    if (result.isSuccess) {
        // 1. Persist new state FIRST
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[widgetDataKey] = newSerializedState
        }
        // 2. Then trigger recomposition
        MyWidget().update(context, glanceId)
    }
}
```

**Warning signs:**
- Widget action appears to do nothing on first tap, works on the second tap (second tap triggers a periodic refresh that actually reads fresh data)
- Logcat shows `update()` called but the displayed content does not change

**Phase to address:** Phase 3 — Action Handling. Applies to every interactive element.

---

### Pitfall 9: Multiple Widget Instances Require updateAll() Not update()

**What goes wrong:**
Developer calls `MyWidget().update(context, hardcodedGlanceId)` inside the WorkManager worker. This updates only the widget instance that had that `glanceId` at a specific point in time. If the user placed the same widget twice (each showing a different document), only one instance is updated. The other becomes permanently stale.

**Why it happens:**
Each widget placement generates a unique `glanceId`. Developers testing with a single instance never notice the problem. The correct call iterates over all placed instances.

**How to avoid:**
Always use the iterator pattern in WorkManager workers and repository update functions:

```kotlin
val manager = GlanceAppWidgetManager(context)
val glanceIds = manager.getGlanceIds(MyWidget::class.java)
glanceIds.forEach { glanceId ->
    MyWidget().update(context, glanceId)
}
// Or simply:
MyWidget().updateAll(context)
```

Only use `update(context, specificGlanceId)` inside `ActionCallback.onAction()` where the specific instance that received the action is known.

**Warning signs:**
- Second widget instance placed shows correct data on first load, then never updates
- Removing the first widget instance and keeping the second causes updates to stop

**Phase to address:** Phase 2 — WorkManager Sync Setup. Must be correct in the initial worker implementation.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Use `updatePeriodMillis` instead of WorkManager | Simpler setup | Unreliable on OEM devices, silently capped at 30 min | Never — set to 0 and always use WorkManager |
| Store access token in plain `SharedPreferences` to bridge widget auth gap | Unblocks widget auth immediately | Token readable by other apps on rooted devices; undermines existing security posture | Never — use the encrypted DataStore already present |
| `SizeMode.Exact` for layout flexibility | Full control over layout at any size | Recomposition on every resize; unreliable on Android 11 and below | Only if `SizeMode.Responsive` is truly insufficient after testing |
| Re-use the main app's singleton `OkHttpClient` from widget | Avoids creating a second client | OkHttp cookie jar is in-memory; widget process may not have the jar populated | Never — widget needs its own client with DataStore-backed cookie loading |
| Hard-code a single `glanceId` for update calls | Simpler code | Breaks when user places multiple widget instances | Never — always iterate over `GlanceAppWidgetManager.getGlanceIds()` |
| Skip configuration activity — pin first document automatically | Simpler initial implementation | Removes the core "choose which document" requirement | Never for this milestone's requirements |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Hilt + GlanceAppWidget | `@AndroidEntryPoint` on `GlanceAppWidget` (unsupported component type) | `@EntryPoint` interface + `EntryPointAccessors.fromApplication()` inside `provideGlance()` |
| Hilt + GlanceAppWidgetReceiver | Injecting `@ActivityRetainedScoped` or `@ViewModelScoped` bindings | Only `@Singleton` or unscoped bindings are safe in widget receivers |
| Hilt + WorkManager (widget worker) | Creating the worker manually with `new` instead of using Hilt | Use `@HiltWorker` + `@AssistedInject` on the worker class; Hilt WorkManager integration is supported |
| OkHttp + widget auth | Assume singleton client cookie jar has cookies populated | Create a worker-scoped OkHttp client; load the refresh cookie from encrypted DataStore before each API call |
| DataStore + Glance state | Mix Glance's `PreferencesGlanceStateDefinition` with the app's `DataStore` | Keep them separate: Glance state stores widget-specific data (selected document ID, loading flag); app DataStore stores the cached bullet list |
| WorkManager periodic job | Enqueue a new `PeriodicWorkRequest` on every `onUpdate()` call | Use `enqueueUniquePeriodicWork(KEEP)` with a stable tag; only one periodic job should exist per device |
| Glance action targeting an Activity | Launch Activity from lambda callback (`onClick = { startActivity(...) }`) | Use `actionStartActivity<MyActivity>()` — Android 12+ blocks activity launches from broadcast receiver trampolines |
| Glance `ActionCallback` API | Using deprecated `onRun()` method (older tutorials) | Current API uses `onAction()` — method was renamed; old tutorials are wrong |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Network call inside `GlanceAppWidget.Content()` composable | Widget ANR or "Widget unavailable" error | All I/O in WorkManager worker or `ActionCallback.onAction()`; `Content()` only reads pre-cached DataStore state | Any real network call from inside the composable |
| Fetching full document tree for widget | Slow widget renders, excessive data transfer | Add a dedicated API endpoint (or query parameter) that returns root-level bullets only for the selected document | Documents with deep nesting or 100+ bullets |
| `SizeMode.Exact` with complex layout | UI jank when user resizes widget | Use `SizeMode.Responsive` with 2-3 predefined sizes | Every resize event |
| `updateAll()` in worker without checking if any widget is placed | Worker fetches data even when no widgets exist on the home screen | Check `GlanceAppWidgetManager.getGlanceIds().isNotEmpty()` before making any network call | Any user who removes all widgets but WorkManager job persists |
| Serializing the entire bullet list to DataStore | Large serialized payload written on every sync | Store only root-level bullets (widget view is root-only by design); cache as JSON string with a size cap | Documents with 50+ root bullets |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Store access token in plain `SharedPreferences` to bridge the widget auth gap | Token readable in plain text on rooted devices; violates the existing security posture | Read refresh cookie from existing encrypted DataStore (Tink); issue a fresh access token per widget network call via the refresh endpoint |
| Log access token or cookie value in widget debug logging | Token leaks to logcat, visible to any app with `READ_LOGS` permission | Never log token values; log only success/failure status codes |
| Pass `bulletId` in `ActionParameters` without server-side validation | A crafted broadcast could inject an arbitrary ID and delete an unintended bullet | Validate the ID against the cached DataStore state inside `onAction()` before sending to the API; server already enforces user ownership |
| `android:exported="true"` on configuration Activity without protection | Other apps can invoke the configuration Activity outside of widget placement flow | The configuration Activity must be exported (launchers require it), but restrict to `ACTION_APPWIDGET_CONFIGURE` intent filter and validate `EXTRA_APPWIDGET_ID` on entry |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No loading state while widget fetches initial data | Widget shows blank or stale content with no indication something is loading | Store a `isLoading: Boolean` in Glance DataStore state; show a loading placeholder composable before the first successful fetch |
| No error state when network is unavailable or auth fails | Widget appears to show correct data but is actually stale with no indication | Store a `lastUpdated: Long` timestamp in DataStore; show "Updated X min ago" and a "Refresh" button when data is stale |
| Configuration activity requires user to be logged in but no guard | Widget placement fails if no auth credential exists in DataStore | Check for refresh cookie presence on configuration Activity start; if absent, show a "Please log in to the Notes app first" message and finish with `RESULT_CANCELED` |
| Tapping a widget action with no visual feedback | User taps delete; nothing appears to happen; user taps again (duplicate action) | Optimistically update DataStore state immediately on action tap so the widget recomposes before the API call completes |
| Widget shows all bullets including deeply nested ones | The widget becomes unreadable for any document with nested content | Filter to depth = 0 (root-level only) in both the API response and the DataStore cache |
| Widget uses the same Inter Variable font as the app | Widget falls back to system default without error — but developer expects the custom font | Design widget typography around system fonts from the start; do not reference the `R.font.*` resources inside Glance composables |

---

## "Looks Done But Isn't" Checklist

- [ ] **Multiple widget instances:** Verify each instance independently shows a different document and each updates independently after an edit in the app.
- [ ] **Widget after phone reboot:** Cold reboot the test device; the widget must restore its content and the WorkManager periodic job must resume without manual intervention.
- [ ] **Widget after app update:** Deploy a new APK; verify the WorkManager job is still running and the widget still updates.
- [ ] **Widget when user is logged out:** Clear the app's DataStore (simulate logout); verify the widget shows a "Please log in" state rather than crashing or showing an empty list.
- [ ] **Delete action deduplication:** Tap delete on the same bullet twice in rapid succession; verify the second tap does not send a second API request for a bullet that no longer exists.
- [ ] **Configuration Back press:** Press Back during the document picker without confirming; verify no blank widget appears on the home screen.
- [ ] **Widget font:** Custom fonts (Inter Variable) are not rendered by Glance — verify the widget is designed and tested with system fonts, not relying on `R.font.inter`.
- [ ] **`LazyColumn` item count:** Test with a document that has 30+ root bullets; verify Glance's `LazyColumn` (backed by `ListView`) does not crash or silently truncate items.
- [ ] **WorkManager unique job:** Add the widget, remove it, add it again; verify only one periodic WorkManager job exists (not duplicates accumulating over time).
- [ ] **Stale data on manual refresh:** Disable network, tap the manual refresh button; verify the widget shows a meaningful error state rather than silently keeping stale data.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong Hilt injection pattern (used `@AndroidEntryPoint` on widget) | LOW | Replace with `@EntryPoint` interface + `EntryPointAccessors` — compile-time change, no data migration |
| In-memory token gap (widget 401s) | MEDIUM | Add DataStore-backed token refresh in WorkManager worker; requires new auth flow logic in the worker and integration testing |
| Glance/Compose import confusion | LOW | Fix imports; restructure widget composables into a separate `widget/` package with Glance-only imports |
| Race condition on state update | MEDIUM | Add `Mutex` coordination in repository; switch to `enqueueUniqueWork(REPLACE)` for on-demand refreshes |
| `updatePeriodMillis` set instead of WorkManager | LOW | Change XML to 0, add WorkManager periodic request — no data migration |
| Configuration activity not returning `RESULT_OK` | LOW | Add `setResult()` call before `finish()` — one-line fix; test the placement flow end-to-end |
| `SizeMode.Exact` causing layout jank | MEDIUM | Switch to `SizeMode.Responsive` and redesign layout for 2-3 fixed breakpoints |
| `update()` called without DataStore write | LOW | Reorder the `onAction` body: write DataStore first, then call `update()` |
| Single `update()` call missing secondary instances | LOW | Replace with `updateAll()` or the `getGlanceIds()` iterator |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Hilt entry point pattern | Phase 1: Widget Foundation | `EntryPoint` resolves repository with no NPE when widget placed with app force-stopped |
| Glance vs. Compose composables | Phase 1: Widget Foundation | No `androidx.compose.material3` or `androidx.compose.foundation` imports inside the `widget/` package |
| Widget size and SizeMode | Phase 1: Widget Foundation | Widget resizes cleanly from 2-cell to 4-cell on Pixel emulator and Samsung physical device |
| Configuration activity contract | Phase 1: Widget Foundation | Place widget → pick document → confirm → widget shows data; Back press → no widget placed |
| In-memory token gap | Phase 2: Auth Integration | Widget makes successful API calls when app is force-stopped via device Settings |
| WorkManager reliability | Phase 2: WorkManager Sync | Widget updates after 15 minutes with app force-stopped; WorkManager inspector confirms RUNNING state |
| Multiple widget instances `updateAll()` | Phase 2: WorkManager Sync | Two widget instances placed pointing to different documents; both update independently |
| Persist-before-update ordering | Phase 3: Action Handling | Add bullet from widget → bullet appears without needing manual refresh |
| Race condition on state update | Phase 3: Action Handling | Rapid-tap delete 5 times in 2 seconds — no duplicate entries and no bullet reappearance |

---

## Sources

- [Manage and update GlanceAppWidget — Android Developers](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) — HIGH confidence (official docs)
- [Build UI with Glance — Android Developers](https://developer.android.com/develop/ui/compose/glance/build-ui) — HIGH confidence (official docs)
- [Handle user interaction — Glance — Android Developers](https://developer.android.com/develop/ui/compose/glance/user-interaction) — HIGH confidence (official docs)
- [Use Hilt with other Jetpack libraries — Android Developers](https://developer.android.com/training/dependency-injection/hilt-jetpack) — HIGH confidence (confirms no Glance component listed)
- [Hilt support for GlanceAppWidget — Google Issue Tracker #218520083](https://issuetracker.google.com/issues/218520083) — HIGH confidence (open feature request, no first-class support confirmed)
- [Android: Jetpack Glance with Hilt — Medium](https://medium.com/@debuggingisfun/android-jetpack-glance-with-hilt-6dce38cc9ff6) — MEDIUM confidence (practitioner article documenting the EntryPoint workaround)
- [Taming Glance Widgets: Fast & Reliable Widget Updates — Medium, Nov 2025](https://medium.com/@abdalmoniemalhifnawy/taming-glance-widgets-a-deep-dive-into-fast-reliable-widget-updates-ae44bfc4c75a) — MEDIUM confidence (race condition and update lock analysis)
- [Jetpack Glance 1.0.0 Widget Update Issues — w3tutorials.net](https://www.w3tutorials.net/blog/android-jetpack-glance-1-0-0-problems-updating-widget/) — MEDIUM confidence (recomposition trigger failure modes)
- [How to reliably update widgets on Android — arkadiuszchmura.com](https://arkadiuszchmura.com/posts/how-to-reliably-update-widgets-on-android/) — MEDIUM confidence (WorkManager vs. `updatePeriodMillis` with OEM battery restriction analysis)
- [Demystifying Jetpack Glance for app widgets — Android Developers Medium](https://medium.com/androiddevelopers/demystifying-jetpack-glance-for-app-widgets-8fbc7041955c) — HIGH confidence (official Android Developers publication)
- [Enable users to configure app widgets — Android Developers](https://developer.android.com/develop/ui/views/appwidgets/configuration) — HIGH confidence (official docs, configuration Activity contract)
- [Goodbye EncryptedSharedPreferences: A 2026 Migration Guide — ProAndroidDev, Dec 2025](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a) — MEDIUM confidence (confirms DataStore + Tink as the current encrypted storage path)
- [How to Reliably Refresh Your Widgets — Ackee blog](https://www.ackee.agency/blog/how-to-reliably-refresh-widgets) — MEDIUM confidence (vendor battery restriction patterns)

---
*Pitfalls research for: Jetpack Glance home screen widget added to existing Kotlin/Compose app with Hilt, DataStore+Tink, Retrofit/OkHttp, in-memory access tokens*
*Researched: 2026-03-14*
