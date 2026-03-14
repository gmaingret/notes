---
phase: 15-interactive-actions
verified: 2026-03-14T15:42:18Z
status: passed
score: 13/13 must-haves verified
re_verification: false
---

# Phase 15: Interactive Actions Verification Report

**Phase Goal:** Users can add and delete bullets directly from the home screen widget without opening the app
**Verified:** 2026-03-14T15:42:18Z
**Status:** passed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

#### Plan 01 Truths (ACT-02: Delete)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | Each bullet row in the widget shows a bare x delete icon on the right side | VERIFIED | `WidgetContent.kt` BulletRow renders a `Box` with `\u00D7` after the inner dot+text Row (lines 218–238) |
| 2 | Tapping the x icon removes the bullet from the widget instantly (optimistic) | VERIFIED | `performDelete` filters bullet from store and calls `store.saveBullets(filtered)` before the API call (lines 77–82 of DeleteBulletActionCallback.kt); `pushStateToGlance` is called in `onAction` immediately after |
| 3 | If the server delete fails, the bullet reappears and a toast shows | VERIFIED | Rollback path restores `store.saveBullets(originalBullets)`, sets `DisplayState.CONTENT`, returns `"Couldn't delete"` toast message (lines 96–101 of DeleteBulletActionCallback.kt) |
| 4 | If the server returns 401, the widget transitions to SESSION_EXPIRED state | VERIFIED | `isAuthError` checks `HttpException code 401`; on match `store.saveDisplayState(DisplayState.SESSION_EXPIRED)` (line 94); verified by unit test `on auth error, display state transitions to SESSION_EXPIRED` |
| 5 | Completed bullets have the same delete icon as active bullets | VERIFIED | x icon Box is unconditionally rendered for all `WidgetBullet` values; color uses `textColor` which is computed for both complete (gray) and active (onSurface) but the icon always appears |

#### Plan 02 Truths (ACT-01: Add)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 6 | Tapping the [+] button opens a transparent overlay dialog over the home screen | VERIFIED | `HeaderRow` creates `addIntent` targeting `AddBulletActivity` with `FLAG_ACTIVITY_NEW_TASK`, `AddBulletActivity` uses `Theme.Notes.Overlay` (manifest line 53) with `windowIsFloating=true`, `windowIsTranslucent=true` |
| 7 | The dialog shows a pre-focused text field with keyboard auto-showing | VERIFIED | `AddBulletDialog` uses `FocusRequester` + `LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboardController?.show() }` (lines 139–142) |
| 8 | Pressing Enter with text creates a bullet at the top of the list and closes the dialog | VERIFIED | `KeyboardActions.onDone` calls `onConfirm(trimmed)` when non-blank; `performAddBullet` inserts at position 0; on `AddBulletResult.Success` the activity calls `finish()` |
| 9 | Pressing Enter with empty text does nothing | VERIFIED | `onDone` guard: `if (trimmed.isNotBlank()) onConfirm(trimmed)` (line 161) — blank input is ignored |
| 10 | Tapping Cancel or outside the dialog dismisses it | VERIFIED | `TextButton("Cancel") { onDismiss() }` calls `finish()`; `setFinishOnTouchOutside(true)` in `onCreate` (line 71) |
| 11 | The new bullet appears optimistically in the widget immediately | VERIFIED | `store.saveBullets(listOf(tempBullet) + original)` + `pushStateToGlance` called before API result; temp ID has `"temp-${nanoTime()}"` prefix |
| 12 | On API failure, dialog stays open with text preserved and a toast shows | VERIFIED | `AddBulletResult.Failure` branch shows toast but does NOT call `finish()` (line 99 comment: "Dialog stays open — do NOT finish()") |
| 13 | On API success, temp bullet ID is replaced with real server-assigned ID | VERIFIED | Success path: `store.saveBullets(listOf(realBullet) + original)` where `realBullet.id = bullet.id` (server-assigned); verified by unit test `on success, temp bullet is replaced with server-assigned ID` |

