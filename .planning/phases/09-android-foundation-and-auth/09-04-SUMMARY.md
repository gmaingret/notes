---
phase: 09-android-foundation-and-auth
plan: "04"
subsystem: auth-ui
tags: [android, kotlin, compose, navigation3, hilt, splashscreen, material3]

# Dependency graph
requires:
  - phase: 09-03
    provides: LoginUseCase, RegisterUseCase, LoginWithGoogleUseCase, CheckAuthUseCase, LogoutUseCase, TokenStore

provides:
  - AuthUiState and AuthTab enum
  - AuthViewModel with inline validation and HTTP error mapping
  - AuthScreen with Login/Register tabs, eye toggle, loading spinner, Snackbar
  - SplashViewModel with authState + coldStartNetworkError flows
  - MainScreen with TopAppBar, logout dropdown, Welcome greeting
  - MainViewModel reading user email from TokenStore
  - Navigation3 routes (AuthRoute with showNetworkError, MainRoute)
  - NotesApp composable wiring the full nav graph
  - MainActivity with SplashScreen cold start flow

affects:
  - 09-05 and later (NotesApp is the root composable; MainScreen is the host for Phase 10 drawer)

# Tech tracking
tech-stack:
  added:
    - hilt-navigation-compose 1.2.0 (hiltViewModel() in composables)
    - lifecycle-runtime-compose 2.9.0 (collectAsStateWithLifecycle, State<T> delegate)
  patterns:
    - Navigation3 rememberNavBackStack with typed routes (AuthRoute/MainRoute)
    - SplashScreen setKeepOnScreenCondition reading StateFlow.value (main-thread safe)
    - Cold-start network error propagation: exception -> coldStartNetworkError=true -> AuthRoute(showNetworkError=true) -> LaunchedEffect Snackbar
    - LaunchedEffect(showNetworkErrorOnStart) one-shot trigger for snackbar on screen entry

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/presentation/auth/AuthUiState.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/auth/AuthViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/auth/AuthScreen.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainScreen.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/main/MainViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/navigation/NavRoutes.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/navigation/NotesApp.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/splash/SplashViewModel.kt
  modified:
    - android/app/src/main/java/com/gmaingret/notes/MainActivity.kt
    - android/app/build.gradle.kts
    - android/gradle/libs.versions.toml

key-decisions:
  - "AuthViewModel error mapping: HttpException 409 + field='email' -> emailError; 401 -> passwordError 'Wrong email or password'; IOException -> snackbar (network)"
  - "AuthRoute is a data class (not object) to carry showNetworkError boolean from splash flow to AuthScreen"
  - "collectAsStateWithLifecycle() used in MainActivity (not collectAsState) for lifecycle-aware state collection per Compose best practice"

requirements-completed:
  - AUTH-01
  - AUTH-02
  - AUTH-06

# Metrics
duration: 9min
completed: "2026-03-12"
---

# Phase 9 Plan 04: Auth UI, Navigation, and SplashScreen Summary

**Complete Compose auth UI with Login/Register tabs, Navigation3 routing, SplashScreen cold start, and MainScreen landing — full auth flow from cold start to logout**

## Performance

- **Duration:** 9 min
- **Started:** 2026-03-12T09:29:03Z
- **Completed:** 2026-03-12T09:38:00Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Implemented the complete user-facing auth flow: cold start splash check, login/register with inline validation, post-auth main screen with user greeting, and logout
- AuthScreen implements all locked UX decisions: tabbed layout at top, OutlinedTextField fields with inline supporting text errors, eye icon toggle, CircularProgressIndicator in submit button (fields remain visible during load), HorizontalDivider "or" divider, conditional Google SSO button, Snackbar with retry action for network errors
- Cold-start network failure propagated from SplashViewModel through MainActivity to AuthRoute(showNetworkError=true) and surfaced via LaunchedEffect-triggered Snackbar in AuthScreen

## Task Commits

