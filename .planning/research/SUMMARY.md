# Project Research Summary

**Project:** Notes v2.0 — Native Android Client
**Domain:** Native Android outliner (Kotlin/Jetpack Compose) consuming an existing Express REST API
**Researched:** 2026-03-12
**Confidence:** HIGH

## Executive Summary

This milestone adds a native Android client to an already-complete Express + PostgreSQL backend. No backend changes are needed. The architecture question is entirely about how to consume the existing REST API in a Kotlin/Jetpack Compose application. The recommended approach is strict Clean Architecture layering (presentation → domain → data) with MVVM, Hilt DI, Retrofit 3.0 for networking, and a flat LazyColumn tree model that ports the existing web client's `flattenTree` algorithm directly. The entire library stack uses KSP (not KAPT), Navigation3 (not Navigation 2), and DataStore + Tink (not the deprecated EncryptedSharedPreferences). All version choices have been verified against official sources as of 2026-03-12.

The critical architectural decision — and the one with the most pitfall surface — is the bullet tree rendering model. The web client solved the "tree in a flat list" problem with a single flat SortableContext. The Android client must apply the identical pattern: a recursive `flattenTree` DFS that produces a `List<FlatBullet>` (bullet + depth int), rendered in a single `LazyColumn` with `paddingStart` per depth. Collapse/expand, drag-to-reorder, zoom, and swipe gestures all depend on this model being correct before they are built. The two use cases that port this algorithm from the web client (`FlattenTreeUseCase`, `ComputeDragProjectionUseCase`) must live in the domain layer with zero Android SDK imports — making them unit-testable without a device.

Three build-order constraints override all others: (1) auth infrastructure — specifically the `Mutex`-synchronized `TokenAuthenticator` that prevents concurrent 401 refresh races — must be correct in Phase 1 before any screen is built; (2) the flat tree ViewModel must be designed with optimistic updates from the start, not retrofitted; (3) the Android project must live in `/android/` (not the repo root) to avoid Gradle scanning `node_modules`. These are not polish decisions — they are architectural choices that carry HIGH recovery cost if shipped incorrectly.

## Key Findings

### Recommended Stack

The Android client uses a modern Kotlin-first stack with all components selected for compatibility and long-term support. Kotlin 2.3.0 with KSP 2.3.0-1.0.31 replaces the KAPT-era approach entirely. Jetpack Compose BOM 2025.12.00 pins all Compose/Material 3 versions in sync. Navigation3 1.0.1 (stable November 2025) replaces Navigation 2's error-prone string routes with type-safe data class destinations. Retrofit 3.0.0 has native coroutine support — no Jake Wharton adapter needed. The EncryptedSharedPreferences API was deprecated in April 2025; DataStore 1.2.1 + Tink 1.8.0 is the mandated replacement. One hard constraint: OkHttp must stay at 4.12.0 because Retrofit 3.0 does not yet support OkHttp 5.x.

See `.planning/research/STACK.md` for the complete Gradle version catalog TOML and full version compatibility matrix.

**Core technologies:**
- Kotlin 2.3.0 + KSP 2.3.0-1.0.31: primary language + annotation processor — K2 compiler is production-grade; 2x faster than KAPT for Hilt and Room
- Jetpack Compose BOM 2025.12.00 (Compose 1.10.0, Material 3 1.4.0): declarative UI — all required patterns (drawer, swipe, pull-to-refresh, search bar) are in Material 3 1.4.0
- Hilt 2.56 + hilt-lifecycle-viewmodel-compose 1.3.0: compile-time DI — integrates directly with ViewModel and Compose; `hiltViewModel()` without transitive Nav2 dependency at 1.3.0
- Navigation3 1.0.1: in-app navigation — type-safe destinations; correct choice for greenfield Compose-only apps in 2026
- Retrofit 3.0.0 + OkHttp 4.12.0: HTTP client — native coroutine suspend functions; kotlinx.serialization 1.10.0 converter for null-safe JSON; do not upgrade OkHttp to 5.x
- DataStore 1.2.1 + Tink 1.8.0: secure persistent storage — replaces deprecated EncryptedSharedPreferences for refresh cookie and access token persistence
- sh.calvin.reorderable 3.0.0: drag-to-reorder in LazyColumn — the only actively maintained Compose reorder library; uses `Modifier.animateItem` (v2.1 feature but the LazyColumn structure must be compatible from v2.0)

