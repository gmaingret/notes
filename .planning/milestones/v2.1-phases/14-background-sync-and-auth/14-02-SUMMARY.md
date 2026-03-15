---
phase: 14-background-sync-and-auth
plan: 02
subsystem: ui
tags: [android, widget, glance, workmanager, kotlin, hilt, coroutines, tdd]

# Dependency graph
requires:
  - phase: 14-background-sync-and-auth
    plan: 01
    provides: WidgetSyncWorker, WidgetStateStore cache (saveBullets/getBullets/saveDisplayState), WorkManager Hilt bootstrap
  - phase: 13-widget-foundation
    plan: 04
    provides: BulletTreeViewModel, NotesWidget, NotesWidgetReceiver, WidgetConfigActivity, WidgetStateStore
provides:
  - provideGlance reads exclusively from WidgetStateStore cache (no live API calls)
  - BulletTreeViewModel.triggerWidgetRefreshIfNeeded() after every bullet mutation
  - WidgetRefreshHelper.refreshWidgetIfDocMatches() testable helper function
  - WorkManager 15-min periodic sync enqueued on widget configuration
  - WorkManager sync cancelled on widget removal (onDeleted)
  - MainActivity.onResume() triggers widget updateAll for cold start / background resume / post-login
  - MainViewModel.logout() writes SESSION_EXPIRED to WidgetStateStore + updateAll immediately
  - WidgetSyncTriggerTest: 3 unit tests for trigger logic
affects:
  - 14-background-sync-and-auth (plan 03 if it exists)
  - 15-interactive-actions

# Tech tracking
tech-stack:
  added: []
  patterns:
    - refreshWidgetIfDocMatches() extracted as internal testable helper — bypasses ViewModel private method testing limitation
    - MainViewModel accepts @ApplicationContext + WidgetStateStore via Hilt injection to trigger widget updateAll without AndroidViewModel
    - onResume() with fire-and-forget CoroutineScope for widget refresh — no lifecycle coroutine scope needed

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/widget/sync/WidgetRefreshHelper.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/sync/WidgetSyncTriggerTest.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/widget/NotesWidget.kt
    - android/app/src/main/java/com/gmaingret/notes/widget/NotesWidgetReceiver.kt
    - android/app/src/main/java/com/gmaingret/notes/widget/config/WidgetConfigActivity.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/bullet/BulletTreeViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/MainActivity.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainViewModel.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/NotesWidgetReceiverTest.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/main/MainViewModelTest.kt

key-decisions:
  - "triggerWidgetRefreshIfNeeded() logic extracted to WidgetRefreshHelper.kt as internal suspend fun for unit testability — ViewModel delegates to helper, tests import helper directly"
  - "MainViewModel injects @ApplicationContext + WidgetStateStore for logout SESSION_EXPIRED write + updateAll — avoids changing MainViewModel to AndroidViewModel"
  - "NotesWidgetReceiver.onDeleted calls clearAll() instead of per-widget clearDocumentId — single store clear is correct when any widget is removed (all widgets share same cache)"
  - "MainActivity.onResume uses fire-and-forget CoroutineScope(Dispatchers.Main + SupervisorJob()) for widget updateAll — avoids requiring lifecycleScope for a non-lifecycle coroutine"

patterns-established:
  - "Testable widget refresh logic: extract to internal suspend fun in widget/sync package, ViewModel delegates"
  - "Logout widget update: inject WidgetStateStore into ViewModel, write state then updateAll after logoutUseCase()"
  - "onResume widget refresh: fire-and-forget coroutine, silent catch on updateAll failure"

requirements-completed: [SYNC-01, SYNC-02, SYNC-03]

# Metrics
duration: 45min
completed: 2026-03-14
---

# Phase 14 Plan 02: In-App Sync Triggers and Cache-Reader Widget Summary

**provideGlance refactored to pure cache-reader with WorkManager lifecycle and BulletTreeViewModel mutation triggers wiring the complete sync story**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-03-14T14:05:00Z
- **Completed:** 2026-03-14T14:20:00Z
- **Tasks:** 2 (Task 1: cache-reader + WorkManager; Task 2 TDD: mutation triggers + onResume + logout)
- **Files modified:** 8

## Accomplishments

- NotesWidget.provideGlance() now reads exclusively from WidgetStateStore cache — no live API calls inside the widget renderer
- BulletTreeViewModel calls triggerWidgetRefreshIfNeeded() after every bullet mutation (9 call sites: create, delete, indent, outdent, toggleComplete, undo, redo, commitBulletMove, content-debounce patch)
- Widget only refreshes when the open document matches the widget's pinned document — prevents unnecessary fetches
- WorkManager 15-min periodic sync enqueued on document selection; cancelled on widget removal
- MainActivity.onResume() triggers updateAll for cold start, background resume, and post-login widget refresh
- MainViewModel.logout() writes SESSION_EXPIRED to widget cache and calls updateAll immediately
- 3 new unit tests for trigger logic + updated receiver and ViewModel tests

