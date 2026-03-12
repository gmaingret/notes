---
phase: 09-android-foundation-and-auth
plan: "01"
subsystem: android
tags: [android, kotlin, compose, hilt, retrofit, okhttp, material3, gradle, clean-architecture]

# Dependency graph
requires: []
provides:
  - Compilable Android project scaffold in /android/ subdirectory
  - Version catalog (libs.versions.toml) with all locked library versions
  - Material 3 theme using #2563EB seed color with light/dark support
  - Adaptive launcher icon (blue background, white notepad foreground)
  - Domain interfaces: AuthRepository, User, AuthState
  - Data contracts: AuthApi (Retrofit), AuthResponse, RefreshResponse, request bodies
  - Clean Architecture directory structure (data/domain/presentation/di)
affects:
  - 09-02 (backend Google token endpoint)
  - 09-03 (data layer implements AuthRepository, AuthApi)
  - 09-04 (presentation uses NotesTheme, domain models)
  - 09-05 (CI/testing uses existing test config)

# Tech tracking
tech-stack:
  added:
    - "Gradle 8.13 (wrapper)"
    - "AGP 8.9.1 (bumped from 8.9.0 to satisfy transitive deps)"
    - "Kotlin 2.1.20 + KSP 2.1.20-1.0.31"
    - "Hilt 2.56.1 (downgraded from 2.59.2 — AGP 8.x compatible)"
    - "Compose BOM 2025.02.00 + Material3"
    - "Retrofit 3.0.0 + OkHttp 4.12.0 + converter-gson 3.0.0"
    - "Navigation3 1.0.1"
    - "DataStore Preferences 1.2.1"
    - "Tink Android 1.8.0"
    - "Credential Manager 1.6.0-rc02 + googleid 1.1.1"
    - "core-splashscreen 1.0.1"
    - "MockK 1.13.14 + Robolectric 4.16"
  patterns:
    - "Version catalog (libs.versions.toml) for all dependency management"
    - "Clean Architecture: data/api, data/model, data/repository, domain/model, domain/repository, domain/usecase, presentation/auth, presentation/main, presentation/theme, di"
    - "Kotlin Result<T> for error handling (not custom sealed class)"
    - "Interface-first design: AuthRepository interface defined before implementation"
    - "BuildConfig field for secrets read from local.properties"

key-files:
  created:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts
    - android/app/src/main/java/com/gmaingret/notes/domain/repository/AuthRepository.kt
    - android/app/src/main/java/com/gmaingret/notes/data/api/AuthApi.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/AuthResponse.kt
    - android/app/src/main/java/com/gmaingret/notes/data/model/RefreshResponse.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/theme/Theme.kt
    - android/app/src/main/java/com/gmaingret/notes/presentation/theme/Color.kt
  modified: []

key-decisions:
  - "Hilt downgraded from 2.59.2 to 2.56.1 (2.59.x requires AGP 9.0+; 2.56.1 is latest AGP-8.x-compatible version)"
  - "AGP bumped from 8.9.0 to 8.9.1 (Navigation3 1.0.1 and activity-compose 1.10.1 require 8.9.1+)"
  - "compileSdk bumped from 35 to 36 (Navigation3 1.0.1 resolves activity-compose 1.12.0 transitively which requires compileSdk 36; Android SDK 36 available locally)"
  - "XML theme base uses android:Theme.Material.NoActionBar (com.google.android.material not a dependency; Compose Material3 applied at runtime via NotesTheme composable)"
  - "GOOGLE_WEB_CLIENT_ID read from local.properties at build time into BuildConfig (gitignored, empty string default)"

patterns-established:
  - "Version catalog: all Android deps pinned in libs.versions.toml, referenced as libs.* in build files"
  - "Interface-first contracts: domain/repository interfaces defined in Plan 01, implementations deferred to Plan 03"
  - "Gson @SerializedName annotations on all API response data classes to ensure JSON field mapping"

requirements-completed:
  - AUTH-04

# Metrics
duration: 18min
completed: "2026-03-12"
---

# Phase 9 Plan 01: Android Foundation and Auth Scaffold Summary

