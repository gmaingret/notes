# Phase 13: Widget Foundation - Research

**Researched:** 2026-03-14
**Domain:** Jetpack Glance 1.1.1 — Android home screen app widget with Hilt DI, custom DataStore, Material 3 theming
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Widget Visual Layout**
- Rounded card shape (Material 3 Glance card)
- Header row: document title (left-aligned, single-line truncated with ellipsis) + [+] add button (right-aligned), thin divider line below
- No branding (no app icon, no accent border) — clean card only
- Same background throughout header and bullet list (no distinct header shade)
- Follow system dark/light mode automatically (consistent with app's system-preference approach)
- Tapping the document title in the header opens the document in the full Notes app
- Default size: 4x2 minimum, user-resizable

**Document Picker**
- Full-screen Activity with scrollable list of document names (name only, no metadata)
- Tap a document to instantly confirm and close — no separate confirm button
- Uses app's NotesTheme (Material 3, #2563EB seed, dark mode support)
- If user isn't logged in: show login form inline (email/password + Google SSO) before document list
- Reconfigurable via Android long-press widget menu (re-opens picker to change document)

**Bullet Row Display**
- Each row: bullet dot + text + bare x delete icon (right-aligned, same color as text; Phase 15 concern)
- Completed bullets: strikethrough + reduced opacity (matching app behavior)
- Long text: single line, truncated with ellipsis
- Basic formatting: render bold and strikethrough (Glance SpannableString). Skip links, chips, italic
- Plain text for markdown syntax that can't render (strip #tags, @mentions, !!dates syntax)

**Widget Sizing**
- SizeMode.Responsive with breakpoints (already decided in research)
- Larger widget = more visible rows; font size and padding stay constant across all sizes

**Scroll Behavior**
- Bullet list is scrollable within the widget via Glance LazyColumn

**Empty State**
- Centered "No bullets yet" text inside the widget card

**Loading State**
- 3-4 placeholder shimmer rows mimicking bullet rows (dots + gray rectangles)

**Error State**
- "Couldn't load — tap to retry" centered in widget. Tapping triggers refresh

**Deleted Document State**
- "Document not found" + reconfigure prompt (tap to re-open document picker)

**Session Expired State**
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

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SETUP-01 | User can add the Notes widget to their Android home screen | appwidget-provider XML + GlanceAppWidgetReceiver registration in AndroidManifest |
| SETUP-02 | User is presented with a document picker when adding the widget | WidgetConfigActivity with android:configure in appwidget-provider + RESULT_CANCELED/OK pattern |
| DISP-01 | Widget shows the document title in a header row | Glance Row + Text composable with GlanceTheme colors |
| DISP-02 | Widget shows root-level bullets as a scrollable flat list | Glance LazyColumn backed by ListView; root bullet filter (parentId == null) |
| DISP-03 | Widget shows an empty state when the document has no bullets | Conditional Glance composable when bullet list is empty |
| DISP-04 | Widget shows loading and error states appropriately | WidgetUiState sealed class; static placeholder rows for loading shimmer |
| DISP-05 | Widget uses Material 3 theming consistent with the app | glance-material3 ColorProviders using app's LightColorScheme/DarkColorScheme |
</phase_requirements>

---

## Summary

Phase 13 introduces a Jetpack Glance 1.1.1 app widget that displays a single document's root-level bullets. The widget is purely display-only in this phase: it fetches data from the existing Retrofit/OkHttp stack, stores the selected document ID in a new custom Tink-encrypted DataStore singleton, and renders with Material 3 colors via `glance-material3`. Background sync and interactive actions are out of scope.

The two most important constraints for this phase are architectural: (1) Hilt cannot inject dependencies into a `GlanceAppWidget` directly — the `@EntryPoint` pattern via `EntryPointAccessors.fromApplication()` inside `provideGlance()` is the only supported approach, and (2) `WidgetConfigActivity` must call `setResult(RESULT_CANCELED, ...)` at the very start of `onCreate()` — if the user navigates back without picking a document, the launcher silently discards widget placement only if this result is set.

A known constraint flagged in STATE.md is that Glance's `LazyColumn` translates to a `ListView` under RemoteViews. While no hard numeric cap is documented, large lists (30+ items) should be tested empirically. Widget process isolation means the in-memory access token is always null; the widget reads the persisted cookie from `DataStoreCookieJar` and calls `/auth/refresh` directly to obtain a fresh access token before each data fetch.

**Primary recommendation:** Follow the existing project's `EncryptedDataStoreFactory` pattern exactly for `WidgetStateStore` (new DataStore singleton, Tink-encrypted, per-widget-instance document ID), and use `@EntryPoint` + `EntryPointAccessors.fromApplication()` inside `provideGlance()` to access Hilt singletons (Retrofit, TokenStore, DataStoreCookieJar).

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.glance:glance-appwidget` | 1.1.1 | Compose-based app widget rendering | Only supported Compose-to-RemoteViews bridge; 1.1.1 includes CVE-2024-7254 fix |
| `androidx.glance:glance-material3` | 1.1.1 | Material 3 `ColorProviders` for `GlanceTheme` | Required separate artifact for M3 color tokens in Glance |
| Hilt 2.56.1 (existing) | 2.56.1 | DI via `@EntryPoint` in widget | Already used; widget uses `EntryPointAccessors.fromApplication()` |
| DataStore Preferences 1.2.1 (existing) | 1.2.1 | Persist per-widget document ID | Already used for TokenStore/CookieJar |
| Tink Android 1.8.0 (existing) | 1.8.0 | Encrypt widget state (doc ID is not sensitive, but pattern consistency) | Existing `EncryptedDataStoreFactory` reuse |
| Retrofit 3.0.0 / OkHttp 4.12.0 (existing) | existing | Fetch bullets and documents from API | Existing `BulletApi`, `DocumentApi` reuse |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Coroutines 1.10.1 (existing) | existing | Async I/O inside `provideGlance()` with `withContext(Dispatchers.IO)` | All network calls in widget |
| MockK 1.13.14 (existing) | existing | Unit tests for WidgetConfigViewModel, WidgetContentViewModel | Same as app tests |
| Robolectric 4.16 (existing) | existing | JVM-based Android unit tests | Same as app tests |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom `WidgetStateStore` DataStore | Glance `PreferencesGlanceStateDefinition` | Glance state is only accessible during `provideGlance()`/`provideContent{}` — not from `WidgetConfigActivity` before first render or from `onDeleted()`; custom DataStore is required |
| `updateAll()` everywhere | `update(context, glanceId)` inside `ActionCallback` | Inside `ActionCallback.onAction()` the `glanceId` is already provided — use it directly; for all other call sites (workers, config activity) use `updateAll()` to avoid stale ID bugs |
| `updatePeriodMillis` broadcast polling | WorkManager | OEM battery management (Doze, manufacturer restrictions) reliably suppresses `updatePeriodMillis` broadcasts; set `updatePeriodMillis="0"` and let Phase 14 WorkManager own the schedule |

**Installation (add to `app/build.gradle.kts` dependencies):**
```kotlin
// Glance
implementation("androidx.glance:glance-appwidget:1.1.1")
implementation("androidx.glance:glance-material3:1.1.1")
```

Add to `libs.versions.toml`:
```toml
[versions]
glance = "1.1.1"

[libraries]
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
```

---

## Architecture Patterns

### Recommended Project Structure
```
android/app/src/main/java/com/gmaingret/notes/
├── widget/
│   ├── NotesWidget.kt              # GlanceAppWidget — provideGlance() + provideContent()
│   ├── NotesWidgetReceiver.kt      # GlanceAppWidgetReceiver — onDeleted cleanup
│   ├── WidgetStateStore.kt         # Custom DataStore singleton for per-widget doc ID
│   ├── WidgetEntryPoint.kt         # @EntryPoint interface declaring Hilt deps
│   ├── WidgetContent.kt            # Glance composables (header, bullet row, states)
│   └── config/
│       ├── WidgetConfigActivity.kt # Full-screen doc picker Activity
│       └── WidgetConfigViewModel.kt
│
android/app/src/main/res/
├── xml/
│   └── notes_widget_info.xml       # appwidget-provider metadata
└── layout/
    └── (none needed — Glance generates its own)
```

### Pattern 1: GlanceAppWidget with Hilt EntryPoint
**What:** `GlanceAppWidget` cannot be annotated with `@AndroidEntryPoint`. Hilt singletons are accessed by defining an `@EntryPoint` interface and calling `EntryPointAccessors.fromApplication()` inside `provideGlance()`.

**When to use:** Every time the widget needs to access a Hilt-managed singleton (repositories, use cases, TokenStore, DataStoreCookieJar).

**Example:**
```kotlin
// Source: https://developer.android.com/training/dependency-injection/hilt-android#not-supported
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bulletRepository(): BulletRepository
    fun documentRepository(): DocumentRepository
    fun tokenStore(): TokenStore
    fun dataStoreCookieJar(): DataStoreCookieJar
}

class NotesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val widgetStateStore = WidgetStateStore.getInstance(context)
        val docId = widgetStateStore.getDocumentId(id)

        // All I/O before provideContent {}
        val uiState: WidgetUiState = withContext(Dispatchers.IO) {
            if (docId == null) return@withContext WidgetUiState.NotConfigured
            fetchWidgetData(context, entryPoint, docId)
        }

        provideContent {
            GlanceTheme(colors = NotesWidgetColorScheme.colors) {
                WidgetContent(uiState)
            }
        }
    }
}
```

### Pattern 2: WidgetConfigActivity — RESULT_CANCELED First
**What:** Config activity must set `RESULT_CANCELED` at the very start of `onCreate()`. This is the single most common cause of widgets being silently discarded by launchers when the user backs out.

**When to use:** Always, in every WidgetConfigActivity.

**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/views/appwidgets/configuration
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // CRITICAL: set CANCELED immediately so back-press discards the widget
        setResult(RESULT_CANCELED, Intent().putExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
        ))

        // ... show document picker ...
        // Only on user confirmation:
        //   widgetStateStore.saveDocumentId(glanceId, selectedDocId)
        //   NotesWidget().update(context, glanceId)
        //   setResult(RESULT_OK, Intent().putExtra(EXTRA_APPWIDGET_ID, appWidgetId))
        //   finish()
    }
}
```

