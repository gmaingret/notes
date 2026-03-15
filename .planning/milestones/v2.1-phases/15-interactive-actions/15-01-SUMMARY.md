---
phase: 15-interactive-actions
plan: 01
subsystem: widget
tags: [android, kotlin, glance, widget, actioncallback, optimistic-ui, tdd]

# Dependency graph
requires:
  - phase: 14-background-sync-and-auth
    provides: WidgetStateStore, DisplayState, NotesWidget.pushStateToGlance, DeleteBulletUseCase

provides:
  - DeleteBulletActionCallback with optimistic delete + rollback pattern
  - BULLET_ID_PARAM ActionParameters.Key shared between WidgetContent and callback
  - WidgetEntryPoint exposes deleteBulletUseCase()
  - BulletRow with visible x delete icon; inner row for dot+text opens app

affects:
  - 15-02 (AddBulletActivity launch via HeaderRow [+] button — BulletRow pattern established)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ActionParameters.Key companion object in ActionCallback for type-safe parameter passing"
    - "performDelete() extracted as internal suspend fun for pure JVM unit testability (no Robolectric)"
    - "Outer row has no clickable; inner row (dot+text) has actionStartActivity; x Box has actionRunCallback — innermost clickable wins"
    - "Optimistic store update before API call; rollback saveBullets(original) on failure"
    - "isAuthError helper: checks HttpException code 401 + message keywords"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/widget/DeleteBulletActionCallback.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/DeleteBulletActionCallbackTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/widget/WidgetEntryPoint.kt
    - android/app/src/main/java/com/gmaingret/notes/widget/WidgetContent.kt

key-decisions:
  - "performDelete() extracted as internal suspend fun so unit tests call it directly without Robolectric — Toast and pushStateToGlance remain in onAction only"
  - "Outer Row has no clickable; inner Row wraps dot+text with actionStartActivity; x Box has its own actionRunCallback — Glance innermost clickable wins, no nested clickable conflict"
  - "Alignment.Center used for x icon Box (not Alignment.CenterVertically which is Alignment.Vertical and only valid for Row verticalAlignment)"

# Metrics
duration: 9min
completed: 2026-03-14
---

# Phase 15 Plan 01: Widget Delete Bullet Action Summary

**Optimistic delete-bullet ActionCallback with rollback, WidgetEntryPoint extension, and x delete icon on every BulletRow — all tested with 6 pure JVM unit tests**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-14T15:18:32Z
- **Completed:** 2026-03-14T15:27:32Z
- **Tasks:** 2
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments

- WidgetEntryPoint extended with `deleteBulletUseCase()` to expose DeleteBulletUseCase via Hilt entry point pattern
- DeleteBulletActionCallback created implementing ActionCallback: reads BULLET_ID_PARAM, performs optimistic store update, calls server delete, rolls back on failure, transitions to SESSION_EXPIRED on 401
- `performDelete()` extracted as `internal suspend fun` for pure JVM unit testability — all Android-specific calls (Toast, pushStateToGlance) remain in `onAction`
- 6 unit tests cover: optimistic removal, correct usecase invocation, rollback, auth error, no-op on missing id, post-success absence of deleted bullet
- BulletRow modified with two-layer clickable: inner Row (dot+text) with `actionStartActivity`, outer x Box with `actionRunCallback<DeleteBulletActionCallback>` — innermost clickable wins per Glance semantics

## Task Commits

Each task was committed atomically:

1. **test(15-01): add failing tests for DeleteBulletActionCallback** — `81a4b37` (RED phase)
2. **feat(15-01): add DeleteBulletActionCallback with optimistic delete and rollback** — `0113552` (GREEN phase)
3. **feat(15-01): add delete icon to BulletRow in WidgetContent** — `936800a`

## Files Created/Modified

- `android/app/src/main/java/com/gmaingret/notes/widget/WidgetEntryPoint.kt` — Added `deleteBulletUseCase(): DeleteBulletUseCase` + import
- `android/app/src/main/java/com/gmaingret/notes/widget/DeleteBulletActionCallback.kt` — New: ActionCallback with optimistic delete + rollback; BULLET_ID_PARAM companion; performDelete() extracted
- `android/app/src/test/java/com/gmaingret/notes/widget/DeleteBulletActionCallbackTest.kt` — New: 6 unit tests for performDelete()
- `android/app/src/main/java/com/gmaingret/notes/widget/WidgetContent.kt` — BulletRow: split into inner content row + outer x delete icon Box; import actionParametersOf

## Decisions Made

- `performDelete()` extracted as `internal suspend fun` — enables pure JVM testing without Robolectric; Toast and `pushStateToGlance` in `onAction` only. Test suite calls `performDelete` directly and mocks `WidgetStateStore` + `DeleteBulletUseCase` with MockK.
- Two-layer clickable pattern: remove `.clickable()` from outer Row, put it on inner Row wrapping dot+text, put delete clickable on x Box — in Glance, the innermost clickable wins, so tapping x triggers delete while tapping the text/dot opens the app.
- `Alignment.Center` for x icon Box — `Alignment.CenterVertically` is `Alignment.Vertical` (for `Row.verticalAlignment`) not `Alignment` (for `Box.contentAlignment`), which caused a compilation error that was caught and fixed during Task 2.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Alignment.CenterVertically used as Box contentAlignment**
- **Found during:** Task 2 (assembleDebug build)
- **Issue:** Plan specified `contentAlignment = Alignment.CenterVertically` for the x icon Box, but `Alignment.CenterVertically` is `Alignment.Vertical` — only valid for `Row.verticalAlignment`. Box requires `Alignment` (2D).
- **Fix:** Changed to `contentAlignment = Alignment.Center`
- **Files modified:** android/app/src/main/java/com/gmaingret/notes/widget/WidgetContent.kt
- **Commit:** 936800a

---

**Total deviations:** 1 auto-fixed (type mismatch from plan's incorrect alignment constant)

## Self-Check: PASSED

- DeleteBulletActionCallback.kt: FOUND
- DeleteBulletActionCallbackTest.kt: FOUND
- WidgetEntryPoint.kt deleteBulletUseCase: FOUND
- WidgetContent.kt BULLET_ID_PARAM reference: FOUND
- Commit 0113552: FOUND
- Commit 936800a: FOUND
- All 6 unit tests: PASSED

---
*Phase: 15-interactive-actions*
*Completed: 2026-03-14*
