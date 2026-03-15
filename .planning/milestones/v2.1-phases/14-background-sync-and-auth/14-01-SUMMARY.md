---
phase: 14-background-sync-and-auth
plan: 01
subsystem: widget
tags: [workmanager, hilt, android, kotlin, background-sync, datastore, tink, encryption]

# Dependency graph
requires:
  - phase: 13-widget-foundation
    provides: WidgetStateStore, WidgetBullet, WidgetUiState, BulletRepository, DocumentRepository, NotesWidget, WidgetModule

provides:
  - WorkManager 2.11.1 + Hilt Work 1.3.0 dependency wiring
  - NotesApplication implements Configuration.Provider with HiltWorkerFactory
  - AndroidManifest disables default WorkManager auto-initializer
  - DisplayState enum for widget display state persistence
  - WidgetStateStore extended with saveBullets/getBullets, saveDisplayState/getDisplayState, getFirstDocumentId, clearAll
  - WidgetSyncWorker: @HiltWorker CoroutineWorker for periodic background sync

affects:
  - 14-02 (WorkManager schedule registration — will register WidgetSyncWorker as periodic task)
  - 15-interactive-actions (worker handles SessionExpired which drives re-auth flow)

# Tech tracking
tech-stack:
  added:
    - androidx.work:work-runtime-ktx:2.11.1
    - androidx.hilt:hilt-work:1.3.0
    - androidx.hilt:hilt-compiler:1.3.0 (ksp)
    - androidx.work:work-testing:2.11.1 (test)
  patterns:
    - "@HiltWorker + @AssistedInject constructor for DI in CoroutineWorker"
    - "Configuration.Provider in Application class for custom WorkerFactory"
    - "tools:node=remove to disable WorkManager auto-initializer in AndroidManifest"
    - "WidgetSyncWorker returns Result.success() always to preserve periodic schedule"
    - "Non-auth network errors keep stale cache rather than overwriting with error state"

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/widget/sync/WidgetSyncWorker.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/sync/WidgetSyncWorkerTest.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/app/src/main/java/com/gmaingret/notes/NotesApplication.kt
    - android/app/src/main/AndroidManifest.xml
    - android/app/src/main/java/com/gmaingret/notes/widget/WidgetStateStore.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/WidgetStateStoreTest.kt

key-decisions:
  - "WidgetSyncWorker always returns Result.success() even on errors to keep the periodic schedule alive — Result.failure() triggers exponential backoff and can cancel the schedule"
  - "Non-auth network errors keep the stale bullet cache unchanged — stale data is better than a blank widget"
  - "getFirstDocumentId() iterates DataStore keys matching widget_doc_* prefix to find any configured widget without needing appWidgetId in worker context"
  - "@After teardown in WidgetStateStoreTest calls clearAll() to prevent DataStore state leakage between Robolectric tests that share the same backing file"
  - "WidgetSyncWorker tests construct the worker directly via its AssistedInject constructor (plain Kotlin constructor at JVM level) rather than using TestListenableWorkerBuilder — simpler, no factory wiring needed"

patterns-established:
  - "DisplayState enum at file level in WidgetStateStore.kt for colocation with the store that persists it"
  - "Tink-encrypted DataStore for all WidgetStateStore values using associated data strings for domain separation"
  - "triggerWidgetUpdate() wraps NotesWidget().updateAll() in try/catch — safe to ignore exceptions when no instances exist"

requirements-completed: [SYNC-02, SYNC-03]

# Metrics
duration: 14min
completed: 2026-03-14
---

# Phase 14 Plan 01: WorkManager Bootstrap and WidgetSyncWorker Summary

**WorkManager 2.11.1 periodic sync infrastructure with @HiltWorker CoroutineWorker, WidgetStateStore bullet cache, and full unit test coverage for auth/network/not-found error handling**

## Performance

- **Duration:** 14 min
- **Started:** 2026-03-14T13:47:36Z
- **Completed:** 2026-03-14T14:01:30Z
- **Tasks:** 2
- **Files modified:** 8 (6 modified, 2 created)

## Accomplishments

- WorkManager + Hilt Work dependencies added to version catalog and wired into NotesApplication via Configuration.Provider
- WidgetStateStore extended with Tink-encrypted bullet cache (saveBullets/getBullets), display state persistence (saveDisplayState/getDisplayState), getFirstDocumentId, and clearAll
- WidgetSyncWorker created as @HiltWorker CoroutineWorker that fetches documents+bullets, handles auth/404/network errors, and always returns Result.success() to preserve the periodic schedule

## Task Commits

Each task was committed atomically:

1. **Task 1: Gradle dependencies, WorkManager bootstrap, WidgetStateStore cache extensions** - `3b7b5a5` (feat)
2. **Task 2: WidgetSyncWorker with @HiltWorker DI and unit tests** - `21ad780` (feat)

**Plan metadata:** (docs commit below)

## Files Created/Modified

- `android/gradle/libs.versions.toml` - Added workManager 2.11.1, hiltWork 1.3.0, work-testing entries
- `android/app/build.gradle.kts` - Added work-runtime-ktx, hilt-work, hilt-work-compiler, work-testing dependencies
- `android/app/src/main/java/com/gmaingret/notes/NotesApplication.kt` - Added Configuration.Provider + HiltWorkerFactory injection
- `android/app/src/main/AndroidManifest.xml` - Added xmlns:tools + WorkManagerInitializer tools:node="remove" provider
- `android/app/src/main/java/com/gmaingret/notes/widget/WidgetStateStore.kt` - Added DisplayState enum, saveBullets/getBullets, saveDisplayState/getDisplayState, getFirstDocumentId, clearAll
- `android/app/src/main/java/com/gmaingret/notes/widget/sync/WidgetSyncWorker.kt` - New: @HiltWorker CoroutineWorker for background widget sync
- `android/app/src/test/java/com/gmaingret/notes/widget/WidgetStateStoreTest.kt` - Extended with 7 new tests + @After teardown
- `android/app/src/test/java/com/gmaingret/notes/widget/sync/WidgetSyncWorkerTest.kt` - New: 6 unit tests covering all sync behaviors

## Decisions Made

- Worker always returns `Result.success()` — ensures the periodic schedule is never cancelled by WorkManager's exponential backoff logic. Error states are communicated via WidgetStateStore display state instead.
- Non-auth network errors keep stale cache — blank widget is worse UX than slightly stale content when the server is temporarily unreachable.
- `getFirstDocumentId()` iterates DataStore preference keys with `widget_doc_*` prefix — avoids coupling worker to a specific appWidgetId.
- Added `@After teardown` to WidgetStateStoreTest — Robolectric reuses the same DataStore backing file within a test class run, causing cross-test state leakage without explicit cleanup.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Kotlin Result / WorkManager Result name collision in WidgetSyncWorkerTest**
- **Found during:** Task 2 (WidgetSyncWorker unit tests)
- **Issue:** Importing `androidx.work.ListenableWorker.Result` star-clashed with `kotlin.Result` used in repository return types, causing type mismatch compilation errors
- **Fix:** Changed import to `androidx.work.ListenableWorker` (no static inner import) and qualified worker result assertions as `ListenableWorker.Result.success()`; Kotlin `Result.success()/Result.failure()` in coEvery mocks remained unqualified
- **Files modified:** android/app/src/test/java/com/gmaingret/notes/widget/sync/WidgetSyncWorkerTest.kt
- **Verification:** Compilation succeeded, all 6 tests passed
- **Committed in:** 21ad780 (Task 2 commit)

**2. [Rule 2 - Missing Critical] Added @After teardown to WidgetStateStoreTest to prevent cross-test DataStore contamination**
- **Found during:** Task 1 (WidgetStateStore TDD GREEN phase)
- **Issue:** Robolectric reuses DataStore backing file within a test class run; `getBullets returns empty list` and `getFirstDocumentId returns null` tests failed because earlier tests had written data to the same store
- **Fix:** Added `@After fun tearDown() = runTest { store.clearAll() }` so each test starts with a clean DataStore
- **Files modified:** android/app/src/test/java/com/gmaingret/notes/widget/WidgetStateStoreTest.kt
- **Verification:** All 11 WidgetStateStoreTest tests pass (existing 4 + new 7)
- **Committed in:** 3b7b5a5 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 bug — import collision, 1 missing critical — test isolation)
**Impact on plan:** Both fixes were necessary for correct test execution. No scope creep.

## Issues Encountered

- Glance's `updateAll()` extension function on `GlanceAppWidget` requires explicit import `androidx.glance.appwidget.updateAll` — the Kotlin extension isn't auto-resolved without the import. Added import and compilation succeeded.

## Next Phase Readiness

- WorkManager infrastructure is complete and tested — Phase 14 Plan 02 can register WidgetSyncWorker as a periodic task (15-minute interval) via WorkManager.enqueueUniquePeriodicWork
- WidgetStateStore cache is ready for Phase 15 to read from (widget render from cache rather than live network call)
- WidgetSyncWorker unit tests pass on all 6 error cases — ready for integration

---
*Phase: 14-background-sync-and-auth*
*Completed: 2026-03-14*