### Pattern 3: WidgetStateStore — Custom Encrypted DataStore
**What:** A singleton DataStore that maps GlanceId → document UUID. Mirrors the `TokenStore` pattern but is widget-specific. Doc IDs are not sensitive, but using the existing Tink pattern maintains codebase consistency and avoids a second unencrypted file.

**When to use:** Anywhere that needs to read/write the document selection for a widget instance.

**Key design:** `GlanceId` is serialized to string via `GlanceAppWidgetManager.getId(glanceId)` (returns an `Int`) as the DataStore key. On `NotesWidgetReceiver.onDeleted()`, clear the key for each deleted `appWidgetId`.

```kotlin
// DataStore key: "widget_doc_${appWidgetId}"
private val Context.widgetDataStore by preferencesDataStore(name = "widget_state")

class WidgetStateStore(private val context: Context) {
    // Use appWidgetId (Int) from GlanceAppWidgetManager.getId(glanceId)
    suspend fun saveDocumentId(appWidgetId: Int, docId: String) { ... }
    suspend fun getDocumentId(appWidgetId: Int): String? { ... }
    suspend fun clearDocumentId(appWidgetId: Int) { ... }

    companion object {
        @Volatile private var instance: WidgetStateStore? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: WidgetStateStore(context.applicationContext).also { instance = it }
        }
    }
}
```

### Pattern 4: SizeMode.Responsive Breakpoints
**What:** Glance pre-renders one Composable per declared `DpSize`. The system picks the best fit. Larger sizes reveal more rows.

**Recommended breakpoints for a 4x2 minimum widget:**

Using the formula `(73n - 16)` dp wide, `(118m - 16)` dp tall for portrait:
- 4-column minimum: `(73×4 - 16) = 276 dp` wide
- 2-row minimum: `(118×2 - 16) = 220 dp` tall

```kotlin
// Source: https://developer.android.com/reference/kotlin/androidx/glance/appwidget/SizeMode.Responsive
companion object {
    private val SMALL = DpSize(200.dp, 100.dp)   // narrow/short, portrait compact
    private val MEDIUM = DpSize(276.dp, 220.dp)  // 4×2 standard
    private val LARGE = DpSize(276.dp, 380.dp)   // 4×3+ tall widget
}

override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))
```

In `provideContent {}`, use `LocalSize.current` to conditionally adjust how many rows to hint at rendering (not strictly necessary since LazyColumn scrolls, but useful for showing placeholder count).

### Pattern 5: GlanceTheme with App Colors
**What:** `glance-material3` provides `ColorProviders` which wraps `lightColorScheme` + `darkColorScheme` into a Glance-compatible form.

```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/theme
import androidx.glance.material3.ColorProviders

object NotesWidgetColorScheme {
    val colors = ColorProviders(
        light = LightColorScheme,  // existing from com.gmaingret.notes.presentation.theme.Color
        dark = DarkColorScheme     // existing from same file
    )
}

// Usage in provideContent:
GlanceTheme(colors = NotesWidgetColorScheme.colors) {
    WidgetContent(uiState)
}
```

Access colors inside Glance composables via `GlanceTheme.colors.primary`, `GlanceTheme.colors.surface`, etc.

### Pattern 6: All I/O Before provideContent
**What:** `provideGlance()` runs on the main thread by default. Long-running operations (network, DataStore reads) MUST be wrapped in `withContext(Dispatchers.IO)` BEFORE calling `provideContent {}`. The `provideContent {}` lambda is the Glance composition scope — performing blocking I/O there causes ANR or dropped frames.

