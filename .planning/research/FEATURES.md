# Feature Research

**Domain:** Android home screen list widget (Jetpack Glance) for a bullet-tree outliner
**Researched:** 2026-03-14
**Confidence:** HIGH — official Android/Glance documentation verified via WebFetch; existing app code inspected directly

---

## Scope Note

This file covers the **v2.1 Android Home Screen Widget** milestone. The existing Android app (v2.0)
is complete with 12,200 LOC Kotlin. The widget is additive — it reuses the existing API layer,
`TokenStore`, and Hilt DI graph. The question is: what does a home screen list widget need to feel
polished, what is unique to the widget platform, and what is out of bounds entirely?

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features Android widget users assume exist. Missing these = widget feels broken or incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Document picker config activity | Every list widget lets you choose what it shows before placing it | MEDIUM | Launched by launcher via `ACTION_APPWIDGET_CONFIGURE`. The config activity is responsible for the first widget render — the OS does NOT call `onUpdate()` when a config activity exists. Must call `AppWidgetManager.updateAppWidget()` and `setResult(RESULT_OK)` before finishing. |
| Scrollable bullet list | Core display surface — a non-scrollable widget silently cuts off content | MEDIUM | Glance `LazyColumn` translates to a real `ListView` under the hood; same RemoteViews collection constraints apply. Per-row click actions must use `ActionParameters` — lambda closures are not serializable across widget process boundaries. |
| Empty state ("No bullets yet") | Widget placed on a blank document must not look broken or blank | LOW | Render a styled placeholder text row when bullet list is empty. Driven by a sealed state class (`Loading / Empty / Loaded / Error`) evaluated in the Glance composable with a `when` expression. |
| Loading state | First paint while API fetch is in-flight; widget must not flash blank content | LOW | Glance does not have `CircularProgressIndicator`. Use `Text("Loading…")` or a custom shimmer-style layout. The loading state is shown on initial render and cleared once DataStore cache is populated. |
| Error state (network or auth failure) | Widget must not go blank or show stale data silently on API failure | LOW | Show an explicit "Could not load — tap to retry" message. Glance has a built-in `glance_error_layout` fallback for render exceptions; override it with a custom error composable for API-level failures. |
| Manual refresh button | User changes data on another device and wants to pull in updates | LOW | A refresh icon in the widget header triggers `GlanceAppWidget.updateAll(context)` via `actionRunCallback`. This is the standard pattern in every production list widget (Google Keep, Tasks). |
| Periodic background sync | Widget must not go stale between user sessions | MEDIUM | `WorkManager` `PeriodicWorkRequest` with minimum 15-minute interval. Set `android:updatePeriodMillis="0"` in `appwidget-provider` XML when WorkManager owns the schedule — the OS does not double-schedule. |
| In-app broadcast to widget | Changes made inside the main app must reflect on the widget without user action | MEDIUM | After any create/delete bullet operation in the main app, call `GlanceAppWidget.updateAll(context)`. The widget re-reads from a DataStore cache (not live API) to keep the update fast and not block the main-app coroutine. |
| Per-instance document binding | Two widgets on the same home screen can show two different documents | MEDIUM | Store `(appWidgetId → documentId, documentTitle)` in a `DataStore<Preferences>` keyed by `appWidgetId`. Each widget instance reads only its own key. Clean up keys in `GlanceAppWidgetReceiver.onDeleted()`. |

### Differentiators (Competitive Advantage)