**What not to add:**
- Room: no offline mode in scope per PROJECT.md; adds schema migration complexity for zero gain
- LiveData: replaced by StateFlow + `collectAsStateWithLifecycle()` in Compose
- Navigation 2 (`navigation-compose:2.9.7`): string-based routes are runtime-error-prone; Nav3 is stable and preferred
- WorkManager / FCM: background sync and push notifications are out of scope
- OkHttp 5.x: incompatible with Retrofit 3.0.0
- KAPT: deprecated; KSP is 2x faster and all modern Jetpack processors support it
- `retrofit2-kotlin-coroutines-adapter` (Jake Wharton): redundant with Retrofit 3.0's built-in coroutine support

### Expected Features

The v2.0 milestone delivers the core outliner experience on Android. The web client (v1.0 + v1.1) is the feature reference; the question is what "table stakes" means specifically for a native Material Design 3 context.

**Must have (table stakes for v2.0 launch):**
- Email/password login with JWT + refresh cookie persistence — nothing works without auth
- ModalNavigationDrawer with document list + CRUD — native Android navigation pattern; Dynalist/Workflowy both use drawer
- Bullet tree as flat LazyColumn with depth-based indentation — the only viable approach; nested LazyColumn causes touch event conflicts
- Tap to edit inline (BasicTextField per row) — separate edit screen is unacceptable outliner UX
- Enter = new bullet at same level, Tab/Shift+Tab = indent/outdent — core outliner keyboard loop
- Collapse/expand bullets, zoom into bullet as root, breadcrumb trail — core outliner interactions
- Swipe right = complete, swipe left = delete with proportional color/icon reveal — matches web v1.1 and Workflowy Android
- Long-press context menu (indent, outdent, complete, zoom, delete) — required for indent/outdent when no physical keyboard
- Pull to refresh — universal Android sync pattern; explicit sync trigger
- Undo button in top app bar (existing server-side undo API)
- Search via Material 3 SearchBar reusing existing `/api/search` endpoint
- Material 3 dark/light follows system; dynamic color (Material You) on Android 12+
- Optimistic updates on all mutations — must be designed in from the start, not retrofitted
- Predictive back gesture (opt in via manifest; Android 13+)

**Should have (v2.1 — after v2.0 stabilizes):**
- Drag-to-reorder bullets and documents — Calvin-LL/Reorderable; depends on v2.0 flat LazyColumn being stable
- Inline markdown rendering (AnnotatedString) — read mode only; raw text when bullet has focus
- #tags, @mentions, !!dates as tappable chips — depends on inline markdown
- Bookmarks screen — low complexity quick win
- Physical keyboard shortcuts (Tab, Shift+Tab, Ctrl+Z for Bluetooth keyboards/Chromebooks)

**Defer (v3+):**
- Google SSO — Firebase/Play Services dependency; low ROI for a self-hosted app
- File attachments — SAF integration is substantial; most usage is on desktop
- Offline mode — requires CRDT or conflict resolution; explicitly out of scope per PROJECT.md
- Widgets / App Links deep-link to document

**Anti-features (do not build in v2.0):**
- Separate "edit mode" per bullet — doubles tap count for the most frequent action
- Bottom navigation bar — wrong pattern for single-surface app; Dynalist/Workflowy both use drawer-only
- Nested LazyColumn / RecyclerView — touch event conflicts and perf issues at scale
- Real-time collaborative sync — no WebSocket infrastructure in backend
- Kanban / board view — orthogonal paradigm; this is an outliner, not a project manager