```kotlin
override suspend fun provideGlance(context: Context, id: GlanceId) {
    // I/O here — before provideContent
    val data = withContext(Dispatchers.IO) {
        // network call, DataStore read
    }

    // UI composition here — no I/O allowed
    provideContent {
        WidgetContent(data)
    }
}
```

### Pattern 7: appwidget-provider XML
```xml
<!-- res/xml/notes_widget_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minWidth="276dp"
    android:minHeight="220dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="0"
    android:configure="com.gmaingret.notes.widget.config.WidgetConfigActivity"
    android:widgetFeatures="reconfigurable"
    android:previewLayout="@layout/glance_default_loading_layout" />
```

Key notes:
- `updatePeriodMillis="0"` — WorkManager owns scheduling (Phase 14)
- `android:configure` — links to the config activity
- `android:widgetFeatures="reconfigurable"` — enables long-press → Reconfigure in launcher
- `initialLayout="@layout/glance_default_loading_layout"` — Glance's built-in placeholder; do not create a custom XML layout

### Pattern 8: AndroidManifest Registration
```xml
<!-- Receiver for the widget -->
<receiver
    android:name=".widget.NotesWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/notes_widget_info" />
</receiver>

<!-- Config activity -->
<activity
    android:name=".widget.config.WidgetConfigActivity"
    android:exported="true"
    android:theme="@style/Theme.Notes">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
    </intent-filter>
</activity>
```

### Pattern 9: WidgetUiState Sealed Class
```kotlin
sealed interface WidgetUiState {
    data object Loading : WidgetUiState
    data object NotConfigured : WidgetUiState
    data object Empty : WidgetUiState
    data class Content(
        val documentTitle: String,
        val bullets: List<WidgetBullet>
    ) : WidgetUiState
    data class Error(val message: String) : WidgetUiState
    data object DocumentNotFound : WidgetUiState
    data object SessionExpired : WidgetUiState
}

data class WidgetBullet(
    val id: String,
    val content: String,        // pre-processed: chips stripped, bold/~~strike~~ kept
    val isComplete: Boolean
)
```

### Anti-Patterns to Avoid
- **I/O inside `provideContent {}`:** Will cause ANR or silent failures. All coroutine work must precede `provideContent {}` within `provideGlance()`.
- **`@AndroidEntryPoint` on GlanceAppWidget:** Not supported by Hilt. Causes a crash at runtime. Use `@EntryPoint` only.
- **Hardcoding a single `glanceId` for updates:** Use `updateAll(context)` everywhere except inside `ActionCallback.onAction()` which already receives the correct `glanceId`.
- **Forgetting RESULT_CANCELED in config activity:** The launcher silently discards the widget placement — no error, no log. This is the #1 gotcha with config activities.
- **Using Glance `PreferencesGlanceStateDefinition` as WidgetStateStore:** This state is only accessible inside `provideGlance()`/`provideContent{}`. `WidgetConfigActivity` runs before the widget exists and needs to write the doc ID before the first render.
- **Calling `DataStoreCookieJar.loadForRequest()` or `TokenStore.getAccessToken()` from widget process directly:** The widget and app share the same process on modern Android, but the in-memory access token is not guaranteed to be populated. Always perform `/auth/refresh` to get a fresh token rather than reading `getAccessToken()` which may be null after process death.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Widget-to-RemoteViews rendering | Custom XML layout inflation | `GlanceAppWidget` + Glance composables | RemoteViews inflation from Compose is deeply complex; Glance handles all `RemoteViews` lifecycle |
| Text-to-RemoteViews span rendering | Custom `RemoteViews.setTextViewText(SpannableString)` | `androidx.glance.text.TextStyle` with `TextDecoration.LineThrough` for strikethrough | Glance translates `TextStyle` properly; raw RemoteViews text spans have API level restrictions |
| Widget size change detection | Custom broadcast receiver for `ACTION_APPWIDGET_OPTIONS_CHANGED` | `SizeMode.Responsive` | Glance handles size transitions automatically for declared breakpoints |
| Widget update scheduling | `updatePeriodMillis` broadcast | WorkManager (Phase 14) | OEM battery management suppresses broadcasts; WorkManager is the only reliable path |
| Hilt injection into widget | `@AndroidEntryPoint` | `@EntryPoint` + `EntryPointAccessors.fromApplication()` | Glance widgets are not Activities/Fragments; `@AndroidEntryPoint` is not supported |

