# Architecture Research

**Domain:** Native Android client for an existing Express REST API outliner app
**Researched:** 2026-03-12
**Confidence:** HIGH — based on direct inspection of all server route files, middleware, and web client patterns

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                      EXISTING BACKEND (unchanged)                    │
│  Express + Drizzle ORM + PostgreSQL                                  │
│  https://notes.gregorymaingret.fr                                    │
│                                                                      │
│  /api/auth/register|login|refresh|logout                            │
│  /api/documents  /api/bullets  /api/search                          │
│  /api/bookmarks  /api/attachments  /api/tags                        │
│  /api/undo       /api/redo       /api/undo/status                   │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTPS / REST
                                │ Authorization: Bearer <jwt>
                                │ Cookie: refreshToken (httpOnly)
                     ┌──────────▼──────────┐
                     │   OkHttp + Retrofit  │
                     │  AuthInterceptor     │
                     │  TokenAuthenticator  │
                     │  JavaNetCookieJar    │
                     └──────────┬──────────┘
┌──────────────────────────────────────────────────────────────────────┐
│                      ANDROID CLIENT — /android                        │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                   PRESENTATION LAYER                         │    │
│  │  ┌────────────┐ ┌──────────────┐ ┌────────────────────────┐ │    │
│  │  │  AuthVM    │ │ DocumentsVM  │ │    BulletTreeVM         │ │    │
│  │  │ StateFlow  │ │  StateFlow   │ │  StateFlow<TreeUiState> │ │    │
│  │  └─────┬──────┘ └──────┬───────┘ └──────────┬─────────────┘ │    │
│  │        │               │                     │               │    │
│  │  ┌─────▼──────┐ ┌──────▼───────┐ ┌──────────▼─────────────┐ │    │
│  │  │LoginScreen │ │DrawerScreen  │ │   BulletTreeScreen       │ │    │
│  │  │RegisterScr │ │(doc list)    │ │   (LazyColumn rows)      │ │    │
│  │  └────────────┘ └──────────────┘ └────────────────────────┘ │    │
│  ├─────────────────────────────────────────────────────────────┤    │
│  │                     DOMAIN LAYER                             │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │    │
│  │  │  AuthRepo    │  │  DocumentRepo│  │  BulletRepo      │  │    │
│  │  │  (interface) │  │  (interface) │  │  (interface)     │  │    │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘  │    │
│  │  ┌──────────────────────────┐  ┌──────────────────────────┐ │    │
│  │  │  FlattenTreeUseCase      │  │  ComputeDragProjection   │ │    │
│  │  │  (port of web flattenTree│  │  UseCase (port of web    │ │    │
│  │  │  — pure Kotlin function) │  │  computeDragProjection)  │ │    │
│  │  └──────────────────────────┘  └──────────────────────────┘ │    │
│  ├─────────────────────────────────────────────────────────────┤    │
│  │                      DATA LAYER                              │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │    │
│  │  │AuthRepoImpl  │  │DocumentRepo  │  │  BulletRepoImpl  │  │    │
│  │  │EncryptedPrefs│  │  Impl        │  │  (Retrofit calls)│  │    │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘  │    │
│  │  ┌─────────────────────────────────────────────────────┐    │    │
│  │  │         NotesApiService (Retrofit interface)         │    │    │
│  │  │         ~30 endpoints mirroring Express routes       │    │    │
│  │  └─────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| `NotesApiService` | Retrofit interface mirroring all Express routes | `@GET`, `@POST`, `@PATCH`, `@DELETE` with suspend functions |
| `AuthInterceptor` | Attach `Authorization: Bearer <token>` to every request | OkHttp `Interceptor`, reads token from `TokenStore` |
| `TokenAuthenticator` | On 401, call `/api/auth/refresh`, retry with new token | OkHttp `Authenticator`, `@Synchronized` to prevent races |
| `JavaNetCookieJar` | Persist and re-send `refreshToken` httpOnly cookie | `java.net.CookieManager` with `CookiePolicy.ACCEPT_ALL` |
| `TokenStore` | Hold access token in memory + `EncryptedSharedPreferences` | Hilt singleton, survives process death |
| `AuthRepoImpl` | Login, register, logout, silent refresh on cold start | Calls `NotesApiService`, writes to `TokenStore` |
| `BulletRepoImpl` | All bullet CRUD, indent/outdent, move, undo checkpoint | Calls `NotesApiService`, returns domain models |
| `BulletTreeViewModel` | Flattened tree state, drag projection, keyboard actions | `StateFlow<TreeUiState>` with embedded `List<FlatBullet>` |
| `FlattenTreeUseCase` | Port of web `flattenTree` — recursive DFS respecting collapse | Pure Kotlin function, no Android imports |
| `ComputeDragProjectionUseCase` | Port of web drag projection — layout coords + horizontal delta | Pure Kotlin function, no Android imports |

