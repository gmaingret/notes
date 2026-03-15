# Phase 15: Interactive Actions - Research

**Researched:** 2026-03-14
**Domain:** Jetpack Glance ActionCallback, Android overlay Activity, optimistic widget state
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Widget Layout**
- Header row: document title (left-aligned, single-line truncated with ellipsis) + [+] icon (right-aligned)
- Thin divider line between header and bullet list
- Same background throughout header and list (no distinct header shade)
- Tapping the document title opens the document in the full Notes app
- Bullet rows: dot + text + bare x icon (right-aligned)
- Delete icon only appears on actual bullet rows (not empty/loading/error states)
- Empty state: centered "No bullets yet" text (no add prompt — header [+] is sufficient)

**Add Bullet UX**
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

**Delete Bullet UX**
- Always-visible bare x icon on every bullet row (no circular background)
- x icon color matches bullet text color (same gray/white depending on theme)
- One tap deletes — no confirmation dialog
- Completed bullets treated identically to active bullets for deletion
- Instant removal (no fade-out animation)
- Tapping bullet text/dot area opens document in full app (document level, not specific bullet)

**Optimistic Updates**
- Add: dialog closes immediately, new bullet appears optimistically in widget at top
- Delete: bullet row vanishes instantly from widget
- On add failure: dialog stays open (text preserved), toast "Couldn't add bullet"
- On delete failure: bullet reappears in original position, toast "Couldn't delete"
- No visual highlight on rollback — just toast

**Error Handling**
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
- Whether add action goes through ActionCallback -> Activity or direct Activity launch

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| ACT-01 | User can tap a "+" button to add a new bullet at the top of the list via a lightweight overlay dialog with pre-focused text field | Overlay Activity via actionStartActivity from Glance; CreateBulletUseCase(CreateBulletRequest(docId, parentId=null, afterId=null, content)) covers top insertion; optimistic state in WidgetStateStore + pushStateToGlance covers instant feedback |
| ACT-02 | User can tap a delete icon on any bullet to remove it | actionRunCallback with ActionParameters.Key<String> passes bullet ID to DeleteActionCallback; DeleteBulletUseCase(id) covers removal; optimistic list manipulation + rollback in WidgetStateStore covers instant feedback |
</phase_requirements>

---

## Summary

Phase 15 adds two interactive actions to the widget built in Phases 13 and 14: adding a bullet via an overlay dialog and deleting a bullet by tapping a row icon. The infrastructure (use cases, repositories, WidgetStateStore, pushStateToGlance, ActionCallback pattern) is already in place. This phase is primarily integration and new-file work rather than architectural invention.

The most architecturally novel element is the overlay Activity for adding bullets. This is a `ComponentActivity` registered with a dialog-style theme in `AndroidManifest.xml`, launched via `actionStartActivity` from the [+] button in Glance. It uses `NotesTheme` and Jetpack Compose, with `LaunchedEffect` to auto-focus the text field and show the keyboard on entry. The Activity does the heavy lifting: calls `CreateBulletUseCase` directly (it has access to the Hilt component via `@AndroidEntryPoint`), applies optimistic state to `WidgetStateStore`, then calls `NotesWidget.pushStateToGlance` to trigger recomposition — all before the API call resolves.

For delete, the existing `ActionCallback` pattern (`RetryActionCallback`, `ReconfigureActionCallback`) is extended: a new `DeleteBulletActionCallback` receives the bullet ID via `ActionParameters`, applies optimistic removal to `WidgetStateStore`, calls `pushStateToGlance`, then fires `DeleteBulletUseCase` and rolls back on failure.