**Compilable Android project with Gradle 8.13, Hilt DI, Compose Material3 (#2563EB theme), and typed auth contracts (AuthRepository interface + AuthApi Retrofit service) for downstream implementation plans**

## Performance

- **Duration:** 18 min
- **Started:** 2026-03-12T08:54:57Z
- **Completed:** 2026-03-12T09:13:27Z
- **Tasks:** 3
- **Files modified:** 28 (all new)

## Accomplishments
- Android project compiles from clean state with `./gradlew assembleDebug` BUILD SUCCESSFUL
- Version catalog with all locked library versions (OkHttp 4.12.0, Retrofit 3.0.0, Kotlin 2.1.20, etc.) in a single source of truth
- Material 3 theme with #2563EB seed color using system dark mode — light/dark color schemes generated from tonal palette
- Adaptive launcher icon: white notepad outline on brand blue background
- Full Clean Architecture directory structure ready for Plan 03 (data layer) and Plan 04 (UI)
- AuthRepository interface and AuthApi Retrofit service define the complete auth operation surface

## Task Commits

Each task was committed atomically:

1. **Task 1: Android project scaffold with Gradle and version catalog** - `211eddc` (feat)
2. **Task 2: Material 3 theme and app identity resources** - `502eb5b` (feat)
3. **Task 3: Domain contracts and data models (interfaces-first)** - `aee9d05` (feat)

## Files Created/Modified
- `android/gradle/libs.versions.toml` - Version catalog with all locked dependency versions
- `android/build.gradle.kts` - Root build with plugin declarations (apply false)
- `android/settings.gradle.kts` - Project includes :app, repository config
- `android/gradle.properties` - AndroidX, Kotlin code style, JVM args
- `android/app/build.gradle.kts` - App module: namespace, compileSdk 36, Hilt+KSP, Compose, BuildConfig
- `android/app/src/main/AndroidManifest.xml` - INTERNET permission, application theme, launcher activity
- `android/app/src/main/java/com/gmaingret/notes/NotesApplication.kt` - @HiltAndroidApp Application class
- `android/app/src/main/java/com/gmaingret/notes/MainActivity.kt` - @AndroidEntryPoint, enableEdgeToEdge, NotesTheme
- `android/app/src/main/java/com/gmaingret/notes/presentation/theme/Color.kt` - LightColorScheme + DarkColorScheme from #2563EB
- `android/app/src/main/java/com/gmaingret/notes/presentation/theme/Theme.kt` - NotesTheme composable (isSystemInDarkTheme)
- `android/app/src/main/java/com/gmaingret/notes/presentation/theme/Type.kt` - Default M3 typography
- `android/app/src/main/java/com/gmaingret/notes/domain/model/User.kt` - data class User(id, email)
- `android/app/src/main/java/com/gmaingret/notes/domain/model/AuthState.kt` - enum AuthState { CHECKING, AUTHENTICATED, UNAUTHENTICATED }
- `android/app/src/main/java/com/gmaingret/notes/domain/repository/AuthRepository.kt` - Full auth interface contract
- `android/app/src/main/java/com/gmaingret/notes/data/api/AuthApi.kt` - Retrofit interface + request body data classes
- `android/app/src/main/java/com/gmaingret/notes/data/model/AuthResponse.kt` - AuthResponse(accessToken, user: UserDto)
- `android/app/src/main/java/com/gmaingret/notes/data/model/RefreshResponse.kt` - RefreshResponse(accessToken)
- `android/app/src/main/res/values/themes.xml` - Theme.Notes.Splash + Theme.Notes base
- `android/app/src/main/res/values/colors.xml` - seed_blue = #2563EB
- `android/app/src/main/res/drawable/ic_launcher_foreground.xml` - White notepad outline vector
- `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` - Adaptive icon definition

## Decisions Made
- Hilt downgraded from 2.59.2 to 2.56.1 because Hilt 2.59.x requires AGP 9.0+; 2.56.1 is the latest version that works with AGP 8.x
- AGP bumped from 8.9.0 to 8.9.1 to satisfy minimum version required by Navigation3 1.0.1 and activity-compose 1.10.1
- compileSdk bumped from 35 to 36 because Navigation3 1.0.1 resolves activity-compose 1.12.0 transitively (requires compileSdk 36); Android SDK 36 is installed locally
- XML theme base uses `android:Theme.Material.NoActionBar` (not Theme.Material3.DayNight.NoActionBar) because the M3 Material XML theme requires the `com.google.android.material` library which is not in the project dependencies — Compose Material3 is applied at runtime via the NotesTheme composable

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Hilt 2.59.2 incompatible with AGP 8.9.0**
- **Found during:** Task 1 (first build attempt)
- **Issue:** Hilt 2.59.x Gradle plugin only works with AGP 9.0+; plan specified Hilt 2.59.2 with AGP 8.9.0
- **Fix:** Downgraded Hilt to 2.56.1 (latest version compatible with AGP 8.x, confirmed from OutlinerGod reference project)
- **Files modified:** android/gradle/libs.versions.toml
- **Verification:** Build succeeds with Hilt 2.56.1
- **Committed in:** 211eddc (Task 1)

**2. [Rule 3 - Blocking] Navigation3 1.0.1 requires AGP 8.9.1+**
- **Found during:** Task 1 (second build attempt after Hilt fix)
- **Issue:** navigation3-ui 1.0.1 and activity-compose 1.10.1 resolve transitive deps requiring AGP 8.9.1; plan had AGP 8.9.0
- **Fix:** Bumped AGP from 8.9.0 to 8.9.1 (patch increment)
- **Files modified:** android/gradle/libs.versions.toml
- **Verification:** AAR metadata check passes
- **Committed in:** 211eddc (Task 1)

**3. [Rule 3 - Blocking] Transitive activity-compose 1.12.0 requires compileSdk 36**
- **Found during:** Task 1 (third build attempt)
- **Issue:** Navigation3 1.0.1 forces activity-compose 1.12.0 transitively; 1.12.0 requires compileSdk 36; plan specified 35
- **Fix:** Bumped compileSdk from 35 to 36 (targetSdk remains 35); Android SDK 36 available locally
- **Files modified:** android/app/build.gradle.kts
- **Verification:** Build succeeds, targetSdk unchanged at 35
- **Committed in:** 211eddc (Task 1)

**4. [Rule 3 - Blocking] Theme.Material3.DayNight.NoActionBar not available without com.google.android.material**
- **Found during:** Task 2 (resource linking)
- **Issue:** Plan specified `Theme.Material3.DayNight.NoActionBar` as XML base theme; requires `com.google.android.material` library which is not a project dependency (project uses Compose Material3, not legacy View-based Material)
- **Fix:** Used `android:Theme.Material.NoActionBar` as XML theme base; Material3 applied at runtime via NotesTheme Compose composable (correct pattern for Compose-only apps)
- **Files modified:** android/app/src/main/res/values/themes.xml
- **Verification:** Resource linking succeeds, theme renders correctly in Compose
- **Committed in:** 502eb5b (Task 2)

---

**Total deviations:** 4 auto-fixed (4 blocking)
**Impact on plan:** All auto-fixes required to resolve version incompatibilities in the locked version set. Hilt 2.56.1 provides identical DI functionality to 2.59.2. compileSdk 36 is a compile-only bump (targetSdk stays 35). No behavioral changes, no scope creep.

## Issues Encountered
The locked version combination in STATE.md contained an incompatibility: Hilt 2.59.2 was specified alongside AGP 8.9.0, but Hilt 2.59.x requires AGP 9.0+. Additionally, the specified libraries collectively require AGP 8.9.1 and compileSdk 36. All resolved via patch-level version bumps.

## User Setup Required
None - no external service configuration required for scaffold/contracts. Google Web Client ID for Plan 03/04 will require local.properties setup (gitignored placeholder already in place).

## Next Phase Readiness
- Plan 02 (backend Google token endpoint): No Android dependency, backend work only
- Plan 03 (data layer): AuthRepository interface and AuthApi Retrofit service are stable contracts to implement against
- Plan 04 (presentation): NotesTheme composable ready, domain models available, Clean Architecture directories created
- All downstream plans can build against stable interfaces without exploration

---
*Phase: 09-android-foundation-and-auth*
*Completed: 2026-03-12*