Features that set this widget apart from the Google Keep / Tasks widget baseline.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Add-item overlay Activity | Fastest possible bullet capture without launching the full app | MEDIUM | Launch a transparent dialog-themed `Activity` (set `Theme.AppCompat.Dialog` or `Theme.Material3.Dialog` style) from the widget's add button via `actionStartActivity`. The Activity shows a single `TextField` + confirm button. On confirm: POST to API, update DataStore cache, call `GlanceAppWidget.updateAll()`, then finish. This is the standard Android pattern used by Keep and Tasks — a Dialog-themed Activity, NOT `SYSTEM_ALERT_WINDOW` (requires a separate dangerous permission and is unnecessary). |
| Delete bullet directly from widget | Avoids opening the app for list maintenance — the primary use case for grocery/task widgets | MEDIUM | Each list row has a delete button. In Glance, per-row actions pass the `bulletId` via `ActionParameters` to an `ActionCallback`. The callback calls `DELETE /api/bullets/{id}`, then calls `GlanceAppWidget.updateAll()`. This is the idiomatic Glance pattern — not `setPendingIntentTemplate` (that is the older non-Glance RemoteViews API). |
| Tap row → deep-link into app at that document | Provides a frictionless path from widget to full editing experience | LOW | Use `actionStartActivity` with an `Intent` carrying `documentId` as an extra. The app's `MainActivity` reads the extra and navigates directly to that document. This reuses the existing navigation graph with no new screens. |
| Widget reconfiguration (Android 12+) | Power users want to swap which document a widget shows without removing and re-adding | LOW | Set `android:widgetFeatures="reconfigurable"` in `appwidget-provider`. Long-press the widget → Reconfigure re-launches the config activity with the same `EXTRA_APPWIDGET_ID`. Minimal code: the config activity already handles this flow. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Inline text editing in the widget | "Just tap a row to edit it in place" | `EditText` / editable text views are not supported by RemoteViews or Glance. This is a fundamental platform constraint — no workaround exists. | Launch the transparent overlay Activity pre-filled with the bullet text for editing. |
| Sub-15-minute background refresh | "Keep the widget always current" | WorkManager's minimum `PeriodicWorkRequest` interval is 15 minutes by OS policy. Shorter intervals are silently coerced to 15 min. Aggressive polling drains battery and violates Doze/App Standby restrictions. | Use in-app broadcast for immediate post-write updates + 15-min WorkManager for background catch-up. Together these cover all use cases without polling. |
| Drag-to-reorder bullets in widget | "Same as in the full app" | RemoteViews and Glance have no drag-and-drop mechanism whatsoever. This is a fundamental platform limitation with no workaround. | Reorder is available inside the full app; widget is intentionally read-first with targeted mutations only. |
| Nested bullet tree in widget | "Show child bullets too" | Widget screen real estate is small; nested indented items become illegible. The declared product goal is "at-a-glance root items." | Widget shows root-level bullets only; tapping a row opens the full app to see depth. |
| Auth flow inside widget | "Auto-prompt login if session expired" | Widgets run in a restricted process; launching OAuth or credential flows reliably from widget context is not supported. Showing credentials UI in a widget is a security anti-pattern. | Detect unauthenticated state (null token or 401 response) and render a single "Open app to sign in" message with a `actionStartActivity` tap target that opens `MainActivity`. |
| Offline write queue | "Let me add bullets even when the server is down" | Requires a local Room database, conflict resolution, and a sync engine. This is wildly out of proportion for a v2.1 milestone; offline is explicitly deferred in PROJECT.md. | Fail gracefully in the overlay Activity on API error; show a Toast; the user retries. |
| Real-time sync (WebSocket/SSE) | "Widget updates the instant I change something on the web" | The backend has no WebSocket/SSE infrastructure; adding push just for the widget is disproportionate cost. | In-app broadcast handles same-device immediacy; WorkManager handles cross-device catch-up within 15 minutes. |

---

## Feature Dependencies

```
[Config Activity — document picker]
    └──required before──> [Scrollable bullet list display]
                              └──required before──> [Delete bullet from widget]
    └──writes to──> [Widget DataStore (appWidgetId → documentId)]

[Widget DataStore (appWidgetId → documentId)]
    └──read by──> [WorkManager periodic sync worker]
    └──read by──> [GlanceAppWidget composable — all rendering]
    └──read by──> [In-app broadcast handler]
    └──required by──> [Per-instance document binding]

[Add-item overlay Activity]
    └──reads──> [TokenStore] (existing; Tink-encrypted DataStore in main app)
    └──calls──> [BulletApi.createBullet()] (existing Retrofit interface)
    └──triggers──> [GlanceAppWidget.updateAll()] on success

[Delete from widget (ActionCallback)]
    └──reads──> [ActionParameters: bulletId]
    └──calls──> [BulletApi.deleteBullet()] (existing Retrofit interface)
    └──triggers──> [GlanceAppWidget.updateAll()] on success

[WorkManager periodic sync]
    └──reads──> [TokenStore] for auth token
    └──calls──> [BulletApi.getBullets(docId)] and [DocumentApi.getDocuments()]
    └──writes──> [Widget DataStore bullet cache]
    └──conflicts with──> [updatePeriodMillis > 0] — must set to 0 when WorkManager owns schedule

[In-app broadcast refresh]
    └──triggered by──> [Add-item overlay Activity] (after create)
    └──triggered by──> [Delete ActionCallback] (after delete)
    └──triggered by──> [Main app bullet mutations] (existing ViewModel, call updateAll)
    └──calls──> [GlanceAppWidget.updateAll(context)]
```