**Primary recommendation:** Launch add dialog as a standalone `@AndroidEntryPoint` Activity with dialog theme (fastest, avoids Glance ActionCallback coroutine timeout constraints on I/O). Use `ActionCallback` for delete (no UI needed, coroutine-safe I/O pattern already proven in `RetryActionCallback`).

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Glance | 1.1.1 | Widget framework | Already in project (Phase 13) |
| Glance Material3 | 1.1.1 | Widget theming | Already in project |
| Hilt | 2.51.x | DI for ActionCallback and Activity | Already in project; @AndroidEntryPoint works on Activity |
| Kotlin Coroutines | 1.8.x | Async in ActionCallback and ViewModel | Already in project |
| AndroidX Activity Compose | latest | setContent in overlay Activity | Already used in WidgetConfigActivity |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| android.widget.Toast | system | Error feedback from ActionCallback | Showing "Couldn't delete" from callback context |
| ActionParameters (Glance) | 1.1.1 | Pass bullet ID to ActionCallback | Delete action needs bullet ID in callback |
| actionStartActivity (Glance) | 1.1.1 | Launch overlay Activity from [+] button | Add action — launches AddBulletActivity |
| actionRunCallback (Glance) | 1.1.1 | Trigger delete callback from x button | Delete action — no Activity needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Activity for add dialog | ActionCallback that shows a Dialog | ActionCallback runs in widget process — no Activity context for keyboard/focus; Activity is cleaner |
| Direct Activity launch from [+] | ActionCallback -> startActivity inside onAction | Both work; direct actionStartActivity(Intent) is simpler and avoids ActionCallback overhead for the add path |
| Toast in ActionCallback | Snackbar | Snackbar requires a View context; Toast works with application context from ActionCallback |

**Installation:** No new dependencies needed — all libraries already in `android/app/build.gradle.kts`.

---

## Architecture Patterns

### Recommended Project Structure

```
android/app/src/main/java/com/gmaingret/notes/widget/
├── NotesWidget.kt              # Existing — no change needed (uses WidgetStateStore)
├── WidgetContent.kt            # MODIFY — add [+] to HeaderRow, add × to BulletRow
├── WidgetStateStore.kt         # MODIFY — add optimistic add/delete helpers
├── WidgetEntryPoint.kt         # MODIFY — expose CreateBulletUseCase, DeleteBulletUseCase
├── DeleteBulletActionCallback.kt   # NEW — receives bulletId param, calls delete
├── add/
│   └── AddBulletActivity.kt       # NEW — overlay dialog Activity for add
```

### Pattern 1: ActionParameters for Delete

**What:** Pass the bullet ID from Glance composable to ActionCallback using typed key-value parameters.
**When to use:** Any ActionCallback that needs item-specific data (e.g., which bullet to delete).

```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/user-interaction
// In WidgetContent.kt — define key at file level
private val BULLET_ID_KEY = ActionParameters.Key<String>("bullet_id")

// In BulletRow composable — attach to × icon click
Text(
    text = "×",
    modifier = GlanceModifier.clickable(
        actionRunCallback<DeleteBulletActionCallback>(
            actionParametersOf(BULLET_ID_KEY to bullet.id)
        )
    )
)

// In DeleteBulletActionCallback.kt
class DeleteBulletActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val bulletId = parameters[BULLET_ID_KEY] ?: return
        // optimistic remove, call use case, rollback on failure
    }
}
```

**Key constraint:** `BULLET_ID_KEY` must be accessible from both `WidgetContent.kt` (to build parameters) and `DeleteBulletActionCallback.kt` (to read them). Define it in `WidgetContent.kt` as `internal` or in a shared constants file.

### Pattern 2: Overlay Activity for Add Dialog

**What:** A `ComponentActivity` themed as a floating dialog, launched via `actionStartActivity` from Glance.
**When to use:** Any widget interaction that requires keyboard input or complex UI not achievable in Glance.

```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/user-interaction
// In WidgetContent.kt — [+] button in HeaderRow
val addIntent = Intent(context, AddBulletActivity::class.java).apply {
    flags = FLAG_ACTIVITY_NEW_TASK
    putExtra("doc_id", documentId)
}
Text(
    text = "+",
    modifier = GlanceModifier.clickable(actionStartActivity(addIntent))
)

// In AndroidManifest.xml
<activity
    android:name=".widget.add.AddBulletActivity"
    android:exported="false"
    android:theme="@style/Theme.Notes.Overlay" />

// In themes.xml — new overlay style
<style name="Theme.Notes.Overlay" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowIsFloating">true</item>
    <item name="android:backgroundDimEnabled">true</item>
    <item name="android:backgroundDimAmount">0.5</item>
    <item name="android:windowBackground">@android:color/transparent</item>
</style>
```

