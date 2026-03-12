---
phase: 09-android-foundation-and-auth
plan: "03"
subsystem: auth
tags: [android, kotlin, tink, datastore, okhttp, retrofit, hilt, cookiejar, jwt]

# Dependency graph
requires:
  - phase: 09-01
    provides: domain models (User, AuthState), repository interface (AuthRepository), API interface (AuthApi), data models (AuthResponse, RefreshResponse)

provides:
  - Tink AES256-GCM encryption helpers (EncryptedDataStoreFactory)
  - Encrypted token persistence surviving process death (TokenStore)
  - Persistent CookieJar for refreshToken cookie across process death (DataStoreCookieJar)
  - OkHttp AuthInterceptor adding Bearer token to every request
  - Mutex-synchronized TokenAuthenticator handling 401 with concurrent-refresh race prevention
  - AuthRepositoryImpl implementing all AuthRepository methods
  - Five injectable use cases (Login, Register, LoginWithGoogle, CheckAuth, Logout)
  - Hilt DI graph wiring full network + data layers

affects:
  - 09-04 (auth UI ViewModels inject these use cases)
  - 09-05 and later plans (all network calls use this OkHttpClient)

# Tech tracking
tech-stack:
  added:
    - Tink 1.8.0 (AES256-GCM via AndroidKeysetManager, keyset stored in SharedPreferences)
    - DataStore Preferences 1.2.1 (two separate stores: auth_tokens, cookie_jar)
    - dagger.Lazy<AuthApi> (circular DI break for TokenAuthenticator)
  patterns:
    - Encrypt-before-store: individual string values encrypted with Tink before DataStore write
    - Mutex-synchronized OkHttp Authenticator: stale-token check prevents duplicate refresh
    - TokenStore fresh-read: AuthInterceptor reads token from store on every request (no field caching)
    - runBlocking in OkHttp callbacks: acceptable because OkHttp runs on its thread pool

key-files:
  created:
    - android/app/src/main/java/com/gmaingret/notes/data/local/EncryptedDataStoreFactory.kt
    - android/app/src/main/java/com/gmaingret/notes/data/local/TokenStore.kt
    - android/app/src/main/java/com/gmaingret/notes/data/local/DataStoreCookieJar.kt
    - android/app/src/main/java/com/gmaingret/notes/data/api/AuthInterceptor.kt
    - android/app/src/main/java/com/gmaingret/notes/data/api/TokenAuthenticator.kt
    - android/app/src/main/java/com/gmaingret/notes/data/repository/AuthRepositoryImpl.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/LoginUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/RegisterUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/LoginWithGoogleUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/CheckAuthUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/domain/usecase/LogoutUseCase.kt
    - android/app/src/main/java/com/gmaingret/notes/di/NetworkModule.kt
    - android/app/src/main/java/com/gmaingret/notes/di/DataModule.kt
  modified: []

key-decisions:
  - "EncryptedDataStoreFactory uses AndroidKeysetManager (keyset in SharedPreferences, master key in Android Keystore) — standard Tink 1.8.0 Android pattern; not MasterKey from security-crypto (different API)"
  - "DataStoreCookieJar stores one DataStore entry per cookie keyed by 'host|name'; filters by host prefix on loadForRequest to avoid loading all cookies on every request"
  - "TokenAuthenticator stale-token check: after acquiring Mutex, compare store token vs request token — if different, another coroutine already refreshed so retry immediately without another network call"
  - "CheckAuthUseCase calls refresh() (not getAccessToken) to validate the refreshToken cookie is still server-side valid on cold start"

patterns-established:
  - "Encrypt-before-store: use EncryptedDataStoreFactory.encrypt/decrypt with per-field associated data for domain separation"
  - "No token field caching in interceptors: always read from TokenStore to avoid concurrent refresh races"
  - "dagger.Lazy<T> for circular DI: inject Lazy<AuthApi> into TokenAuthenticator to break OkHttpClient -> Authenticator -> AuthApi -> OkHttpClient cycle"

