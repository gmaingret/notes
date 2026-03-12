# Pitfalls Research

**Domain:** Native Android client (Kotlin/Jetpack Compose) added to existing Express + PostgreSQL web app
**Researched:** 2026-03-12
**Confidence:** HIGH (pitfalls derived from direct inspection of backend auth code, tree algorithm, CORS config + verified community sources)

---

## Critical Pitfalls

### Pitfall 1: Token Refresh Race Condition — Multiple Concurrent 401s

**What goes wrong:**
When the Android app has multiple in-flight API calls and the access token expires, every call receives a 401 simultaneously. OkHttp's `Authenticator.authenticate()` fires independently for each. Without synchronization, each call independently POSTs to `/api/auth/refresh`. The server issues a new access token on the first call; if the CookieJar still has the original refresh cookie, subsequent calls each also receive a new access token — but those extra calls consume server resources and can cause divergent token state. In a future where refresh token rotation is added to the backend, the second concurrent refresh will fail with 401, unexpectedly logging the user out.

**Why it happens:**
`Authenticator.authenticate()` runs on a background thread per request. The naive implementation does `runBlocking { postRefreshToken() }` with no awareness that another thread is already refreshing. OkHttp does not coordinate between concurrent `Authenticator` invocations.

**How to avoid:**
Use a `Mutex` inside the `Authenticator`. Before calling the refresh endpoint, acquire the lock. After acquiring, compare the stored access token against the token that was on the failed request — if they differ, another coroutine already refreshed; skip the network call and re-attach the current token. Only call `/api/auth/refresh` if the tokens still match (no refresh has happened yet).

```kotlin
// Enforce in Phase 1 (Foundation)
private val mutex = Mutex()

override fun authenticate(route: Route?, response: Response): Request? {
    val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
    return runBlocking {
        mutex.withLock {
            val currentToken = tokenStore.getAccessToken()
            if (currentToken != null && "Bearer $currentToken" != failedToken) {
                // Another coroutine already refreshed — reuse current token
                return@runBlocking response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }
            val newToken = performRefresh() ?: return@runBlocking null
            tokenStore.saveAccessToken(newToken)
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
    }
}
```

**Warning signs:**
- Server logs show multiple simultaneous POST `/api/auth/refresh` calls within milliseconds of each other
- User is randomly logged out during normal multi-screen navigation
- Refresh fails with 401 even though the user just logged in

**Phase to address:** Phase 1 (Foundation) — build correctly from the start; retrofitting synchronized token refresh is painful.

---

### Pitfall 2: SameSite=Strict Cookie — Refresh Endpoint Returns 401 Forever

**What goes wrong:**
The Express backend sets `sameSite: 'strict'` on the `refreshToken` cookie (confirmed in `server/src/services/authService.ts` line 24). On browsers, `SameSite=Strict` blocks the cookie from being sent on cross-origin requests. OkHttp does not enforce `SameSite` — it is a browser-specific security attribute. However, the bigger risk is that several popular third-party `CookieJar` implementations for OkHttp (notably older versions of `PersistentCookieJar`) silently discard cookies with the `HttpOnly` flag when parsing `Set-Cookie` response headers. If the `refreshToken` cookie is dropped during parsing, every call to `/api/auth/refresh` will return 401 even with a valid session, making the user appear permanently logged out.

**Why it happens:**
The web implementation uses `HttpOnly` for XSS protection (correct for browsers). OkHttp does not have a DOM and cannot be exploited via XSS, so `HttpOnly` is irrelevant for security — but some CookieJar implementations copy the browser's behavior of not exposing httpOnly cookies to "JavaScript," which in OkHttp translates to not storing them at all.

**How to avoid:**
Use `JavaNetCookieJar` with `CookieManager(null, CookiePolicy.ACCEPT_ALL)` as the baseline — it is part of the Java standard library and correctly stores httpOnly cookies. Persist the refresh cookie by serializing it to `EncryptedSharedPreferences` in `saveFromResponse` and reloading in `loadForRequest`. Write a unit test: log in, assert `Cookie: refreshToken=...` appears in the next request to `/api/auth/refresh`. Do NOT use `PersistentCookieJar` from `franmontiel` without auditing for httpOnly handling.