**Score:** 13/13 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `android/app/src/main/java/com/gmaingret/notes/widget/DeleteBulletActionCallback.kt` | ActionCallback with optimistic delete + rollback | VERIFIED | 115 lines; `ActionCallback` impl; `BULLET_ID_PARAM` companion; `performDelete` extracted as `internal suspend fun` |
| `android/app/src/main/java/com/gmaingret/notes/widget/WidgetEntryPoint.kt` | Hilt entry point exposing both use cases | VERIFIED | Exposes `deleteBulletUseCase(): DeleteBulletUseCase` (line 31) and `createBulletUseCase(): CreateBulletUseCase` (line 32) |
| `android/app/src/main/java/com/gmaingret/notes/widget/WidgetContent.kt` | BulletRow with x icon + HeaderRow with [+] wired to AddBulletActivity | VERIFIED | `BULLET_ID_PARAM` referenced (line 225); `actionRunCallback<DeleteBulletActionCallback>` in BulletRow (line 223); `AddBulletActivity` intent in HeaderRow (line 138) |
| `android/app/src/test/java/com/gmaingret/notes/widget/DeleteBulletActionCallbackTest.kt` | 6 unit tests for optimistic delete and rollback | VERIFIED | 6 named tests covering: optimistic removal, correct usecase invocation, rollback, auth error, no-op on missing id, post-success absence of deleted bullet |
| `android/app/src/main/java/com/gmaingret/notes/widget/add/AddBulletActivity.kt` | Overlay Activity for adding bullets from widget | VERIFIED | 276 lines; `@AndroidEntryPoint`; `ComponentActivity`; `@Inject lateinit var createBulletUseCase`; `performAddBullet` extracted; `AddBulletResult` sealed class |
| `android/app/src/main/res/values/themes.xml` | Theme.Notes.Overlay with translucent floating window | VERIFIED | `Theme.Notes.Overlay` defined with `windowIsTranslucent=true`, `windowIsFloating=true`, `backgroundDimAmount=0.5` (lines 12–20) |
| `android/app/src/main/AndroidManifest.xml` | AddBulletActivity registration | VERIFIED | `.widget.add.AddBulletActivity` registered with `exported=false` and `theme=@style/Theme.Notes.Overlay` (lines 50–53) |
| `android/app/src/test/java/com/gmaingret/notes/widget/add/AddBulletActionTest.kt` | 6 unit tests for add bullet logic | VERIFIED | 6 named tests covering: correct request, temp ID replacement, rollback, auth error 401, optimistic insert at index 0, CONTENT state after success |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `WidgetContent.kt` BulletRow x icon | `DeleteBulletActionCallback` | `actionRunCallback<DeleteBulletActionCallback>(actionParametersOf(BULLET_ID_PARAM to bullet.id))` | WIRED | Lines 222–227 of WidgetContent.kt |
| `DeleteBulletActionCallback` | `WidgetEntryPoint` | `EntryPointAccessors.fromApplication` → `entryPoint.deleteBulletUseCase()` | WIRED | Lines 42–50 of DeleteBulletActionCallback.kt |
| `DeleteBulletActionCallback` | `WidgetStateStore + pushStateToGlance` | `store.saveBullets` (optimistic) → `pushStateToGlance` in `onAction` | WIRED | `store.saveBullets(filtered)` line 81; `pushStateToGlance` line 53 |
| `WidgetContent.kt` HeaderRow [+] button | `AddBulletActivity` | `actionStartActivity(Intent(context, AddBulletActivity::class.java))` with `doc_id` extra | WIRED | Lines 138–151 of WidgetContent.kt |
| `AddBulletActivity` | `CreateBulletUseCase` | `@AndroidEntryPoint` + `@Inject lateinit var createBulletUseCase` | WIRED | Lines 55, 59 of AddBulletActivity.kt |
| `AddBulletActivity` | `WidgetStateStore + pushStateToGlance` | `store.saveBullets` (optimistic) → `pushStateToGlance` after mutation | WIRED | Lines 226, 89 of AddBulletActivity.kt |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| ACT-01 | 15-02-PLAN.md | User can tap a "+" button to add a new bullet at the top of the list via a lightweight overlay dialog with pre-focused text field | SATISFIED | `AddBulletActivity` overlay dialog fully implemented; [+] button wired in `HeaderRow`; optimistic insert at index 0 |
| ACT-02 | 15-01-PLAN.md | User can tap a delete icon on any bullet to remove it | SATISFIED | `DeleteBulletActionCallback` with optimistic delete + rollback; x icon on every `BulletRow` |

Both requirements are marked `[x]` complete in REQUIREMENTS.md Traceability table (Phase 15).

No orphaned requirements — no other requirement IDs map to Phase 15 in REQUIREMENTS.md.

---

### Anti-Patterns Found

None. The following were investigated and cleared:

- `"placeholder"` comments in `WidgetContent.kt` (lines 249, 258, 266): these are code comments describing skeleton shimmer shapes in `LoadingContent` — not stubs.
- `placeholder = { Text("New bullet") }` in `AddBulletActivity.kt` (line 155): this is the `OutlinedTextField` input hint label — correct usage.
- `return null` in `DeleteBulletActionCallback.kt` (line 75): intentional no-op return for missing bullet ID — documented behavior (Test 5).

---

### Human Verification Required

The following behaviors cannot be verified programmatically:

#### 1. Overlay dialog appearance on home screen

**Test:** Add widget to home screen, tap [+] button.
**Expected:** A floating dialog appears over the home screen with a dimmed background (50% dim), rounded corners, pre-focused text field showing "New bullet" placeholder, soft keyboard visible.
**Why human:** Visual rendering of a translucent floating Activity over the launcher cannot be verified by static analysis.

#### 2. Tap-outside dismissal

**Test:** Open the add dialog, tap the dimmed area outside the dialog card.
**Expected:** Dialog closes with no bullet created.
**Why human:** `setFinishOnTouchOutside(true)` is set in code but actual touch interception depends on window management and cannot be tested with unit tests.

#### 3. Delete icon visual alignment

**Test:** Open widget with bullets; observe each bullet row.
**Expected:** Each row shows dot + text (left, fills width) and a bare x icon on the right side, color matching the bullet text (gray for completed, primary-on-surface for active).
**Why human:** Glance composable layout rendering in a real widget cannot be verified by static analysis.

#### 4. Optimistic feedback latency

**Test:** Tap x on a bullet while on a slow or offline connection.
**Expected:** Bullet disappears from widget immediately; widget does not freeze; if offline, bullet reappears within a few seconds with a toast.
**Why human:** Real-time UI feedback and latency behavior require a live device.

---

## Gaps Summary

No gaps. All 13 must-have truths are verified, all 8 artifacts are substantive and wired, all 6 key links are confirmed, and both requirement IDs (ACT-01, ACT-02) are satisfied with evidence.

---

_Verified: 2026-03-14T15:42:18Z_
_Verifier: Claude (gsd-verifier)_
