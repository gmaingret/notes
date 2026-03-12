---
phase: 09-android-foundation-and-auth
plan: "05"
subsystem: auth-google-sso-tests-ci
tags: [android, kotlin, compose, credential-manager, google-sso, testing, mockk, robolectric, github-actions, ci]

# Dependency graph
requires:
  - phase: 09-02
    provides: LoginWithGoogleUseCase, POST /api/auth/google/token backend endpoint
  - phase: 09-04
    provides: AuthViewModel stub (onGoogleSignIn), AuthScreen Google button placeholder, AuthUiState.isGoogleSignInAvailable

provides:
  - GoogleSignInUseCase with Credential Manager two-step flow (silent + full picker fallback)
  - GoogleSignInUseCase.isGoogleSignInAvailable() companion for device capability check
  - AuthViewModel.setGoogleSignInAvailability() and onGoogleSignIn(context, onSuccess)
  - AuthScreen Google button fully wired with context, loading spinner, availability guard
  - Unit tests: LoginUseCaseTest, RegisterUseCaseTest, CheckAuthUseCaseTest (MockK)
  - Compose UI tests: AuthScreenTest (Robolectric, JVM-only)
  - GitHub Actions CI: android-ci.yml (push phase-*, PR main, android/** path filter)

affects:
  - Phase 10+ (GoogleSignInUseCase is the complete Credential Manager integration; no changes needed)

# Tech tracking
tech-stack:
  added:
    - Credential Manager two-step pattern (GetGoogleIdOption silent then GetSignInWithGoogleOption fallback)
    - MockK 1.13.14 (already in version catalog, first usage in tests)
    - Robolectric 4.16 (already in version catalog, first usage in tests)
    - GitHub Actions (android-ci.yml: checkout@v4, setup-java@v4, gradle/actions/setup-gradle@v4)
  patterns:
    - GoogleSignInUseCase companion isGoogleSignInAvailable() avoids holding Activity context in ViewModel
    - setGoogleSignInAvailability() called from AuthScreen LaunchedEffect(Unit) — ViewModel stays context-free
    - testReleaseUnitTest disabled in build.gradle.kts (Compose UI tests need debug test manifest)
    - robolectric.properties pins sdk=34 for consistent Robolectric behavior across test variants

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/GoogleSignInUseCase.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/LoginUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/RegisterUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/domain/usecase/CheckAuthUseCaseTest.kt
    - android/app/src/test/java/com/gmaingret/notes/presentation/auth/AuthScreenTest.kt
    - android/app/src/test/resources/robolectric.properties
    - .github/workflows/android-ci.yml
  modified:
    - android/app/src/main/java/com/gmaingret/notes/presentation/auth/AuthViewModel.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/auth/AuthScreen.kt
    - android/app/build.gradle.kts

key-decisions:
  - "GoogleSignInUseCase.isGoogleSignInAvailable() is a companion fun (not extension on ViewModel) so AuthScreen can call it without ViewModel holding Context"
  - "testReleaseUnitTest disabled: Robolectric createComposeRule() requires debug test manifest (ui-test-manifest is debugImplementation-only)"
  - "CI uses testDebugUnitTest not test (avoids re-running disabled release variant and is more explicit)"

requirements-completed:
  - AUTH-03
  - AUTH-01
  - AUTH-02

# Metrics
duration: 11min
completed: "2026-03-12"
---

# Phase 9 Plan 05: Google SSO, Tests, and CI Summary

**Google SSO integrated via Credential Manager two-step flow, auth use case unit tests and Compose UI tests written with MockK+Robolectric, GitHub Actions CI configured for Android**

## Performance

- **Duration:** 11 min
- **Started:** 2026-03-12T09:42:35Z
- **Completed:** 2026-03-12T09:53:35Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments

- Implemented `GoogleSignInUseCase` with the full two-step Credential Manager flow: silent `GetGoogleIdOption` (filterByAuthorizedAccounts=true) then `NoCredentialException` fallback to full `GetSignInWithGoogleOption` picker. Uses Web client ID from `BuildConfig.GOOGLE_WEB_CLIENT_ID` per research Pitfall 7.
- Added `GoogleSignInUseCase.isGoogleSignInAvailable()` companion function — called from `AuthScreen LaunchedEffect(Unit)` so ViewModel never holds Activity context. Result passed to ViewModel via `setGoogleSignInAvailability()`.
- Rewired `AuthViewModel.onGoogleSignIn(context, onSuccess)` to use `GoogleSignInUseCase` directly (was a placeholder calling `LoginWithGoogleUseCase` with a pre-extracted token). Google button shows loading spinner and is disabled during sign-in.
- Unit tests for `LoginUseCase`, `RegisterUseCase`, `CheckAuthUseCase` using MockK — verify repository delegation and Result success/failure paths.
- Compose UI tests for `AuthScreen` via Robolectric: tab display, tab switching, password field presence, submit button state.
- GitHub Actions CI configured: triggers on `phase-*` push and PR to `main` when `android/**` files change. JDK 21 + Gradle caching + `testDebugUnitTest` + `assembleDebug`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Integrate Google SSO via Credential Manager** - `8626e53` (feat)
2. **Task 2: Write unit tests and Compose UI tests** - `e4b3577` (feat)
3. **Task 3: Set up GitHub Actions CI** - `de44ae4` (chore)

## Files Created/Modified

- `domain/usecase/GoogleSignInUseCase.kt` - Two-step Credential Manager flow; isGoogleSignInAvailable() companion
- `presentation/auth/AuthViewModel.kt` - Replaced LoginWithGoogleUseCase with GoogleSignInUseCase; added setGoogleSignInAvailability(); onGoogleSignIn(Context, onSuccess)
- `presentation/auth/AuthScreen.kt` - Added LocalContext; LaunchedEffect(Unit) for availability; Google button onClick wired; loading spinner on Google button
- `test/.../LoginUseCaseTest.kt` - 2 unit tests with MockK
- `test/.../RegisterUseCaseTest.kt` - 2 unit tests with MockK
- `test/.../CheckAuthUseCaseTest.kt` - 3 unit tests with MockK
- `test/.../AuthScreenTest.kt` - 5 Compose UI tests via Robolectric
- `test/resources/robolectric.properties` - sdk=34 pin
- `app/build.gradle.kts` - Disabled testReleaseUnitTest (Compose UI tests need debug test manifest)
- `.github/workflows/android-ci.yml` - Android CI pipeline

## Decisions Made

- `GoogleSignInUseCase.isGoogleSignInAvailable()` is a companion function (not a method on ViewModel) — this keeps the ViewModel context-free while allowing `AuthScreen` to check availability via `LocalContext.current` in a `LaunchedEffect`
- `testReleaseUnitTest` disabled in `build.gradle.kts` because `createComposeRule()` requires the debug test manifest (`ui-test-manifest` is `debugImplementation`-only); CI uses `testDebugUnitTest` explicitly
- CI uses `testDebugUnitTest` rather than `test` — more explicit and avoids running the disabled release variant task

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Compose UI test "Log In" ambiguous node selector**
- **Found during:** Task 2 — `onNodeWithText("Log In").assertIsDisplayed()` fails because "Log In" appears as both a tab label and button label (2 nodes match)
- **Fix:** Changed to `onAllNodesWithText("Log In")[0]` to select the first node
- **Files modified:** `AuthScreenTest.kt`
- **Commit:** `e4b3577`

**2. [Rule 1 - Bug] Robolectric release build variant fails with `createComposeRule()`**
- **Found during:** Task 2 — `testReleaseUnitTest` fails with "Unable to resolve activity for Intent ... ComponentActivity" because `ui-test-manifest` is `debugImplementation`-only
- **Fix:** Added `afterEvaluate { tasks.named("testReleaseUnitTest") { enabled = false } }` in `build.gradle.kts`; added `robolectric.properties` pinning sdk=34
- **Files modified:** `android/app/build.gradle.kts`, `android/app/src/test/resources/robolectric.properties`
- **Commit:** `e4b3577`

**3. [Rule 3 - Plan Refinement] CI uses `testDebugUnitTest` instead of `test`**
- **Found during:** Task 3 — given the release test variant is disabled, the CI should explicitly run `testDebugUnitTest` rather than `test` to make the intent clear
- **Fix:** CI workflow uses `./gradlew testDebugUnitTest`
- **Files modified:** `.github/workflows/android-ci.yml`
- **Commit:** `de44ae4`

## Issues Encountered

None beyond the auto-fixed deviations above. All tests pass, build succeeds.

## User Setup Required

None. Google SSO requires a real device with a Google account for manual testing — not automated.

## Self-Check: PASSED

All 7 source files and SUMMARY.md exist on disk. All 3 task commits (8626e53, e4b3577, de44ae4) confirmed in git log.

## Phase 9 Readiness

- AUTH-03 (Google SSO) is complete: `GoogleSignInUseCase` handles the full Credential Manager flow end-to-end
- Auth use case tests established: 7 unit tests + 5 Compose UI tests, all passing on JVM via Robolectric
- CI pipeline ready: will trigger automatically on the next push to `phase-9/*` branch
- Phase 9 (Android Foundation and Auth) is now complete — all auth requirements (AUTH-01, AUTH-02, AUTH-03) implemented

---
*Phase: 09-android-foundation-and-auth*
*Completed: 2026-03-12*