## Recommended Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── java/fr/gregorymaingret/notes/
│   │       ├── data/
│   │       │   ├── api/
│   │       │   │   ├── NotesApiService.kt      # Retrofit interface — all endpoints
│   │       │   │   ├── dto/
│   │       │   │   │   ├── AuthDto.kt          # LoginRequest, RegisterRequest, AuthResponse
│   │       │   │   │   ├── DocumentDto.kt      # DocumentDto, CreateDocumentRequest, etc.
│   │       │   │   │   └── BulletDto.kt        # BulletDto, PatchBulletRequest, MoveBulletRequest
│   │       │   │   └── NetworkModule.kt        # Hilt: OkHttp + Retrofit + CookieJar providers
│   │       │   ├── auth/
│   │       │   │   ├── AuthInterceptor.kt      # Injects Bearer token header
│   │       │   │   ├── TokenAuthenticator.kt   # 401 -> refresh -> retry
│   │       │   │   └── TokenStore.kt           # EncryptedSharedPreferences wrapper
│   │       │   └── repository/
│   │       │       ├── AuthRepositoryImpl.kt
│   │       │       ├── DocumentRepositoryImpl.kt
│   │       │       ├── BulletRepositoryImpl.kt
│   │       │       ├── SearchRepositoryImpl.kt
│   │       │       └── UndoRepositoryImpl.kt
│   │       ├── domain/
│   │       │   ├── model/
│   │       │   │   ├── Document.kt             # Domain model — no Retrofit/JSON annotations
│   │       │   │   ├── Bullet.kt               # Mirrors server schema fields
│   │       │   │   └── FlatBullet.kt           # Bullet + depth for LazyColumn rendering
│   │       │   ├── repository/
│   │       │   │   ├── AuthRepository.kt       # Interfaces only — no impl
│   │       │   │   ├── DocumentRepository.kt
│   │       │   │   ├── BulletRepository.kt
│   │       │   │   ├── SearchRepository.kt
│   │       │   │   └── UndoRepository.kt
│   │       │   └── usecase/
│   │       │       ├── FlattenTreeUseCase.kt   # buildBulletMap + flattenTree (port from web)
│   │       │       └── ComputeDragProjectionUseCase.kt
│   │       ├── presentation/
│   │       │   ├── auth/
│   │       │   │   ├── AuthViewModel.kt
│   │       │   │   ├── LoginScreen.kt
│   │       │   │   └── RegisterScreen.kt
│   │       │   ├── documents/
│   │       │   │   ├── DocumentsViewModel.kt
│   │       │   │   └── DocumentsDrawer.kt      # ModalNavigationDrawer content
│   │       │   ├── bullets/
│   │       │   │   ├── BulletTreeViewModel.kt
│   │       │   │   ├── BulletTreeScreen.kt     # LazyColumn of FlatBullet rows
│   │       │   │   └── BulletRow.kt            # Single row composable
│   │       │   └── search/
│   │       │       ├── SearchViewModel.kt
│   │       │       └── SearchScreen.kt         # Bottom sheet or full screen
│   │       ├── ui/
│   │       │   └── theme/
│   │       │       ├── Color.kt
│   │       │       ├── Theme.kt                # Material 3 lightColorScheme/darkColorScheme
│   │       │       └── Type.kt
│   │       ├── navigation/
│   │       │   └── AppNavGraph.kt              # Single file for all Navigation Compose routes
│   │       └── di/
│   │           ├── AppModule.kt                # Singleton bindings (TokenStore, etc.)
│   │           └── RepositoryModule.kt         # @Binds interfaces to implementations
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

### Structure Rationale