**AddBulletActivity pattern:**
```kotlin
@AndroidEntryPoint
class AddBulletActivity : ComponentActivity() {
    @Inject lateinit var createBulletUseCase: CreateBulletUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val docId = intent.getStringExtra("doc_id") ?: run { finish(); return }

        setContent {
            NotesTheme {
                AddBulletDialog(
                    onConfirm = { content ->
                        lifecycleScope.launch {
                            performAdd(docId, content)
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}
```

### Pattern 3: Optimistic State Management

**What:** Mutate `WidgetStateStore` immediately, push to Glance, then perform network I/O. Roll back on failure.
**When to use:** Both add and delete actions — gives instant visual feedback without waiting for network.

```kotlin
// Delete optimistic pattern (in DeleteBulletActionCallback)
val store = WidgetStateStore.getInstance(context)
val original = store.getBullets().toMutableList()

// 1. Optimistic remove
store.saveBullets(original.filter { it.id != bulletId })
NotesWidget.pushStateToGlance(context)

// 2. Perform actual delete
val result = entryPoint.deleteBulletUseCase().invoke(bulletId)

// 3. Roll back on failure
result.onFailure {
    store.saveBullets(original)
    NotesWidget.pushStateToGlance(context)
    Toast.makeText(context, "Couldn't delete", Toast.LENGTH_SHORT).show()
}

// Add optimistic pattern (in AddBulletActivity before/after API call)
// 1. Build WidgetBullet with temp content
val tempBullet = WidgetBullet(id = "temp-${System.nanoTime()}", content = content, isComplete = false)
val original = store.getBullets()
store.saveBullets(listOf(tempBullet) + original)
NotesWidget.pushStateToGlance(applicationContext)
finish() // Close dialog immediately

// 2. In background — replace temp ID with real bullet after API responds
// Note: since dialog is closed, errors shown via Toast from background coroutine
```

### Pattern 4: EntryPoint Access in ActionCallback

**What:** Glance `ActionCallback` cannot use `@AndroidEntryPoint`; use `EntryPointAccessors` — the same pattern already used in `NotesWidget.provideGlance`.
**When to use:** Every `ActionCallback` that needs DI-provided objects.

```kotlin
// Already established in Phase 13 — apply same pattern in DeleteBulletActionCallback
val entryPoint = EntryPointAccessors.fromApplication(
    context.applicationContext,
    WidgetEntryPoint::class.java
)
val result = entryPoint.deleteBulletUseCase().invoke(bulletId)
```

`WidgetEntryPoint` must expose `createBulletUseCase()` and `deleteBulletUseCase()`.

### Anti-Patterns to Avoid

- **Calling I/O from provideContent{} lambda:** Already documented as established project rule. Actions must not trigger widget re-fetches from inside Glance composition.
- **Hard-coding glanceId in pushStateToGlance:** Already solved — `NotesWidget.pushStateToGlance` iterates all glanceIds. No changes needed.
- **Theme.AppCompat for overlay Activity:** The app's `Theme.Notes` parent is `android:Theme.Material.NoActionBar`. The overlay style must also use a `android:Theme.*` parent (not `Theme.AppCompat.*`) to avoid AppCompat dependency.
- **android:windowIsFloating without windowIsTranslucent:** On some OEMs, `windowIsFloating=true` alone causes black background. Always pair with `windowIsTranslucent=true`.
- **Showing keyboard without requestFocus:** In Compose, auto-keyboard requires both `FocusRequester.requestFocus()` and `SoftwareKeyboardController.show()` in a `LaunchedEffect(Unit)`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Passing bullet ID to ActionCallback | Custom Intent extras or global shared state | `ActionParameters.Key<String>` + `actionParametersOf` | Type-safe, built into Glance 1.1.1 |
| Widget state update after action | Custom BroadcastReceiver | `NotesWidget.pushStateToGlance(context)` (already exists) | Already pushes to all glanceIds |
| Error toast from ActionCallback | Custom notification | `Toast.makeText(context, ..., LENGTH_SHORT).show()` | Works with application context, no View needed |
| Auth check before action | Upfront token validity check | Let API call return 401, map to SESSION_EXPIRED display state | "Let it fail naturally" — established project philosophy |
| Keyboard management in Compose | WindowInsetsController directly | `LocalSoftwareKeyboardController.current?.show()` + `FocusRequester` | Standard Compose keyboard API |