**Key insight:** Glance abstracts the hardest parts of `RemoteViews` (view IDs, click intents, collection adapters). Only reach for `AndroidRemoteViews` interop if Glance lacks a specific composable needed.

---

## Common Pitfalls

### Pitfall 1: Launcher Silently Discards Widget (Missing RESULT_CANCELED)
**What goes wrong:** User places widget, config activity opens, user backs out — widget disappears from home screen without any error. Seems like a framework bug but is developer error.
**Why it happens:** If `RESULT_CANCELED` is not set before the config activity appears, the launcher does not know whether to keep or discard the widget on back-press. Many launchers assume `RESULT_OK` was not set and silently drop the placement.
**How to avoid:** First line in `onCreate()` after extracting `appWidgetId`: `setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_APPWIDGET_ID, appWidgetId))`.
**Warning signs:** Widget vanishes silently when pressing back in the config activity.

### Pitfall 2: I/O Inside provideContent Lambda
**What goes wrong:** Network call or DataStore read inside `provideContent {}` blocks or crashes silently. Widget may show initial loading state forever.
**Why it happens:** `provideContent {}` is a Glance composition block that mirrors the Compose composition model — it is not a coroutine scope for blocking work.
**How to avoid:** Structure `provideGlance()` as: (1) all I/O with `withContext(Dispatchers.IO)`, (2) then `provideContent {}` receives only already-computed values.
**Warning signs:** Widget stuck on loading state; logcat shows `NetworkOnMainThreadException` or similar.

### Pitfall 3: @AndroidEntryPoint on GlanceAppWidget
**What goes wrong:** Compile-time or runtime crash — Hilt component hierarchy does not include `GlanceAppWidget`.
**Why it happens:** Hilt only supports `@AndroidEntryPoint` for Activities, Fragments, Views, Services, and BroadcastReceivers. `GlanceAppWidget` is none of these.
**How to avoid:** Define `@EntryPoint @InstallIn(SingletonComponent::class) interface WidgetEntryPoint { ... }` and use `EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)` in `provideGlance()`.
**Warning signs:** Hilt annotation processor error or `IllegalStateException: Not a Hilt component`.

### Pitfall 4: Glance LazyColumn with Many Items
**What goes wrong:** LazyColumn is backed by a `ListView` via RemoteViews. The `RemoteViews` collection protocol serializes each item and sends it through Binder IPC. Very large lists (hundreds of items) can exceed the 1 MB Binder transaction limit.
**Why it happens:** `RemoteViews` collections do not have a documented hard cap, but the Binder IPC buffer is 1 MB per transaction. Each row's RemoteViews is roughly a few KB; practical limit is approximately 100-200 rows before risking `TransactionTooLargeException`.
**How to avoid:** Cap displayed bullets at 50. Root bullets only (parentId == null) limits the set naturally; most documents have far fewer. Add a "30+ bullets — open app to see all" fallback if needed.
**Warning signs:** `TransactionTooLargeException` in logcat; widget shows partial list or blank.

### Pitfall 5: updateAll() vs update() in ActionCallback
**What goes wrong:** Calling `updateAll(context)` inside `ActionCallback.onAction()` works but is slightly wasteful. Calling `update(context, glanceId)` outside of a context that has the `glanceId` causes an `IllegalArgumentException`.
**Why it happens:** `glanceId` is valid only within `provideGlance()` and `ActionCallback.onAction()`.
**How to avoid:** Inside `ActionCallback.onAction()` use the `glanceId` parameter directly. In all other contexts (ViewModel, WorkManager, config activity after save) use `updateAll(context)`.
**Warning signs:** `IllegalArgumentException: GlanceId not found` at runtime.

### Pitfall 6: Glance Text Strikethrough vs Regular Compose
**What goes wrong:** Importing `androidx.compose.ui.text.style.TextDecoration` instead of `androidx.glance.text.TextDecoration` causes a type mismatch in Glance `TextStyle`.
**Why it happens:** Glance defines its own parallel type hierarchy in `androidx.glance.text` that is distinct from `androidx.compose.ui.text`. The two are not interchangeable.
**How to avoid:** In all widget composables, import `androidx.glance.text.TextStyle`, `androidx.glance.text.TextDecoration`, `androidx.glance.text.FontWeight` — not their Compose counterparts.
**Warning signs:** Compile error `type mismatch: inferred type is TextDecoration but TextDecoration was expected` pointing to the compose vs glance packages.

