# Stack Research

**Domain:** Self-hosted multi-user outliner / PKM web app (Dynalist/Workflowy clone)
**Researched:** 2026-03-09 (v1.0) | Updated: 2026-03-10 (v1.1) | Updated: 2026-03-12 (v2.0 Android) | Updated: 2026-03-14 (v2.1 Widget) | Updated: 2026-03-19 (v2.3 Robustness)
**Confidence:** HIGH (all new package versions verified against npm registry and GitHub API 2026-03-19)

---

## v2.3 Robustness & Quality

**Scope:** This section covers ONLY new additions for the v2.3 milestone. The entire prior stack (React 19, Vite, TypeScript, Express, Drizzle, PostgreSQL, Docker, Nginx, zustand, @dnd-kit, lucide-react, TanStack Query, Android/Compose, widget) is already validated and unchanged.

### New Client Dependencies

| Library | Version | Purpose | Why Recommended |
|---------|---------|---------|-----------------|
| sonner | 2.0.7 | Toast notifications for mutation errors | 2-3KB gzipped; imperative API (`toast.error(...)`) works in TanStack Query `onError` callbacks without hooks or context; `peerDeps: react ^18 \|\| ^19`; 7M+ weekly npm downloads; ships with sensible dark-mode-aware default styles |
| react-error-boundary | 6.1.1 | Declarative error boundaries for React components | Avoids class component boilerplate; provides `useErrorBoundary` hook so event handler and async errors can trigger boundaries; `resetKeys` prop for automatic recovery on navigation; `peerDeps: react ^18 \|\| ^19`; current stable as of 2026-03-19 |

### No New Server Dependencies

All v2.3 server work (standardized error response format, extended undo coverage, env var wiring) uses only already-installed packages: Express, Zod, Drizzle, Vitest.

### No New CI/CD Tools

GitHub Actions workflows use the GitHub-hosted runner with existing tooling (Node 22, npm, vitest, eslint, tsc). No new npm packages required.

---

### CI/CD: GitHub Actions

Two new workflow files needed — no npm packages to install.

| Action | Version | Purpose | Why |
|--------|---------|---------|-----|
| actions/checkout | v6 (v6.0.2) | Checkout repository in workflow | Current stable as of 2025; v4 is outdated |
| actions/setup-node | v6 (v6.3.0) | Set up Node.js with npm cache | Current stable; `cache: 'npm'` built in |
| actions/cache | v5 (v5.0.4) | Cache `node_modules` across runs | Cuts CI time ~60% after first run; optional but recommended |

**Workflow structure (two files):**

`.github/workflows/server-ci.yml` — triggers on `push` to `phase-*` branches and PRs to `main`, path-filtered to `server/**`:
1. `actions/checkout@v6`
2. `actions/setup-node@v6` with `node-version: '22'` and `cache: 'npm'`
3. `npm ci` (in `server/`)
4. `npx tsc --noEmit` — TypeScript type check
5. `npx eslint .` (if eslint config exists; currently server has no eslint config — add `eslint.config.js` to server or skip this step for now)
6. `npx vitest run` — runs existing server tests

Note on server tests and database: current server tests use `supertest` against a real Express app but mock the DB layer. Verify before CI setup — if tests require a live PostgreSQL connection, add a `services: postgres:17-alpine` container to the workflow. If tests are isolated, no service container is needed.

`.github/workflows/client-ci.yml` — triggers on `push` to `phase-*` branches and PRs to `main`, path-filtered to `client/**`:
1. `actions/checkout@v6`
2. `actions/setup-node@v6` with `node-version: '22'` and `cache: 'npm'`
3. `npm ci` (in `client/`)
4. `npx tsc -b` — TypeScript type check (matches existing `npm run build` first step)
5. `npm run lint` — ESLint (eslint v9 flat config already in `client/`)
6. `npx vitest run` — unit tests
7. `npx vite build` — build validation (catches import errors TypeScript misses)

The existing `android-ci.yml` already demonstrates the correct GitHub Actions pattern for this repo — server and client workflows follow the same structure.

---

### Token Refresh Interceptor: No New Library

The existing `ApiClient` class (`client/src/api/client.ts`) is a thin custom fetch wrapper. Extend `ApiClient.request()` directly — no axios migration needed.

**Pattern to add to `ApiClient`:**

```typescript
// Fields to add to ApiClient class
private refreshPromise: Promise<string | null> | null = null;
private onAuthExpired: (() => void) | null = null;

// New method
setAuthExpiredCallback(cb: () => void) {
  this.onAuthExpired = cb;
}

// In request(): after receiving a 401 response, before throwing
if (res.status === 401 && !options._isRetry) {
  // Queue concurrent 401s behind a single refresh attempt
  if (!this.refreshPromise) {
    this.refreshPromise = fetch('/api/auth/refresh', {
      method: 'POST', credentials: 'include'
    })
      .then(r => r.ok ? r.json() : null)
      .then(data => data?.accessToken ?? null)
      .finally(() => { this.refreshPromise = null; });
  }
  const newToken = await this.refreshPromise;
  if (newToken) {
    this.setToken(newToken);
    // Notify AuthContext to sync React state
    return this.request<T>(path, { ...options, _isRetry: true });
  } else {
    this.onAuthExpired?.();
    throw Object.assign(new Error('Session expired'), { status: 401 });
  }
}
```

`AuthContext` registers `apiClient.setAuthExpiredCallback(clearAuth)` on mount. The `_isRetry` flag prevents infinite loops on persistent 401s (e.g. server-side revoked tokens).

**Why no library:** `axios` adds ~45KB and requires migrating all existing `apiClient` calls. The `refresh-fetch` library is minimally maintained and solves the same ~30 lines of logic.

---

### Error Boundary Placement

```
App.tsx
  └── <RootErrorBoundary fallback="Something went wrong — reload page">
        └── Router
              └── AppPage
                    ├── Sidebar (no boundary — sidebar errors should bubble to root)
                    └── <DocumentErrorBoundary resetKeys={[documentId]} fallback="Failed to load document">
                          └── DocumentView → BulletTree → BulletNode (no per-node boundaries)
```

Use `resetKeys={[documentId]}` on the document boundary so navigating to a different document automatically resets the error state without a page reload.

---

### Toast Placement

One `<Toaster />` in `App.tsx` root (outside Router, inside any providers — sonner needs none).

Call sites:
- TanStack Query mutation `onError` callbacks: `toast.error("Failed to save")`
- Undo/redo error responses from server: `toast.error("Undo failed — history unavailable")`
- Do NOT replace the existing `UndoToast` component — it has an interactive "Undo" action button and custom rendering; sonner is for error/success notifications only

---

### Installation

```bash
# Client only — run from client/ directory
npm install sonner react-error-boundary
```

---

### Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| sonner | react-hot-toast | Hot-toast's `.loading` → `.success` promise chain toast is marginally better; either works; sonner preferred for smaller bundle (2-3KB vs 5KB) and better default styling |
| sonner | react-toastify | react-toastify is heavier (8KB+), requires a separate CSS import, older API design; no reason to use it on a React 19 project starting in 2026 |
| react-error-boundary | Hand-written class component | Only if zero-dependency is a hard constraint; the class boilerplate is ~30 lines but lacks the `useErrorBoundary` hook and `resetKeys` reset logic |
| Extend ApiClient | axios | Only if starting greenfield with no existing HTTP layer; migrating an existing fetch-based ApiClient to axios is high-churn for minimal benefit |
| GitHub Actions | CircleCI / GitLab CI | When the repository is not hosted on GitHub; this project is `gmaingret/notes` on GitHub — Actions has zero additional cost or setup |

---

### What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| axios | Project already has a working `ApiClient` wrapping fetch; migrating all call sites costs more than the 401-interceptor is worth | Extend `ApiClient.request()` with ~30 lines of inline refresh logic |
| react-toastify | Heavier bundle, requires separate CSS import, older API | sonner |
| notistack | Material UI-centric; adds MUI peer dependency | sonner |
| actions/setup-node@v4, actions/checkout@v4 | v4 is outdated; v6 is current stable as of 2025 | `@v6` for both |
| Per-bullet-node error boundaries | Too granular; `ErrorBoundary` has render overhead and catching individual bullet render errors provides no useful recovery path | Wrap at DocumentView level with `resetKeys` |
| Server-side error tracking library (Sentry, etc.) | Out of scope for v2.3; self-hosted app with single user doesn't need paid error tracking | Morgan already logs server errors; add structured logging if needed in a future milestone |

---

### Version Compatibility

| Package | Version | Compatible With | Notes |
|---------|---------|-----------------|-------|
| sonner | 2.0.7 | react@19.2.0 | Verified: `peerDeps: "react": "^18.0.0 \|\| ^19.0.0 \|\| ^19.0.0-rc"` |
| react-error-boundary | 6.1.1 | react@19.2.0 | Verified: `peerDeps: "react": "^18.0.0 \|\| ^19.0.0"` |
| actions/checkout | v6.0.2 | ubuntu-latest runner | Current stable; confirmed via GitHub API tags 2026-03-19 |
| actions/setup-node | v6.3.0 | Node 22 LTS | Supports all LTS versions including 22; confirmed via GitHub API tags 2026-03-19 |
| actions/cache | v5.0.4 | npm, ubuntu-latest | Standard cache action; confirmed via GitHub API tags 2026-03-19 |
| Existing: vitest@4.x (client) | vite@7.x | No conflict | Both already in `client/package.json` — no version change |
| Existing: vitest@3.x (server) | tsx@4.x, node@22 | No conflict | Already in `server/package.json` — no version change |

---

### Sources