**Key insight:** The add and delete use cases, bullet repository, WidgetStateStore, and pushStateToGlance mechanism are all fully implemented. Phase 15 assembles new files that wire these together — it does not build new infrastructure.

---

## Common Pitfalls

### Pitfall 1: ActionCallback onAction Timeout
**What goes wrong:** `ActionCallback.onAction` has a time limit (like a BroadcastReceiver). Running network I/O that takes >10s can be killed by the system.
**Why it happens:** Glance ActionCallback runs in a coroutine but on a bounded context.
**How to avoid:** For delete, the API call is fast (single DELETE request). For add, the dialog Activity handles I/O directly in `lifecycleScope` — no timeout concern. For `DeleteBulletActionCallback`, keep I/O to a single Retrofit call with no retries.
**Warning signs:** Widget appears to delete/add but then immediately reverts — system killed the callback mid-execution.

### Pitfall 2: AddBulletActivity Receives No docId
**What goes wrong:** `AddBulletActivity.onCreate` gets a null `doc_id` extra and finishes immediately.
**Why it happens:** `actionStartActivity(intent)` in Glance requires the Intent to carry extras from within the provideContent composition context.
**How to avoid:** Pass `documentId` from `WidgetUiState.Content` into `WidgetContent` composable and thread it down to `HeaderRow`, then put it in the Intent.
**Warning signs:** Dialog opens and immediately closes.

### Pitfall 3: Temp Bullet ID Persists After API Failure
**What goes wrong:** Optimistic add creates a `WidgetBullet` with a temp ID (e.g., `temp-123`). If the Activity is closed before the API responds and the API fails, the temp bullet stays in `WidgetStateStore`.
**Why it happens:** The Activity closes immediately (dialog UX requirement). The background coroutine must perform rollback even after Activity is gone.
**How to avoid:** Keep a reference to the original bullet list before optimistic insert. In the background coroutine (inside `lifecycleScope.launch`), wrap API call in try/catch and restore original list + call `pushStateToGlance` on failure.

### Pitfall 4: WidgetStateStore.getBullets() Returns Empty During Optimistic Update Race
**What goes wrong:** Two rapid deletes: delete callback 1 reads `getBullets()`, callback 2 reads `getBullets()` before callback 1 writes. Callback 2's rollback restores stale list that re-adds bullet 1.
**Why it happens:** DataStore `edit` is not atomic across two concurrent reads.
**How to avoid:** For this phase's scope (one action at a time from widget), this race is highly unlikely. No special handling needed — acknowledged as acceptable edge case.

### Pitfall 5: Overlay Activity Leaves Home Screen in Wrong State
**What goes wrong:** If `AddBulletActivity` crashes, the home screen stays dimmed.
**Why it happens:** `windowIsTranslucent + backgroundDimEnabled` dims the launcher behind the Activity. On crash, the dim is released by the OS — but may take a moment.
**How to avoid:** Standard Activity lifecycle — no special handling needed. OS always releases window decorations on Activity death.

### Pitfall 6: × Icon Touch Target Too Small
**What goes wrong:** Users accidentally open the document instead of deleting the bullet.
**Why it happens:** Glance text elements have minimal default touch targets.
**How to avoid:** Wrap × text in a `Box` or add `.padding(8.dp)` to the modifier to expand the tap area without changing visual size.

---

## Code Examples

Verified patterns from official sources and existing codebase:

