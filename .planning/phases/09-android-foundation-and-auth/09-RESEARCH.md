# Phase 9: Android Foundation and Auth - Research

**Researched:** 2026-03-12
**Domain:** Android (Kotlin/Compose) — project scaffold, auth screens, Credential Manager Google SSO, OkHttp JWT+cookie auth, DataStore/Tink persistence, backend /auth/google/token endpoint
**Confidence:** HIGH (all critical decisions verified against official docs and authoritative sources)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Login/Register UX**
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

**Google SSO Flow**
- New backend endpoint: POST /auth/google/token — accepts Google ID token, verifies server-side with google-auth-library, returns { accessToken, user } + sets refreshToken cookie
- Android uses Credential Manager API to get Google ID token on-device
- If Credential Manager unavailable (no Play Services): hide Google button entirely, no error
- After user picks Google account: Google button shows spinner (same pattern as email/password)
- SSO errors (network, backend rejection): Snackbar at bottom — "Google sign-in failed. Please try again."
- Google Client ID stored in local.properties (gitignored), read as BuildConfig field via build.gradle.kts

**Post-Auth Landing**
- Minimal Scaffold with TopAppBar: app name "Notes", overflow menu with "Log out"
- Hamburger icon shown in TopAppBar but non-functional (placeholder for Phase 10 drawer)
- Body: centered "Welcome, greg@example.com" showing logged-in user's email
- Cold start: brief splash with app icon on brand color while refresh token is verified (~300ms)
  - Token valid -> main screen
  - Token invalid/expired -> login screen (silent, no "session expired" message)
- Logout: immediate (no confirmation dialog) — clear DataStore tokens, POST /auth/logout, navigate to login

**Network Error Handling**
- Server unreachable during login: Snackbar with Retry action — "Can't reach server. Check your connection." Form stays filled
- Server unreachable on cold start token refresh: navigate to login screen, show Snackbar
- OkHttp: retryOnConnectionFailure = false (each screen handles own retry)
- Timeouts: connectTimeout = 15s, readTimeout = 15s, writeTimeout = 15s

**Package/Module Structure**
- Single :app module with package-based layer separation
- Package name: com.gmaingret.notes
- Layers: data/ (api, model, repository), domain/ (model, repository interfaces, usecases), presentation/ (auth, main, theme), di/
- Use cases as separate classes with operator fun invoke() — ViewModels inject use cases, not repositories
- Error handling: Kotlin Result<T> (not custom sealed class)
- Gradle: Kotlin DSL (.kts) with version catalog (gradle/libs.versions.toml)

**Testing Strategy**
- Unit tests for auth logic: use cases, repository, token handling (MockK for mocking)
- Compose UI tests with Robolectric (JVM, no emulator needed)
- GitHub Actions CI: triggers on PR to main + push to phase-* branches
- Local test command: ./gradlew test

**Build & Signing**
- Debug builds only in Phase 9 (release signing, ProGuard/R8 deferred)
- Gradle 8.13, AGP 8.9.0, Kotlin 2.1.20, JDK 21
- android/.gitignore for Android-specific build artifacts

**App Identity**
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

