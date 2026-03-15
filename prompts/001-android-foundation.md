<objective>
Create a native Android project in `/android` within the existing Notes monorepo. Set up the full project scaffolding with Kotlin/Jetpack Compose, Material Design 3, Hilt dependency injection, Retrofit networking with JWT auth, and Login/Register screens.

At the end of this phase, the user can launch the app, see a Material 3 login/register screen, authenticate with email/password against the existing backend at `https://notes.gregorymaingret.fr`, and land on a placeholder "logged in" screen. Session persists across app restarts via token refresh.
</objective>

<context>
This is a new Android client for an existing Notes web app. The backend (Express + PostgreSQL) stays as-is. The web client is React 19 + TypeScript — we are building a native Android replacement.

Read these files to understand the auth API contract:
- `server/src/routes/auth.ts` — request validation, response shapes, cookie behavior
- `client/src/contexts/AuthContext.tsx` — token refresh flow and session restoration

The backend auth flow:
- `POST /api/auth/login` with `{ email, password }` → returns `{ accessToken, user: { id, email } }` + sets httpOnly refresh cookie
- `POST /api/auth/register` with `{ email, password }` → same response shape, also creates an "Inbox" document
- `POST /api/auth/refresh` (with cookie) → returns `{ accessToken }` or 401
- `POST /api/auth/logout` → clears cookie
- Password minimum: 8 characters
- Auth endpoints rate-limited: 20 requests per 15 minutes
- Error shapes: `{ error: "message" }` or `{ errors: { field: ["message"] } }`
</context>

<requirements>
1. **Project setup**: Create `android/` directory with a standard Android project (Gradle Kotlin DSL, version catalog)
2. **Min SDK 26** (Android 8.0), target latest stable SDK
3. **Jetpack Compose with Material 3** using Compose BOM
4. **Hilt for DI** — `@HiltAndroidApp`, `@AndroidEntryPoint`, modules for network and app-level dependencies
5. **Retrofit + OkHttp** for networking with:
   - `AuthInterceptor` that attaches `Authorization: Bearer <token>` to every request
   - `TokenRefreshAuthenticator` (OkHttp `Authenticator`) that on 401 calls `/api/auth/refresh`, gets new access token, retries. Must use mutex to prevent concurrent refresh races.
   - `PersistentCookieJar` (use `com.github.franmontiel:PersistentCookieJar` library) to persist httpOnly refresh cookies
   - Logging interceptor for debug builds
6. **Token storage** via `EncryptedSharedPreferences` — store access token only (refresh token is in cookies)
7. **Auth screens**: Combined Login/Register screen with tabs (matching web client UX)
   - Material 3 `OutlinedTextField` for email and password
   - Validation: email format, password min 8 chars (client-side + server error display)
   - Loading state on submit button
   - Error display for server validation errors
8. **Auto-login on app start**: Call `/api/auth/refresh` — if valid, navigate to main screen; if 401, show login
9. **Navigation**: Jetpack Navigation Compose with `auth` and `main` routes
10. **Theme**: Material 3 with both light and dark color schemes, respect system dark mode
11. **No Google OAuth for now** — email/password only (Google OAuth requires server-side changes for native redirect)
</requirements>

<implementation>
Package structure:
```
fr.gregorymaingret.notes/
├── NotesApplication.kt
├── MainActivity.kt
├── di/
│   ├── NetworkModule.kt          — OkHttpClient, Retrofit, CookieJar, TokenManager
│   └── AppModule.kt              — EncryptedSharedPreferences
├── data/
│   ├── local/
│   │   └── TokenManager.kt       — get/set/clear access token
│   ├── remote/
│   │   ├── api/
│   │   │   └── AuthApi.kt        — Retrofit interface
│   │   ├── dto/
│   │   │   └── AuthDtos.kt       — LoginRequest, RegisterRequest, AuthResponse
│   │   └── interceptor/
│   │       ├── AuthInterceptor.kt
│   │       └── TokenRefreshAuthenticator.kt
│   └── repository/
│       └── AuthRepository.kt
├── domain/
│   ├── model/
│   │   └── User.kt
│   └── repository/
│       └── IAuthRepository.kt
└── presentation/
    ├── auth/
    │   ├── AuthViewModel.kt
    │   └── LoginScreen.kt
    ├── main/
    │   └── MainScreen.kt         — Placeholder "Welcome, {email}" screen
    ├── navigation/
    │   ├── NavGraph.kt
    │   └── Screen.kt
    └── theme/
        ├── Theme.kt
        ├── Color.kt
        └── Type.kt
```

Key Gradle dependencies (use version catalog `libs.versions.toml`):
- Compose BOM (latest), Material 3, Navigation Compose
- Hilt + hilt-navigation-compose
- Retrofit + converter-gson
- OkHttp + logging-interceptor
- PersistentCookieJar (`com.github.franmontiel:PersistentCookieJar:v1.0.1`)
- security-crypto for EncryptedSharedPreferences
- lifecycle-viewmodel-compose, lifecycle-runtime-compose

Base URL: `https://notes.gregorymaingret.fr` — configure via `BuildConfig.BASE_URL` in `build.gradle.kts`.

The `TokenRefreshAuthenticator` MUST synchronize concurrent 401 responses:
```kotlin
// Pseudocode — only one thread refreshes at a time
synchronized(lock) {
    val currentToken = tokenManager.getAccessToken()
    if (currentToken != failedToken) {
        // Another thread already refreshed — retry with new token
        return request.newBuilder().header("Authorization", "Bearer $currentToken").build()
    }
    // Actually refresh
    val response = refreshCall.execute()
    if (response.isSuccessful) {
        tokenManager.setAccessToken(newToken)
        return request.newBuilder().header("Authorization", "Bearer $newToken").build()
    } else {
        tokenManager.clearAll()
        return null // Force logout
    }
}
```

Use `collectAsStateWithLifecycle()` everywhere to collect StateFlows in Compose — it's lifecycle-aware and avoids wasted recompositions.
</implementation>

<output>
All files go under `./android/` in the repo root. The project must build with `./gradlew assembleDebug` from the `android/` directory.
</output>

<verification>
1. `cd android && ./gradlew assembleDebug` builds without errors
2. Install on emulator, app launches to login screen
3. Register a new account — navigates to placeholder main screen
4. Kill and reopen app — auto-login restores session (no login screen)
5. Login with wrong password — shows error message
6. Register with existing email — shows "already registered" error
7. Check logcat for Retrofit logs showing correct API calls
</verification>

<success_criteria>
- Clean Architecture package structure with proper separation
- Hilt DI wired correctly (no runtime injection errors)
- Auth flow works end-to-end against production backend
- Token refresh works (force a 401 by waiting or manually clearing access token)
- Material 3 theme renders correctly in both light and dark mode
- No crashes, proper error handling on network failures
</success_criteria>