requirements-completed:
  - AUTH-04
  - AUTH-05

# Metrics
duration: 6min
completed: "2026-03-12"
---

# Phase 9 Plan 03: Auth Data Layer Summary

**Tink-encrypted DataStore persistence for tokens and cookies, Mutex-synchronized OkHttp authenticator, AuthRepositoryImpl, and five use cases fully wired via Hilt DI**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-12T09:18:25Z
- **Completed:** 2026-03-12T09:23:58Z
- **Tasks:** 3
- **Files modified:** 13

## Accomplishments

- Solved the STATE.md critical blocker: refreshToken cookie now persists across process death via DataStoreCookieJar backed by encrypted DataStore (previously JavaNetCookieJar held cookies in memory only)
- Implemented Mutex-synchronized TokenAuthenticator with stale-token check, preventing duplicate refresh calls when multiple requests fail with 401 simultaneously
- Wired complete Hilt DI graph: OkHttpClient with all auth infrastructure, Retrofit with hardcoded production base URL, AuthApi, and AuthRepository bound to its implementation

## Task Commits

Each task was committed atomically:

1. **Task 1: Encrypted token persistence and DataStore CookieJar** - `714df62` (feat)
2. **Task 2: OkHttp auth infrastructure, AuthRepository, and use cases** - `eee17cd` (feat)
3. **Task 3: Hilt DI modules for network and data layers** - `de716c3` (feat)

## Files Created/Modified

- `data/local/EncryptedDataStoreFactory.kt` - Tink AES256-GCM encrypt/decrypt helpers via AndroidKeysetManager
- `data/local/TokenStore.kt` - Encrypted access token and user email persistence in DataStore
- `data/local/DataStoreCookieJar.kt` - Persistent OkHttp CookieJar; serializes cookies as encrypted JSON, filters expired cookies on load
- `data/api/AuthInterceptor.kt` - Adds Bearer token header; reads TokenStore fresh on every request
- `data/api/TokenAuthenticator.kt` - Handles 401 with Mutex + stale-token check + dagger.Lazy<AuthApi>
- `data/repository/AuthRepositoryImpl.kt` - Implements AuthRepository; calls saveUserEmail after login/register/googleToken
- `domain/usecase/LoginUseCase.kt` - Wraps authRepository.login
- `domain/usecase/RegisterUseCase.kt` - Wraps authRepository.register
- `domain/usecase/LoginWithGoogleUseCase.kt` - Wraps authRepository.loginWithGoogle
- `domain/usecase/CheckAuthUseCase.kt` - Calls refresh() for cold-start session validation
- `domain/usecase/LogoutUseCase.kt` - Wraps authRepository.logout
- `di/NetworkModule.kt` - Provides OkHttpClient (15s timeouts, retryOnConnectionFailure=false), Retrofit, AuthApi
- `di/DataModule.kt` - Binds AuthRepository -> AuthRepositoryImpl

## Decisions Made

- AndroidKeysetManager used for keyset management (keyset persisted in SharedPreferences, master key in Android Keystore hardware-backed store) — the standard Tink 1.8.0 Android pattern
- DataStoreCookieJar keys cookies as `"host|name"` and filters on host prefix during loadForRequest for O(n) lookup without loading all cookies
- CheckAuthUseCase calls `refresh()` rather than checking local token presence: a refresh RPC proves the refreshToken cookie is still valid server-side, which getAccessToken() alone cannot verify

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Full auth data layer is injectable and compiled
- Plan 04 (auth UI) can inject LoginUseCase, RegisterUseCase, LoginWithGoogleUseCase, CheckAuthUseCase, LogoutUseCase directly into ViewModels
- TokenStore.getUserEmail() is available for MainScreen greeting without an extra network call
- Blocker "Refresh cookie persistence across process death" is resolved

---
*Phase: 09-android-foundation-and-auth*
*Completed: 2026-03-12*