### Deferred Ideas (OUT OF SCOPE)
- Release signing + ProGuard/R8 — when ready for distribution
- Manual dark mode toggle — deferred to v1.2 (web app doesn't have it either)
- Configurable server URL — not needed for personal self-hosted app
- Instrumented/E2E tests on device — can be added later
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUTH-01 | User can register with email and password | Backend POST /api/auth/register already exists; Android needs RegisterUseCase + ViewModel + UI |
| AUTH-02 | User can log in with email and password | Backend POST /api/auth/login already exists; Android needs LoginUseCase + ViewModel + UI |
| AUTH-03 | User can log in with Google SSO (Credential Manager API) | New backend endpoint POST /api/auth/google/token needed; Android uses androidx.credentials + googleid library |
| AUTH-04 | JWT bearer token injected on all API requests via OkHttp Interceptor | AuthInterceptor reads access token from DataStore, adds Authorization: Bearer header |
| AUTH-05 | Token refresh via httpOnly cookie with Mutex-synchronized Authenticator | Custom CookieJar persists refreshToken cookie; TokenAuthenticator with Mutex prevents concurrent refresh races |
| AUTH-06 | Silent re-login on cold start via persisted refresh cookie | core-splashscreen setKeepOnScreenCondition delays splash until token check; DataStore/Tink survives process death |
</phase_requirements>

---

## Summary

Phase 9 builds a new Android project from scratch inside the `/android/` subdirectory. The backend is already fully implemented (register, login, refresh, logout endpoints) with one exception: a new `POST /auth/google/token` endpoint must be added so the Android Credential Manager flow can exchange a Google ID token for app JWTs. This is a native token exchange — not the redirect-based OAuth flow used by the web app.

The two most technically complex deliverables are the **DataStore/Tink-backed CookieJar** (the critical blocker documented in STATE.md — JavaNetCookieJar is memory-only and does not survive process death) and the **Mutex-synchronized TokenAuthenticator** (must be correct from day one; retrofitting is high-cost). Both are well-understood patterns with reference implementations available.

All version decisions are locked by STATE.md: Gradle 8.13, AGP 8.9.0, Kotlin 2.1.20, Retrofit 3.0.0 + OkHttp 4.12.0, Navigation3 1.0.1, DataStore 1.2.1 + Tink 1.8.0. Do not deviate from these — especially do not upgrade OkHttp to 5.x (Retrofit 3 is incompatible with OkHttp 5.x).

**Primary recommendation:** Implement the DataStore/Tink CookieJar first (Wave 0 prerequisite), then build auth infrastructure (Interceptor + Authenticator), then UI screens, then Credential Manager Google SSO, then the new backend endpoint, then cold start splash flow.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.1.20 | Language | K2 compiler stable; kapt replaced by KSP 2.1.20-1.0.31 by default |
| AGP | 8.9.0 | Android Gradle Plugin | March 2025 stable; requires Gradle 8.11.1+ |
| Gradle | 8.13 | Build system | Feb 2025 stable; required by locked toolchain |
| Jetpack Compose BOM | Latest 2025.x | Compose version alignment | BOM pins all Compose artifact versions consistently |
| Hilt | 2.59.2 | Dependency injection | Google-blessed DI for Android; integrates with ViewModel lifecycle |
| Retrofit | 3.0.0 | HTTP client | Native suspend function support; no Call<T> wrapper needed |
| OkHttp | 4.12.0 | HTTP engine under Retrofit | CRITICAL: Do NOT upgrade to 5.x — Retrofit 3 incompatible |
| Navigation3 | 1.0.1 | Compose navigation | AndroidX Compose-first nav; stable Nov 2025; type-safe with @Serializable |
| DataStore (Preferences) | 1.2.1 | Access token persistence | Async, coroutine-native; replaces deprecated SharedPreferences |
| Tink | 1.8.0 | Encryption for DataStore | StreamingAead encrypts DataStore file; replaces deprecated EncryptedSharedPreferences |
| Credential Manager | 1.6.0-rc02 | Google SSO | Unified API replacing deprecated play-services-auth; no Firebase required |
| googleid | latest | Google ID token extraction | GoogleIdTokenCredential.createFrom(credential.data) |
| core-splashscreen | 1.0.1 | Splash screen compat | Backports Android 12 SplashScreen API to minSdk 26; setKeepOnScreenCondition |
| MockK | 1.13.x | Mocking in unit tests | Kotlin-native mocking; idiomatic coroutine support |
| Robolectric | 4.16 | JVM Compose UI tests | Runs compose tests without emulator; supports API 23–36 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx.serialization | 2.x | NavKey @Serializable for routes | Required for Navigation3 type-safe back stack |
| Gson / Moshi converter | 3.0.0 | JSON deserialization for Retrofit | Use with Retrofit 3 converter-gson or converter-moshi |
| Kotlin Coroutines | 1.9.x | Async | viewModelScope, Mutex for token refresh |
| Compose UI Test | BOM-aligned | Testing | createComposeRule() + Robolectric for JVM UI tests |
| androidx.test.core | 1.6.x | Test activity context | Required for Compose tests with Robolectric |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Navigation3 1.0.1 | Navigation2 with type-safe APIs | Nav3 is Compose-first with SnapshotStateList back stack; Nav2 still works but Nav3 is the future direction |
| DataStore + Tink | EncryptedSharedPreferences | EncryptedSharedPreferences deprecated as of security-crypto 1.1.0-alpha07; Tink is the official replacement |
| Custom CookieJar (DataStore) | JavaNetCookieJar | JavaNetCookieJar is memory-only — dies on process death. Must write custom CookieJar |
| Credential Manager | legacy play-services-auth | play-services-auth deprecated April 2024, removal in 2025. Credential Manager is the replacement |

### Installation
```bash
# Run inside /android/ directory after project is scaffolded
./gradlew dependencies
```

Gradle version catalog (`gradle/libs.versions.toml`) manages all versions — no inline version strings.

---

## Architecture Patterns

### Recommended Project Structure
```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/gmaingret/notes/
│   │   │   │   ├── data/
│   │   │   │   │   ├── api/          # Retrofit service interfaces
│   │   │   │   │   ├── model/        # API response data classes
│   │   │   │   │   └── repository/   # Repository implementations
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/        # Domain entities
│   │   │   │   │   ├── repository/   # Repository interfaces
│   │   │   │   │   └── usecase/      # Use case classes (operator fun invoke)
│   │   │   │   ├── presentation/
│   │   │   │   │   ├── auth/         # AuthScreen, AuthViewModel
│   │   │   │   │   ├── main/         # MainScreen, MainViewModel
│   │   │   │   │   └── theme/        # MaterialTheme, Color, Typography
│   │   │   │   └── di/               # Hilt modules (NetworkModule, etc.)
│   │   │   └── res/
│   │   │       ├── drawable/         # adaptive icon foreground
│   │   │       ├── mipmap-*/         # launcher icons
│   │   │       └── values/           # themes.xml, colors.xml
│   │   └── test/
│   │       └── java/com/gmaingret/notes/
│   │           ├── domain/usecase/   # Use case unit tests
│   │           └── presentation/auth/ # Compose UI tests (Robolectric)
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml            # Version catalog
├── build.gradle.kts                   # Root build file
├── settings.gradle.kts
├── gradle.properties
├── local.properties                   # GOOGLE_WEB_CLIENT_ID (gitignored)
└── .gitignore
```

### Pattern 1: OkHttp Interceptor + Authenticator (AUTH-04, AUTH-05)

**What:** Two-class auth infrastructure. `AuthInterceptor` proactively adds Bearer token to every request. `TokenAuthenticator` handles 401 responses by refreshing the token with a Mutex to prevent concurrent refresh races.

**When to use:** Every request that hits the protected API. Retrofit is configured with both.

```kotlin
// Source: STATE.md arch decisions + hoc081098/Refresh-Token-Sample pattern

// AuthInterceptor — runs before every request
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore  // DataStore wrapper
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStore.getAccessToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

// TokenAuthenticator — only called on 401 response
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApi: AuthApi
) : Authenticator {
    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null

        return runBlocking {
            mutex.withLock {
                // Re-read after acquiring lock — another coroutine may have already refreshed
                val currentToken = tokenStore.getAccessToken()
                val requestToken = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")

                if (currentToken != null && currentToken != requestToken) {
                    // Token was refreshed by another request while we waited for the lock
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                } else {
                    // We need to refresh
                    try {
                        val refreshResponse = authApi.refresh()
                        tokenStore.saveAccessToken(refreshResponse.accessToken)
                        response.request.newBuilder()
                            .header("Authorization", "Bearer ${refreshResponse.accessToken}")
                            .build()
                    } catch (e: Exception) {
                        tokenStore.clearAll()
                        null  // Return null = stop retrying, caller gets 401
                    }
                }
            }
        }
    }
}
```

### Pattern 2: DataStore-backed CookieJar (AUTH-05, AUTH-06)

**What:** Custom OkHttp CookieJar that persists the `refreshToken` httpOnly cookie to DataStore (encrypted with Tink). Survives process death. JavaNetCookieJar does NOT survive process death — this is the critical blocker documented in STATE.md.

**When to use:** The OkHttp client used for the `/auth/refresh` call must use this CookieJar.

```kotlin
// Source: STATE.md blockers + fi5t/secured-datastore pattern + hoc081098/Refresh-Token-Sample

class DataStoreCookieJar @Inject constructor(
    private val context: Context
) : CookieJar {
    // DataStore stores: Map<String, StoredCookie> keyed by "host|name"
    // StoredCookie is a simple data class (not OkHttp Cookie — not serializable)

    data class StoredCookie(
        val name: String, val value: String,
        val expiresAt: Long, val domain: String,
        val path: String, val secure: Boolean,
        val httpOnly: Boolean
    )

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // runBlocking is acceptable here — OkHttp calls this on its thread pool
        runBlocking {
            val toStore = cookies.filter { it.persistent }
                .map { it.toStoredCookie() }
            cookieDataStore.updateData { current ->
                current.toMutableMap().apply {
                    toStore.forEach { put("${url.host}|${it.name}", it) }
                }.toMap()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return runBlocking {
            cookieDataStore.data.first()
                .filterKeys { it.startsWith("${url.host}|") }
                .values
                .filter { it.expiresAt > System.currentTimeMillis() }
                .map { it.toOkHttpCookie() }
        }
    }
}
```

**Tink encryption setup for DataStore:**
```kotlin
// Source: fi5t/secured-datastore, hoc081098/Refresh-Token-Sample

private fun buildEncryptedDataStore(context: Context): DataStore<Preferences> {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    // Use Tink's AesGcmHkdfStreaming for DataStore encryption
    // Wrap the standard PreferencesSerializer with a TinkStreamingAead layer
}
```

### Pattern 3: Navigation3 Type-Safe Routes (AUTH-04, AUTH-06)

**What:** Navigation3 uses `SnapshotStateList<NavKey>` as the back stack. Routes are `@Serializable` objects/data classes. `NavDisplay` renders content based on back stack head.

```kotlin
// Source: https://developer.android.com/jetpack/androidx/releases/navigation3

@Serializable object AuthRoute : NavKey
@Serializable object MainRoute : NavKey

@Composable
fun NotesApp() {
    val backStack = rememberNavBackStack(AuthRoute)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<AuthRoute> {
                AuthScreen(
                    onAuthSuccess = {
                        backStack.clear()
                        backStack.add(MainRoute)
                    }
                )
            }
            entry<MainRoute> {
                MainScreen(
                    onLogout = {
                        backStack.clear()
                        backStack.add(AuthRoute)
                    }
                )
            }
        }
    )
}
```

### Pattern 4: Credential Manager Google SSO (AUTH-03)

**What:** Two-step flow. First try `GetGoogleIdOption(filterByAuthorizedAccounts = true)` (silent — shows bottom sheet if one authorized account). If that throws `NoCredentialException`, fall back to `GetSignInWithGoogleOption` (full picker). Both return a `GoogleIdTokenCredential` containing an ID token to send to the backend.

```kotlin
// Source: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation

suspend fun signInWithGoogle(context: Context): String {
    val credentialManager = CredentialManager.create(context)

    // Step 1: Try silent sign-in with authorized accounts
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setAutoSelectEnabled(false)  // Show picker even with 1 account
        .build()

    val result = try {
        credentialManager.getCredential(
            context = context,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
        )
    } catch (e: NoCredentialException) {
        // Step 2: Fall back to full picker
        val signInOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        credentialManager.getCredential(
            context = context,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
        )
    }

    // Extract ID token
    val credential = result.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return googleIdTokenCredential.idToken  // Send this to POST /auth/google/token
    }
    throw IllegalStateException("Unexpected credential type")
}
```

**Availability check:**
```kotlin
// Source: CONTEXT.md decision — hide Google button if Credential Manager unavailable
val isGoogleSignInAvailable: Boolean = try {
    CredentialManager.create(context)
    true
} catch (e: UnsupportedOperationException) {
    false
}
```

### Pattern 5: New Backend Endpoint POST /auth/google/token

**What:** Server-side Google ID token verification using `google-auth-library` npm package. Same account-linking logic as existing Passport Google strategy. Returns same shape as email/password login.

```typescript
// Source: server/src/middleware/auth.ts account-linking logic + google-auth-library docs
// Add to server/src/routes/auth.ts

import { OAuth2Client } from 'google-auth-library';

const googleClient = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

authRouter.post('/google/token', async (req, res) => {
  const { idToken } = req.body;
  if (!idToken) return res.status(400).json({ error: 'Missing idToken' });

  try {
    const ticket = await googleClient.verifyIdToken({
      idToken,
      audience: process.env.GOOGLE_CLIENT_ID,
    });
    const payload = ticket.getPayload();
    if (!payload?.email || !payload?.sub) {
      return res.status(400).json({ error: 'Invalid Google token' });
    }

    // Same account-linking logic as Passport strategy in auth.ts
    let user = await db.query.users.findFirst({ where: eq(users.googleId, payload.sub) });
    if (!user) {
      user = await db.query.users.findFirst({ where: eq(users.email, payload.email) });
      if (user) {
        await db.update(users).set({ googleId: payload.sub, updatedAt: new Date() })
          .where(eq(users.id, user.id));
      } else {
        const [newUser] = await db.insert(users)
          .values({ email: payload.email, googleId: payload.sub }).returning();
        user = newUser;
        await createInboxIfNotExists(user.id);
      }
    }

    const accessToken = issueAccessToken(user.id);
    setRefreshCookie(res, user.id);
    return res.json({ accessToken, user: { id: user.id, email: user.email } });
  } catch (e) {
    return res.status(401).json({ error: 'Google token verification failed' });
  }
});
```

**Backend dependency to add:**
```bash
npm install google-auth-library
```
Note: `google-auth-library` is not currently in `server/package.json` — must be added.

### Pattern 6: SplashScreen with Token Check (AUTH-06)

**What:** `core-splashscreen:1.0.1` `setKeepOnScreenCondition` holds the splash until a ViewModel completes a token check. The ViewModel posts to a StateFlow; MainActivity observes it.

```kotlin
// Source: https://developer.android.com/develop/ui/views/launch/splash-screen

class MainActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash screen until token check completes
        splashScreen.setKeepOnScreenCondition {
            viewModel.authState.value == AuthState.CHECKING
        }

        setContent {
            NotesTheme {
                val authState by viewModel.authState.collectAsStateWithLifecycle()
                // Route based on authState.value (AUTHENTICATED / UNAUTHENTICATED)
                NotesApp(initialRoute = if (authState == AuthState.AUTHENTICATED) MainRoute else AuthRoute)
            }
        }
    }
}

// SplashViewModel
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val checkAuthUseCase: CheckAuthUseCase
) : ViewModel() {
    val authState = MutableStateFlow(AuthState.CHECKING)

    init {
        viewModelScope.launch {
            authState.value = if (checkAuthUseCase()) AuthState.AUTHENTICATED else AuthState.UNAUTHENTICATED
        }
    }
}
```

### Anti-Patterns to Avoid

- **JavaNetCookieJar for production:** Memory-only — refreshToken cookie lost on process death. AUTH-06 will fail silently.
- **Synchronized(this) in Authenticator:** Blocks the thread pool. Use Kotlin `Mutex` instead.
- **Storing access token in Interceptor as a field:** Race condition on concurrent refresh. Always read from DataStore in each intercept call.
- **OkHttp 5.x:** Retrofit 3.0.0 is incompatible. Locked to OkHttp 4.12.0.
- **play-services-auth (deprecated):** Removed from Google Play Services SDK in 2025. Use Credential Manager only.
- **EncryptedSharedPreferences:** Deprecated as of security-crypto 1.1.0-alpha07. Use DataStore + Tink.
- **Navigation2 `rememberNavController()`:** Navigation3 uses `rememberNavBackStack()`. APIs are different.
- **Blocking the main thread in setKeepOnScreenCondition:** The condition lambda is called on the main thread — must return immediately from a StateFlow, not do I/O.
- **Verifying Google ID token client-side:** Never. Always verify on the server via `google-auth-library verifyIdToken()`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Concurrent token refresh | Manual synchronized flag | OkHttp `Authenticator` + Kotlin `Mutex` | Race conditions are subtle; Mutex handles all concurrent 401 cases correctly |
| Google Sign-In picker | Custom WebView OAuth flow | Credential Manager `GetSignInWithGoogleOption` | Google deprecated all non-Credential Manager approaches; security, UX |
| Token encryption at rest | Custom AES encryption | Tink `StreamingAead` + DataStore | Tink handles key rotation, IV generation, GCM tag; mistakes in crypto are silent |
| Splash screen token check | Custom SplashActivity | `core-splashscreen` `setKeepOnScreenCondition` | SplashActivity anti-pattern; OS-native transition; compat back to minSdk 26 |
| Version management | Inline version strings in build files | `gradle/libs.versions.toml` version catalog | Version catalog prevents version drift, enables IDE suggestions, required by locked decision |
| DI wiring | Manual factory / singleton pattern | Hilt `@HiltViewModel`, `@Inject constructor` | Constructor injection is testable; Hilt handles lifecycle-scoped components |
| ID token verification | JWT library decode manually | `google-auth-library` `OAuth2Client.verifyIdToken()` | Verifies signature, audience, expiry, issuer — all required; manual implementations miss steps |

**Key insight:** The OkHttp Interceptor + Authenticator + Mutex pattern is the industry-standard answer to JWT auth on Android. Any custom solution reinvents it poorly. The DataStore/Tink combination for cookie persistence is equally non-negotiable — it's the only approach that survives process death without using a deprecated API.

---

## Common Pitfalls

### Pitfall 1: JavaNetCookieJar Cookie Loss on Process Death
**What goes wrong:** The app installs, login works, but after the Android OS kills the app process (background, low memory), the refresh cookie is gone. Cold start silently re-routes to login every time.
**Why it happens:** `JavaNetCookieJar` wraps an in-memory `CookieManager`. It never writes to disk.
**How to avoid:** Implement a custom `CookieJar` that reads/writes the refreshToken cookie value to DataStore. Validate against the production server's `Set-Cookie` header format (`refreshToken=...; Path=/; HttpOnly; Secure; SameSite=Strict`) during initial development.
**Warning signs:** Unit tests pass, but AUTH-06 cold start acceptance test fails after task-killing the app.

### Pitfall 2: Double Token Refresh (Concurrent 401s)
**What goes wrong:** Two simultaneous API calls both get 401. Both enter `authenticate()`. Both call `POST /auth/refresh`. The first succeeds; the second uses a stale refresh cookie and gets 401 back from the refresh endpoint. User gets logged out.
**Why it happens:** No synchronization in `TokenAuthenticator`.
**How to avoid:** Use `Mutex.withLock` in `TokenAuthenticator`. After acquiring the lock, compare the token that triggered the 401 with the current token in DataStore. If they differ, another coroutine already refreshed — just retry with the new token.
**Warning signs:** Works fine with sequential requests; intermittently fails when multiple tabs/screens are visible simultaneously.

### Pitfall 3: Retrofit 3 + OkHttp 5.x Incompatibility
**What goes wrong:** `./gradlew assembleDebug` fails with linkage errors or `NoSuchMethodError` at runtime.
**Why it happens:** Retrofit 3.0.0 bundles OkHttp 4.x internally. Upgrading to OkHttp 5.x creates API incompatibility.
**How to avoid:** Lock `com.squareup.okhttp3:okhttp:4.12.0` explicitly in the version catalog. Do not accept Gradle's suggested upgrades.
**Warning signs:** Gradle sync shows an OkHttp version conflict warning, or build succeeds but HTTP calls crash.

### Pitfall 4: Credential Manager Returns Wrong Credential Type
**What goes wrong:** `credential.type` is not `GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL` — instead it's `PasswordCredential` or `PublicKeyCredential` (passkey).
**Why it happens:** Credential Manager can return any saved credential, not just Google accounts.
**How to avoid:** Always check `credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL` before calling `GoogleIdTokenCredential.createFrom()`.
**Warning signs:** `GoogleIdTokenParsingException` or ClassCastException in Google sign-in handler.

### Pitfall 5: SplashScreen Hangs Indefinitely
**What goes wrong:** The splash screen never dismisses; app appears frozen.
**Why it happens:** `setKeepOnScreenCondition` returns `true` indefinitely because the ViewModel's token-check coroutine never completes (exception swallowed, StateFlow never updated).
**How to avoid:** Wrap the token check in a try/catch that always sets `authState.value` to a terminal state. Add a timeout (e.g., 5s) to the DataStore read.
**Warning signs:** Splash hangs on devices with slow storage or on first install where DataStore is empty.

### Pitfall 6: Google Button Shown on Devices Without Play Services
**What goes wrong:** App crashes or shows an ugly error when tapping the Google Sign-In button on a non-GMS device (e.g., Huawei).
**Why it happens:** `CredentialManager.create(context)` may throw on non-GMS devices.
**How to avoid:** Check Credential Manager availability at ViewModel init time and expose a `isGoogleSignInAvailable: Boolean` state to the UI. Locked decision: hide the button entirely if unavailable.
**Warning signs:** Crash reports from non-Google-certified Android devices.

### Pitfall 7: GOOGLE_WEB_CLIENT_ID vs GOOGLE_ANDROID_CLIENT_ID
**What goes wrong:** Credential Manager call succeeds on device but backend `verifyIdToken` returns "Wrong recipient".
**Why it happens:** The `serverClientId` parameter in `GetGoogleIdOption.Builder()` must be the **Web client ID** (from the Google Cloud project), not the Android client ID. The Android client ID is used internally by Google Play Services for the device-level credential; the Web client ID is the `audience` the backend verifies.
**How to avoid:** Use the Web client ID in `local.properties` / `BuildConfig.GOOGLE_WEB_CLIENT_ID`, and pass the same value as `audience` to `verifyIdToken()` on the server.
**Warning signs:** `verifyIdToken` throws "Wrong recipient, payload audience does not match".

---

## Code Examples

Verified patterns from official and authoritative sources:

### Version Catalog (gradle/libs.versions.toml)
```toml
# Source: Official Gradle docs + locked version decisions from STATE.md

[versions]
agp = "8.9.0"
kotlin = "2.1.20"
hilt = "2.59.2"
retrofit = "3.0.0"
okhttp = "4.12.0"
navigation3 = "1.0.1"
datastore = "1.2.1"
tink = "1.8.0"
credentials = "1.6.0-rc02"
splashscreen = "1.0.1"
robolectric = "4.16"
mockk = "1.13.14"
compose-bom = "2025.02.00"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
tink-android = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }
credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentials" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version = "1.1.1" }
splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "splashscreen" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.1.20-1.0.31" }
```

### Robolectric Compose Test Setup
```kotlin
// Source: https://robolectric.org/androidx_test/ + Android official Compose testing docs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginTab_showsEmailAndPasswordFields() {
        composeTestRule.setContent {
            NotesTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }
}
```

```kotlin
// build.gradle.kts test config required for Robolectric
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
```

### BuildConfig for Google Client ID
```kotlin
// Source: CONTEXT.md decisions — local.properties → BuildConfig pattern

// local.properties (gitignored):
// GOOGLE_WEB_CLIENT_ID=24598090780-xxxx.apps.googleusercontent.com

// build.gradle.kts:
android {
    defaultConfig {
        val localProperties = Properties().apply {
            load(rootProject.file("local.properties").inputStream())
        }
        buildConfigField(
            "String", "GOOGLE_WEB_CLIENT_ID",
            "\"${localProperties["GOOGLE_WEB_CLIENT_ID"]}\""
        )
    }
    buildFeatures { buildConfig = true }
}
```

### Material 3 Seed Color Theme
```kotlin
// Source: Material 3 docs — dynamicColorScheme disabled per locked decision

private val seedColor = Color(0xFF2563EB)  // blue-600

@Composable
fun NotesTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(/* generated from seed */)
    } else {
        lightColorScheme(/* generated from seed */)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
// Use Material Theme Builder (m3.material.io) to generate light/dark tokens from #2563EB
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| EncryptedSharedPreferences | DataStore + Tink | security-crypto 1.1.0-alpha07 deprecation (2024) | Must use DataStore + Tink — no fallback |
| play-services-auth Google Sign-In | Credential Manager API | Deprecated April 2024, removed 2025 | Credential Manager is the only supported path |
| Navigation2 rememberNavController | Navigation3 rememberNavBackStack | Nav3 stable Nov 2025 | Compose-first; back stack is plain SnapshotStateList |
| SplashActivity custom screen | core-splashscreen setKeepOnScreenCondition | Android 12 (2021), compat library since | SplashActivity is an anti-pattern; OS handles the transition |
| Retrofit 2 + Call<T> | Retrofit 3 + suspend fun | Retrofit 3.0.0 May 2025 | No CallAdapter needed; suspend functions directly |
| kapt annotation processor | KSP (Kotlin Symbol Processing) | Kotlin 2.1.20 enables KSP by default | KSP 2x faster than kapt for Hilt; kapt still works |

**Deprecated/outdated:**
- `com.google.android.gms:play-services-auth`: Removed in 2025. Do not use.
- `androidx.security:security-crypto` (EncryptedSharedPreferences): Deprecated, not receiving new features.
- JavaNetCookieJar for production cookie persistence: Never was production-ready; in-memory only.
- Navigation2 with `rememberNavController()`: Still works but project is locked to Nav3.

---

## Open Questions

1. **Credential Manager version — rc02 vs stable**
   - What we know: `androidx.credentials:credentials:1.6.0-rc02` is the version found in official docs (March 2026). A stable 1.6.0 may exist by the time this is implemented.
   - What's unclear: Whether 1.6.0 has shipped as a stable release yet.
   - Recommendation: Check Maven Central at plan-time for `androidx.credentials` latest stable; use stable if available, otherwise rc02 is safe for production.

2. **`googleid` library exact version**
   - What we know: The library is `com.google.android.libraries.identity.googleid:googleid`. Version 1.1.1 is widely referenced.
   - What's unclear: Whether a later version has shipped.
   - Recommendation: Check Maven Central at plan-time; use latest stable.

3. **Tink 1.8.0 StreamingAead API stability**
   - What we know: Tink 1.8.0 is locked in STATE.md. The StreamingAead API for DataStore is the community-established pattern.
   - What's unclear: Exact boilerplate for the DataStore `Serializer` wrapper around Tink.
   - Recommendation: Follow the `fi5t/secured-datastore` or `hoc081098/Refresh-Token-Sample` reference implementation exactly. Do not hand-roll the crypto layer.

4. **`google-auth-library` version for backend**
   - What we know: Package is `google-auth-library` on npm. Currently not in `server/package.json`.
   - Recommendation: `npm install google-auth-library` and pin the installed version. `OAuth2Client.verifyIdToken()` is the standard server-side verification method.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 + MockK 1.13.x + Robolectric 4.16 + androidx.compose.ui:ui-test-junit4 |
| Config file | android/app/build.gradle.kts (testOptions.unitTests.isIncludeAndroidResources = true) |
| Quick run command | `./gradlew :app:testDebugUnitTest` (from android/ directory) |
| Full suite command | `./gradlew test` (from android/ directory) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | RegisterUseCase returns success with valid email+password | unit | `./gradlew :app:testDebugUnitTest --tests "*.RegisterUseCaseTest"` | ❌ Wave 0 |
| AUTH-01 | RegisterUseCase returns error for duplicate email | unit | `./gradlew :app:testDebugUnitTest --tests "*.RegisterUseCaseTest"` | ❌ Wave 0 |
| AUTH-02 | LoginUseCase returns success with valid credentials | unit | `./gradlew :app:testDebugUnitTest --tests "*.LoginUseCaseTest"` | ❌ Wave 0 |
| AUTH-02 | LoginUseCase returns failure for wrong password | unit | `./gradlew :app:testDebugUnitTest --tests "*.LoginUseCaseTest"` | ❌ Wave 0 |
| AUTH-03 | GoogleSignInUseCase returns idToken string on success | unit | `./gradlew :app:testDebugUnitTest --tests "*.GoogleSignInUseCaseTest"` | ❌ Wave 0 |
| AUTH-04 | AuthInterceptor adds Bearer header when token present | unit | `./gradlew :app:testDebugUnitTest --tests "*.AuthInterceptorTest"` | ❌ Wave 0 |
| AUTH-04 | AuthInterceptor skips header when no token | unit | `./gradlew :app:testDebugUnitTest --tests "*.AuthInterceptorTest"` | ❌ Wave 0 |
| AUTH-05 | TokenAuthenticator refreshes once for concurrent 401s (Mutex) | unit | `./gradlew :app:testDebugUnitTest --tests "*.TokenAuthenticatorTest"` | ❌ Wave 0 |
| AUTH-05 | TokenAuthenticator returns null (forces logout) on refresh failure | unit | `./gradlew :app:testDebugUnitTest --tests "*.TokenAuthenticatorTest"` | ❌ Wave 0 |
| AUTH-06 | SplashViewModel routes to Main when token valid | unit | `./gradlew :app:testDebugUnitTest --tests "*.SplashViewModelTest"` | ❌ Wave 0 |
| AUTH-06 | SplashViewModel routes to Auth when token invalid/absent | unit | `./gradlew :app:testDebugUnitTest --tests "*.SplashViewModelTest"` | ❌ Wave 0 |
| AUTH-01/02 | AuthScreen shows email + password fields | compose UI (Robolectric) | `./gradlew :app:testDebugUnitTest --tests "*.AuthScreenTest"` | ❌ Wave 0 |
| AUTH-01/02 | AuthScreen shows inline error on failed submit | compose UI (Robolectric) | `./gradlew :app:testDebugUnitTest --tests "*.AuthScreenTest"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "*.{RelevantTestClass}"` (targeted, < 30s)
- **Per wave merge:** `./gradlew test` (full suite from android/ directory)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/RegisterUseCaseTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/LoginUseCaseTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/GoogleSignInUseCaseTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/domain/usecase/SplashViewModelTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/data/remote/interceptor/AuthInterceptorTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/data/remote/interceptor/TokenAuthenticatorTest.kt`
- [ ] `android/app/src/test/java/com/gmaingret/notes/presentation/auth/AuthScreenTest.kt`
- [ ] Android project scaffold itself — entire `/android/` directory does not yet exist
- [ ] Framework install: `./gradlew wrapper --gradle-version=8.13` + AGP 8.9.0 in build.gradle.kts

---

## Sources

### Primary (HIGH confidence)
- `https://developer.android.com/jetpack/androidx/releases/navigation3` — Navigation3 1.0.1 stable, APIs: NavDisplay, rememberNavBackStack, entryProvider, @Serializable NavKey
- `https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation` — Credential Manager implementation: dependencies, GetGoogleIdOption, GetSignInWithGoogleOption, GoogleIdTokenCredential
- `https://developer.android.com/build/releases/agp-8-9-0-release-notes` — AGP 8.9.0 requirements: Gradle 8.11.1+, SDK 35, JDK 17
- `https://developer.android.com/develop/ui/views/launch/splash-screen` — core-splashscreen dependency, windowSplashScreenBackground, setKeepOnScreenCondition behavior
- `https://blog.jetbrains.com/kotlin/2025/03/kotlin-2-1-20-released/` — Kotlin 2.1.20 stable, March 2025, Gradle 7.6.3–8.11 compat, KSP default
- `https://docs.gradle.org/8.13/release-notes.html` — Gradle 8.13 stable February 2025
- `https://github.com/google/dagger/releases` — Hilt 2.59.2 stable February 2025
- `https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html` — Nav3 stable announcement

### Secondary (MEDIUM confidence)
- `https://github.com/hoc081098/Refresh-Token-Sample` — OkHttp Authenticator + Mutex + DataStore/Tink pattern (authoritative reference, widely cited)
- `https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a` — DataStore + Tink migration rationale, December 2025
- `https://github.com/googleapis/google-auth-library-nodejs` — google-auth-library npm package, verifyIdToken usage
- `https://robolectric.org/getting-started/` — Robolectric 4.16 setup, testImplementation dependency, @RunWith annotation
- `https://samsetdev.medium.com/retrofit-3-0-tutorial-key-differences-from-retrofit-2-682f9fd07a9a` — Retrofit 3.0 suspend function support confirmed

### Tertiary (LOW confidence, flagged for validation)
- `credentials:1.6.0-rc02` version — rc status may have changed; verify Maven Central at plan-time
- `googleid:1.1.1` version — verify Maven Central for latest stable at plan-time

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified against official release notes and Maven Central
- Architecture: HIGH — OkHttp Authenticator + Mutex pattern is industry standard with reference implementations; Navigation3 APIs verified from official docs
- Backend changes: HIGH — POST /auth/google/token pattern is identical to existing Passport strategy; google-auth-library is the Google-recommended server-side verification library
- Pitfalls: HIGH — JavaNetCookieJar memory limitation, Retrofit/OkHttp incompatibility, and concurrent refresh race conditions are documented failure modes

**Research date:** 2026-03-12
**Valid until:** 2026-04-12 (stable ecosystem; Credential Manager rc02 status may change sooner)