**Warning signs:**
- POST `/api/auth/refresh` always returns 401 from the Android client, but works in a browser
- OkHttp request logs show no `Cookie` header on the refresh call
- `Set-Cookie: refreshToken=...; HttpOnly` visible in login response but the cookie is absent on subsequent requests

**Phase to address:** Phase 1 (Foundation) — validate the cookie round-trip before any other auth code is written.

---

### Pitfall 3: flattenTree Port — Collapsed Subtree Leak

**What goes wrong:**
The TypeScript `flattenTree` in `BulletTree.tsx` uses recursive `flatMap` with an early return on `b.isCollapsed` — this naturally prevents any descendant of a collapsed bullet from appearing. When porting to Kotlin using an iterative stack-based approach (common for Android performance concerns), the "skip subtree" logic must explicitly track when we are inside a collapsed subtree at a given depth. Getting the depth-comparison boundary condition wrong (off-by-one on mixed-depth trees) causes bullets that belong to a collapsed subtree to appear visibly in the LazyColumn.

**Why it happens:**
The TypeScript version's correctness is implicit in the recursion — you simply do not recurse when `isCollapsed`. An iterative Kotlin port must make this state explicit. The off-by-one occurs when checking `if (currentDepth <= collapsedDepth)` vs `< collapsedDepth` when exiting a collapsed section.

**How to avoid:**
Port as a direct recursive function first — Kotlin handles recursion fine for typical bullet tree depths (< 100 levels; a personal outliner never hits stack overflow). Only optimize to iterative if profiling proves a bottleneck (it will not be). Add unit tests with: (a) a bullet with 3 levels of children where the top is collapsed — assert grandchildren absent; (b) adjacent collapsed bullets at the same level — assert neither's children appear.

```kotlin
// Direct recursive port — correct by construction
fun flattenTree(
    map: Map<String, Bullet>,
    parentId: String? = null,
    depth: Int = 0
): List<FlatBullet> = getChildren(map, parentId).flatMap { bullet ->
    buildList {
        add(FlatBullet(bullet, depth))
        if (!bullet.isCollapsed) {
            addAll(flattenTree(map, bullet.id, depth + 1))
        }
    }
}
```

**Warning signs:**
- Bullets that should be hidden (children of a collapsed parent) appear in the list
- Collapsing a bullet removes its direct children but grandchildren remain visible
- Depth values of visible bullets jump from 0 to 2 (missing the depth-1 intermediary)

**Phase to address:** Phase 3 (Bullet Tree) — write unit tests for `flattenTree` before wiring to the UI.

---

### Pitfall 4: computeDragProjection DOM Dependency — No Android Equivalent

**What goes wrong:**
The web `computeDragProjection` uses `document.getElementById('bullet-row-${id}')` and `getBoundingClientRect()` to determine insertion index from the pointer Y position. These DOM APIs do not exist on Android. The Android equivalent — `LazyListState.layoutInfo.visibleItemsInfo` — provides item bounds in `LazyColumn-local` coordinates, not screen coordinates. Touch `pointerY` arrives in root-composable coordinates. The coordinate system mismatch is non-obvious and causes the drag drop to always insert at the wrong position if the LazyColumn is not at the top of the screen.

**Why it happens:**
The web implementation is tightly coupled to the DOM for coordinate lookup. Developers porting the algorithm often use `LazyListState.layoutInfo` directly without converting to screen coordinates, or use screen coordinates without accounting for LazyColumn scroll offset.

**How to avoid:**
Option A (recommended): Use the `Reorderable` library (Calvin-LL/Reorderable on GitHub) which handles all coordinate transforms, scroll-while-dragging, and haptic feedback internally. Wire `rememberReorderableLazyListState` to the existing `flattenTree` output — this is a 1-2 day integration, not a full rewrite.

