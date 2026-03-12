# Phase 9: Android Foundation and Auth - Context

**Gathered:** 2026-03-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Project scaffold with Clean Architecture + Hilt DI, OkHttp auth infrastructure, login/register screens, Google SSO via Credential Manager, and silent re-authentication. Users can register, log in (email/password or Google), and reach a placeholder main screen. A new backend endpoint (POST /auth/google/token) is required for native Google SSO.

</domain>

<decisions>
## Implementation Decisions

### Login/Register UX
- Single screen with Login/Register tabs (not two separate screens)
- OutlinedTextField style for all form fields
- Validation triggers on submit only — no live validation while typing
- Errors clear as user corrects the field
- Client validation errors shown inline under relevant fields (Material 3 isError + supportingText)
- Server errors (wrong password, email taken) also shown inline on the relevant field
- Password field has eye icon visibility toggle (trailingIcon)
- No confirm password field on Register tab — eye toggle is sufficient
- Google SSO button appears on both tabs, below an "or" divider
- Button label: always "Sign in with Google" (per Google branding guidelines)
- Loading state: button text replaced with CircularProgressIndicator, button disabled, fields remain visible
- Server URL hardcoded to https://notes.gregorymaingret.fr (build-time constant, no user config)
- Immediate transition to main screen on successful auth (no success toast)

### Google SSO Flow
- New backend endpoint: POST /auth/google/token — accepts Google ID token, verifies server-side with google-auth-library, returns { accessToken, user } + sets refreshToken cookie
- Android uses Credential Manager API to get Google ID token on-device
- If Credential Manager unavailable (no Play Services): hide Google button entirely, no error
- After user picks Google account: Google button shows spinner (same pattern as email/password)
- SSO errors (network, backend rejection): Snackbar at bottom — "Google sign-in failed. Please try again."
- Google Client ID stored in local.properties (gitignored), read as BuildConfig field via build.gradle.kts

### Post-Auth Landing
- Minimal Scaffold with TopAppBar: app name "Notes", overflow menu with "Log out"
- Hamburger icon shown in TopAppBar but non-functional (placeholder for Phase 10 drawer)
- Body: centered "Welcome, greg@example.com" showing logged-in user's email
- Cold start: brief splash with app icon on brand color while refresh token is verified (~300ms)
  - Token valid -> main screen
  - Token invalid/expired -> login screen (silent, no "session expired" message)
- Logout: immediate (no confirmation dialog) — clear DataStore tokens, POST /auth/logout, navigate to login

### Network Error Handling
- Server unreachable during login: Snackbar with Retry action — "Can't reach server. Check your connection." Form stays filled
- Server unreachable on cold start token refresh: navigate to login screen, show Snackbar
- OkHttp: retryOnConnectionFailure = false (each screen handles own retry)
- Timeouts: connectTimeout = 15s, readTimeout = 15s, writeTimeout = 15s

### Package/Module Structure
- Single :app module with package-based layer separation
- Package name: com.gmaingret.notes
- Layers: data/ (api, model, repository), domain/ (model, repository interfaces, usecases), presentation/ (auth, main, theme), di/
- Use cases as separate classes with operator fun invoke() — ViewModels inject use cases, not repositories
- Error handling: Kotlin Result<T> (not custom sealed class)
- Gradle: Kotlin DSL (.kts) with version catalog (gradle/libs.versions.toml)

### Testing Strategy
- Unit tests for auth logic: use cases, repository, token handling (MockK for mocking)
- Compose UI tests with Robolectric (JVM, no emulator needed)
- GitHub Actions CI: triggers on PR to main + push to phase-* branches
- Local test command: ./gradlew test

### Build & Signing
- Debug builds only in Phase 9 (release signing, ProGuard/R8 deferred)
- Gradle 8.13, AGP 8.9.0, Kotlin 2.1.20, JDK 21
- android/.gitignore for Android-specific build artifacts

### App Identity
- App name: "Notes"
- Material 3 seed color: #2563EB (blue-600) — generates full light/dark palette
- Theme mode: system preference only (isSystemInDarkTheme), no manual toggle
- Launcher icon: adaptive icon — white notepad outline on blue (#2563EB) background
- Edge-to-edge with transparent system bars (status + navigation)
- minSdk 26 (Android 8.0), targetSdk 35, compileSdk 35

### Claude's Discretion
- Exact spacing, typography scale, and Material 3 component customization
- Splash screen implementation details (Android 12+ SplashScreen API)
- Hilt module organization (single NetworkModule vs split)
- DataStore + Tink cookie persistence implementation details
- Exact Compose test coverage scope (which screens, which interactions)

</decisions>

<specifics>
## Specific Ideas

- Auth screen matches the mockup: tabs at top, fields, submit button, "or" divider, Google button at bottom
- Splash screen flow: app icon on brand color -> quick token check -> route to main or login
- Backend research needed: google-auth-library for Node.js to verify ID tokens in the new /auth/google/token endpoint

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- server/src/routes/auth.ts: Existing auth API — register, login, refresh, logout endpoints with established request/response contracts
- server/src/services/authService.ts: Token issuance (15min access, 7-day refresh), cookie settings (httpOnly, secure, sameSite: strict)
- server/src/middleware/auth.ts: Google OAuth strategy with account linking logic (find by googleId, link by email, create new) — same logic needed in new /auth/google/token endpoint

### Established Patterns
- JWT auth: access token in Authorization header (Bearer), refresh token in httpOnly cookie named "refreshToken"
- Google OAuth: server handles account creation/linking transparently (find by googleId -> link by email -> create new)
- Validation: Zod schemas server-side (email: valid email, password: min 8 chars)

### Integration Points
- Backend API base: https://notes.gregorymaingret.fr/api/auth/* (register, login, refresh, logout, + new google/token)
- Android project in /android/ subdirectory (prevents Gradle from scanning node_modules)
- Google Cloud Console project 461904459737: needs Android client OAuth credential added

</code_context>

<deferred>
## Deferred Ideas

- Release signing + ProGuard/R8 — when ready for distribution
- Manual dark mode toggle — deferred to v1.2 (web app doesn't have it either)
- Configurable server URL — not needed for personal self-hosted app
- Instrumented/E2E tests on device — can be added later

</deferred>

---

*Phase: 09-android-foundation-and-auth*
*Context gathered: 2026-03-12*