### Dependency Notes

- **Config activity must persist before first render:** The config activity writes `(appWidgetId → documentId)` to DataStore and triggers the first widget render. Without this write, all subsequent WorkManager wakes and broadcasts have no document to fetch.
- **Add-item overlay uses existing Hilt graph:** The overlay Activity is a standard `@AndroidEntryPoint` Activity in the same process. It gets `TokenStore` and `BulletApi` injected via Hilt with zero new DI setup.
- **Per-row delete requires ActionParameters:** Per-row click actions in Glance LazyColumn rows must pass data (the `bulletId`) through `ActionParameters` typed keys — lambda closures are not serializable across RemoteViews process boundaries. This is a Glance platform constraint, not a design choice.
- **WorkManager conflicts with updatePeriodMillis:** Setting both causes double-scheduling. `android:updatePeriodMillis="0"` is mandatory when WorkManager owns the update schedule.
- **TokenStore is accessible from WorkManager workers:** The existing `TokenStore` uses `@ApplicationContext` DataStore — accessible from a `CoroutineWorker` via the application context without Hilt injection into the worker (or via `HiltWorker` if preferred).

---

## MVP Definition

### Launch With (v2.1)

Minimum viable product for the widget milestone as specified in PROJECT.md.

- [ ] Config activity — document picker on widget add; reconfigurable on long-press (Android 12+)
- [ ] Scrollable `LazyColumn` of root-level bullet text
- [ ] Loading / Empty / Error states with user-readable messages
- [ ] Delete bullet from widget via per-row delete button (`ActionParameters` pattern)
- [ ] Add new bullet via transparent overlay Activity
- [ ] Manual refresh button in widget header
- [ ] Periodic sync via WorkManager (15-minute interval)
- [ ] In-app broadcast after create/delete to immediately update widget
- [ ] Per-instance document binding (multiple widget instances, each with its own doc)
- [ ] Tap row → deep-link opens app at that document
- [ ] Unauthenticated state → "Open app to sign in" message

### Add After Validation (v2.1.x)

- [ ] Completed-bullet strikethrough rendering — requires the `completed` flag from the existing API response to be surfaced in widget state
- [ ] Error state retry button — currently "Open app" message; an `actionRunCallback` retry is a one-liner add

### Future Consideration (v2.2+)

- [ ] Mark-complete action from widget — extends the delete action pattern but adds visual state complexity
- [ ] Widget theming — Glance supports `GlanceMaterialTheme`; defer until app theming is finalized

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Config activity (document picker) | HIGH | MEDIUM | P1 |
| Scrollable bullet list | HIGH | MEDIUM | P1 |
| Loading / Empty / Error states | HIGH | LOW | P1 |
| Manual refresh | HIGH | LOW | P1 |
| Delete from widget | HIGH | MEDIUM | P1 |
| Add via overlay Activity | HIGH | MEDIUM | P1 |
| WorkManager periodic sync | HIGH | MEDIUM | P1 |
| In-app broadcast refresh | HIGH | LOW | P1 |
| Per-instance document binding | HIGH | MEDIUM | P1 |
| Unauthenticated state handling | HIGH | LOW | P1 |
| Tap row → open app at document | MEDIUM | LOW | P2 |
| Widget reconfiguration (Android 12+) | MEDIUM | LOW | P2 |
| Completed-bullet visual | LOW | LOW | P3 |
| Mark-complete from widget | LOW | HIGH | P3 |