- **data/api/dto/**: DTOs live in the data layer, not domain. They carry JSON annotations and are API-shaped. Domain models are kept separate so the API can change without touching domain logic.
- **data/auth/**: The three auth infrastructure classes form a tight cluster that references each other (`TokenAuthenticator` reads from `TokenStore`, `AuthInterceptor` reads from `TokenStore`). Grouping prevents circular import hunting.
- **domain/usecase/**: `FlattenTreeUseCase` and `ComputeDragProjectionUseCase` are pure functions ported from the web client. They must live in domain (no Android imports) to be unit-testable without a device.
- **presentation/**: One sub-package per screen. ViewModels and composables co-located — they are always changed together.
- **navigation/**: One file. Navigation Compose graphs should not be scattered across feature packages because routes reference each other and the graph must be centrally composable.

## Architectural Patterns

### Pattern 1: OkHttp Interceptor + Authenticator (not a single interceptor)

**What:** Two separate OkHttp hooks handle auth. `AuthInterceptor` attaches the Bearer token to outgoing requests. `TokenAuthenticator` handles 401 responses by calling `/api/auth/refresh` and retrying.

**When to use:** Whenever the API uses short-lived JWT access tokens with a separate refresh mechanism. The split is mandatory — a single interceptor that both attaches and refreshes creates infinite retry loops when the refresh itself returns 401.

**Trade-offs:** Slightly more code than one interceptor. The `@Synchronized` on `TokenAuthenticator.authenticate()` is essential to prevent concurrent refresh races when multiple in-flight requests all 401 simultaneously.

**Example:**
```kotlin
// AuthInterceptor.kt
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else chain.request()
        return chain.proceed(request)
    }
}

// TokenAuthenticator.kt
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val apiService: Lazy<NotesApiService> // Lazy to break DI cycle
) : Authenticator {
    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // If token already refreshed by another concurrent thread, just retry
        val currentToken = tokenStore.accessToken
        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (currentToken != null && currentToken != requestToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentToken")
                .build()
        }
        // Refresh — cookie sent automatically by CookieJar
        return try {
            val refreshed = runBlocking { apiService.get().refresh() }
            tokenStore.accessToken = refreshed.accessToken
            response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshed.accessToken}")
                .build()
        } catch (e: Exception) {
            tokenStore.clear() // Force re-login
            null
        }
    }
}
```

### Pattern 2: JavaNetCookieJar for httpOnly Refresh Cookie

**What:** The server sets a `refreshToken` httpOnly cookie on login/register/refresh responses (`Set-Cookie: refreshToken=...; HttpOnly; Path=/api/auth`). OkHttp must persist and re-send this cookie automatically. `JavaNetCookieJar` with a standard `CookieManager` handles this with zero custom code.

**When to use:** Any backend using httpOnly cookies for token delivery. The cookie is server-controlled — never read or write it manually from Android code.

**Trade-offs:** Standard `CookieManager` holds cookies in memory only. The cookie is lost on process death. On cold start the app must attempt a silent `/api/auth/refresh`; if the cookie is gone (process killed since last login), the call returns 401 and the user is sent to the Login screen. This matches the web client's `useEffect` silent refresh on mount — same failure mode.

**Example:**
```kotlin
// NetworkModule.kt (Hilt @Module)
@Provides @Singleton
fun provideCookieJar(): CookieJar {
    val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    CookieHandler.setDefault(cookieManager)
    return JavaNetCookieJar(cookieManager)
}

@Provides @Singleton
fun provideOkHttpClient(
    authInterceptor: AuthInterceptor,
    tokenAuthenticator: TokenAuthenticator,
    cookieJar: CookieJar
): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(authInterceptor)
    .authenticator(tokenAuthenticator)
    .cookieJar(cookieJar)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
```

### Pattern 3: StateFlow<UiState> + collectAsStateWithLifecycle

**What:** ViewModels expose a sealed `UiState` class via `StateFlow`. Composables collect with `collectAsStateWithLifecycle()` (from `androidx.lifecycle:lifecycle-runtime-compose`), not `collectAsState()`. The lifecycle-aware variant stops collection when the app is backgrounded, preventing unnecessary work.

**When to use:** All screens. This is the canonical Jetpack Compose + Kotlin Coroutines pattern.

**Trade-offs:** More boilerplate than `mutableStateOf` inside composables, but correctly isolates UI logic from business logic and survives configuration changes (ViewModel outlives the Composable).

**Example:**
```kotlin
// BulletTreeViewModel.kt
sealed class TreeUiState {
    object Loading : TreeUiState()
    data class Success(
        val visibleItems: List<FlatBullet>,
        val documentTitle: String,
        val canUndo: Boolean,
        val canRedo: Boolean
    ) : TreeUiState()
    data class Error(val message: String) : TreeUiState()
}

@HiltViewModel
class BulletTreeViewModel @Inject constructor(
    private val bulletRepo: BulletRepository,
    private val flattenTree: FlattenTreeUseCase,
    private val undoRepo: UndoRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TreeUiState>(TreeUiState.Loading)
    val uiState: StateFlow<TreeUiState> = _uiState.asStateFlow()
}

// BulletTreeScreen.kt
@Composable
fun BulletTreeScreen(viewModel: BulletTreeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state) {
        is TreeUiState.Loading -> CircularProgressIndicator()
        is TreeUiState.Success -> BulletList((state as TreeUiState.Success).visibleItems)
        is TreeUiState.Error -> ErrorView((state as TreeUiState.Error).message)
    }
}
```

### Pattern 4: Optimistic Updates with Rollback

**What:** Before making an API call, immediately update the local `StateFlow` with the expected result. If the API call fails, restore the snapshot of the previous state and emit a one-shot error event.

**When to use:** All bullet mutations — content edits, complete toggle, delete, indent/outdent, collapse toggle. Prevents perceptible lag on every user action.

**Trade-offs:** A snapshot of the previous state must be held locally during the async operation. Rollback logic adds ~5 lines per mutation. Without this, every tap takes 200-400ms to feel responsive over mobile networks.

**Example:**
```kotlin
fun toggleComplete(bulletId: String) {
    val current = _uiState.value as? TreeUiState.Success ?: return
    val snapshot = current // snapshot for rollback

    // Optimistic: flip isComplete in the flat list immediately
    val optimistic = current.visibleItems.map {
        if (it.id == bulletId) it.copy(isComplete = !it.isComplete) else it
    }
    _uiState.value = current.copy(visibleItems = optimistic)

    viewModelScope.launch {
        try {
            val updated = bulletRepo.patchBullet(bulletId, isComplete = !originalIsComplete)
            // Confirm: update just the one item with server response
            val confirmed = (_uiState.value as? TreeUiState.Success)?.visibleItems?.map {
                if (it.id == bulletId) it.copy(isComplete = updated.isComplete) else it
            } ?: return@launch
            _uiState.value = (_uiState.value as TreeUiState.Success).copy(visibleItems = confirmed)
        } catch (e: Exception) {
            _uiState.value = snapshot // rollback
            _events.send(UiEvent.ShowError("Failed to update"))
        }
    }
}
```

### Pattern 5: Retrofit Service Interface Mirroring Express Routes

**What:** `NotesApiService` is a single Retrofit interface with one suspend function per Express endpoint. Method naming follows the route semantics. DTOs match the server's request/response JSON shapes exactly.

**When to use:** Single interface for the entire backend is appropriate for this size API (~30 endpoints across 8 route files). Split into multiple interfaces only if the codebase grows significantly.

**Example:**
```kotlin
interface NotesApiService {
    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/refresh")  // CookieJar sends refreshToken cookie automatically
    suspend fun refresh(): RefreshResponse

    @POST("api/auth/logout")
    suspend fun logout()

    // Documents
    @GET("api/documents")
    suspend fun listDocuments(): List<DocumentDto>

    @POST("api/documents")
    suspend fun createDocument(@Body body: CreateDocumentRequest): DocumentDto

    @PATCH("api/documents/{id}")
    suspend fun renameDocument(@Path("id") id: String, @Body body: RenameDocumentRequest): DocumentDto

    @PATCH("api/documents/{id}/position")
    suspend fun reorderDocument(@Path("id") id: String, @Body body: ReorderRequest): DocumentDto

    @POST("api/documents/{id}/open")
    suspend fun markDocumentOpened(@Path("id") id: String)

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String)

    // Bullets
    @GET("api/bullets/documents/{docId}/bullets")
    suspend fun getBullets(@Path("docId") docId: String): List<BulletDto>

    @POST("api/bullets")
    suspend fun createBullet(@Body body: CreateBulletRequest): BulletDto

    @PATCH("api/bullets/{id}")
    suspend fun patchBullet(@Path("id") id: String, @Body body: PatchBulletRequest): BulletDto

    @DELETE("api/bullets/{id}")
    suspend fun deleteBullet(@Path("id") id: String)

    @DELETE("api/bullets/documents/{docId}/completed")
    suspend fun deleteCompleted(@Path("docId") docId: String)

    @POST("api/bullets/{id}/indent")
    suspend fun indentBullet(@Path("id") id: String): BulletDto

    @POST("api/bullets/{id}/outdent")
    suspend fun outdentBullet(@Path("id") id: String): BulletDto

    @POST("api/bullets/{id}/move")
    suspend fun moveBullet(@Path("id") id: String, @Body body: MoveBulletRequest): BulletDto

    @POST("api/bullets/{id}/undo-checkpoint")
    suspend fun undoCheckpoint(@Path("id") id: String, @Body body: UndoCheckpointRequest)

    // Search
    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<BulletDto>

    // Undo/Redo
    @POST("api/undo")
    suspend fun undo(): UndoResult

    @POST("api/redo")
    suspend fun redo(): UndoResult

    @GET("api/undo/status")
    suspend fun undoStatus(): UndoStatus

    // Bookmarks
    @GET("api/bookmarks")
    suspend fun getBookmarks(): List<BookmarkDto>

    @POST("api/bookmarks")
    suspend fun addBookmark(@Body body: AddBookmarkRequest): BookmarkDto

    @DELETE("api/bookmarks/{bulletId}")
    suspend fun removeBookmark(@Path("bulletId") bulletId: String)

    // Tags
    @GET("api/tags")
    suspend fun getTags(): List<TagDto>

    @GET("api/tags/{type}/{value}/bullets")
    suspend fun getBulletsForTag(
        @Path("type") type: String,
        @Path("value") value: String
    ): List<BulletDto>

    // Attachments
    @GET("api/attachments/bullets/{bulletId}")
    suspend fun getAttachments(@Path("bulletId") bulletId: String): List<AttachmentDto>

    @POST("api/attachments/bullets/{bulletId}")
    @Multipart
    suspend fun uploadAttachment(
        @Path("bulletId") bulletId: String,
        @Part file: MultipartBody.Part
    ): AttachmentDto

    @DELETE("api/attachments/{id}")
    suspend fun deleteAttachment(@Path("id") id: String)

    @Streaming
    @GET("api/attachments/{id}/file")
    suspend fun downloadAttachment(@Path("id") id: String): ResponseBody
}
```

## Data Flow

### Request Flow — Standard API Call

```
User taps "Complete bullet"
        |
BulletRow.kt (Composable) — calls viewModel.toggleComplete(bulletId)
        |
BulletTreeViewModel
  (1) Snapshot current TreeUiState.Success
  (2) Apply optimistic update to StateFlow immediately
  (3) viewModelScope.launch { ... }
        |
BulletRepositoryImpl.patchBullet(id, isComplete=true)
        |
NotesApiService.patchBullet() — Retrofit suspend call
        |
OkHttp pipeline:
  AuthInterceptor adds "Authorization: Bearer <token>"
  CookieJar sends refreshToken cookie if present
  HTTPS POST to https://notes.gregorymaingret.fr/api/bullets/:id
        |
  If 401: TokenAuthenticator -> POST /api/auth/refresh -> retry
        |
BulletDto response -> mapped to domain Bullet -> BulletRepoImpl returns
        |
ViewModel confirms StateFlow with server response
        |
BulletTreeScreen recomposes (no visible change — optimistic was correct)
```

### Auth Cold Start Flow

```
App launches (MainActivity onCreate)
        |
AuthViewModel.trySilentLogin() called from LaunchedEffect
        |
AuthRepositoryImpl calls NotesApiService.refresh()
  CookieJar sends refreshToken cookie (in-memory, present if not killed since login)
        |
  200: store new accessToken in TokenStore -> navigate to Main screen
  401: cookie gone (process death) -> navigate to Login screen
```

### Token Refresh Race Prevention

```
3 concurrent API calls all 401 simultaneously
        |
TokenAuthenticator.authenticate() is @Synchronized
        |
Thread 1: token == requestToken -> calls /api/auth/refresh -> stores new token -> retries
Thread 2: wakes, sees currentToken != requestToken (already refreshed) -> retries with new token
Thread 3: same as Thread 2
        |
Single refresh round-trip regardless of concurrency
```

### Key Data Flows

1. **Tree flattening:** Server returns `List<BulletDto>` sorted by `position` (FLOAT8). `FlattenTreeUseCase` builds a map keyed by bullet ID, then DFS-walks from `parentId=null` to produce `List<FlatBullet>` (Bullet + depth integer). Collapsed bullets (`isCollapsed=true`) skip their subtree. This is a direct port of `buildBulletMap` + `flattenTree` from `BulletTree.tsx`.

2. **Drag reorder:** On drag start, `ComputeDragProjectionUseCase` receives the flat list, the active item's index, and the current pointer position (Y coordinate + horizontal delta in pixels). It excludes the dragged item and its descendants from the projection list, finds the insertion index from Y, then bounds the depth from the horizontal offset. On drop, ViewModel calls `bulletRepo.moveBullet(id, newParentId, afterId)` mapping to `POST /api/bullets/:id/move`.

3. **Debounced content save:** User types in `BasicTextField` inside `BulletRow`. A `LaunchedEffect(content)` with `delay(800L)` debounces saves: `PATCH /api/bullets/:id` with `{ content }`. When the debounce settles, the ViewModel also calls `POST /api/bullets/:id/undo-checkpoint` with current and previous content — identical to the web client pattern.

4. **Document reorder:** Server uses FLOAT8 midpoint positioning. Client sends `PATCH /api/documents/:id/position` with `{ afterId: UUID | null }`. Server computes the midpoint FLOAT8 — client never stores or manipulates position floats. This is the same contract used in the web client's drag reorder.

5. **Undo/Redo:** After every structural mutation (create, delete, indent, outdent, move), ViewModel calls `GET /api/undo/status` to update `canUndo`/`canRedo` in the `TreeUiState.Success`. Undo/Redo buttons trigger `POST /api/undo` / `POST /api/redo`, then reload the full bullet list (undo can restructure the entire tree).

## Navigation Compose Graph Structure

```
AppNavGraph
├── AuthGraph (when not authenticated)
│   ├── login (start destination)
│   └── register
└── MainGraph (when authenticated)
    └── main (single destination)
        ├── ModalNavigationDrawer (document list, CRUD)
        └── BulletTreeScreen (current document content)
            └── SearchScreen (modal bottom sheet)
```

**Routing logic:**

```kotlin
// AppNavGraph.kt
@Composable
fun AppNavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = if (authState.isAuthenticated) "main" else "login"
    ) {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("main") { MainScreen() }  // drawer + bullet tree
    }
}
```

**Why a single Main route (not separate document/bullet routes):** The UX pattern (from Dynalist/Workflowy and the web client) is a persistent split-pane where the document list is always available via a drawer. On Android this maps to `ModalNavigationDrawer` wrapping `BulletTreeScreen`. Document switching is a ViewModel state change (`viewModel.openDocument(id)`), not a navigation event. Separate routes would reload the bullet tree on every document switch and break scroll position.

## Integration Points

### New Components (Android-only, no backend changes needed)

| Component | What it integrates with | Notes |
|-----------|------------------------|-------|
| `NotesApiService` | Express REST API — all 8 route files | Single interface covers all ~30 endpoints |
| `AuthInterceptor` | JWT `Authorization: Bearer` header | Bearer token strategy confirmed in `server/src/middleware/auth.ts` |
| `TokenAuthenticator` | `POST /api/auth/refresh` + `refreshToken` cookie | Refresh returns `{ accessToken }` — confirmed in `auth.ts` |
| `JavaNetCookieJar` | `refreshToken` httpOnly cookie set by server | Server uses `setRefreshCookie()` — client must not touch this cookie manually |
| `TokenStore` | `EncryptedSharedPreferences` | Replaces web's in-memory React context; must survive process death for UX |
| `FlattenTreeUseCase` | Port of `flattenTree()` from `BulletTree.tsx` | Identical algorithm — recursive DFS, respects `isCollapsed`, returns depth |
| `ComputeDragProjectionUseCase` | Port of `computeDragProjection()` from `BulletTree.tsx` | Replace `getBoundingClientRect()` with Compose layout coordinates |

### Existing Backend Endpoints Used by Android (all unchanged)

| Endpoint | Phase | Usage |
|----------|-------|-------|
| `POST /api/auth/register` | Phase 1 | Register screen |
| `POST /api/auth/login` | Phase 1 | Login screen |
| `POST /api/auth/refresh` | Phase 1 | TokenAuthenticator + cold start silent login |
| `POST /api/auth/logout` | Phase 1 | Drawer logout action |
| `GET /api/documents` | Phase 2 | DocumentsDrawer initial load |
| `POST /api/documents` | Phase 2 | New document button |
| `PATCH /api/documents/:id` | Phase 2 | Inline rename in drawer |
| `PATCH /api/documents/:id/position` | Phase 2 | Drag reorder in drawer |
| `POST /api/documents/:id/open` | Phase 2 | Called on document tap (tracks last-opened) |
| `DELETE /api/documents/:id` | Phase 2 | Swipe-to-delete or context menu |
| `GET /api/bullets/documents/:docId/bullets` | Phase 3 | BulletTreeScreen initial load + refresh |
| `POST /api/bullets` | Phase 3 | Enter key creates new bullet |
| `PATCH /api/bullets/:id` | Phase 3 | Content edit (debounced), complete, collapse |
| `DELETE /api/bullets/:id` | Phase 3 | Backspace on empty or swipe gesture |
| `DELETE /api/bullets/documents/:docId/completed` | Phase 3 | Bulk delete completed |
| `POST /api/bullets/:id/indent` | Phase 3 | Tab / indent button |
| `POST /api/bullets/:id/outdent` | Phase 3 | Shift+Tab / outdent button |
| `POST /api/bullets/:id/move` | Phase 3 | Drag-drop reorder |
| `POST /api/bullets/:id/undo-checkpoint` | Phase 3 | After debounced content save settles |
| `GET /api/search?q=` | Phase 4 | Search screen with 300ms debounce |
| `POST /api/undo` | Phase 4 | Undo button |
| `POST /api/redo` | Phase 4 | Redo button |
| `GET /api/undo/status` | Phase 4 | Polled after each mutation to update button state |

### Internal Layer Boundaries

| Boundary | Communication | Rule |
|----------|---------------|------|
| Presentation -> Domain | Direct calls to use cases + repository interfaces | ViewModels never import data layer classes |
| Domain -> Data | Repository interfaces only — Hilt `@Binds` wires impls | Domain layer has zero Android SDK imports |
| Data -> Network | `NotesApiService` suspend functions | All exceptions propagate up; ViewModels catch at the boundary |
| ViewModel -> Composable | `StateFlow` for state + `Channel<UiEvent>` for one-shot events | One-shot events: snackbars, navigation triggers; never pass suspend lambdas into composables |

## Suggested Build Order for Phases

### Phase 1 — Foundation (prerequisite for everything)

Build first. All subsequent phases require authenticated network access.

1. Gradle project scaffold (`build.gradle.kts`, `settings.gradle.kts`, `AndroidManifest.xml`)
2. `NotesApiService` — auth + document endpoints (bullets can be stubbed as empty lists)
3. `TokenStore` with `EncryptedSharedPreferences`
4. `AuthInterceptor` + `TokenAuthenticator` + `JavaNetCookieJar` wired via `NetworkModule`
5. `AuthRepositoryImpl` — login, register, silent refresh, logout
6. `AuthViewModel` — cold start check, login/register actions
7. `LoginScreen` + `RegisterScreen` composables (Material 3 form)
8. `AppNavGraph` — Auth/Main routing
9. `Theme.kt` — Material 3 `lightColorScheme` + `darkColorScheme`
10. Verify: register, login, silent refresh, logout all work against production server

### Phase 2 — Documents (drawer)

Depends on Phase 1. The drawer is the entry point to all document content.

1. `DocumentRepositoryImpl` — list, create, rename, reorder, open, delete
2. `DocumentsViewModel` — `StateFlow<DocumentsUiState>`
3. `DocumentsDrawer` composable — `ModalNavigationDrawer` with CRUD + drag reorder
4. Main route scaffold with empty `BulletTreeScreen` placeholder content
5. Last-opened persistence — call `POST /api/documents/:id/open` on tap; store ID in `DataStore` for cold start restoration
6. Verify: full document CRUD, drag reorder calls `/position`, last-opened restored on restart

### Phase 3 — Bullet Tree (core feature)

Depends on Phase 2. Highest complexity phase.

1. `FlattenTreeUseCase` — port `buildBulletMap` + `flattenTree` from `client/src/components/DocumentView/BulletTree.tsx`
2. `BulletRepositoryImpl` — get, create, patch, delete, indent, outdent, move
3. `BulletTreeViewModel` — `StateFlow<TreeUiState>` with optimistic updates
4. `BulletRow` composable — `BasicTextField` for content, indent offset display, collapse chevron, complete toggle
5. `BulletTreeScreen` — `LazyColumn` rendering the flat list
6. Keyboard handling — Enter creates child bullet at same depth; Backspace on empty calls delete
7. Indent/outdent — toolbar buttons call `POST /api/bullets/:id/indent|outdent`
8. Collapse/expand — tap chevron calls `PATCH /api/bullets/:id` with `{ isCollapsed }`, re-flatten tree
9. Drag-drop reorder — `ComputeDragProjectionUseCase` + `POST /api/bullets/:id/move`
10. Debounced content save + undo checkpoint
11. Verify: full tree interaction, drag projection produces correct server positions

### Phase 4 — Reactivity and Polish

Depends on Phase 3. No new architecture — quality and completeness pass.

1. Pull-to-refresh (`PullToRefreshBox` from Compose Material 3)
2. Swipe gestures on `BulletRow` — right = complete, left = delete (`SwipeToDismiss`)
3. `SearchViewModel` + `SearchScreen` with 300ms debounce
4. Undo/Redo toolbar buttons + `GET /api/undo/status` polling
5. `AnimatedVisibility` for collapse/expand animation
6. `animateItem` modifier on `LazyColumn` items for reorder animation
7. `Crossfade` for document switching in the content area
8. Loading skeletons or `CircularProgressIndicator` while initial data loads
9. Error states with retry buttons
10. `SnackbarHostState` for error and confirmation messages
11. Verify: 60fps animations, all error paths handled, no crashes

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Personal (1 user, self-hosted) | Current approach is sufficient — no local caching layer needed |
| Small team (2-10 users) | Add Room for offline bullet caching; sync on reconnect — explicitly deferred |
| Large scale | Out of scope — self-hosted personal tool with no sharing features |

### Scaling Priorities

1. **First bottleneck:** Cookie loss on process death forces re-login. Add `PersistentCookieJar` (a third-party OkHttp CookieJar library) to persist the refresh cookie across process death. The `EncryptedSharedPreferences` access token handles the common case; persistent cookies handle the edge case.
2. **Second bottleneck:** No offline support means unusable on airplane mode or poor connectivity. Room + offline-first architecture is explicitly deferred per `PROJECT.md`. Pull-to-refresh provides a manual sync affordance.

## Anti-Patterns

### Anti-Pattern 1: Single OkHttp Interceptor for Both Token Injection and Refresh

**What people do:** One interceptor checks for 401, calls refresh inside `intercept()`, and retries with the new token — all in one class.

**Why it's wrong:** When multiple in-flight requests all return 401 simultaneously, each thread calls refresh independently, resulting in multiple refresh calls with the same (now invalid) refresh cookie. The second and third refresh calls return 401, and those requests fail. Additionally, a single interceptor cannot use OkHttp's built-in retry-count protection.

**Do this instead:** `Interceptor` for token injection only. `Authenticator` for 401 handling — OkHttp calls it exclusively on 401 responses and has built-in max-retry protection. `@Synchronized` on `authenticate()` prevents concurrent refresh races.

### Anti-Pattern 2: Storing JWT Access Token in Unencrypted SharedPreferences

**What people do:** Write `accessToken` to plain `SharedPreferences` for persistence across process death.

**Why it's wrong:** Plain `SharedPreferences` files are world-readable with root access, readable via ADB backup on unencrypted devices, and accessible to other apps on rooted devices. JWT access tokens are credentials.

**Do this instead:** Keep access token in memory during the session. Use `EncryptedSharedPreferences` (Jetpack Security) only for cold-start restoration. The httpOnly refresh cookie is the primary persistence mechanism — a valid cookie can always generate a new access token.

### Anti-Pattern 3: Calling Retrofit from Composables via LaunchedEffect

**What people do:** `LaunchedEffect(Unit) { val bullets = repo.getBullets(docId); ... }` directly in a composable.

**Why it's wrong:** Composables recompose on every state change. `LaunchedEffect` keys must be carefully managed. Business logic in composables is untestable without a full Compose UI test environment.

**Do this instead:** All API calls go through ViewModel methods. Composables call `viewModel.loadDocument(id)` and observe `StateFlow`. The ViewModel's `init {}` or a dedicated load function triggers the network call.

### Anti-Pattern 4: Re-fetching the Entire Bullet Tree After Every Patch

**What people do:** After `PATCH /api/bullets/:id`, call `GET /api/bullets/documents/:docId/bullets` to resync.

**Why it's wrong:** Each patch endpoint already returns the updated `BulletDto`. Re-fetching the full tree on every keystroke (debounced content save fires every 800ms) creates unnecessary latency and server load.

**Do this instead:** On successful patch, update only the affected bullet in the ViewModel's local `List<FlatBullet>` with the returned DTO. Re-fetch the full tree only after undo/redo (which can restructure the entire tree) or on explicit pull-to-refresh.

### Anti-Pattern 5: Separate Navigation Routes for Document List and Bullet Tree

**What people do:** `NavHost` with `documents/`, `document/{id}/bullets` as distinct routes, navigating between them like separate pages.

**Why it's wrong:** The Dynalist/Workflowy UX pattern (which this app replicates) is a persistent split-pane — the document list is always accessible. Separate routes cause the bullet tree to unmount and remount on every document switch, resetting scroll position and triggering a full reload.

**Do this instead:** One `Main` composable with `ModalNavigationDrawer`. Document switching is `viewModel.openDocument(id)` — a state change, not a navigation event. The bullet tree stays mounted and replaces its content reactively.

## Sources

- Direct inspection: `server/src/routes/auth.ts`, `documents.ts`, `bullets.ts`, `search.ts`, `undo.ts`, `bookmarks.ts`, `attachments.ts`, `tags.ts` — all endpoint signatures verified — HIGH confidence
- Direct inspection: `server/src/middleware/auth.ts` — JWT Bearer token strategy via `passport-jwt` confirmed — HIGH confidence
- Direct inspection: `client/src/contexts/AuthContext.tsx` — silent refresh on mount, token in memory only, refresh cookie flow — HIGH confidence
- Direct inspection: `client/src/components/DocumentView/BulletTree.tsx` — `flattenTree`, `buildBulletMap`, `computeDragProjection` algorithms — HIGH confidence
- `ANDROID-PLAN.md` (project root) — planned library choices confirmed by project owner: Hilt, Retrofit, Coroutines/Flow, Navigation Compose, Material 3 — HIGH confidence
- OkHttp `Authenticator` interface semantics — standard OkHttp design; `authenticate()` is called exclusively on HTTP 401 with built-in retry limiting — HIGH confidence

---
*Architecture research for: Kotlin/Jetpack Compose Android client integrating with Express REST API*
*Researched: 2026-03-12*