Each task was committed atomically:

1. **Task 1: Build AuthScreen with Login/Register tabs and AuthViewModel** - `c4c60be` (feat)
2. **Task 2: Build MainScreen, Navigation3 routing, and SplashScreen cold start flow** - `60cc569` (feat)

## Files Created/Modified

- `presentation/auth/AuthUiState.kt` - Data class with all UI state fields; AuthTab enum
- `presentation/auth/AuthViewModel.kt` - HiltViewModel; submit validation; HTTP 409/401/IOException error mapping; showNetworkError() for cold-start signal
- `presentation/auth/AuthScreen.kt` - Tabbed Compose screen with all locked UX decisions implemented
- `presentation/main/MainViewModel.kt` - Reads TokenStore.getUserEmail() on init; calls LogoutUseCase on logout
- `presentation/main/MainScreen.kt` - Scaffold + TopAppBar with hamburger (Phase 10 placeholder) + overflow logout
- `presentation/navigation/NavRoutes.kt` - AuthRoute(showNetworkError) data class + MainRoute object
- `presentation/navigation/NotesApp.kt` - Navigation3 NavDisplay wiring AuthScreen and MainScreen
- `presentation/splash/SplashViewModel.kt` - CheckAuthUseCase in init; always reaches terminal AuthState
- `MainActivity.kt` - installSplashScreen + setKeepOnScreenCondition + NotesApp with initial route
- `app/build.gradle.kts` - Added hilt-navigation-compose, lifecycle-runtime-compose
- `gradle/libs.versions.toml` - Added hiltNavigationCompose version + 2 library entries

## Decisions Made

- AuthRoute is a data class (not object) to carry `showNetworkError: Boolean` from splash to auth screen — objects cannot hold state, data classes can
- HttpException 409 with `field="email"` maps to `emailError`; 401 maps to `passwordError = "Wrong email or password"`; IOException maps to snackbar — all inline, no separate error screen
- `collectAsStateWithLifecycle()` used in MainActivity rather than `collectAsState()` for lifecycle-aware collection (pauses when activity is backgrounded)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Missing Dependency] hilt-navigation-compose and lifecycle-runtime-compose not in version catalog**
- **Found during:** Task 1 — AuthScreen uses `hiltViewModel()` which requires `hilt-navigation-compose`; MainActivity uses `collectAsStateWithLifecycle()` which requires `lifecycle-runtime-compose`
- **Fix:** Added `hilt-navigation-compose = "1.2.0"` and `lifecycle-runtime-compose` (version 2.9.0 from lifecycleRuntime alias) to `libs.versions.toml` and `app/build.gradle.kts`
- **Files modified:** `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`
- **Commit:** `c4c60be`

**2. [Rule 1 - Bug] Missing `import androidx.compose.runtime.getValue` in MainActivity**
- **Found during:** Task 2 build — `Type 'State<AuthState>' has no method 'getValue(Nothing?, KProperty0<*>)'` compilation error
- **Fix:** Added `import androidx.compose.runtime.getValue` to enable `by` delegation on `collectAsStateWithLifecycle()` result
- **Files modified:** `android/app/src/main/java/com/gmaingret/notes/MainActivity.kt`
- **Commit:** `60cc569`

## Issues Encountered

None beyond the auto-fixed deviations above.

## User Setup Required

None — auth UI connects to the existing backend at `https://notes.gregorymaingret.fr`.

## Self-Check: PASSED

All 9 source files and SUMMARY.md exist on disk. Both task commits (c4c60be, 60cc569) confirmed in git log.

## Next Phase Readiness

- Complete auth flow is functional: register, login, cold start re-auth, logout
- MainScreen is the host for the Phase 10 navigation drawer (hamburger icon is already a non-functional placeholder)
- NotesApp is the root composable and can be extended with additional entries in Phase 10

---
*Phase: 09-android-foundation-and-auth*
*Completed: 2026-03-12*