**Priority key:**
- P1: Must have for v2.1 launch — without these the milestone fails
- P2: Should have; include if time permits
- P3: Nice to have — future consideration

---

## Competitor Feature Analysis

| Feature | Google Keep Widget | Google Tasks Widget | Our v2.1 Approach |
|---------|-------------------|--------------------|-------------------|
| List/document picker | Yes — choose a note | Yes — choose a list | Config Activity on widget add |
| Scrollable items | Yes | Yes | Glance `LazyColumn` |
| Add item from widget | Yes — opens overlay | Yes — opens overlay | Transparent Activity overlay |
| Delete from widget | No | No (check off only) | Yes — per-row delete button (differentiator) |
| Manual refresh | No explicit button | No | Yes — header refresh icon |
| Background sync | System-managed | System-managed | WorkManager 15-min + in-app broadcast |
| Error state | Silent (shows stale) | Silent | Explicit error message |
| Multi-instance (different lists) | Yes | Yes | Yes — per-instance DataStore key |
| Reconfigure widget | Yes (Android 12+) | Yes (Android 12+) | Yes — `reconfigurable` flag |
| Row tap → open app | Yes (opens note) | Yes (opens task) | Yes — deep-link to document |

---

## Dependencies on Existing App Code (v2.0)

The following already-built components are directly reusable for the widget with no modification:

| Existing Component | Widget Usage | Location |
|-------------------|-------------|----------|
| `TokenStore` | WorkManager worker reads access token to authenticate API calls | `data/local/TokenStore.kt` |
| `BulletApi` | `getBullets(docId)`, `createBullet()`, `deleteBullet()` — all three widget API calls | `data/api/BulletApi.kt` |
| `DocumentApi` | Config activity calls `getDocuments()` to populate the document picker | `data/api/DocumentApi.kt` |
| `NetworkModule` / `DataModule` | Hilt modules provide OkHttp + Retrofit + TokenStore to widget components | `di/` |
| Hilt application component | Widget Activity and WorkManager workers are `@AndroidEntryPoint` / `@HiltWorker` | existing setup |

New infrastructure required:
- `androidx.glance:glance-appwidget` dependency (not currently in `build.gradle.kts`)
- `androidx.work:work-runtime-ktx` dependency (not currently in `build.gradle.kts`)
- Widget-specific `DataStore<Preferences>` namespace for `(appWidgetId → documentId)` and bullet cache
- `appwidget-provider` XML descriptor and widget layout XML
- Broadcast call sites in the existing main-app ViewModels (after bullet create/delete)

---

## Sources

- [Manage and update GlanceAppWidget — official Android docs](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) — HIGH confidence
- [Build UI with Glance — official Android docs](https://developer.android.com/develop/ui/compose/glance/build-ui) — HIGH confidence
- [Enable users to configure app widgets — official Android docs](https://developer.android.com/develop/ui/views/appwidgets/configuration) — HIGH confidence
- [Handle errors with Glance — official Android docs](https://developer.android.com/develop/ui/compose/glance/error-handling) — HIGH confidence
- [App widgets overview — official Android docs](https://developer.android.com/develop/ui/views/appwidgets/overview) — HIGH confidence
- [Updating widgets with Jetpack WorkManager — DEV Community](https://dev.to/tkuenneth/updating-widgets-with-jetpack-workmanager-g0b) — MEDIUM confidence
- [Demystifying Jetpack Glance — Android Developers Medium](https://medium.com/androiddevelopers/demystifying-jetpack-glance-for-app-widgets-8fbc7041955c) — MEDIUM confidence
- [Widgets with Glance: Beyond String States — ProAndroidDev](https://proandroiddev.com/widgets-with-glance-beyond-string-states-2dcc4db2f76c) — MEDIUM confidence
- Existing app source: `data/api/BulletApi.kt`, `data/api/DocumentApi.kt`, `data/local/TokenStore.kt`, `app/build.gradle.kts` — HIGH confidence (direct inspection)

---

*Feature research for: Notes v2.1 Android Home Screen Widget milestone*
*Researched: 2026-03-14*