### Architecture Approach

The Android client follows strict Clean Architecture with three layers: presentation (ViewModels + Composables), domain (repository interfaces + pure use cases), and data (Retrofit implementations + DTOs). The domain layer has zero Android SDK imports, making `FlattenTreeUseCase` and `ComputeDragProjectionUseCase` unit-testable without a device. The navigation graph has two routes (Auth / Main) — document switching is a `viewModel.openDocument(id)` state change, not a navigation event, which preserves scroll position and avoids remounting the bullet tree on every document tap.

See `.planning/research/ARCHITECTURE.md` for the full component diagram, all data flow sequences, the complete `NotesApiService` Retrofit interface (~30 endpoints), and the suggested build order per phase.

**Major components:**
1. `NotesApiService` — Retrofit interface mirroring all ~30 Express routes; single interface appropriate for this API size; suspend functions throughout
2. `AuthInterceptor` + `TokenAuthenticator` + `JavaNetCookieJar` — split auth: Interceptor attaches Bearer token, Authenticator handles 401 with Mutex-synchronized refresh, JavaNetCookieJar persists the httpOnly refresh cookie
3. `TokenStore` — in-memory access token + DataStore/Tink refresh cookie persistence; Hilt singleton
4. `FlattenTreeUseCase` — pure Kotlin port of `buildBulletMap` + `flattenTree` from `BulletTree.tsx`; recursive DFS, respects `isCollapsed`; drives all tree rendering, collapse, zoom, and drag
5. `BulletTreeViewModel` — `StateFlow<TreeUiState>` with optimistic update + rollback pattern for all mutations; snapshot before API call, revert on failure
6. `AppNavGraph` — two routes (Auth / Main); `ModalNavigationDrawer` wraps `BulletTreeScreen`; document switching is ViewModel state, not navigation

### Critical Pitfalls

1. **Token refresh race condition** — Multiple concurrent 401s each independently call `/api/auth/refresh` without synchronization. Prevention: `Mutex.withLock` in `TokenAuthenticator.authenticate()`; compare stored token against the failed request's token before calling refresh. Must be correct in Phase 1. Recovery cost: HIGH.

2. **httpOnly cookie dropped by CookieJar** — Some third-party OkHttp CookieJar implementations silently discard cookies with the `HttpOnly` flag, causing `/api/auth/refresh` to always return 401. Prevention: use `JavaNetCookieJar(CookieManager(null, CookiePolicy.ACCEPT_ALL))` as the baseline; do NOT use `franmontiel/PersistentCookieJar` without auditing. Validate the cookie round-trip as the first Phase 1 test.

3. **flattenTree collapsed-subtree leak** — An iterative Kotlin port of the TypeScript `flattenTree` will have an off-by-one in the "skip subtree" logic. Prevention: port as a direct recursive function first (no stack overflow risk for personal outliner depths < 100 levels); unit-test with collapsed parents and grandchildren before wiring to UI. Recovery cost: HIGH — this algorithm underlies every tree feature.

4. **Debounced content save cancelled on navigation** — `viewModelScope` is cancelled on `onCleared()`. If the user types and navigates away within the ~500ms debounce window, the save and undo checkpoint are dropped. Prevention: override `onCleared()` to flush pending saves via `runBlocking(NonCancellable)`.

5. **Gradle root pollution** — Placing `settings.gradle.kts` at the repo root causes Gradle to scan `server/node_modules` and `client/node_modules`. Prevention: Android project lives in `/android/` subdirectory; all `./gradlew` commands run from `/android/`; separate GitHub Actions workflows for Node vs Android changes.

## Implications for Roadmap

Four phases map naturally from the architecture's suggested build order. The structure is dictated by hard dependencies: auth must precede everything; documents must precede bullets; the tree model must be stable before swipe/drag/search are layered on top.

### Phase 1: Foundation (Auth + Project Scaffold)