### Pitfall 7: Shimmer Animations Are Not Possible in Glance
**What goes wrong:** Attempting to use `Animatable`, `InfiniteTransition`, or any Compose animation in a Glance widget fails at compile time or is silently ignored.
**Why it happens:** Glance translates to `RemoteViews` which has no animation composition model. The Compose runtime animation APIs are not available in Glance's restricted subset.
**How to avoid:** Implement the loading state as **static placeholder rows** — `Box` composables with a gray background color representing the expected bullet row shape. No animation. This is the correct Glance-native approach.
**Warning signs:** Compiler errors referencing missing animation APIs; or animation code that compiles but does nothing in the widget.

---

## Code Examples

### Minimal NotesWidget Structure
```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/create-app-widget
class NotesWidget : GlanceAppWidget() {

    companion object {
        private val SMALL_SIZE = DpSize(200.dp, 100.dp)
        private val MEDIUM_SIZE = DpSize(276.dp, 220.dp)
        private val LARGE_SIZE = DpSize(276.dp, 380.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_SIZE, MEDIUM_SIZE, LARGE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val widgetStateStore = WidgetStateStore.getInstance(context)
        val appWidgetId = GlanceAppWidgetManager(context).getId(id)
        val docId = widgetStateStore.getDocumentId(appWidgetId)

        val uiState = withContext(Dispatchers.IO) {
            fetchWidgetData(context, entryPoint, docId)
        }

        provideContent {
            GlanceTheme(colors = NotesWidgetColorScheme.colors) {
                WidgetContent(uiState = uiState, context = context)
            }
        }
    }
}
```

### Completed Bullet Row in Glance
```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/build-ui
@Composable
fun BulletRow(bullet: WidgetBullet) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bullet dot
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .background(if (bullet.isComplete)
                    ColorProvider(Color(0xFF9CA3AF))   // gray-400 for completed
                else
                    GlanceTheme.colors.primary)
        )

        Spacer(GlanceModifier.width(8.dp))

        // Bullet text — strikethrough + lighter color for completed
        Text(
            text = bullet.content,
            maxLines = 1,
            style = TextStyle(
                fontSize = 14.sp,
                color = if (bullet.isComplete)
                    ColorProvider(Color(0xFF9CA3AF))   // reduced opacity via lighter color
                else
                    GlanceTheme.colors.onSurface,
                textDecoration = if (bullet.isComplete)
                    TextDecoration.LineThrough
                else
                    TextDecoration.None
            ),
            modifier = GlanceModifier.defaultWeight()
        )
    }
}
```

**Note:** Glance does not support a direct `alpha` modifier on `Text`. Reduced opacity for completed bullets is achieved by using a lighter/muted color value (e.g. `gray-400`) rather than an alpha channel.

### Static Loading Placeholder (No Animation)
```kotlin
@Composable
fun LoadingContent() {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(4) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Placeholder dot
                Box(
                    modifier = GlanceModifier
                        .size(6.dp)
                        .background(ColorProvider(Color(0xFFD1D5DB)))  // gray-300
                )
                Spacer(GlanceModifier.width(8.dp))
                // Placeholder text bar (varying widths for realistic skeleton)
                Box(
                    modifier = GlanceModifier
                        .height(12.dp)
                        .width(if (it % 2 == 0) 180.dp else 120.dp)
                        .background(ColorProvider(Color(0xFFD1D5DB)))  // gray-300
                )
            }
        }
    }
}
```

### GlanceMaterial3 Color Setup
```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/theme
import androidx.glance.material3.ColorProviders
import com.gmaingret.notes.presentation.theme.DarkColorScheme
import com.gmaingret.notes.presentation.theme.LightColorScheme

object NotesWidgetColorScheme {
    val colors = ColorProviders(
        light = LightColorScheme,
        dark = DarkColorScheme
    )
}
```

### ActionCallback for Retry / Open App
```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/glance-app-widget
class RetryActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Trigger re-render (provideGlance will re-fetch)
        NotesWidget().update(context, glanceId)
    }
}

// In composable:
Box(modifier = GlanceModifier.clickable(actionRunCallback<RetryActionCallback>())) {
    Text("Couldn't load — tap to retry")
}
```