### ActionParameters — Define Key and Pass Value
```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/user-interaction
// File: WidgetContent.kt

internal val BULLET_ID_PARAM = ActionParameters.Key<String>("bullet_id")

// In BulletRow:
Text(
    text = "×",
    modifier = GlanceModifier
        .padding(8.dp)
        .clickable(
            actionRunCallback<DeleteBulletActionCallback>(
                actionParametersOf(BULLET_ID_PARAM to bullet.id)
            )
        )
)
```

### ActionParameters — Read in ActionCallback
```kotlin
// Source: https://developer.android.com/develop/ui/compose/glance/user-interaction
class DeleteBulletActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val bulletId = parameters[BULLET_ID_PARAM] ?: return
        // ... use bulletId
    }
}
```

### Launch Add Activity from Glance [+] Button
```kotlin
// Source: Existing codebase pattern (ReconfigureActionCallback uses same FLAG_ACTIVITY_NEW_TASK)
val addIntent = Intent(context, AddBulletActivity::class.java).apply {
    flags = FLAG_ACTIVITY_NEW_TASK
    putExtra("doc_id", documentId)
}
Text(
    text = "+",
    modifier = GlanceModifier.clickable(actionStartActivity(addIntent))
)
```

### Auto-Focus and Show Keyboard in Compose
```kotlin
// Source: Standard Jetpack Compose pattern (used in main app screens)
val focusRequester = remember { FocusRequester() }
val keyboardController = LocalSoftwareKeyboardController.current

LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboardController?.show()
}

OutlinedTextField(
    value = text,
    onValueChange = { text = it },
    modifier = Modifier.focusRequester(focusRequester),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text) })
)
```

### Overlay Activity Theme (themes.xml)
```xml
<!-- Source: Android developer docs — dialog-themed Activity pattern -->
<style name="Theme.Notes.Overlay" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowIsFloating">true</item>
    <item name="android:backgroundDimEnabled">true</item>
    <item name="android:backgroundDimAmount">0.5</item>
    <item name="android:windowBackground">@android:color/transparent</item>
</style>
```

### Optimistic Delete in ActionCallback
```kotlin
// Source: Phase 12 BulletTreeScreen established pattern; adapted for ActionCallback
val store = WidgetStateStore.getInstance(context)
val before = store.getBullets()
store.saveBullets(before.filter { it.id != bulletId })
NotesWidget.pushStateToGlance(context)

val entryPoint = EntryPointAccessors.fromApplication(
    context.applicationContext, WidgetEntryPoint::class.java
)
entryPoint.deleteBulletUseCase().invoke(bulletId).onFailure { e ->
    store.saveBullets(before)
    NotesWidget.pushStateToGlance(context)
    val msg = if (isAuthError(e)) "Session expired" else "Couldn't delete"
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    if (isAuthError(e)) store.saveDisplayState(DisplayState.SESSION_EXPIRED)
}
```