**Rationale:** Every subsequent screen requires authenticated network access. The three critical infrastructure decisions — Mutex-synchronized token refresh, httpOnly cookie handling, and `/android/` project location — must be made and validated before any feature work begins. This phase has no user-visible product output but is the highest-leverage phase for preventing HIGH recovery-cost failures.

**Delivers:** Working auth round-trip against production server (register, login, silent refresh on cold start, logout); Hilt DI module; OkHttp client with AuthInterceptor + TokenAuthenticator + JavaNetCookieJar; TokenStore with DataStore/Tink; Material 3 theme (light + dark, dynamic color on Android 12+); AppNavGraph with Auth/Main routing; LoginScreen + RegisterScreen composables.

**Addresses:** Auth (JWT + refresh cookie), Material 3 theme, dark mode system-follow, predictive back manifest opt-in.

**Avoids:** Token refresh race condition (Pitfall 1 — Mutex in Authenticator), httpOnly cookie drop (Pitfall 2 — JavaNetCookieJar), CORS confusion (Pitfall 5 — no Origin header in OkHttp), Gradle root pollution (Pitfall 8 — /android/ subdirectory).

### Phase 2: Document Management (Drawer)

**Rationale:** Document management is the entry point to all content. It is architecturally simpler than the bullet tree (flat list, not recursive) and validates the full data layer stack — Retrofit calls, DTO mapping, ViewModel StateFlow, Composable collection — before the complex tree work begins. Last-opened persistence (DataStore) lives here.

**Delivers:** ModalNavigationDrawer with full document CRUD (list, create, rename, delete); document position reorder calling `PATCH /api/documents/:id/position`; document selection navigates to placeholder BulletTreeScreen; last-opened document ID persisted in DataStore and restored on cold start.

**Addresses:** Document drawer navigation, document CRUD, last-opened persistence.

**Uses:** DataStore 1.2.1 for last-opened ID; full DocumentRepositoryImpl + DocumentsViewModel + DocumentsDrawer composable.

### Phase 3: Bullet Tree (Core Feature)