- npm registry direct query (2026-03-19) — `sonner@2.0.7`, peerDeps confirmed (HIGH confidence)
- npm registry direct query (2026-03-19) — `react-error-boundary@6.1.1`, peerDeps confirmed (HIGH confidence)
- GitHub API `/repos/actions/checkout/tags` (2026-03-19) — v6.0.2 latest (HIGH confidence)
- GitHub API `/repos/actions/setup-node/tags` (2026-03-19) — v6.3.0 latest (HIGH confidence)
- GitHub API `/repos/actions/cache/tags` (2026-03-19) — v5.0.4 latest (HIGH confidence)
- [Knock: Top React notification libraries 2026](https://knock.app/blog/the-top-notification-libraries-for-react) — sonner adoption and download data (MEDIUM confidence)
- [LogRocket: React toast libraries compared 2025](https://blog.logrocket.com/react-toast-libraries-compared-2025/) — sonner vs react-hot-toast analysis (MEDIUM confidence)
- [GitHub: bvaughn/react-error-boundary](https://github.com/bvaughn/react-error-boundary) — API, resetKeys, useErrorBoundary hook (HIGH confidence)
- [React 19 blog](https://react.dev/blog/2024/12/05/react-19) — error boundary API changes, onCaughtError/onUncaughtError (HIGH confidence)
- [LogRocket: CI/CD Node.js GitHub Actions](https://blog.logrocket.com/ci-cd-node-js-github-actions/) — workflow structure patterns (MEDIUM confidence)

---
*v2.3 Robustness & Quality stack additions researched: 2026-03-19*

---

## v2.1 Android Home Screen Widget

This section documents ONLY the new dependencies required for the widget milestone. The existing Android app stack (Kotlin 2.1.20, Compose BOM 2025.02.00, Material 3, Hilt 2.56.1, Retrofit 3.0.0 + OkHttp 4.12.0, DataStore 1.2.1 + Tink 1.8.0, Navigation3 1.0.1, Coil 3.1.0, AGP 8.9.1, compileSdk 36, minSdk 26, Java 21) is validated and unchanged.

### New Dependencies to Add

#### Version Catalog Entries (`android/gradle/libs.versions.toml`)

```toml
[versions]
# Add these — all others already exist
glance = "1.1.1"
workManager = "2.11.1"
hiltWork = "1.3.0"         # androidx.hilt group — distinct from com.google.dagger:hilt 2.56.1

[libraries]
# Add these
glance-appwidget       = { group = "androidx.glance", name = "glance-appwidget",  version.ref = "glance" }
glance-material3       = { group = "androidx.glance", name = "glance-material3",  version.ref = "glance" }
work-runtime-ktx       = { group = "androidx.work",   name = "work-runtime-ktx",  version.ref = "workManager" }
hilt-work              = { group = "androidx.hilt",    name = "hilt-work",         version.ref = "hiltWork" }
hilt-work-compiler     = { group = "androidx.hilt",    name = "hilt-compiler",     version.ref = "hiltWork" }
```

Note: `hilt-work-compiler` uses `androidx.hilt:hilt-compiler`, not to be confused with the existing `hilt-android-compiler` entry (`com.google.dagger:hilt-android-compiler`). Both must be present.

#### `android/app/build.gradle.kts` Additions

```kotlin
dependencies {
    // Widget (Jetpack Glance)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Periodic background refresh
    implementation(libs.work.runtime.ktx)

    // Hilt-WorkManager bridge (@HiltWorker + HiltWorkerFactory)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
}
```

### Core Widget Technologies

| Technology | Version | Artifact | Purpose | Why |
|------------|---------|----------|---------|-----|
| Glance AppWidget | 1.1.1 | `androidx.glance:glance-appwidget` | Widget rendering via Compose | Official Jetpack widget framework. Pulls in `glance` core transitively — do not add `glance` separately. 1.1.1 includes the protobuf security fix (CVE-2024-7254). |
| Glance Material 3 | 1.1.1 | `androidx.glance:glance-material3` | Map app's M3 color scheme into widget | Provides `GlanceMaterialTheme`. Without it, the widget ignores the app's Material 3 theme tokens and renders with default colors. |
| WorkManager KTX | 2.11.1 | `androidx.work:work-runtime-ktx` | 15-min periodic background refresh | Standard Jetpack background scheduler. `CoroutineWorker` from the KTX variant fits Kotlin suspend-based code; minSdk raised to 23 in 2.11.0 — no conflict with project minSdk 26. |
| Hilt Work | 1.3.0 | `androidx.hilt:hilt-work` | Inject repositories into CoroutineWorker | `@HiltWorker` + `@AssistedInject` eliminates manual WorkerFactory wiring. Same Hilt version (2.56.1 Dagger) already in the project. |
| Hilt Work Compiler | 1.3.0 | `androidx.hilt:hilt-compiler` | KSP annotation processing for hilt-work | Required companion to `hilt-work`. Must use `ksp(...)` — project already uses KSP, no kapt involved. |

### Integration Requirements

#### 1. Hilt + WorkManager (Application Class Change)

`hilt-work` requires WorkManager to use `HiltWorkerFactory`. Two changes needed:

**Application class** — create or update to implement `Configuration.Provider`:

```kotlin
@HiltAndroidApp
class NotesApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

**AndroidManifest.xml** — disable the default WorkManager auto-initializer:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

If the app does not yet have a custom `Application` class, one must be created and declared in the manifest `android:name=".NotesApplication"`.

#### 2. Hilt + GlanceAppWidget (EntryPoint Pattern)

`@AndroidEntryPoint` does NOT work on `GlanceAppWidget` — Glance constructs it via reflection with no constructor injection hook. Use `@EntryPoint` + `EntryPointAccessors` inside `provideGlance` instead:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bulletsRepository(): BulletsRepository
    fun tokenStorage(): TokenStorage
}

class NotesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val repo = entryPoint.bulletsRepository()
        // fetch data, then provideContent { ... }
    }
}
```

`@AndroidEntryPoint` CAN be applied to `GlanceAppWidgetReceiver` (it is a standard BroadcastReceiver), but the widget class itself requires the EntryPoint workaround.

#### 3. Widget Refresh Strategy

| Trigger | Mechanism | Notes |
|---------|-----------|-------|
| App makes a change (add/delete bullet) | `NotesWidget().updateAll(context)` from ViewModel after successful API mutation | Suspend function — launch from `viewModelScope` |
| Periodic background (15-min) | `PeriodicWorkRequest` with `CoroutineWorker` annotated `@HiltWorker` | 15 minutes is WorkManager's minimum interval |
| Manual widget refresh action | Same as app change — call `updateAll` from the action `lambda` | Widget actions must be enqueued via `actionRunCallback` |

`updateAll` is a suspend function — never call it from the main thread directly.

### Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| Glance 1.1.1 (stable) | Glance 1.2.0-rc01 | RC since December 2025, no stable promotion yet as of March 2026. The preview API in 1.2.0 is not needed for this milestone. Stable is the correct choice for production. |
| `glance-material3` artifact | Custom manual colors in widget | `GlanceMaterialTheme` handles the M3 `ColorScheme` → widget surface color mapping automatically. Rolling it manually requires understanding how Glance maps `ColorProviders` to `RemoteViews` — significant boilerplate with no benefit. |
| `hilt-work` with `@HiltWorker` | Manual `WorkerFactory` subclass | Manual factory requires a boilerplate class and a binding registration. `@HiltWorker` is the Jetpack-endorsed pattern for Hilt projects and generates the factory via KSP. |
| `work-runtime-ktx` | `work-runtime` (Java flavor) | KTX provides `CoroutineWorker` natively. The project is Kotlin-first throughout; using the Java `ListenableWorker` base class would be a regression. |
| `@EntryPoint` in GlanceAppWidget | Constructor injection | Not possible — Glance calls the no-arg constructor via reflection. The EntryPoint pattern is the official workaround (confirmed in the Glance issue tracker). |

### What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `androidx.glance:glance` (core) separately | Already pulled in transitively by `glance-appwidget` | Just add `glance-appwidget` |
| `androidx.glance:glance-material` (Material 2) | App uses Material 3 throughout | `glance-material3` |
| `kapt` for `hilt-work-compiler` | Project already uses KSP; mixing kapt + KSP causes build time penalties and occasional conflicts | `ksp(libs.hilt.work.compiler)` |
| `work-multiprocess` | Widget runs in the same process as the app; multiprocess support is for isolated-process widgets | Not needed |
| `hilt-navigation-compose` (again) | Already declared in existing `libs.versions.toml` as `hiltNavigationCompose = "1.2.0"` | Use existing entry |
| `androidx.glance:glance-wear-tiles` | Wear OS only; this is a phone home screen widget | Not applicable |
| FCM / push for widget refresh | Explicitly out of scope in PROJECT.md; adds Play Services dependency | WorkManager periodic pull is sufficient |

### Version Compatibility

| Package | Version | Compatible With | Notes |
|---------|---------|-----------------|-------|
| `glance-appwidget:1.1.1` | Compose BOM 2025.02.00 | Compatible | Glance 1.x is not BOM-managed; self-versioned at 1.1.1 |
| `glance-material3:1.1.1` | `material3` from BOM 2025.02.00 | Compatible | Reads M3 `ColorScheme` tokens; must match `glance-appwidget` version |
| `work-runtime-ktx:2.11.1` | minSdk 26 | Compatible | 2.11.0 raised minSdk to 23; project minSdk is 26 — no conflict |
| `hilt-work:1.3.0` | Hilt (Dagger) 2.56.1 | Compatible | `androidx.hilt` is a separate group from `com.google.dagger`; they cooperate via the same Hilt runtime |
| `hilt-work:1.3.0` | KSP 2.1.20-1.0.31 | Compatible | Uses the KSP processor already in the project |
| `hilt-work:1.3.0` | `work-runtime-ktx:2.11.1` | Compatible | Standard combination; `HiltWorkerFactory` delegates to WorkManager's built-in factory |

### Sources

- [Glance release notes](https://developer.android.com/jetpack/androidx/releases/glance) — confirmed 1.1.1 latest stable (October 2024); 1.2.0-rc01 as latest RC (December 2025); no stable 1.2.0 yet (HIGH confidence)
- [Glance setup guide](https://developer.android.com/develop/ui/compose/glance/setup) — confirmed `glance-appwidget` + `glance-material3` as the two required artifacts (HIGH confidence)
- [MVN Repository glance-material3](https://mvnrepository.com/artifact/androidx.glance/glance-material3/1.1.1) — confirmed separate `glance-material3` artifact exists at 1.1.1 (HIGH confidence)
- [WorkManager release notes](https://developer.android.com/jetpack/androidx/releases/work) — confirmed 2.11.1 stable (January 28, 2026); minSdk 23 since 2.11.0 (HIGH confidence)
- [Hilt Jetpack releases](https://developer.android.com/jetpack/androidx/releases/hilt) — confirmed `androidx.hilt:hilt-work` + `hilt-compiler` at 1.3.0 stable (September 2025) (HIGH confidence)
- [Hilt with Jetpack guide](https://developer.android.com/training/dependency-injection/hilt-jetpack) — confirmed `@HiltWorker` + `HiltWorkerFactory` + `Configuration.Provider` pattern (HIGH confidence)
- [Glance create-app-widget guide](https://developer.android.com/develop/ui/compose/glance/create-app-widget) — confirmed GlanceAppWidgetReceiver + GlanceAppWidget structure (HIGH confidence)
- [Glance issue tracker #218520083](https://issuetracker.google.com/issues/218520083) — confirmed no native `@AndroidEntryPoint` support on `GlanceAppWidget`; EntryPoint workaround is the official recommendation (HIGH confidence)
- [Medium — Glance with Hilt](https://medium.com/@debuggingisfun/android-jetpack-glance-with-hilt-6dce38cc9ff6) — EntryPoint pattern implementation example (MEDIUM confidence, consistent with issue tracker)

---

## v2.0 Android Client

This section documents the full stack for the new native Android client milestone. The existing backend (Express + PostgreSQL) is unchanged — the Android client is a new consumer of the same REST API.

### What Already Exists (Backend — Do Not Re-Add or Change)

- Express 5.x REST API at `https://notes.gregorymaingret.fr`
- JWT auth: access token in response body, refresh token via `httpOnly` cookie
- All endpoints documented and working: auth, documents, bullets (tree), search, bookmarks, tags, attachments, comments
- No new backend endpoints are needed for the Android client milestone

### Platform & Build Tooling

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.0 | Primary language | Stable Dec 2025 release; K2 compiler is production-grade; first-class Coroutines and Compose support; all Jetpack libraries target Kotlin 2.x |
| Android Gradle Plugin (AGP) | 9.1.0 | Build system plugin | Latest stable March 2026; requires Gradle 9.1+; introduces built-in Kotlin support (no longer need to apply `org.jetbrains.kotlin.android` separately in AGP 9.0+) |
| KSP (Kotlin Symbol Processing) | 2.3.0-1.0.31 | Annotation processor (replaces KAPT) | Up to 2x faster than KAPT for Hilt, Room, and Retrofit; Hilt 2.48+ and Room 2.6+ both require/prefer KSP; KAPT is being deprecated |
| Android min SDK | 26 (Android 8.0) | Minimum supported API level | Covers 95%+ of active Android devices; required for AES-GCM key generation via Android Keystore without workarounds; EncryptedSharedPreferences replacement (Tink) targets API 24+, but API 26 is the safe floor for modern crypto |
| Android target/compile SDK | 35 (Android 15) | Target platform | Latest stable; required to ship on Google Play |

### Core Android Stack

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Jetpack Compose (via BOM) | BOM 2025.12.00 → Compose 1.10.0 | Declarative UI framework | The standard for new Android UI; Material 3 1.4.0 included in this BOM; use BOM to pin all Compose library versions in sync |
| Material Design 3 | 1.4.0 (via BOM) | Design system | Google's current Material spec; includes NavigationDrawer, TopAppBar, FloatingActionButton, SwipeToDismiss, PullToRefresh, SearchBar, Chip — covers all required Notes UI patterns |
| Hilt (Dagger Hilt) | 2.56 | Dependency injection | Google's official Android DI; integrates directly with ViewModel, WorkManager, Navigation; KSP-based (faster than KAPT); the standard for new Android projects; Koin is simpler but Hilt's compile-time verification is worth the setup cost for a project this size |
| Hilt AndroidX extensions | 1.3.0 | Hilt + Compose ViewModel bridge | `hilt-lifecycle-viewmodel-compose` provides `hiltViewModel()` in Compose without transitive Navigation dependency (API change in 1.3.0); `hilt-navigation-compose` only needed if using Navigation2 + Hilt together |
| Kotlin Coroutines | 1.10.x | Async programming | Built into Kotlin; `viewModelScope` for ViewModel-scoped coroutines; `lifecycleScope` + `repeatOnLifecycle(STARTED)` for UI collection; do NOT collect flows in `LazyColumn` without lifecycle awareness |
| Kotlin Flow / StateFlow | Built into Coroutines | Reactive state streams | StateFlow replaces LiveData for Compose; `viewModel.uiState: StateFlow<UiState>` is the standard MVVM pattern; SharedFlow for one-time events |
| ViewModel + Lifecycle | 2.10.0 | Architecture: MVVM | `lifecycle-viewmodel-ktx:2.10.0` + `lifecycle-runtime-ktx:2.10.0`; `viewModelScope` cancels automatically on ViewModel destruction; survives configuration changes |

### Networking

| Library | Version | Purpose | Why Recommended |
|---------|---------|---------|-----------------|
| Retrofit | 3.0.0 | HTTP client / API layer | Latest stable; now fully Kotlin-native; native coroutine support built-in (no adapter needed); `suspend fun getDocuments(): List<Document>` just works; eliminates Call<T> wrapper boilerplate |
| OkHttp | 4.12.0 | HTTP transport (Retrofit dependency) | Retrofit 3.0.0 depends on OkHttp 4.12 specifically (the first Kotlin-rewrite version); do not upgrade to OkHttp 5.x — Retrofit 3.0 does not yet support it |
| OkHttp Logging Interceptor | 4.12.0 | Debug request/response logging | Same version as OkHttp; add to debug builds only via BuildConfig.DEBUG flag |
| kotlinx.serialization | 1.10.0 | JSON serialization | Kotlin-native; Retrofit 3.0 ships an official `kotlinx-serialization` converter factory (`com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0`); faster and more null-safe than Gson; no reflection |

### Auth Integration with Existing Backend

The existing backend uses JWT access tokens (short-lived, in response body) + refresh token in `httpOnly` cookie. The Android client must handle both.

| Component | Implementation | Why |
|-----------|---------------|-----|
| Access token storage | In-memory only (ViewModel / singleton) | Matches web client security model; never write access token to disk |
| Refresh token storage | OkHttp `CookieJar` (in-memory) backed by encrypted DataStore | httpOnly cookies from the server are received via `Set-Cookie` header; OkHttp's CookieJar interface intercepts and persists these; encrypted DataStore (see below) survives app restarts |
| Token refresh flow | OkHttp `Authenticator` (not Interceptor) | `Authenticator` is called exactly when a 401 is received; handles the race condition of multiple concurrent requests all triggering refresh — only one refresh fires; simpler than manual Interceptor locking |

**Cookie handling note:** The `httpOnly` flag is a browser-only concept. OkHttp receives and sends all cookies regardless of httpOnly flag — this is correct behavior for a native app. The security model shifts to encrypted storage instead.

### Secure Storage

| Library | Version | Purpose | Why Recommended |
|---------|---------|---------|-----------------|
| DataStore Preferences | 1.2.1 (stable) | Persistent key-value storage | Replacement for SharedPreferences; async, Coroutines-native, no ANR risk; used to persist the refresh cookie value across app restarts |
| Google Tink (Android) | 1.8.0 | AES-GCM encryption for DataStore | `EncryptedSharedPreferences` was deprecated with `security-crypto:1.1.0-alpha07` (April 2025); the recommended replacement is DataStore + Tink; Tink uses Android Keystore for key storage; `tink-android:1.8.0` is the latest stable Android artifact |

**Migration note:** Do NOT use `androidx.security:security-crypto` (EncryptedSharedPreferences). It was deprecated in 2025. The `EncryptedSharedPreferences` API no longer receives updates. Use `DataStore + Tink` instead.

### Navigation

| Library | Version | Purpose | Why Recommended |
|---------|---------|---------|-----------------|
| Navigation3 | 1.0.1 | In-app navigation | Stable as of November 2025; Compose-first design; type-safe destinations using data classes (no string routes); direct back-stack control; simpler ViewModel scoping than Navigation 2; for a new Compose-only app starting in 2026, Nav3 is the correct choice |

**Why not Navigation 2 (`navigation-compose:2.9.7`):** Navigation 2's string-based routes are an impedance mismatch with Kotlin's type system. Nav3's type-safe approach (serialize destinations as data classes) eliminates an entire class of runtime crashes. For a greenfield Compose app, Nav3 is the standard going forward.

**Why not Navigation 2 + Hilt:** `hilt-navigation-compose` was designed for Nav2. With Nav3, use `hilt-lifecycle-viewmodel-compose:1.3.0` instead — it provides `hiltViewModel()` without the transitive Nav2 dependency.

### UI / Interaction Libraries

| Library | Version | Purpose | Why Recommended |
|---------|---------|---------|-----------------|
| sh.calvin.reorderable | 3.0.0 | Drag-and-drop reorder in LazyColumn | The standard Compose drag-reorder library; uses `Modifier.animateItem` (Compose 1.7+ API); handles scroll-while-dragging; supports drag handles; required for document list reorder and bullet tree reorder |
| Coil | 3.4.0 | Image loading (attachments) | Coroutine-native image loader; `io.coil-kt.coil3:coil-compose:3.4.0`; needed to display image attachments inline; Glide is the alternative but Coil 3's Kotlin-first API integrates better with Compose and Coroutines |

### Testing

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| JUnit 4 | 4.13.2 | Unit test runner | Standard Android unit testing |
| Mockk | 1.14.x | Kotlin-idiomatic mocking | Prefer over Mockito for Kotlin; handles coroutines via `coEvery`/`coVerify` |
| Turbine | 1.2.x | Flow testing utility | `app.cash.turbine:turbine`; makes StateFlow/SharedFlow testing ergonomic with `awaitItem()` |
| Compose UI Test | 1.10.0 (via BOM) | Instrumented UI tests | `androidx.compose.ui:ui-test-junit4`; semantic tree-based testing |

---

### Gradle Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kotlin = "2.3.0"
agp = "9.1.0"
ksp = "2.3.0-1.0.31"
composeBom = "2025.12.00"
hilt = "2.56"
hiltAndroidx = "1.3.0"
lifecycle = "2.10.0"
navigation3 = "1.0.1"
retrofit = "3.0.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.10.0"
datastore = "1.2.1"
tink = "1.8.0"
reorderable = "3.0.0"
coil = "3.4.0"
turbine = "1.2.0"
mockk = "1.14.0"

[libraries]
# Compose (managed by BOM — no explicit versions)
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-viewmodel-compose = { group = "androidx.hilt", name = "hilt-lifecycle-viewmodel-compose", version.ref = "hiltAndroidx" }

# Lifecycle / ViewModel
lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Navigation3
navigation3-runtime = { group = "androidx.navigation3", name = "navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { group = "androidx.navigation3", name = "navigation3-ui", version.ref = "navigation3" }

# Networking
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# Secure Storage
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
tink-android = { group = "com.google.crypto.tink", name = "tink-android", version.ref = "tink" }

# UI
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

---

### What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Room | No offline mode in scope; PROJECT.md explicitly defers offline; Room adds schema migration complexity for zero gain | Remove from scope; re-evaluate if offline mode is added in v3.0 |
| EncryptedSharedPreferences (`security-crypto`) | Deprecated April 2025 with `security-crypto:1.1.0-alpha07`; no future updates | DataStore + Tink |
| `retrofit2-kotlin-coroutines-adapter` (Jake Wharton) | Retrofit 3.0 has native coroutine support built in; the adapter is redundant | Remove; use `suspend fun` directly in interface |
| KAPT | Being deprecated in favour of KSP; Hilt 2.48+ and all modern Jetpack processors support KSP; KAPT is 2x slower | KSP |
| LiveData | Predates Coroutines/Flow; Compose integrates with StateFlow natively via `collectAsStateWithLifecycle()` | StateFlow + lifecycle-runtime-compose |
| Navigation 2 (`navigation-compose:2.9.7`) | String-based routes are runtime-error-prone; Nav3 is stable and Compose-first | Navigation3 1.0.1 |
| Koin | Simpler to set up but lacks compile-time DI verification; errors surface at runtime | Hilt (compile-time safe) |
| Push notifications (FCM) | Explicitly out of scope in PROJECT.md | Not needed |
| OkHttp 5.x | Retrofit 3.0 depends on OkHttp 4.12; OkHttp 5.x support in Retrofit is pending | OkHttp 4.12.0 |

---

### Integration Points with Existing Backend

| Concern | Approach |
|---------|----------|
| Base URL | Configurable via `BuildConfig` field; default `https://notes.gregorymaingret.fr`; users can change in Settings |
| Auth headers | OkHttp `Interceptor` adds `Authorization: Bearer <accessToken>` from in-memory token holder |
| Cookie persistence | Custom `CookieJar` reads/writes refresh cookie to encrypted DataStore; ensures refresh survives app restart |
| Token refresh | OkHttp `Authenticator` calls `POST /api/auth/refresh` on 401; updates in-memory access token; retries original request |
| File uploads (attachments) | Retrofit `@Multipart` with `MultipartBody.Part`; matches existing `multipart/form-data` API |
| Error handling | Retrofit 3.0 `suspend fun` throws `HttpException` on 4xx/5xx; catch in ViewModel and emit error state |
| HTTPS only | Self-signed certs are not used; production runs Let's Encrypt via Nginx; no custom `TrustManager` needed |

---

### Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Hilt | Koin | Koin for smaller apps or teams unfamiliar with Dagger; Hilt for anything that needs compile-time DI verification |
| Navigation3 | Navigation 2 (`navigation-compose`) | Nav2 if you need Fragment interop or already have a Nav2 codebase; for pure-Compose greenfield, Nav3 is preferred |
| Retrofit 3.0 | Ktor Client | Ktor for Kotlin Multiplatform; Retrofit for Android-only; this project has no KMP requirement |
| kotlinx.serialization | Moshi | Both work with Retrofit 3.0; Moshi is solid but requires separate annotation processor; kotlinx.serialization is Kotlin-native with no reflection |
| DataStore + Tink | `security-crypto` (EncryptedSharedPreferences) | Only for legacy codebases that haven't migrated; deprecated and receiving no updates |
| sh.calvin.reorderable | Custom drag implementation | Custom only if reorderable's API doesn't fit the tree structure; the tree flattening approach (render flat list of BulletNodes) makes LazyColumn drag-reorder viable |

---

### Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| Retrofit 3.0.0 | OkHttp 4.12.0 | Hard dependency — do not use OkHttp 5.x with Retrofit 3.0 |
| Compose BOM 2025.12.00 | Kotlin 2.3.0 | Compose 1.10 requires the Compose Compiler Kotlin plugin (`org.jetbrains.kotlin.plugin.compose`); separate from `kotlin.android` |
| Hilt 2.56 | KSP 2.3.0-1.0.31 | KSP version format is `<kotlin-version>-<ksp-version>` |
| Navigation3 1.0.1 | Compose 1.10.0 | Nav3 is Compose-only; requires Compose 1.7+ for `Modifier.animateItem` |
| Coil 3.4.0 | OkHttp 4.12.0 | Use `coil-network-okhttp` artifact to share the same OkHttp instance as Retrofit |
| sh.calvin.reorderable 3.0.0 | Compose 1.7+ | Uses `Modifier.animateItem` — available from Compose 1.7 (BOM 2024.09+) |
| DataStore 1.2.1 | Kotlin Coroutines 1.10.x | DataStore is Coroutines-native; no compatibility issues with current versions |

---

### Sources

- https://developer.android.com/develop/ui/compose/bom/bom-mapping — BOM 2025.12.00 verified; Compose 1.10.0, Material3 1.4.0 (HIGH)
- https://developer.android.com/jetpack/androidx/releases/navigation3 — Navigation3 1.0.1 stable November 2025 (HIGH)
- https://developer.android.com/jetpack/androidx/releases/lifecycle — lifecycle 2.10.0 stable November 2025 (HIGH)
- https://developer.android.com/jetpack/androidx/releases/hilt — androidx.hilt 1.3.0 stable; API change for hilt-lifecycle-viewmodel-compose (HIGH)
- https://github.com/square/retrofit/releases — Retrofit 3.0.0 stable; OkHttp 4.12 dependency confirmed (HIGH)
- https://developer.android.com/jetpack/androidx/releases/security — EncryptedSharedPreferences deprecated in security-crypto 1.1.0-alpha07 (HIGH)
- https://developer.android.com/topic/libraries/architecture/datastore — DataStore 1.2.1 stable; official migration from SharedPreferences (HIGH)
- https://mvnrepository.com/artifact/com.google.crypto.tink/tink-android — tink-android 1.8.0 latest stable (MEDIUM)
- https://github.com/Calvin-LL/Reorderable — sh.calvin.reorderable 3.0.0 latest; uses Modifier.animateItem (HIGH)
- https://coil-kt.github.io/coil/ — Coil 3.4.0; coil-network-okhttp for shared OkHttp client (HIGH)
- https://developer.android.com/build/releases/agp-9-1-0-release-notes — AGP 9.1.0 March 2026 (HIGH)
- https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/ — Kotlin 2.3.0 stable December 2025 (HIGH)

---

## v1.1 Additions: Mobile & UI Polish

This section documents the NEW libraries needed for the v1.1 milestone only.
The v1.0 foundation stack (Express, Drizzle, React, TanStack Query, Zustand, dnd-kit) is validated and unchanged.

### What Already Exists (Do Not Re-Add)

- React 19.2.0 + Vite 7.3.1 + TypeScript 5.9.3
- @tanstack/react-query 5.x, zustand 5.x, react-router-dom 6.x
- @dnd-kit/core 6.x + @dnd-kit/sortable 8.x (drag-and-drop)
- dompurify, marked, pdfjs-dist
- Custom `gestures.ts` — pure-function swipe handlers, no animation library
- **No icon library** — Unicode characters used today (▶, ▾, ×, etc.)
- **No dark mode** — single light theme, system font stack in `index.css`
- **No font library** — `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`
- **No PWA manifest**
- **No command palette**

### New Technologies for v1.1

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| lucide-react | ^0.577.0 | Replace Unicode icon chars with SVG components | Tree-shakable by default — only imported icons land in the bundle; 1,400+ icons; MIT; consistent stroke-based style; each icon is a typed React component (`<ChevronRight size={16} />`); largest React icon library by npm dependents (10,675+ packages) in 2025 |
| CSS custom properties (built-in) | n/a | Dark mode token system | Zero dependency; define `--bg-primary`, `--text-primary`, etc. on `:root`, override inside `@media (prefers-color-scheme: dark)`; add `color-scheme: light dark` on `<html>` so browser chrome (scrollbars, form inputs) also themes; WCAG AA requires 4.5:1 contrast ratio for normal text — achievable with CSS tokens |
| vite-plugin-pwa | ^1.2.0 | Generate PWA manifest + optional service worker | Zero-config Vite plugin; v1.0.1+ required for Vite 7 (project uses Vite 7.3.1); v1.2.0 is current; generates `manifest.webmanifest`, injects `<link rel="manifest">`, handles icon array; Workbox under the hood for optional precaching |
| cmdk | ^1.1.1 | Quick-open Ctrl+K command palette | Headless and unstyled — styles are 100% yours, matches project's existing plain-CSS approach; built-in fuzzy search via command-score library; ARIA-compliant out of the box (role="combobox", aria-activedescendant, focus trap); React 19 compatible; used by Vercel, Linear, Raycast; actively maintained (kbar alternative last updated 2022) |
| @fontsource-variable/inter | ^5.2.8 | Body/UI font, self-hosted | Variable font = single file covers all weights (400–900); self-hosting eliminates Google Fonts network request — correct for a privacy-first self-hosted app where all assets should be self-served |
| @fontsource-variable/jetbrains-mono | ^5.2.8 | Monospace font for code/tags/chips | Self-hosted variable font; single file for all weights; used for inline code spans, tag chips (`#tag`), and `@mention` chips to distinguish semantic content visually |

### Optional: Swipe Animation Polish

| Library | Version | Purpose | When to Add |
|---------|---------|---------|-------------|
| motion | ^12.35.2 | Spring-physics swipe animations | Only add if CSS `transition` + `transform` is insufficient for iOS-quality swipe-reveal feel; adds ~35KB gzipped; `motion` is the rebranded `framer-motion` package (same code, same maintainer); React 19 compatible |

Recommendation: Attempt CSS-only swipe polish first. The existing `gestures.ts` already tracks touch delta — translating that to CSS `transform: translateX()` with `transition` covers 80% of the feel. Add `motion` only if spring-back physics are required after testing.

---

## Implementation Details

### Dark Mode Strategy

Use CSS custom properties on `:root` with a `@media (prefers-color-scheme: dark)` override block. This requires no JavaScript, no library, and no user toggle (which is out of scope for v1.1).

```css
/* index.css — root tokens */
:root {
  color-scheme: light dark; /* tells browser chrome to theme itself */
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f5;
  --text-primary: #0d0d0d;
  --text-secondary: #555555;
  --border: #e0e0e0;
  --accent: #2563eb;
  /* ... */
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg-primary: #0d0d0d;
    --bg-secondary: #1a1a1a;
    --text-primary: #f0f0f0;
    --text-secondary: #a0a0a0;
    --border: #333333;
    --accent: #60a5fa;
  }
}
```

WCAG AA minimums: 4.5:1 for normal text, 3:1 for large text (18px+ or 14px+ bold). `#f0f0f0` on `#0d0d0d` is approximately 16.5:1 — comfortably AA and AAA.

Note: The CSS `light-dark()` function (87% browser support, May 2024+) is viable for progressive enhancement but not the primary mechanism. The `@media` approach has universal browser support.

### Icon Migration Pattern

Replace Unicode chars with Lucide components at the call site:

| Current | Replace With |
|---------|-------------|
| `▶` (collapsed) | `<ChevronRight size={14} strokeWidth={2} />` |
| `▾` (expanded) | `<ChevronDown size={14} strokeWidth={2} />` |
| `×` / close | `<X size={16} />` |
| `...` / menu | `<MoreHorizontal size={16} />` |
| `+` / new | `<Plus size={16} />` |
| hamburger | `<Menu size={20} />` |
| search | `<Search size={16} />` |
| bookmark | `<Bookmark size={16} />` / `<BookmarkCheck size={16} />` |
| tag | `<Tag size={14} />` |
| attach | `<Paperclip size={16} />` |
| comment | `<MessageSquare size={16} />` |

Import only what you use — Lucide is tree-shaken at import:
```typescript
import { ChevronRight, ChevronDown, X } from 'lucide-react';
```

### Font Loading Pattern

Import variable fonts at the top of `main.tsx` (or `index.css`). Vite bundles the CSS; the woff2 files are served from the same origin.

```typescript
// main.tsx
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
```

```css
/* index.css */
body {
  font-family: 'Inter Variable', -apple-system, BlinkMacSystemFont, sans-serif;
}

code, .tag-chip, .mention-chip {
  font-family: 'JetBrains Mono Variable', 'Courier New', monospace;
}
```

The `-apple-system` fallback chain remains — fonts load progressively and the fallback covers the flash before fonts render.

### PWA Config Pattern

Add to `vite.config.ts`. For v1.1 scope (manifest + home screen install only, no offline caching):

```typescript
import { VitePWA } from 'vite-plugin-pwa';

plugins: [
  react(),
  VitePWA({
    registerType: 'autoUpdate',
    workbox: {
      globPatterns: [], // no precaching — offline is out of scope
    },
    manifest: {
      name: 'Notes',
      short_name: 'Notes',
      description: 'Personal outliner',
      theme_color: '#2563eb',
      background_color: '#ffffff',
      display: 'standalone',
      start_url: '/',
      icons: [
        { src: '/pwa-192.png', sizes: '192x192', type: 'image/png' },
        { src: '/pwa-512.png', sizes: '512x512', type: 'image/png' },
        { src: '/pwa-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    },
  }),
]
```

Two PNG icons (192x192 and 512x512) must be placed in `client/public/`. Generate from a single source SVG.

### cmdk Integration Pattern

cmdk is fully unstyled. Render it as a modal overlay triggered by `Ctrl+K` / `Cmd+K`:

```typescript
import { Command } from 'cmdk';

// Wrap in a dialog/overlay with backdrop
// Command.Dialog or Command + custom modal shell both work
// Command.Input — the search field (fuzzy filtering is automatic)
// Command.List + Command.Group + Command.Item — the results
// Command.Empty — shown when no results match
```

Key wiring: listen for `Ctrl+K` at the document level (avoid conflict with browser default — `Ctrl+K` focuses address bar in Firefox; use `e.preventDefault()` to override). Close on `Escape` (cmdk handles this).

---

## Installation (v1.1 Additions)

Run from `client/` directory:

```bash
# Icons
npm install lucide-react

# Fonts (self-hosted variable)
npm install @fontsource-variable/inter @fontsource-variable/jetbrains-mono

# Command palette
npm install cmdk

# PWA plugin (dev/build time only)
npm install -D vite-plugin-pwa

# Animation — defer until swipe polish phase; add only if CSS transitions are insufficient
npm install motion
```

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| lucide-react | @phosphor-icons/react | Prefer Phosphor if per-icon weight variants (thin/light/regular/bold/fill/duotone) are needed; heavier package; no strong reason to diverge from Lucide for an outliner's functional icons |
| lucide-react | @heroicons/react | Heroicons has only ~292 icons vs Lucide's 1,400+; coupled to Tailwind's design language; insufficient icon range for this app |
| CSS custom properties | Tailwind `dark:` variant | Only if Tailwind already in the project — it is not; adding Tailwind solely for dark mode is unjustified complexity |
| CSS custom properties | next-themes | Designed for Next.js; not usable in Vite projects |
| CSS custom properties | styled-components ThemeProvider | CSS-in-JS adds a runtime dependency; CSS variables handle this natively with zero overhead |
| @fontsource-variable/* | Google Fonts CDN `<link>` | Google Fonts is simpler but introduces a third-party network request — wrong for a privacy-first self-hosted app where all assets should be self-served |
| @fontsource-variable/* | @fontsource/* (static) | Static packages ship separate files per weight (400, 500, 600, 700…); variable package is a single file covering the full weight range — smaller total payload |
| cmdk | kbar | kbar provides a batteries-included action registry with breadcrumb navigation; last published 2022 (maintenance concern); cmdk is actively maintained and headless is exactly what this project needs |
| cmdk | Custom implementation | Not worth the accessibility engineering; cmdk's ARIA implementation (combobox role, focus trap, activedescendant) would need to be re-implemented from scratch |
| vite-plugin-pwa | Manual `manifest.json` in `public/` | Manual manifest works for installability; use it only if the 10-line vite.config.ts change is somehow undesirable; vite-plugin-pwa also handles icon injection and future service worker |
| CSS transitions | motion (Framer Motion) | Prefer CSS first; motion only justified if spring-physics drag-while-animate is required |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| react-icons | Bundles entire icon libraries unless using deep path imports (`react-icons/fi/...`); easy footgun; no consistent TypeScript prop types | lucide-react (tree-shaken at named import) |
| Google Fonts `<link>` tag | Third-party DNS + TLS handshake per page load; Google can log font requests — violates privacy-first self-hosted design philosophy | @fontsource-variable/* |
| @fontsource/inter (non-variable) | Ships one CSS+woff2 per weight; 4+ weights = 4+ files vs one variable file | @fontsource-variable/inter |
| next-themes | Next.js specific; will not work in Vite | CSS `@media (prefers-color-scheme)` + optional Zustand toggle |
| kbar | Last meaningful update was 2022; maintenance status unclear; cmdk is the active equivalent | cmdk |
| framer-motion (old package name) | Rebranded to `motion`; the old `framer-motion` npm name still works but points to the same code — use the canonical `motion` package name | motion |

---

## Stack Patterns by Variant

**For dark mode v1.1 (system-only, no toggle):**
- Use `@media (prefers-color-scheme: dark)` on `:root` in `index.css`
- Add `color-scheme: light dark` to `<html>` element
- No JavaScript, no user toggle, no dependency
- Result: app follows OS setting automatically

**For dark mode future (user toggle):**
- Add `data-theme` attribute on `<html>` set by a Zustand store
- CSS: `[data-theme="dark"] { --bg-primary: #0d0d0d; ... }`
- Persist to `localStorage`; initialize from `matchMedia('(prefers-color-scheme: dark)')` if no stored preference
- The `light-dark()` CSS function is an alternative but at 87% support it needs a fallback anyway

**For PWA v1.1 (manifest + install only, no offline):**
- Set `globPatterns: []` in workbox config to skip precaching
- Result: users get "Add to Home Screen" prompt; app still requires network
- Offline caching is explicitly out of scope (see PROJECT.md)

**For icons — migration order:**
- Start with high-frequency components: BulletNode chevrons, Sidebar menu trigger, toolbar buttons
- Replace inline Unicode with Lucide components; keep `size` and `strokeWidth` consistent per component type (toolbar = 16px, primary nav = 20px)

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| vite-plugin-pwa@1.2.0 | vite@7.3.1 | Vite 7 support added in v1.0.1; v1.2.0 is current as of March 2026 |
| vite-plugin-pwa@1.2.0 | @vitejs/plugin-react@5.1.1 | No conflicts; both are Vite 7 ecosystem plugins |
| cmdk@1.1.1 | react@19.2.0 | cmdk 1.x targets React 18+; confirmed compatible with React 19 |
| lucide-react@0.577.0 | react@19.2.0 | Each icon is a forwardRef component; React 19 compatible |
| motion@12.35.2 | react@19.2.0 | Framer Motion 12.x (`motion` package); React 19 compatible |
| @fontsource-variable/*@5.2.8 | vite@7.3.1 | Plain CSS import with woff2 assets; no bundler-specific integration |

---

## Sources

- https://www.npmjs.com/package/lucide-react — version 0.577.0 verified 2026-03-10 (HIGH)
- https://lucide.dev/guide/packages/lucide-react — official Lucide React docs, tree-shaking confirmed (HIGH)
- https://www.npmjs.com/package/vite-plugin-pwa — version 1.2.0 verified 2026-03-10 (HIGH)
- https://github.com/vite-pwa/vite-plugin-pwa/releases — Vite 7 support from v1.0.1 confirmed in release notes (HIGH)
- https://www.npmjs.com/package/cmdk — version 1.1.1 verified 2026-03-10 (HIGH)
- https://www.npmjs.com/package/motion — version 12.35.2 verified 2026-03-10 (HIGH)
- https://www.npmjs.com/package/@fontsource-variable/inter — version 5.2.8 verified 2026-03-10 (HIGH)
- https://www.npmjs.com/package/@fontsource-variable/jetbrains-mono — version 5.2.8 verified 2026-03-10 (HIGH)
- https://fontsource.org/docs/getting-started/variable — variable fonts preferred over static for multi-weight (MEDIUM)
- https://caniuse.com/mdn-css_types_color_light-dark — light-dark() at 87% browser support as of 2025 (HIGH)
- https://developer.mozilla.org/en-US/docs/Web/CSS/color-scheme — color-scheme property for browser chrome theming (HIGH)
- WebSearch: cmdk vs kbar comparison 2025 — kbar maintenance status, cmdk ARIA compliance (MEDIUM)

---

## v1.0 Foundation Stack (Validated, Unchanged)

The original v1.0 stack research below remains valid. No changes to the backend, database, auth, editor, or drag-and-drop layers are needed for any milestone above.

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Node.js | 22 LTS | Backend runtime | LTS through 2027; Express 5 requires >= v18; use 22 for longevity |
| Express.js | 5.2.x | HTTP framework | v5 is now stable (April 2025); async error handling built in; no need for express-async-errors wrapper |
| PostgreSQL | 16 or 17 | Primary database | Adjacency list + WITH RECURSIVE is first-class in PG; ltree extension available for path queries |
| Drizzle ORM | 0.40.0 (pinned) | DB access layer | Code-first TypeScript schema; pinned at 0.40.0 — 0.45.x has a broken npm package (missing index.cjs); do not upgrade until resolved |
| React | 19.2.x | Frontend UI | Largest ecosystem; TanStack Query 5.x integrates cleanly; React 19 concurrent features help with complex tree renders |
| Vite | 7.3.x | Frontend build | De facto standard since CRA deprecation; instant HMR |
| TypeScript | 5.9.x | Type safety | Applied to both server and client |

### Auth Libraries

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| passport | 0.7.x | Auth middleware orchestrator | De facto Node.js auth; pluggable strategies |
| passport-local | 1.0.x | Email/password strategy | Standard, stable, battle-tested |
| passport-google-oauth20 | 2.0.x | Google OAuth 2.0 strategy | Official Jared Hanson package; well-maintained |
| passport-jwt | 4.0.x | JWT verification strategy | Verifies Bearer tokens on protected routes |
| jsonwebtoken | 9.0.x | JWT sign/verify | 18M+ weekly downloads; stable |
| bcryptjs | 5.x | Password hashing | Pure JS bcrypt (no native bindings = no compile step in Docker); use cost factor 12 |

### Data Fetching & State

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @tanstack/react-query | 5.x | Server state, mutations, caching | Built-in optimistic updates via onMutate/onError rollback |
| Zustand | 5.x | Local UI state | Lightweight; manages expand/collapse state, focused bullet, undo stack UI state |

### Drag-and-Drop

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @dnd-kit/core | 6.x | Drag-and-drop primitives | Accessible, pointer/touch/keyboard; better mobile support than react-beautiful-dnd (deprecated) |
| @dnd-kit/sortable | 8.x | Sortable lists | SortableContext for bullet reordering within a parent |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| zod | 3.x | Runtime validation | Validate all request bodies; share schemas with client |
| multer | 1.4.x | File upload middleware | Multipart form-data parsing to Docker volume |
| dompurify | 3.x | HTML sanitization | Sanitize markdown output before rendering |
| marked | 17.x | Markdown rendering | Parse bullet content markdown to HTML |

### Key Architecture Decisions (Validated in Production)

| Decision | Outcome |
|----------|---------|
| Adjacency list + FLOAT8 fractional position | Locked in schema — no migrations needed; correct choice |
| Single flat SortableContext over whole tree | Cross-level drag works; nested SortableContexts blocked it |
| AccessToken in React context only (not localStorage) | XSS protection maintained |
| Plain contenteditable per bullet (not ProseMirror) | Tree model conflicts with ProseMirror document model; simpler is better |
| gestures.ts uses closure-based state (not useRef) | Pure functions, unit testable without React |
| Drizzle _journal.json must list ALL migrations | migrate() uses journal for discovery; missing entries = silently skipped SQL |

---

*Stack research for: self-hosted multi-user outliner (Dynalist/Workflowy clone)*
*v1.0 researched: 2026-03-09 | v1.1 additions: 2026-03-10 | v2.0 Android: 2026-03-12 | v2.1 Widget: 2026-03-14 | v2.3 Robustness: 2026-03-19*
