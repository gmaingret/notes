---
phase: 13-widget-foundation
plan: 02
subsystem: ui
tags: [android-widget, hilt, compose, credential-manager, tdd, mockk, widget-config]

# Dependency graph
requires:
  - phase: 13-widget-foundation
    plan: 01
    provides: WidgetStateStore, WidgetEntryPoint, NotesWidget, notes_widget_info.xml (with android:configure)

provides:
  - ConfigUiState sealed interface (Loading, NeedsLogin, DocumentsLoaded, Error)
  - WidgetConfigViewModel: auth-check-on-init, document loading, login, loginWithGoogle, selectDocument
  - WidgetConfigActivity: @AndroidEntryPoint, RESULT_CANCELED pattern, Compose UI, Google SSO
  - WidgetModule: Hilt @Provides binding for WidgetStateStore singleton
  - 8 unit tests for WidgetConfigViewModel (all auth/document states, event emission)

affects:
  - 13-03-widget-ui (widget is now placeable; NotesWidget.fetchWidgetData called after placement)
  - 14-background-sync (WidgetStateStore populated; widget ready for periodic sync)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - WidgetConfigActivity injects GoogleSignInUseCase for Credential Manager flow; ViewModel handles loginWithGoogle(idToken)
    - RESULT_CANCELED set immediately in onCreate before UI render; switches to RESULT_OK only on document tap
    - documentSelectedEvent is a Channel<Unit> received as Flow; Activity collects via LaunchedEffect to finalize result
    - WidgetModule provides WidgetStateStore via @Provides @Singleton bridging manual singleton to Hilt graph

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/widget/config/WidgetConfigViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/widget/config/WidgetConfigActivity.kt
    - android/app/src/main/java/com/gmaingret/notes/di/WidgetModule.kt
    - android/app/src/test/java/com/gmaingret/notes/widget/config/WidgetConfigViewModelTest.kt
  modified: []

key-decisions:
  - "WidgetModule provides WidgetStateStore via @Provides instead of @Inject constructor — manual singleton pattern requires explicit Hilt binding to bridge to ViewModel injection"
  - "Google SSO flow: GoogleSignInUseCase injected into Activity (needs Activity context for Credential Manager); ViewModel only receives the idToken via loginWithGoogle(idToken) — clean separation of concern"
  - "documentSelectedEvent uses Channel<Unit> (not SharedFlow) — one-shot delivery semantics prevent event replay if Activity recreates"
  - "Plan 03 stub files (WidgetContent.kt, ReconfigureActionCallback.kt, RetryActionCallback.kt, WidgetContentHelperTest.kt) moved to .planning/phases/13-widget-foundation/plan03-stubs/ — were created by a previous agent run and caused compile errors"

# Metrics
duration: 8min
completed: 2026-03-14
---

# Phase 13 Plan 02: Widget Config Activity Summary

**WidgetConfigActivity (RESULT_CANCELED pattern) with WidgetConfigViewModel (auth-check, document loading, login flow) and 8 passing unit tests; Google SSO via injected GoogleSignInUseCase; WidgetModule bridges WidgetStateStore manual singleton to Hilt**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-03-14T11:23:43Z
- **Completed:** 2026-03-14T11:32:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- `ConfigUiState` sealed interface with 4 variants (Loading, NeedsLogin, DocumentsLoaded, Error) in WidgetConfigViewModel
- WidgetConfigViewModel: on init — reads TokenStore, refreshes auth, loads documents; handles login/loginWithGoogle/retry
- `documentSelectedEvent` Channel delivers one-shot signal for Activity to finalize RESULT_OK + finish
- `WidgetModule` provides `WidgetStateStore` as a Hilt singleton — bridges manual `getInstance()` to the Hilt graph
- WidgetConfigActivity: sets RESULT_CANCELED immediately in `onCreate`, shows login form or document list, triggers widget `updateAll()` + RESULT_OK on tap
- Google SSO: `GoogleSignInUseCase` injected into Activity for Credential Manager flow; ViewModel receives `idToken` via `loginWithGoogle(idToken)`
- 8 unit tests across all state transitions and event emission — all pass

## Task Commits

1. **Task 1: WidgetConfigViewModel with auth check and document loading** - `2c13abe` (feat, TDD)
2. **Task 2: WidgetConfigActivity with Compose UI** - `7c861c5` (feat)

## Files Created

- `android/app/src/main/java/com/gmaingret/notes/widget/config/WidgetConfigViewModel.kt` — ViewModel with ConfigUiState, auth-on-init, document loading, login, selectDocument
- `android/app/src/main/java/com/gmaingret/notes/widget/config/WidgetConfigActivity.kt` — @AndroidEntryPoint Activity with RESULT_CANCELED, Compose login form, document picker, error view
- `android/app/src/main/java/com/gmaingret/notes/di/WidgetModule.kt` — Hilt @Provides for WidgetStateStore singleton
- `android/app/src/test/java/com/gmaingret/notes/widget/config/WidgetConfigViewModelTest.kt` — 8 unit tests (MockK)

## Decisions Made

- `WidgetModule` bridges `WidgetStateStore.getInstance()` to Hilt's DI graph — the manual singleton pattern doesn't support `@Inject` constructor, so an explicit `@Provides` method is needed
- Google SSO flow separates concerns: `GoogleSignInUseCase` runs in the `Activity` (needs Activity context for Credential Manager), ViewModel only handles the token and post-auth document loading
- `Channel<Unit>` for `documentSelectedEvent` — one-shot semantics prevent event replay on Activity recreation (unlike `SharedFlow` with replay > 0)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Plan 03 stub files were blocking compilation**
- **Found during:** Task 1 (TDD RED phase — test compilation failed due to unresolved WidgetContent references)
- **Issue:** `WidgetContent.kt`, `ReconfigureActionCallback.kt`, `RetryActionCallback.kt`, and `WidgetContentHelperTest.kt` were created by a previous agent run (Plan 03 stubs). They referenced symbols that don't exist yet (`actionRunCallback`, `outlineVariant`) causing compile errors.
- **Fix:** Restored `NotesWidget.kt` to committed state (`git checkout`). Moved Plan 03 stub files to `.planning/phases/13-widget-foundation/plan03-stubs/` — available for Plan 03 executor to review/adapt.
- **Files modified:** Moved 4 Plan 03 stubs out of source tree; restored NotesWidget.kt

**2. [Rule 2 - Missing Critical Functionality] WidgetModule needed for Hilt injection of WidgetStateStore**
- **Found during:** Task 1 implementation
- **Issue:** `WidgetStateStore` uses a manual singleton pattern (no `@Inject` constructor) — Hilt cannot inject it into `WidgetConfigViewModel` without an explicit `@Provides` binding
- **Fix:** Created `WidgetModule.kt` with `@Provides @Singleton fun provideWidgetStateStore(@ApplicationContext context: Context): WidgetStateStore`
- **Files modified:** `android/app/src/main/java/com/gmaingret/notes/di/WidgetModule.kt` (new file)

---

**Total deviations:** 2 auto-fixed (Rule 3 blocking, Rule 2 missing infrastructure)
**Impact on plan:** Minor — no scope change to production logic. Plan 03 stubs preserved in `.planning/phases/13-widget-foundation/plan03-stubs/` for next plan executor.

## Self-Check: PASSED

All key files found on disk. All task commits verified in git log.

---
*Phase: 13-widget-foundation*
*Completed: 2026-03-14*