Option B (if custom is required): Track item center-Y in screen coordinates using `Modifier.onGloballyPositioned { coords -> itemCenters[bullet.id] = coords.positionInRoot().y + coords.size.height / 2f }` on each row. Then compare `pointerY` (also in root coordinates, from `Modifier.pointerInput`) against `itemCenters` — both are in the same coordinate space.

**Warning signs:**
- Drag preview follows the finger but the drop indicator is always at position 0 or at the end of the list
- Drop target is correct only when the list is scrolled to the very top
- `NullPointerException` or compilation error when referencing `getBoundingClientRect`

**Phase to address:** Phase 3 (Bullet Tree) — evaluate Reorderable library in a tech spike before deciding to hand-roll.

---

### Pitfall 5: Express CORS Disabled in Production — Wrong Diagnosis When Android Calls Fail

**What goes wrong:**
The Express CORS config (`server/src/app.ts` line 24) sets `origin: process.env.NODE_ENV === 'development' ? 'http://localhost:5173' : false`. In production, CORS is entirely disabled. OkHttp (native Android HTTP client) does NOT send an `Origin` header, so CORS never activates. However, when Android API calls fail for unrelated reasons (wrong base URL, certificate error, missing auth header), developers sometimes diagnose the failure as "CORS issue" and add an `Origin` header to "fix" it. This activates Express CORS handling where there was none — Express sees an `Origin` header with `cors: false` config and may reject the request with a 500 or return no `Access-Control-Allow-Origin` header, making the actual error harder to diagnose.

**Why it happens:**
CORS is a browser security concept. OkHttp is not a browser. Developers coming from web background assume CORS applies universally and debug Android network failures through that lens.