**Rationale:** Highest-complexity phase. All core outliner behaviors live here. The flat tree model is the foundation for swipe, zoom, and search — these cannot be built until the LazyColumn rendering is stable and the optimistic update pattern is proven. Drag-to-reorder is deferred to v2.1 per the feature research (it depends on this phase's LazyColumn being stable first, and it is a P2 feature).

**Delivers:** Full bullet tree interaction — create, edit (BasicTextField per row), delete, indent (`POST /api/bullets/:id/indent`), outdent, collapse/expand, zoom into bullet as root + breadcrumbs, Enter to create sibling, Backspace on empty to delete; debounced content save (800ms) + undo checkpoint; all mutations optimistic with rollback; `LazyColumn` with `key = { it.id }` on every items block.

**Addresses:** Bullet tree (flat LazyColumn + CRUD), Tab/Shift+Tab indent, Enter new bullet, collapse/expand, zoom + breadcrumbs, optimistic updates, undo.

**Avoids:** flattenTree collapsed-subtree leak (Pitfall 3 — recursive port, unit-tested before UI wiring), computeDragProjection DOM dependency (Pitfall 4 — use Reorderable library for v2.1; spike at Phase 3 start), debounced save drop on navigation (Pitfall 6 — onCleared flush), missing LazyColumn keys, re-fetching full tree on every patch (update only the affected item from the returned DTO).

### Phase 4: Reactivity and Polish

**Rationale:** No new architecture — this phase adds the interaction layer (swipe gestures, search, undo, animations) on top of the stable tree model from Phase 3. Swipe gesture directional discrimination must be implemented from the start (not bolted on) to prevent the scroll/swipe conflict pitfall.

**Delivers:** Swipe right = complete / swipe left = delete (SwipeToDismissBox with proportional color/icon reveal; icon fades in at 25% threshold); SearchBar with 300ms debounce against `/api/search`; undo/redo toolbar buttons with `GET /api/undo/status` polling; pull-to-refresh (PullToRefreshBox); AnimatedVisibility for collapse/expand; `animateItem` on LazyColumn for mutations; Crossfade for document switching; loading skeletons; error states with retry buttons; SnackbarHostState for confirmations; keyboard-aware scroll (`WindowCompat.setDecorFitsSystemWindows` + `Modifier.imePadding()` + `LazyListState.animateScrollToItem` on focus).

**Addresses:** Swipe gestures (complete + delete), search, undo/redo, pull to refresh, animation polish, error handling, empty states.

**Avoids:** Swipe/scroll gesture conflict (Pitfall 7 — directional discrimination: arm swipe only when `abs(deltaX) > abs(deltaY) * 2`), loading spinner on every operation (optimistic updates from Phase 3 already prevent this), focused bullet hidden behind IME (imePadding + scroll to focused item).

### Phase Ordering Rationale

- Auth before everything: JWT + refresh cookie is required for every API call; the Mutex Authenticator must be correct from the start — retrofitting synchronized token refresh is painful and its bugs are subtle.
- Documents before bullets: the simpler flat document list validates the full data layer (Retrofit → DTO → domain model → ViewModel → StateFlow → Composable) before the complex recursive tree model is built.
- Tree model before gestures: swipe, zoom, and search all operate on the flat `List<FlatBullet>`; the list must be stable before interaction layers are added.
- Optimistic updates in Phase 3, not retrofitted: the feature research is explicit — bolt-on optimism after pessimistic mutations doubles state management complexity.
- Drag-to-reorder deferred to v2.1: feature research places it at P2; it depends on Phase 3's LazyColumn being stable and it is not needed for the v2.0 launch milestone.

### Research Flags

Phases likely needing deeper research during planning:

- **Phase 1:** Refresh cookie persistence across process death — the research identifies `JavaNetCookieJar` as correct for in-session persistence but notes that standard `CookieManager` holds cookies in memory only (lost on process death). The exact DataStore/Tink serialization wrapper for the `Set-Cookie` response value needs to be written and validated against the production server's cookie format as the first Phase 1 deliverable.
- **Phase 3:** `ComputeDragProjectionUseCase` — the web algorithm uses `getBoundingClientRect()` which has no Android equivalent. The research recommends using Calvin-LL/Reorderable to avoid hand-rolling coordinate transforms. Confirm this is the right call via a 1-day tech spike at the start of Phase 3 before committing to the drag architecture.

Phases with standard, well-documented patterns (skip research-phase):

- **Phase 2:** Document drawer pattern is standard Material 3; `ModalNavigationDrawer` + StateFlow + CRUD is well-documented with official samples.
- **Phase 4:** SwipeToDismissBox, PullToRefreshBox, SearchBar, and SnackbarHostState are all Material 3 Compose components with official documentation and established patterns.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified against official release pages and changelogs 2026-03-12; Gradle version catalog TOML provided in STACK.md |
| Features | HIGH | Material 3 gesture patterns and Compose APIs well-documented; competitor Android UX parity (Workflowy/Dynalist) verified against app store behavior — MEDIUM on that subset |
| Architecture | HIGH | Derived from direct inspection of all server route files, middleware, and web client algorithms; Retrofit interface covers all ~30 endpoints with verified signatures |
| Pitfalls | HIGH | Critical pitfalls (token refresh, cookie handling, flattenTree) derived from direct inspection of production backend code; confirmed by official OkHttp and Android docs |

**Overall confidence:** HIGH

### Gaps to Address

- **Refresh cookie serialization across process death:** The research identifies `JavaNetCookieJar` as correct for in-session use but does not provide a complete implementation for persisting the refresh cookie to DataStore/Tink across process death. This must be written and tested as the very first Phase 1 deliverable — before any login UI is built.

- **Gboard Tab key interception:** FEATURES.md documents that Gboard and some OEM keyboards intercept Tab before the app receives it as a KeyEvent. The Phase 3 plan must treat toolbar indent/outdent buttons as the primary interaction path and physical keyboard Tab as a secondary convenience — not the reverse. This is a UX decision that should be noted in Phase 3 acceptance criteria.

- **Dynamic color brand consistency (Material You):** Material You changes the app's color scheme per device based on wallpaper. PITFALLS.md flags this as a UX concern. The Phase 1 theme implementation must establish an explicit decision: opt in with a consistent fallback seed color, or opt out entirely. Document this decision in `Theme.kt`.

- **`computeDragProjection` Android port:** The web implementation is tightly coupled to DOM APIs (`getBoundingClientRect`). Confirm the Reorderable library approach before Phase 3 begins via a tech spike — if the library's API does not fit the flat-tree model, the custom `Modifier.onGloballyPositioned` approach documented in PITFALLS.md is the fallback.

## Sources

### Primary (HIGH confidence)

- Direct code inspection: `server/src/routes/auth.ts`, `documents.ts`, `bullets.ts`, `search.ts`, `undo.ts`, `bookmarks.ts`, `attachments.ts`, `tags.ts` — all endpoint signatures verified
- Direct code inspection: `server/src/middleware/auth.ts` — JWT Bearer token strategy via passport-jwt confirmed
- Direct code inspection: `client/src/components/DocumentView/BulletTree.tsx` — `flattenTree`, `buildBulletMap`, `computeDragProjection` algorithms verified
- Direct code inspection: `client/src/contexts/AuthContext.tsx` — silent refresh on mount, access token in memory only, refresh cookie flow confirmed
- https://developer.android.com/develop/ui/compose/bom/bom-mapping — BOM 2025.12.00; Compose 1.10.0, Material3 1.4.0
- https://developer.android.com/jetpack/androidx/releases/navigation3 — Navigation3 1.0.1 stable November 2025
- https://developer.android.com/jetpack/androidx/releases/lifecycle — lifecycle 2.10.0 stable
- https://developer.android.com/jetpack/androidx/releases/hilt — androidx.hilt 1.3.0; hilt-lifecycle-viewmodel-compose API change
- https://github.com/square/retrofit/releases — Retrofit 3.0.0 stable; OkHttp 4.12 dependency confirmed
- https://developer.android.com/jetpack/androidx/releases/security — EncryptedSharedPreferences deprecated in security-crypto 1.1.0-alpha07
- https://developer.android.com/build/releases/agp-9-1-0-release-notes — AGP 9.1.0 March 2026
- https://blog.jetbrains.com/kotlin/2025/12/kotlin-2-3-0-released/ — Kotlin 2.3.0 stable December 2025
- https://developer.android.com/develop/ui/compose/performance/bestpractices — LazyColumn keys; collectAsStateWithLifecycle
- https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling — gesture conflict resolution
- https://m3.material.io/foundations/interaction/gestures — Material 3 swipe gesture patterns
- https://github.com/Calvin-LL/Reorderable — sh.calvin.reorderable 3.0.0; Modifier.animateItem support

### Secondary (MEDIUM confidence)

- https://github.com/hoc081098/Refresh-Token-Sample — OkHttp Authenticator + Mutex pattern (widely referenced)
- https://mvnrepository.com/artifact/com.google.crypto.tink/tink-android — tink-android 1.8.0 latest stable
- WebSearch: EncryptedSharedPreferences deprecation migration 2026 — DataStore + Tink pattern (corroborated by official release notes)
- WebSearch: Retrofit 3.0.0 migration guide — suspend fun, no coroutines adapter needed
- Workflowy Android UX behavior observed for swipe and drawer patterns
- Obsidian Android community (forum.obsidian.md) — Gboard Tab key interception behavior

### Tertiary (LOW confidence)

- Obsidian Android forum confirmation of Gboard Tab behavior scope — needs direct device testing; behavior may vary by OEM keyboard variant

---
*Research completed: 2026-03-12*
*Ready for roadmap: yes*
