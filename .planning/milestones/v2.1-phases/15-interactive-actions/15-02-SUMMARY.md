---
phase: 15-interactive-actions
plan: "02"
subsystem: android-widget
tags: [widget, add-bullet, overlay-dialog, tdd, optimistic-update]
dependency_graph:
  requires: ["15-01", "14-02"]
  provides: ["ACT-01", "add-from-widget"]
  affects: ["WidgetContent.kt", "WidgetEntryPoint.kt", "AndroidManifest.xml"]
tech_stack:
  added: []
  patterns:
    - "@AndroidEntryPoint overlay Activity for widget actions"
    - "performAddBullet extracted as internal suspend fun for pure JVM unit testability"
    - "Sealed class AddBulletResult (Success/Failure/AuthError) for exhaustive result handling"
    - "Optimistic insert at position 0 with temp-${nanoTime()} ID replaced on success"
key_files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/widget/add/AddBulletActivity.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/add/AddBulletActionTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/widget/WidgetEntryPoint.kt
    - android/app/src/main/java/com/gmaingret/notes/widget/WidgetContent.kt
    - android/app/src/main/res/values/themes.xml
    - android/app/src/main/AndroidManifest.xml
decisions:
  - "AddBulletActivity uses @AndroidEntryPoint (not @EntryPoint) — Activity context works with Hilt direct injection"
  - "performAddBullet returns sealed AddBulletResult instead of nullable String for exhaustive when handling"
  - "[+] button only appears in ContentView HeaderRow — non-Content states have no doc_id to pass"
metrics:
  duration: "~20 minutes"
  completed: "2026-03-14T15:39:28Z"
  tasks_completed: 2
  files_changed: 6
---

# Phase 15 Plan 02: Widget Add Bullet Action Summary

**One-liner:** Overlay dialog Activity with auto-focused text field, optimistic bullet insert at top, temp ID replaced on success, rollback on failure, SESSION_EXPIRED on 401.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create AddBulletActivity overlay dialog with optimistic add logic | 7fcb804 | AddBulletActivity.kt, WidgetEntryPoint.kt, themes.xml, AndroidManifest.xml |
| 1 (test) | TDD RED — failing tests for performAddBullet | 9637552 | AddBulletActionTest.kt |
| 2 | Wire [+] header button to launch AddBulletActivity | 4372144 | WidgetContent.kt |

## What Was Built

### AddBulletActivity (`widget/add/AddBulletActivity.kt`)

- `@AndroidEntryPoint class AddBulletActivity : ComponentActivity()`
- Injects `CreateBulletUseCase` via `@Inject lateinit var`
- Reads `doc_id` from intent extra — `finish()` if null
- `setFinishOnTouchOutside(true)` for dismiss-on-tap-outside
- `setContent { NotesTheme { AddBulletDialog(...) } }`
- On confirm: launches coroutine with `performAddBullet`, calls `pushStateToGlance` after mutation
- On `Success`: `finish()`
- On `Failure`: toast stays visible, dialog stays open
- On `AuthError`: toast + `finish()`

### AddBulletDialog composable

- Auto-focused `OutlinedTextField` with `FocusRequester` + keyboard show `LaunchedEffect`
- `ImeAction.Done` + `KeyboardActions.onDone` → confirm on Enter (non-blank only)
- `TextButton("Cancel")` → dismiss
- `Surface` with `RoundedCornerShape(12.dp)` and `tonalElevation`

### performAddBullet (testable core)

- Optimistic insert: `WidgetBullet(id = "temp-${nanoTime()}", ...)` prepended to existing list
- Sets `DisplayState.CONTENT` immediately
- Calls `CreateBulletUseCase` with `parentId = null, afterId = null` for root-level top position
- Success: replaces temp bullet with `WidgetBullet(id = serverBullet.id, ...)`
- Failure: rolls back to original list
- Auth error (HTTP 401): rolls back + sets `DisplayState.SESSION_EXPIRED`

### AddBulletResult sealed class

```kotlin
sealed class AddBulletResult {
    data object Success : AddBulletResult()
    data class Failure(val message: String) : AddBulletResult()
    data object AuthError : AddBulletResult()
}
```

### Theme.Notes.Overlay (themes.xml)

Translucent floating window over home screen with 50% dim background.

### WidgetEntryPoint updated

Added `fun createBulletUseCase(): CreateBulletUseCase` to the interface.

### WidgetContent.kt [+] button re-wired

`HeaderRow` [+] button now launches `AddBulletActivity` with `doc_id` intent extra instead of `MainActivity`. Document title still opens the full app.

## Unit Tests

6 tests in `AddBulletActionTest.kt` — all passing:
1. `CreateBulletUseCase` called with correct `CreateBulletRequest` (documentId, parentId=null, afterId=null, content)
2. Temp bullet replaced with server-assigned ID on success
3. Original list restored on failure (rollback)
4. `DisplayState.SESSION_EXPIRED` on HTTP 401
5. Optimistic insert places new bullet at index 0 with `"temp-"` prefix ID
6. `DisplayState.CONTENT` set after successful add (even when list was empty)

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check

- [x] `AddBulletActivity.kt` exists at correct path
- [x] `AddBulletActionTest.kt` exists at correct path
- [x] `WidgetEntryPoint.kt` contains `createBulletUseCase()`
- [x] `themes.xml` contains `Theme.Notes.Overlay`
- [x] `AndroidManifest.xml` contains `.widget.add.AddBulletActivity`
- [x] `WidgetContent.kt` `[+]` button uses `AddBulletActivity` intent
- [x] All 6 unit tests pass
- [x] `assembleDebug` succeeds