### Widget Title Click (existing pattern to reuse for document title in header)
```kotlin
// Source: WidgetContent.kt — existing HeaderRow code (already handles title click)
// No change needed — HeaderRow already has clickable title via actionStartActivity(openAppIntent)
// Phase 15 only adds the [+] button to the RIGHT side of this row
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RemoteViews-based widgets with PendingIntent for actions | Glance ActionCallback with typed ActionParameters | Glance 1.0+ | Type-safe parameters, coroutine-based callbacks |
| Custom background tasks in ActionCallback | WorkManager for long-running; direct suspend for quick | Glance 1.1+ | Direct calls safe for fast I/O (single network request) |
| Separate XML layouts for widgets | Glance Composable functions | Glance 1.0+ | Same Compose skills reused |

**Deprecated/outdated:**
- `RemoteViews.setOnClickPendingIntent` for actions: not needed with Glance — use `actionRunCallback` or `actionStartActivity`.
- `onStartCommand` Service for widget actions: replaced by ActionCallback + coroutines for fast I/O, WorkManager for long I/O.

---

## Open Questions

1. **Optimistic add with temp ID vs. full refresh**
   - What we know: Optimistic add inserts a `WidgetBullet` with a temp ID immediately. After the API call succeeds, the real bullet (with server-assigned ID) must replace it. The fastest approach is to call `pushStateToGlance` again with the real bullet.
   - What's unclear: Whether to replace the temp entry in-place (by filtering and prepending the real one) or to do a full `fetchWidgetData` call after success.
   - Recommendation: Replace in-place for the "success path" to avoid an extra network round-trip (fetchWidgetData calls both documents and bullets API). Simply `store.saveBullets(listOf(realBullet) + store.getBullets().filter { it.id != tempId })` then `pushStateToGlance`.

2. **Toast from non-Activity context in ActionCallback**
   - What we know: `Toast.makeText(context, ...).show()` works from application context on API 30+. On older devices (API 28-29), `Toast` from non-Activity context is fully supported for short messages.
   - What's unclear: Whether the project min SDK allows this without concern.
   - Recommendation: Use `Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()`. Application context is safe for Toast on all supported API levels.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + MockK + Kotlin Coroutines Test |
| Config file | `android/app/build.gradle.kts` (testImplementation blocks) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "*.widget.*"` |
| Full suite command | `ssh root@192.168.1.50 "cd /root/notes/android && ./gradlew :app:testDebugUnitTest"` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ACT-01 | Add bullet: optimistic insert at top of list, rollback on failure | unit | `./gradlew :app:testDebugUnitTest --tests "*.AddBulletActivityTest"` | Wave 0 |
| ACT-01 | Add bullet: dialog dismissed immediately after optimistic insert | unit | `./gradlew :app:testDebugUnitTest --tests "*.AddBulletActivityTest"` | Wave 0 |
| ACT-02 | Delete bullet: optimistic remove, rollback on failure | unit | `./gradlew :app:testDebugUnitTest --tests "*.DeleteBulletActionCallbackTest"` | Wave 0 |
| ACT-02 | Delete bullet: correct bullet ID passed via ActionParameters | unit | `./gradlew :app:testDebugUnitTest --tests "*.DeleteBulletActionCallbackTest"` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*.widget.*"`
- **Per wave merge:** `./gradlew :app:testDebugUnitTest`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `android/app/src/test/.../widget/DeleteBulletActionCallbackTest.kt` — covers ACT-02 (optimistic remove, rollback, auth error -> SESSION_EXPIRED transition)
- [ ] `android/app/src/test/.../widget/add/AddBulletActivityTest.kt` — covers ACT-01 (optimistic insert, rollback, temp ID replacement) — note: Activity test may need Robolectric if testing lifecycle; business logic extract into a plain function enables pure JVM test

---

## Sources

### Primary (HIGH confidence)
- Existing codebase: `WidgetContent.kt`, `NotesWidget.kt`, `WidgetStateStore.kt`, `RetryActionCallback.kt`, `ReconfigureActionCallback.kt` — direct code inspection of established patterns
- Existing codebase: `WidgetEntryPoint.kt` — Hilt @EntryPoint pattern already proven in widget
- Existing codebase: `WidgetConfigActivity.kt` — @AndroidEntryPoint Activity with NotesTheme + lifecycleScope pattern
- `STATE.md` accumulated decisions — Phase 13/14 architectural decisions directly applicable

### Secondary (MEDIUM confidence)
- [Glance User Interaction — Android Developers](https://developer.android.com/develop/ui/compose/glance/user-interaction) — ActionParameters / actionRunCallback / actionStartActivity patterns verified against official docs

### Tertiary (LOW confidence)
- WebSearch: dialog-themed transparent overlay Activity approach — cross-verified with `android:windowIsTranslucent` documented attribute; LOW only because exact behavior on all launcher OEMs not tested

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project, no new dependencies
- Architecture: HIGH — ActionParameters pattern verified in official docs; overlay Activity verified in existing WidgetConfigActivity code; optimistic pattern established in Phase 12
- Pitfalls: HIGH — temp ID race and keyboard focus issues are well-known Compose/widget gotchas confirmed by existing code patterns

**Research date:** 2026-03-14
**Valid until:** 2026-04-14 (Glance 1.1.1 is stable; no breaking changes expected in 30 days)