**How to avoid:**
Do NOT manually set an `Origin` header in the OkHttp client. If the server needs to identify Android vs web clients, use `X-Client: android` rather than spoofing `Origin`. When debugging Android network failures, check: (1) base URL is exactly `https://notes.gregorymaingret.fr` with no trailing slash, (2) `Authorization: Bearer <token>` header is present, (3) TLS certificate is valid (Let's Encrypt auto-renews — verify the cert is current).

**Warning signs:**
- Android API calls return 500 only after an `Origin` header was added to OkHttp
- OkHttp logs show `Origin: android-app://...` in request headers — this should never appear
- The same endpoint works in Postman (which also does not send `Origin`) but fails from the app

**Phase to address:** Phase 1 (Foundation) — OkHttp client configuration, documented in code comments.

---

### Pitfall 6: Debounced Content Save + Undo Checkpoint Cancelled on Navigation

**What goes wrong:**
The web client debounces bullet content saves (the `PATCH /api/bullets/:id` call) and after a delay posts an undo checkpoint to `POST /api/bullets/:id/undo-checkpoint`. On Android, these are coroutines launched in `viewModelScope`. When the user navigates away, `viewModelScope` is cancelled (`onCleared()` is called), cancelling all child coroutines. If the user types and immediately navigates away within the debounce window (~500ms), both the content save and the undo checkpoint are dropped. The text typed in those last few seconds is lost, and the server-side undo history is incomplete.

**Why it happens:**
`viewModelScope` uses `SupervisorJob` + `Dispatchers.Main` — all child coroutines are cancelled when the ViewModel is cleared. A debounce-delay coroutine is a direct child of this scope.

**How to avoid:**
Override `onCleared()` in the BulletTree ViewModel to explicitly flush any pending save before the scope is cancelled. Execute the flush with `withContext(NonCancellable)` or via `runBlocking(Dispatchers.IO)` so it is not cancelled when `viewModelScope` is cleared.

```kotlin
override fun onCleared() {
    super.onCleared()
    // Flush any pending debounced content saves synchronously before scope dies
    pendingContentSave?.let { (bulletId, content) ->
        runBlocking(Dispatchers.IO) {
            api.patchBulletContent(bulletId, content)
            api.postUndoCheckpoint(bulletId, content, previousContent)
        }
    }
}
```

**Warning signs:**
- Text entered just before pressing the back button is not visible when returning to the document
- Server-side undo skips back multiple content states instead of one after a navigate-away-and-return sequence
- The bug only reproduces with fast navigation (within ~500ms of the last keystroke); slow navigation always saves correctly

**Phase to address:** Phase 3 (Bullet Tree) for content save flush; Phase 4 (Reactivity & Polish) for end-to-end navigation testing.

---

### Pitfall 7: Swipe Gesture Conflict With LazyColumn Vertical Scroll

**What goes wrong:**
The bullet list is a `LazyColumn` with swipe-to-complete (right) and swipe-to-delete (left) gestures on each row. LazyColumn owns vertical scroll. If swipe gestures are implemented naively with `detectHorizontalDragGestures`, a diagonal touch (common when users intend to scroll) can be misidentified as a horizontal swipe, completing or deleting a bullet the user wanted to scroll past. Conversely, if scroll sensitivity is too high, deliberate horizontal swipes are intercepted by the LazyColumn scroll system before the swipe threshold is reached.

**Why it happens:**
Compose gesture detection resolves conflicts based on which detector first "wins" pointer input. Without explicit directional discrimination, a 30-degree diagonal touch can trigger the horizontal detector even if the user intended vertical scroll.

**How to avoid:**
Use `pointerInput` with a custom gesture detector that measures the initial displacement vector: only arm the horizontal swipe handler if `abs(deltaX) > abs(deltaY) * 2` (i.e., the gesture is more than 2x more horizontal than vertical) within the first 16dp of movement. This is the same directional discrimination used in the web `gestures.ts` implementation. The web version uses `totalDeltaX > 40` as its activation threshold — map that proportionally to dp on Android. Reject gestures that have a vertical component before the horizontal threshold is met.

**Warning signs:**
- Bullets are accidentally completed or deleted when scrolling rapidly through a long list
- Deliberate horizontal swipes are intercepted and interpreted as scroll — no color background appears
- The swipe visual feedback (growing green/red background) appears momentarily then disappears on diagonal touches

**Phase to address:** Phase 4 (Reactivity & Polish) — implement swipe gestures with directional discrimination from the start.

---

### Pitfall 8: Gradle Added to Non-Standard Monorepo Location — Root Project Pollution

**What goes wrong:**
The current repo root (`/`) contains `package.json`, `Dockerfile`, `docker-compose.yml`, `server/`, and `client/` — a Node.js monorepo. Adding an Android project means adding a `settings.gradle.kts` at the repo root OR in a subdirectory (e.g., `/android/`). If `settings.gradle.kts` is placed at the repo root, Gradle will attempt to treat the entire repo as a Gradle project. It will scan `server/node_modules` and `client/node_modules` looking for subprojects, potentially taking minutes for the configuration phase. GitHub Actions CI will also need to detect whether a push affects Android or Node to avoid running Gradle builds on every web-only change.

**Why it happens:**
The standard Android Studio "Add Android module to existing project" workflow assumes a Gradle-native monorepo. Placing `settings.gradle.kts` at the repo root is the standard Android approach, but it conflicts with an existing Node.js root.

**How to avoid:**
Place the entire Android project in a `/android/` subdirectory with its own `settings.gradle.kts` — completely isolated from the Node.js project. All `./gradlew` commands are run from `/android/`. GitHub Actions should have separate workflow files: one for Node/Docker changes (triggered on `server/**` or `client/**` changes), one for Android changes (triggered on `android/**` changes). Gradle build cache should be scoped to `~/.gradle/caches` and keyed to `android/gradle/wrapper/gradle-wrapper.properties` + `android/build.gradle.kts` hash.

**Warning signs:**
- `./gradlew` at the repo root attempts to configure `server/` or `client/` as Gradle subprojects
- GitHub Actions runs a 10-minute Gradle build on a commit that only changed `server/src/routes/auth.ts`
- `node_modules` symlinks appear in Gradle's project scan output

**Phase to address:** Phase 1 (Foundation) — directory structure decision must be made before any Gradle files are written.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| In-memory-only CookieJar (no persistence) | Simpler auth setup | User logged out on every cold start | Never — users expect sessions to persist |
| Hardcoded `https://notes.gregorymaingret.fr` base URL | One less config layer | Cannot point at local dev server for testing | Never — use `BuildConfig.BASE_URL` |
| Missing `key { bullet.id }` in LazyColumn items | Less boilerplate | Full list recomposition on any mutation; broken drag animations | Never for a list with drag-drop |
| `GlobalScope` for API calls | Quick coroutine launch | Calls continue after ViewModel cleared; can crash with stale UI references | Never |
| Copy/paste Bullet model across layers | Faster initial build | Model changes require updates in multiple places; breaks Clean Architecture | Only for a single-phase prototype, refactor before Phase 4 |
| `runBlocking` in `Authenticator` without `Mutex` | Simpler token refresh | Race condition on concurrent 401s (see Pitfall 1) | Never |
| Skipping `EncryptedSharedPreferences` — using plain `SharedPreferences` for access token | Simpler storage | Token readable via ADB backup on non-encrypted devices | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Express `/api/auth/refresh` | Not attaching the same OkHttp instance (with CookieJar) to the Retrofit client used for the refresh call | Use a single OkHttp singleton via Hilt — CookieJar must be on the same instance used for all requests |
| Express `/api/auth/refresh` | Using an `Interceptor` (fires before response) instead of `Authenticator` (fires after 401) for token refresh | Always use `Authenticator` for token refresh; `Interceptor` only for attaching existing tokens |
| Express `/api/bullets` POST | Sending `afterId: undefined` — Kotlin serializes `null` correctly but omitting the field may not match Zod schema expectations | Always include `afterId: null` explicitly in the Kotlin request data class; do not rely on default omission |
| Express `/api/bullets` POST | Sending `parentId` as absent (not serialized) instead of `null` for top-level bullets | Define `parentId: String? = null` in the Kotlin request data class; Retrofit/Gson/Moshi serialize `null` as JSON `null` |
| Express auth rate limiter | Sending burst of concurrent requests on app startup (document list + bullets simultaneously) triggers the 20 req/15min rate limit | Rate limit is on `/api/auth/*` only — startup data requests go to `/api/documents` and `/api/bullets`, not affected |
| Express CORS disabled in production | Adding `Origin` header to "fix" CORS issues that don't exist for OkHttp | Never add `Origin` to OkHttp headers; CORS is browser-only |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| `flattenTree` computed in a Composable (not ViewModel) | Every scroll or focus change re-flattens the entire tree | Derive flat list as a `StateFlow` in the ViewModel via `.map { flattenTree(it) }`; Composable only observes | Documents with > 50 bullets |
| Missing `key { it.id }` in `LazyColumn items { }` | Scroll position jumps after any mutation; drag animations incorrect | Always pass `key = { it.id }` to every `items()` call | Every list mutation |
| `collectAsState()` instead of `collectAsStateWithLifecycle()` | UI state collectors run in background when screen is not visible; CPU waste; potential stale-state crashes | Use `collectAsStateWithLifecycle()` from `lifecycle-runtime-compose` everywhere | Background lifecycle transitions |
| Nested `LazyColumn` inside a `LazyColumn item { }` | Inner LazyColumn loses lazy behavior; `IllegalStateException` on measurement | Never nest LazyColumn — tree is already flattened to a single list by `flattenTree` | Always |
| Network call on Main thread | `NetworkOnMainThreadException` crash | All Retrofit calls are `suspend fun`; call only from `viewModelScope.launch(Dispatchers.IO)` | Always on Android 3.0+ |
| Rebuilding `BulletMap` from flat list on every state update | O(n) allocation per keystroke when typing in a bullet | `buildBulletMap` should be recomputed only when the list itself changes — use `derivedStateOf` or `StateFlow.map` with a structural equality check | Documents with > 100 bullets and fast typing |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing access token in plain `SharedPreferences` | Token readable via ADB backup or on rooted devices | Always use `EncryptedSharedPreferences` (Jetpack Security, API 23+); consider in-memory only for the short-lived access token |
| Logging JWT tokens in Logcat | Token visible in `adb logcat` — trivial to capture in dev | Use `HttpLoggingInterceptor.Level.HEADERS` max in debug, `NONE` in release; never log the Authorization header value |
| Sending `refreshToken` cookie to all endpoints | If any endpoint mishandles the cookie, it could be captured | Scope the `CookieJar` so it only sends the `refreshToken` cookie to `/api/auth/*` paths |
| Certificate pinning on self-hosted Let's Encrypt cert | Pin expires when cert renews (every 90 days) | For a personal self-hosted app, certificate pinning is not required; if added later, pin to the CA (ISRG Root X1) not the leaf cert, and implement a backup pin |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Loading spinner on every bullet operation | Constant flicker; outliner UX requires instant feedback | Optimistic updates: mutate local state immediately, roll back on API error with a `Snackbar` undo option |
| Keyboard appears and pushes bullet list up without focused bullet scrolling into view | Focused bullet hidden behind the keyboard | `WindowCompat.setDecorFitsSystemWindows(window, false)` + `Modifier.imePadding()` on the list + `LazyListState.animateScrollToItem()` on focus change |
| Long-press triggers both context menu and drag start simultaneously | Unpredictable behavior; context menu flashes and closes | Use `detectDragGesturesAfterLongPress` — context menu fires on long-press-without-movement; drag starts only after movement detected post long-press |
| No empty state for a document with zero bullets | Blank screen with no affordance | Show a placeholder prompt ("Tap to add your first bullet"); the web app has this behavior — match it |
| Dynamic color theme changes the brand appearance unpredictably on Android 12+ | App looks different on every device based on wallpaper color | Explicitly opt out of dynamic color when the user has a specific palette preference; provide a consistent fallback theme for all devices |

---

## "Looks Done But Isn't" Checklist

- [ ] **Token refresh:** After 15 minutes, the app transparently retries the failed request without the user seeing a 401 error — test by shortening `ACCESS_TOKEN_TTL` to `'30s'` in a dev backend build
- [ ] **Refresh cookie persistence:** Kill the app process completely (not just background), reopen it, confirm the user is still logged in — the refresh cookie must survive process death via `EncryptedSharedPreferences`
- [ ] **Concurrent 401 handling:** Expire the access token, then tap rapidly on three different documents — server logs must show exactly one POST to `/api/auth/refresh`, not three
- [ ] **Collapsed subtree:** Collapse a bullet with 3+ levels of children, scroll away and back — none of the descendants are visible
- [ ] **Drag-drop depth change:** Drag a top-level bullet and, while dragging, move the finger right to indent it — verify it lands as an indented child, not a sibling
- [ ] **Debounce flush on navigate:** Type in a bullet, immediately press back before 500ms, return to the document — the text must be saved
- [ ] **Dark mode:** Toggle system dark mode while the app is open — all surfaces update immediately with no light-mode flash
- [ ] **Note field:** The `PATCH /api/bullets/:id` with `note: ""` (empty string) should be normalized to `null` by the server — verify an empty note clears correctly
- [ ] **`afterId` null vs. absent:** Creating a bullet as the first child of a parent (no preceding sibling) must send `afterId: null` in the JSON body, not omit the field
- [ ] **Swipe direction lock:** A 45-degree diagonal touch on a bullet row triggers neither swipe nor scroll — it should be consumed by scroll; only near-horizontal touches arm the swipe detector

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Token refresh race condition shipped to production | HIGH | Replace Authenticator implementation with Mutex version; affects all network calls; full regression test of auth flows including concurrent requests |
| httpOnly cookie not persisted (user logged out on restart) | MEDIUM | Replace CookieJar implementation; no data loss; test all auth endpoints including cold-start session restore |
| flattenTree bug (wrong bullets visible) | HIGH | Algorithm underlies all tree operations; fixing requires re-testing collapse, drag, zoom, and indent/outdent |
| Debounce flush on navigate missing | MEDIUM | Add `onCleared` flush; test all navigation paths that exit a document mid-edit |
| Missing `key {}` in LazyColumn | LOW | Add `key = { it.id }` to `items()` call; Compose reconciles on next composition |
| Gradle root pollution from wrong `settings.gradle.kts` location | MEDIUM | Move Android project to `/android/` subdirectory; update all relative paths in build scripts and CI workflows |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Token refresh race condition (Pitfall 1) | Phase 1 (Foundation) | Unit test: 5 concurrent requests with expired token; assert server receives exactly 1 POST to `/api/auth/refresh` |
| httpOnly cookie dropped by CookieJar (Pitfall 2) | Phase 1 (Foundation) | Integration test: login, kill app, reopen; assert session restored via `/api/auth/refresh` |
| flattenTree collapsed subtree leak (Pitfall 3) | Phase 3 (Bullet Tree) | Unit test: collapsed parent with grandchildren; assert grandchildren absent from flat list |
| computeDragProjection DOM dependency (Pitfall 4) | Phase 3 (Bullet Tree) | Manual test: drag across 3 depth levels; drop target matches visual indicator |
| Accidental CORS Origin header (Pitfall 5) | Phase 1 (Foundation) | OkHttp logs show no `Origin` header in any request |
| Debounce flush on navigate (Pitfall 6) | Phase 3 + Phase 4 | E2E test: type → press back immediately → return to document; text is saved |
| Swipe/scroll gesture conflict (Pitfall 7) | Phase 4 (Reactivity & Polish) | Manual test: vertical scroll through 50 bullets with no accidental completions or deletions |
| Gradle root pollution (Pitfall 8) | Phase 1 (Foundation) | `./gradlew` at repo root fails or is absent; all Gradle commands run from `/android/` |
| Missing LazyColumn keys | Phase 3 (Bullet Tree) | Code review: every `items { }` block has `key = { it.id }` |
| ViewModel scope cancellation drops in-flight saves | Phase 3 + Phase 4 | LeakCanary shows no ViewModel leaks; E2E navigation test confirms content persistence |

---

## Sources

- Backend auth contracts (HIGH confidence — direct inspection): `server/src/services/authService.ts` (`sameSite: 'strict'`, `httpOnly: true`, 15-min access TTL), `server/src/routes/auth.ts`, `server/src/middleware/auth.ts`
- Backend CORS config (HIGH confidence — direct inspection): `server/src/app.ts` lines 23-27 (`origin: false` in production)
- Tree algorithm (HIGH confidence — direct inspection): `client/src/components/DocumentView/BulletTree.tsx` `flattenTree`, `computeDragProjection`
- OkHttp httpOnly cookie issue: [square/okhttp GitHub issue #2725](https://github.com/square/okhttp/issues/2725)
- OkHttp Authenticator + Mutex pattern: [hoc081098/Refresh-Token-Sample](https://github.com/hoc081098/Refresh-Token-Sample) — MEDIUM confidence (widely referenced)
- LazyColumn keys / performance: [Android Developers — Best practices for Compose](https://developer.android.com/develop/ui/compose/performance/bestpractices) — HIGH confidence (official docs)
- Gesture conflict in Compose: [Android Developers — Drag, swipe, and fling](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-swipe-fling) — HIGH confidence (official docs)
- Material 3 theming: [Android Developers — Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) — HIGH confidence (official docs)
- viewModelScope cancellation: [Android Developers — Coroutines with lifecycle-aware components](https://developer.android.com/topic/libraries/architecture/coroutines) — HIGH confidence (official docs)
- EncryptedSharedPreferences security: [Secure JWT Storage in Android — Jan 2025](https://jyotishgher.medium.com/secure-jwt-storage-in-android-69ed1368ed2c) — MEDIUM confidence
- Gradle monorepo structure: [Common modularization patterns — Android Developers](https://developer.android.com/topic/modularization/patterns) — HIGH confidence (official docs)
- Reorderable library: [Calvin-LL/Reorderable on GitHub](https://github.com/Calvin-LL/Reorderable) — MEDIUM confidence (community-maintained, active)

---
*Pitfalls research for: Native Android Kotlin/Jetpack Compose client added to Express + PostgreSQL backend*
*Researched: 2026-03-12*