### Open App from Widget (document title tap)
```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/glance-app-widget#actions
val openAppIntent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    putExtra("OPEN_DOCUMENT_ID", documentId)
}

Text(
    text = documentTitle,
    modifier = GlanceModifier.clickable(
        actionStartActivity(openAppIntent)
    )
)
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Override `GlanceAppWidget.Content()` | Override `GlanceAppWidget.provideGlance()` | Glance 1.0 → 1.1.0 | `provideGlance()` runs in a WorkManager session — I/O is safe before `provideContent {}` |
| `kapt` for Hilt annotation processing | `ksp` | Hilt 2.48+ with KSP plugin | Already using `ksp` in this project (see `libs.versions.toml`) |
| `updatePeriodMillis` for periodic updates | WorkManager `PeriodicWorkRequest` | Best practice since Android 8 (Doze) | Set `updatePeriodMillis="0"` in XML; WorkManager is sole scheduler |
| `PreferencesGlanceStateDefinition` for widget state | Custom DataStore singleton | N/A — Glance state only accessible in composition scope | Custom DataStore is required when state must be read outside composition |
| SizeMode.Exact or SizeMode.Single | SizeMode.Responsive with DpSize breakpoints | Glance 1.0 | Responsive pre-renders all breakpoints; system selects best fit |

**Deprecated/outdated:**
- `GlanceAppWidget.Content()` method: Replaced by `provideGlance()` in Glance 1.1. The old `Content()` method still works but is deprecated — use `provideGlance()`.
- `kapt` Hilt processing: This project already uses `ksp`. Do not reintroduce `kapt`.

---

## Open Questions

1. **GlanceId to appWidgetId conversion**
   - What we know: `GlanceAppWidgetManager(context).getId(glanceId)` returns an `Int` appWidgetId. This is the stable key for DataStore.
   - What's unclear: Whether `getId()` is stable across widget updates vs. only at placement time. The inverse — getting a `GlanceId` from an `appWidgetId` for use in `WidgetConfigActivity` — needs `GlanceAppWidgetManager(context).getGlanceId(appWidgetId)`.
   - Recommendation: Always convert to `appWidgetId` (Int) for DataStore keys; convert back to `GlanceId` only when calling `NotesWidget().update()`.

2. **Glance LazyColumn item rendering with large lists**
   - What we know: Backed by `ListView` + `RemoteViews`; Binder IPC limit is 1 MB. STATE.md flags testing 30+ items.
   - What's unclear: The exact per-item serialization cost for this widget's row structure.
   - Recommendation: Cap rendered items at 50 root bullets (parentId == null). Add a `filterRootBullets()` step before passing to `WidgetUiState.Content`. Root-only filtering already reduces the set significantly.

3. **Session handling in Phase 13 (widget auth)**
   - What we know: The widget reads `DataStoreCookieJar` (which persists the `refreshToken` httpOnly cookie) and must call `/auth/refresh` to get a fresh access token before fetching bullets.
   - What's unclear: Whether the app's shared `OkHttpClient` (with `AuthInterceptor` + `TokenAuthenticator`) is safe to call from a coroutine inside `provideGlance()` since it may be constructed before or after the widget is triggered.
   - Recommendation: For Phase 13, reuse the existing Hilt-provided `OkHttpClient` via `@EntryPoint`. The widget shares the same app process. The `TokenAuthenticator` handles 401 refresh automatically. Flag for Phase 14 if a worker-scoped client proves necessary.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + MockK 1.13.14 + Robolectric 4.16 (existing) |
| Config file | No explicit `robolectric.properties` — `testOptions.unitTests.isIncludeAndroidResources = true` in `build.gradle.kts` |
| Quick run command | `./gradlew testDebugUnitTest --tests "com.gmaingret.notes.widget.*" -x lintDebug` |
| Full suite command | `./gradlew testDebugUnitTest -x lintDebug` |

Run from: `/c/Users/gmain/dev/Notes/android/` or on server via `ssh root@192.168.1.50 "cd /root/notes/android && ./gradlew testDebugUnitTest -x lintDebug"`

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SETUP-01 | Widget receiver registered in manifest; GlanceAppWidget class exists | unit (Robolectric) | `./gradlew testDebugUnitTest --tests "*.NotesWidgetReceiverTest" -x lintDebug` | ❌ Wave 0 |
| SETUP-02 | Config activity sets RESULT_CANCELED on creation; sets RESULT_OK after doc selection | unit (Robolectric) | `./gradlew testDebugUnitTest --tests "*.WidgetConfigViewModelTest" -x lintDebug` | ❌ Wave 0 |
| DISP-01 | WidgetUiState.Content contains documentTitle | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ Wave 0 |
| DISP-02 | fetchWidgetData returns root bullets (parentId == null) only | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ Wave 0 |
| DISP-03 | fetchWidgetData returns WidgetUiState.Empty when bullet list is empty | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ Wave 0 |
| DISP-04 | fetchWidgetData returns WidgetUiState.Loading / Error / SessionExpired / DocumentNotFound | unit | `./gradlew testDebugUnitTest --tests "*.NotesWidgetTest" -x lintDebug` | ❌ Wave 0 |
| DISP-05 | NotesWidgetColorScheme.colors wraps LightColorScheme + DarkColorScheme | unit (pure Kotlin) | `./gradlew testDebugUnitTest --tests "*.NotesWidgetColorSchemeTest" -x lintDebug` | ❌ Wave 0 |

**Note on Glance UI testing:** Glance provides `GlanceAppWidgetHostRule` for integration tests, but these are `androidTest` (instrumented) and require a device/emulator. For Phase 13, unit tests focus on the ViewModel, state logic, and data transformation (root bullet filter, content string processing). Visual correctness is validated manually on device.

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest --tests "com.gmaingret.notes.widget.*" -x lintDebug`
- **Per wave merge:** `./gradlew testDebugUnitTest -x lintDebug`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/.../widget/NotesWidgetTest.kt` — covers DISP-01, DISP-02, DISP-03, DISP-04 (fetchWidgetData logic using MockK)
- [ ] `app/src/test/.../widget/WidgetConfigViewModelTest.kt` — covers SETUP-02 (doc selection flow)
- [ ] `app/src/test/.../widget/NotesWidgetReceiverTest.kt` — covers SETUP-01 (receiver existence, onDeleted cleanup)
- [ ] `app/src/test/.../widget/NotesWidgetColorSchemeTest.kt` — covers DISP-05 (color scheme wrapping)
- [ ] `app/src/test/.../widget/WidgetStateStoreTest.kt` — covers DataStore read/write/clear for widget doc IDs

---

## Sources

### Primary (HIGH confidence)
- [developer.android.com — Create an app widget with Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget) — provideGlance, provideContent, GlanceAppWidgetReceiver, withContext pattern
- [developer.android.com — Manage and update GlanceAppWidget](https://developer.android.com/develop/ui/compose/glance/glance-app-widget) — updateAll vs update, GlanceId, SizeMode.Responsive
- [developer.android.com — Implement a Glance theme](https://developer.android.com/develop/ui/compose/glance/theme) — GlanceTheme, ColorProviders, glance-material3
- [developer.android.com — Enable users to configure app widgets](https://developer.android.com/develop/ui/views/appwidgets/configuration) — RESULT_CANCELED, RESULT_OK, reconfigurable widgetFeatures
- [developer.android.com — Build UI with Glance](https://developer.android.com/develop/ui/compose/glance/build-ui) — LazyColumn, Text, TextStyle, composable inventory
- [developer.android.com — Size your widget](https://developer.android.com/develop/ui/views/appwidgets/layouts) — grid cell to dp conversion, targetCellWidth/Height, resizeMode
- [developer.android.com — Glance setup](https://developer.android.com/develop/ui/compose/glance/setup) — glance-appwidget + glance-material3 artifacts
- [developer.android.com — Glance releases](https://developer.android.com/jetpack/androidx/releases/glance) — 1.1.1 stable, CVE-2024-7254 fix
- [mvnrepository.com — glance-material3 1.1.1](https://mvnrepository.com/artifact/androidx.glance/glance-material3/1.1.1) — artifact coordinates confirmed

### Secondary (MEDIUM confidence)
- [developer.android.com — Glance interoperability](https://developer.android.com/develop/ui/compose/glance/interoperability) — AndroidRemoteViews fallback for unsupported features
- [medium.com/@debuggingisfun — Android: Jetpack Glance with Hilt](https://medium.com/@debuggingisfun/android-jetpack-glance-with-hilt-6dce38cc9ff6) — @EntryPoint pattern in practice, verified consistent with official Hilt docs
- [developer.android.com — SizeMode.Responsive API reference](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/SizeMode.Responsive) — DpSize set semantics

### Tertiary (LOW confidence — flag for validation)
- Glance LazyColumn item hard cap: No official numeric limit documented. The 1 MB Binder transaction limit is well-known Android architecture knowledge but its application to Glance ListView serialization was not verified against an official Glance-specific source. The 50-item cap recommendation is conservative and should be validated empirically.
- Shimmer/animation impossibility in Glance: Based on Glance's RemoteViews constraint documentation and absence of animation APIs in the Glance package. Absence of evidence is not definitive. Validated by reviewing the Glance composable inventory which has no animation primitives.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all library versions verified against official Maven/releases pages
- Architecture patterns: HIGH — patterns verified against official Glance and Hilt documentation; existing codebase patterns confirmed by reading source files
- Pitfalls: HIGH (RESULT_CANCELED, I/O in provideContent, @AndroidEntryPoint) / MEDIUM (LazyColumn item limit, alpha via color)
- Shimmer limitation: MEDIUM — inferred from RemoteViews constraint model, no animation API present in Glance composable set

**Research date:** 2026-03-14
**Valid until:** 2026-04-14 (stable library; safe 30-day window; Glance 1.1.1 is current stable as of research date)