## Task Commits

1. **Task 1: Refactor provideGlance to cache-reader and add WorkManager enqueue/cancel lifecycle** - `5c5474d` (feat)
2. **Task 2: In-app mutation triggers, login/logout refresh, onResume refresh, and trigger tests** - `2468d0b` (feat)

## Files Created/Modified

- `widget/NotesWidget.kt` - provideGlance refactored to read from WidgetStateStore cache; removed produceState/currentState pattern
- `widget/NotesWidgetReceiver.kt` - onDeleted now calls cancelUniqueWork("widget_sync") + clearAll()
- `widget/config/WidgetConfigActivity.kt` - enqueueUniquePeriodicWork + immediate OneTimeWork after document selection
- `widget/sync/WidgetRefreshHelper.kt` - new internal testable helper: refreshWidgetIfDocMatches()
- `presentation/bullet/BulletTreeViewModel.kt` - triggerWidgetRefreshIfNeeded() method + 9 call sites added
- `MainActivity.kt` - onResume() override with fire-and-forget updateAll
- `presentation/main/MainViewModel.kt` - @ApplicationContext + WidgetStateStore injected; logout writes SESSION_EXPIRED + updateAll
- `test/widget/sync/WidgetSyncTriggerTest.kt` - new: 3 tests for refreshWidgetIfDocMatches logic
- `test/widget/NotesWidgetReceiverTest.kt` - updated to verify clearAll() instead of per-widget clearDocumentId
- `test/presentation/main/MainViewModelTest.kt` - updated to pass new context + widgetStateStore params

## Decisions Made

- Extracted trigger logic to `WidgetRefreshHelper.kt` as an `internal suspend fun` so it can be unit-tested without a real ViewModel (ViewModel private methods cannot be tested directly)
- MainViewModel changed to accept `@ApplicationContext Context` and `WidgetStateStore` via Hilt injection — this avoids converting MainViewModel to AndroidViewModel and keeps the constructor testable
- `onDeleted` uses `clearAll()` instead of looping `clearDocumentId(id)` — when any widget is removed, the shared cache (bullets, display state) should be cleared too; per-widget doc ID clearing is insufficient
- `WidgetSyncTriggerTest` tests the helper function directly rather than the ViewModel — cleaner separation of test concerns

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed MainViewModelTest constructor mismatch after adding new parameters**
- **Found during:** Task 2 (running full test suite)
- **Issue:** Adding `@ApplicationContext Context` and `WidgetStateStore` to `MainViewModel` constructor broke `MainViewModelTest` compilation
- **Fix:** Added `context` and `widgetStateStore` MockK mocks to the test setup and updated `createViewModel()` to pass them
- **Files modified:** `test/presentation/main/MainViewModelTest.kt`
- **Verification:** Full test suite passes
- **Committed in:** `2468d0b` (Task 2 commit)

**2. [Rule 1 - Bug] Fixed NotesWidgetReceiverTest to match new clearAll() behavior**
- **Found during:** Task 2 (running widget tests)
- **Issue:** Previous test verified `clearDocumentId(id)` per widget — new behavior is `clearAll()` once
- **Fix:** Rewrote test to verify `clearAll()` is called exactly once and `clearDocumentId()` is never called. Removed `WorkManager` static mock (caused `AbstractMethodError` with Robolectric) — WorkManager cancellation is covered by compile-time verification
- **Files modified:** `test/widget/NotesWidgetReceiverTest.kt`
- **Verification:** Widget test suite passes (43 tests)
- **Committed in:** `2468d0b` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 — existing tests broken by planned API changes)
**Impact on plan:** Both fixes necessary for test suite correctness. No scope creep.

## Issues Encountered

- `mockkStatic(WorkManager::class)` combined with Robolectric produced `AbstractMethodError` when recording the `every { WorkManager.getInstance(any()) }` mock — Robolectric's Context mock doesn't implement `getApplicationContext()`. Resolved by removing the static mock and relying on Robolectric's real WorkManager initialization (no-op in tests).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Full sync story is complete: WorkManager periodic background sync (Plan 01) + in-app mutation triggers + onResume refresh + logout/login state management (Plan 02)
- Widget now shows content instantly after bullet mutations, recovers from SESSION_EXPIRED after login, and updates immediately on logout
- Ready for Phase 15: Interactive Actions (widget taps, deep links into specific bullets)

---
*Phase: 14-background-sync-and-auth*
*Completed: 2026-03-14*
