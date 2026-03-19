# Architecture Research

**Domain:** Robustness & Quality improvements to existing Notes app (v2.3)
**Researched:** 2026-03-19
**Confidence:** HIGH (based on direct source-code inspection)

## Standard Architecture

### System Overview (Existing + New Components)

```
┌─────────────────────────────────────────────────────────────────────┐
│                          GitHub Actions CI                           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────────┐  │
│  │  android-ci.yml  │  │  server-ci.yml   │  │  client-ci.yml   │  │
│  │  (existing)      │  │  (NEW)           │  │  (NEW)           │  │
│  └──────────────────┘  └──────────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         React Client (Vite)                          │
├─────────────────────────────────────────────────────────────────────┤
│  main.tsx                                                            │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  BrowserRouter > QueryClientProvider > AuthProvider            │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  ErrorBoundary (NEW — wraps App)                         │  │  │
│  │  │  ┌────────────────────────────────────────────────────┐  │  │  │
│  │  │  │  ToastProvider (NEW — wraps App for toast context) │  │  │  │
│  │  │  │  ┌──────────────────────────────────────────────┐  │  │  │  │
│  │  │  │  │  App → Routes → RequireAuth → AppPage        │  │  │  │  │
│  │  │  │  └──────────────────────────────────────────────┘  │  │  │  │
│  │  │  └────────────────────────────────────────────────────┘  │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ApiClient (client/src/api/client.ts) — MODIFIED                    │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  request() → 401 detected → POST /api/auth/refresh           │    │
│  │           → apply new token → retry original request once    │    │
│  │           → second 401 → onAuthFailure() → redirect /login   │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                       │
│  BulletNode (MODIFIED — swipe gesture shell only after refactor)    │
│  BulletContent (MODIFIED — editing logic; toast on undo failure)    │
│  ┌──────────────────────┐  ┌───────────────────────────────────┐     │
│  │  Cursor helpers      │  │  Keyboard handler logic           │     │
│  │  (extracted, tested) │  │  (extractable sub-unit)           │     │
│  └──────────────────────┘  └───────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
          │  fetch + httpOnly cookie
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        Express Server                                │
├─────────────────────────────────────────────────────────────────────┤
│  app.ts → Routes → Services → Drizzle ORM                           │
│                                                                       │
│  Middleware stack (ORDER MATTERS):                                   │
│  helmet → cors → morgan → json → cookieParser                       │
│  → rateLimiters → passport → [routes] → errorMiddleware (NEW)       │
│                                                                       │
│  errorMiddleware (NEW — server/src/middleware/errors.ts)            │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  (err, req, res, next): void → { error: string } JSON        │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                       │
│  undoRouter (MODIFIED — return structured error when stack empty)   │
│  bulletService (MODIFIED — add recordUndoEvent for mark_complete,   │
│                             note edits, bulk delete)                │
└─────────────────────────────────────────────────────────────────────┘
          │  SQL
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  PostgreSQL + Drizzle ORM                                            │
│  undo_events, undo_cursors, bullets, documents, …                   │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Status |
|-----------|----------------|--------|
| `ApiClient.request()` | HTTP transport with auth header | MODIFIED — add 401 retry loop |
| `AuthContext` | Holds accessToken, manages session | MODIFIED — expose `refreshToken()` callable |
| `ErrorBoundary` | Catch render-phase React errors, show fallback UI | NEW |
| `ToastProvider` + `useToast` | Global ephemeral notifications for mutation failures | NEW |
| `BulletNode` | Swipe gestures, drag handle, layout shell | MODIFIED — wire toast on failures |
| `BulletContent` | Contenteditable editing, keyboard shortcuts | MODIFIED — call `useToast` on undo/redo failure |
| `UndoToast` | Per-bullet delete undo prompt (existing, fixed position) | UNCHANGED — different purpose |
| `server/middleware/errors.ts` | Express 4-arg error handler | NEW |
| `server/routes/undo.ts` | Undo/redo routes | MODIFIED — structured 422 when stack empty |
| `server/services/bulletService.ts` | Bullet CRUD + undo recording | MODIFIED — add undo events for new action types |
| `.github/workflows/server-ci.yml` | Lint + test + build for Express server | NEW |
| `.github/workflows/client-ci.yml` | Lint + type-check + build for React client | NEW |

## Recommended Project Structure Changes

```
.github/workflows/
├── android-ci.yml          # existing — unchanged
├── server-ci.yml           # NEW
└── client-ci.yml           # NEW

server/src/
├── app.ts                  # MODIFIED — register errorMiddleware last
├── middleware/
│   ├── auth.ts             # existing — unchanged
│   └── errors.ts           # NEW — Express error handler
├── routes/
│   └── undo.ts             # MODIFIED — return 422 on empty stack
└── services/
    └── bulletService.ts    # MODIFIED — add recordUndoEvent for new actions

client/src/
├── api/
│   └── client.ts           # MODIFIED — 401 interceptor + refresh retry
├── contexts/
│   └── AuthContext.tsx     # MODIFIED — expose refreshToken(), wire callback
├── components/
│   ├── ErrorBoundary.tsx   # NEW
│   ├── ToastProvider.tsx   # NEW (context + portal ToastContainer)
│   └── DocumentView/
│       ├── BulletNode.tsx  # MODIFIED (minor — use toast on failures)
│       └── BulletContent.tsx # MODIFIED — useToast on undo/redo failure
└── main.tsx                # MODIFIED — add ErrorBoundary + ToastProvider
```

### Structure Rationale

- **`server/src/middleware/errors.ts`:** Isolated so it can be imported and tested independently. Registered last in `app.ts` — Express identifies error middleware by the 4-argument `(err, req, res, next)` signature, and it must come after all route registrations.
- **`client/src/components/ErrorBoundary.tsx`:** React class component (error boundaries cannot be function components). Lives as a standalone file outside `App.tsx` to avoid re-mounting the app tree on re-renders.
- **`client/src/components/ToastProvider.tsx`:** Context-based so any component can call `useToast().show(...)` without prop drilling. Separate from `UndoToast` — that is a per-bullet positioned element with its own lifecycle; `ToastProvider` is a global notification layer rendered via portal.
- **`client/src/api/client.ts`:** All HTTP goes through this single class. 401 interception belongs here, not in individual mutation hooks — one change propagates to all callers automatically.

## Architectural Patterns

### Pattern 1: 401 Interceptor with Refresh Retry

**What:** When `ApiClient.request()` receives a 401, attempt `POST /api/auth/refresh` once using a shared `isRefreshing` flag to prevent parallel refresh races. Apply the new token, then retry the original request. On second 401, treat as definitive auth failure.

**When to use:** Any SPA with short-lived access tokens and httpOnly refresh cookies.

**Trade-offs:** Adds one extra round trip on the first 401 after token expiry. Prevents the user from ever seeing an invisible session expiry error as long as the refresh token is valid.

**Key integration — avoid circular import:**
```typescript
// client/src/api/client.ts
class ApiClient {
  private refreshCallback: (() => Promise<boolean>) | null = null;
  private authFailureCallback: (() => void) | null = null;

  setRefreshCallback(fn: () => Promise<boolean>) { this.refreshCallback = fn; }
  setAuthFailureCallback(fn: () => void) { this.authFailureCallback = fn; }

  async request<T>(path: string, options: RequestOptions & { _isRetry?: boolean } = {}): Promise<T> {
    // ... existing logic ...
    if (!res.ok && res.status === 401 && !options._isRetry && this.refreshCallback) {
      const ok = await this.refreshCallback();
      if (ok) return this.request(path, { ...options, _isRetry: true });
      this.authFailureCallback?.();
      throw Object.assign(new Error('Session expired'), { status: 401 });
    }
    // ... rest of existing logic ...
  }
}
```

```typescript
// client/src/contexts/AuthContext.tsx — in AuthProvider useEffect
useEffect(() => {
  apiClient.setRefreshCallback(async () => {
    const r = await fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' });
    if (r.ok) {
      const data = await r.json();
      applyToken(data.accessToken, data.user);
      return true;
    }
    return false;
  });
  apiClient.setAuthFailureCallback(() => {
    clearAuth();
  });
}, [applyToken, clearAuth]);
```

This avoids a circular import: `client.ts` imports nothing from React; `AuthContext.tsx` passes function references at runtime.

### Pattern 2: Express Centralized Error Middleware

**What:** A single `(err, req, res, next)` handler registered as the last middleware in `app.ts`. Catches any error thrown by route handlers that isn't already handled inline.

**When to use:** Any Express app beyond a few routes. Currently routes use a mix of inline try/catch with manual status decisions and bare `throw err` for fallthrough. The central handler catches the fallthrough cases.

**Trade-offs:** Inline status-code decisions (404 from "not found" string matching) stay in routes — the middleware only handles 500-class unhandled errors. Routes must `throw` or call `next(err)` rather than swallowing errors silently.

**Example:**
```typescript
// server/src/middleware/errors.ts
import type { ErrorRequestHandler } from 'express';

export const errorMiddleware: ErrorRequestHandler = (err, _req, res, _next) => {
  const status = (err as { status?: number }).status ?? 500;
  const message = err instanceof Error ? err.message : 'Internal server error';
  res.status(status).json({ error: message });
};

// server/src/app.ts — after all app.use('/api/...') route registrations
import { errorMiddleware } from './middleware/errors.js';
app.use(errorMiddleware);  // MUST be last
```

### Pattern 3: React Error Boundary

**What:** A class component wrapping the application tree. Catches render-phase errors that would otherwise crash the entire UI. Shows a user-friendly fallback with a "reload" action.

**When to use:** Every production React app. Especially valuable around complex derived-state components like `BulletTree` and `BulletContent`.

**Trade-offs:** Cannot catch async errors (those go through React Query `onError` callbacks and the toast system). Cannot be a function component — must be a class.

**Placement in `main.tsx`:**
```tsx
// ErrorBoundary wraps the App but is inside BrowserRouter/QueryClientProvider/AuthProvider
// so if recovery is needed those hooks remain available in the fallback.
<ErrorBoundary>
  <ToastProvider>
    <App />
  </ToastProvider>
</ErrorBoundary>
```

### Pattern 4: Context-Based Toast System

**What:** A React context that exposes `showToast(message, type)`. A `ToastContainer` component renders stacked toasts at a fixed viewport position via `createPortal`. Mutation `onError` callbacks and undo/redo failure handlers call `showToast`.

**When to use:** Global async failure notifications. Distinct from the existing `UndoToast`, which is a bullet-local confirmation prompt with its own dismiss/undo action.

**Trade-offs:** Multiple simultaneous toasts can stack — needs a bounded queue (e.g., max 3 visible). Auto-dismiss timers must be cleared in cleanup to avoid memory leaks.

### Pattern 5: Extended Undo Coverage via New UndoOp Calls

**What:** The undo system uses a discriminated union `UndoOp` and a `recordUndoEvent` function called inside db transactions. Adding coverage for `mark_complete`, note edits, and bulk delete means calling `recordUndoEvent` inside the corresponding service functions, following the exact pattern used for `create_bullet`, `indent`, etc.

**When to use:** Any bullet mutation a user would want to reverse.

**Example — mark_complete (inside existing transaction in bulletService):**
```typescript
await recordUndoEvent(tx, userId, 'mark_complete',
  { type: 'update_bullet', id, fields: { isComplete: newValue } },    // forwardOp
  { type: 'update_bullet', id, fields: { isComplete: prevValue } }    // inverseOp
);
```

The existing `UndoOp` union already includes `update_bullet` with a `fields` partial — no schema migration or new op types required.

## Data Flow

### Request Flow — Before v2.3 (Current)

```
[User action in BulletContent / hook]
    ↓
[React Query mutation — onError not wired to any user feedback]
    ↓
[ApiClient.request()]
    ↓
fetch() → [Express route] → [Service] → [Drizzle] → [PostgreSQL]
    ↑
[Error thrown → React Query catches it → swallowed silently]
```

### Request Flow — After v2.3

```
[User action]
    ↓
[React Query mutation hook — onError → useToast().show(err.message)]
    ↓
[ApiClient.request()]
    │ 401? → POST /api/auth/refresh (once, guarded by _isRetry flag)
    │   OK → apply new token → retry original request
    │   Fail → clearAuth() → redirect /login
    ↓
fetch() → [Express route] → [Service] → [Drizzle] → [PostgreSQL]
    ↑
[Response — on error: standardized { error: string } JSON from errorMiddleware]
    ↑
[ApiClient throws Error with .status and .data.error populated]
    ↑
[React Query onError → useToast().show(err.message, 'error')]
```

### Undo Data Flow (Extended Actions)

```
[Ctrl+Z or undo button]
    ↓
[POST /api/undo]
    ↓
[undoService.undo(db, userId)]
    ├── fetch event at currentSeq
    ├── applyOp(inverseOp)  ← now includes: mark_complete, note_edit, bulk_delete
    └── decrement cursor → return UndoStatus
    ↓
[Client: queryClient.invalidateQueries(['bullets'])]
    ↓
[React re-renders with restored state]
    │
    if (error) → useToast().show('Nothing left to undo', 'info')
```

### CI Data Flow (New)

```
[git push to phase-* branch]
    ↓
GitHub Actions triggers — path-filtered:
    ├── android-ci.yml  (paths: android/**)  → unit tests + assembleDebug
    ├── server-ci.yml   (paths: server/**)   → lint + tests + build
    └── client-ci.yml   (paths: client/**)   → lint + type-check + build
    ↓
[PR to main: all three CIs run on changed paths]
    ↓
[All checks green → human reviews → merge to main]
    ↓
[Manual deploy: scp + docker compose up + confirm + git pull]
```

## Integration Points

### New vs. Modified Components

| Component | Change | Integration Point |
|-----------|--------|------------------|
| `client/src/api/client.ts` | MODIFIED | Add `setRefreshCallback` and `setAuthFailureCallback` methods; 401 guard inside `request()` |
| `client/src/contexts/AuthContext.tsx` | MODIFIED | Wire callbacks in `useEffect`; add `refreshToken()` logic reused from existing mount effect |
| `client/src/main.tsx` | MODIFIED | Add `<ErrorBoundary>` and `<ToastProvider>` wrapping `<App />` |
| `client/src/components/ErrorBoundary.tsx` | NEW | Standalone; plugs into `main.tsx` provider stack |
| `client/src/components/ToastProvider.tsx` | NEW | Context + portal container; all mutation hooks inside `App` subtree can call `useToast()` |
| `client/src/components/DocumentView/BulletContent.tsx` | MODIFIED | `handleUndo`/`handleRedo` catch block calls `useToast` |
| `server/src/middleware/errors.ts` | NEW | Registered in `app.ts` after all route `app.use()` calls |
| `server/src/app.ts` | MODIFIED | `app.use(errorMiddleware)` as final statement before `return app` |
| `server/src/routes/undo.ts` | MODIFIED | Return `res.status(422).json({ error: 'Nothing to undo' })` when stack is empty |
| `server/src/services/bulletService.ts` | MODIFIED | Add `recordUndoEvent` calls in `markComplete`, `patchBullet` (for note field), and any bulk-delete path |
| `.github/workflows/server-ci.yml` | NEW | Triggers on `push: paths: ['server/**']` and `pull_request: branches: [main]` |
| `.github/workflows/client-ci.yml` | NEW | Triggers on `push: paths: ['client/**']` and `pull_request: branches: [main]` |

### Internal Boundaries and Constraints

| Boundary | Communication | Constraint |
|----------|---------------|-----------|
| `ApiClient` ↔ `AuthContext` | Callback injection at runtime | `client.ts` must not import React or AuthContext — circular dependency. Use `setRefreshCallback()` pattern. |
| `ToastProvider` ↔ mutation hooks | React context via `useToast()` | Hook must be called within `ToastProvider` subtree. All mutations run inside `App`, which is inside `ToastProvider`. |
| `errorMiddleware` ↔ routes | Express `throw` or `next(err)` | Must be registered AFTER all `app.use('/api/...')` route registrations. |
| `bulletService` ↔ `undoService` | Direct function calls inside db transaction | Existing pattern. New undo event calls must stay inside the same `db.transaction()` as the mutation. |
| `BulletContent.handleUndo()` ↔ undo route | Direct `apiClient.post('/api/undo')` | Intentionally bypasses React Query cache to avoid optimistic state conflicts. |
| `UndoToast` ↔ `ToastProvider` | Independent — no connection | Keep `UndoToast` as-is. It handles per-bullet delete confirmation. `ToastProvider` handles system-level errors. |

## Suggested Build Order

Build order is determined by dependencies between layers. Independent streams can proceed in parallel.

### Phase A — Server Foundation (no cross-dependencies)

1. **`server/src/middleware/errors.ts` + register in `app.ts`**
   Zero-risk additive change. All routes already `throw` unhandled errors; currently Express returns HTML. After this they return JSON. No route changes needed.

2. **`server/src/routes/undo.ts` structured error response**
   Small change: add `if (currentSeq === 0) return res.status(422).json({ error: 'Nothing to undo' })` before calling the service. Alternatively this can be handled by the service throwing a structured error caught by the new errorMiddleware.

3. **`.github/workflows/server-ci.yml` and `client-ci.yml`**
   Purely additive. Touch no source files.

### Phase B — Client Infrastructure (independent of Phase A)

4. **`client/src/components/ToastProvider.tsx` + `useToast` hook**
   New file, zero breakage risk.

5. **`client/src/components/ErrorBoundary.tsx`**
   New file, zero breakage risk.

6. **Update `client/src/main.tsx`**
   Wrap existing `<App />` with `<ErrorBoundary><ToastProvider>...</ToastProvider></ErrorBoundary>`. Two lines added.

### Phase C — 401 Token Refresh (depends on B.4 for toast on auth failure)

7. **Extend `AuthContext` with `refreshToken()` and callback wiring**
   Add a `useEffect` that calls `apiClient.setRefreshCallback(...)` and `apiClient.setAuthFailureCallback(...)`. The `refreshToken` logic reuses the existing silent-refresh fetch pattern.

8. **Modify `ApiClient.request()` with 401 interceptor**
   Add `setRefreshCallback`, `setAuthFailureCallback`, `isRefreshing` flag, and `_isRetry` option. The auth failure callback triggers `clearAuth()` which already redirects to `/login` via `RequireAuth`.

### Phase D — Undo Extension (depends on Phase A.1 for error propagation)

9. **Extend `bulletService.ts` with `recordUndoEvent` for `markComplete`, note patch, bulk delete**
   Each addition is atomic inside an existing transaction. Follow the existing `create_bullet` recording pattern. No schema migration needed — `update_bullet` op type already exists.

10. **Wire `useToast` in `BulletContent.handleUndo/handleRedo`**
    Catch errors from `apiClient.post('/api/undo')` and show toast. Depends on Phase B.4.

### Phase E — Component Refactor (independent, run last — regression risk)

11. **`BulletContent` / `BulletNode` decomposition**
    Extract cursor helpers (`isCursorAtStart`, `isCursorAtEnd`, `splitAtCursor`, `setCursorAtPosition`) into a `cursorUtils.ts` utility file. Add unit tests. No behavior change — pure refactor. Run after all other phases so a refactor regression doesn't block other work.

### Dependency Graph

```
A.1 (errorMiddleware)  ────────────────────────────────────────→ D.9 (undo extension)
A.2 (undo route 422)   ────────────────────────────────────────→ D.10 (toast on failure)
A.3 (CI workflows)     → independent
B.4 (ToastProvider)    ──→ B.6 (main.tsx wiring) ──→ C.7/C.8 (auth failure toast)
                                                   ──→ D.10 (undo failure toast)
B.5 (ErrorBoundary)    ──→ B.6 (main.tsx wiring)
C.7 (AuthContext)      ──→ C.8 (ApiClient 401)
E   (refactor)         → independent (run last)
```

## Anti-Patterns

### Anti-Pattern 1: Circular Import Between ApiClient and AuthContext

**What people do:** Import `apiClient` from `client.ts` inside `AuthContext.tsx`, then import `AuthContext` functions back into `client.ts` to call `clearAuth()` on 401.

**Why it's wrong:** Creates a circular ES module dependency. Vite handles some circular imports but initialization order is undefined — `apiClient` may be `undefined` when `AuthContext` first imports it.

**Do this instead:** Callback injection. `ApiClient` exposes `setRefreshCallback(fn)` and `setAuthFailureCallback(fn)`. `AuthContext` calls these inside `useEffect` after mounting, passing its own functions. `client.ts` imports nothing from React.

### Anti-Pattern 2: Error Middleware Registered Before Routes

**What people do:** Call `app.use(errorMiddleware)` near the top of `app.ts` setup alongside other middleware.

**Why it's wrong:** Express only invokes a 4-argument middleware when an error has been passed via `next(err)`. If registered before routes, the regular request flow never passes through it. Errors fall through to Express's default HTML error handler.

**Do this instead:** Register `app.use(errorMiddleware)` as the very last `app.use()` call, after all route registrations. The current `app.ts` factory returns `app` after routes are mounted in `index.ts` — errorMiddleware must be registered in `app.ts` after the health check but before `return app`, then routes are mounted on top in `index.ts`. Actually: since routes are mounted in `index.ts` after `createApp()`, errorMiddleware in `app.ts` would register before routes. The correct fix is to either move errorMiddleware registration to `index.ts` (after route mounting) or use a late-binding pattern. Register it in `index.ts` as the last `app.use()` call.

### Anti-Pattern 3: Merging ToastProvider with UndoToast

**What people do:** Extend the existing `UndoToast` component into a general notification system.

**Why it's wrong:** `UndoToast` renders inside `BulletNode`'s DOM subtree at a fixed bottom-80 position with bullet-specific callbacks (`handleUndo`, `onDismiss`). Generalizing it would require passing context and positioning logic it wasn't designed for.

**Do this instead:** Keep `UndoToast` as-is. Build `ToastProvider` separately as a global context with a portal-rendered container at the `document.body` level, independent of the bullet tree.

### Anti-Pattern 4: Infinite Retry Loop on 401

**What people do:** Retry the original request on 401 without a guard, causing the refresh request itself to also receive 401 and triggering recursion.

**Why it's wrong:** If the refresh token is expired, `/api/auth/refresh` returns 401. Without a `_isRetry: true` guard on the retried request, the interceptor fires again and again.

**Do this instead:** Set `_isRetry: true` on the retried request. If it still returns 401, call `onAuthFailure()` and do not retry. The Android client already implements this exact pattern (commit `0457017`: "only clear tokens on definitive auth failures, not transient errors").

### Anti-Pattern 5: Registering errorMiddleware Too Early (app.ts vs index.ts)

**What people do:** Add `app.use(errorMiddleware)` inside `createApp()` in `app.ts`, expecting it to catch errors from routes mounted later in `index.ts`.

**Why it's wrong:** Express processes middleware in registration order. `createApp()` returns `app` before routes are mounted in `index.ts`. Errors thrown in routes registered after `createApp()` returns cannot reach middleware registered inside `createApp()`.

**Do this instead:** Register `app.use(errorMiddleware)` in `index.ts` as the last `app.use()` call, after all route registrations.

## Scaling Considerations

This is a self-hosted single-to-small-team app. These improvements do not affect scaling posture — they are reliability improvements, not architectural changes.

| Scale | Notes |
|-------|-------|
| 0-10 users | Current Docker monolith is correct. CI adds a safety net. |
| 10-100 users | No changes needed beyond current architecture. |
| 100k+ users | Out of scope for self-hosted personal tool. |

## Sources

- Direct source inspection (HIGH confidence):
  - `client/src/api/client.ts`
  - `client/src/contexts/AuthContext.tsx`
  - `client/src/main.tsx`
  - `server/src/app.ts`
  - `server/src/index.ts`
  - `server/src/routes/undo.ts`
  - `server/src/services/undoService.ts`
  - `server/src/services/bulletService.ts` (lines 1-60 reviewed)
  - `client/src/components/DocumentView/BulletNode.tsx`
  - `client/src/components/DocumentView/BulletContent.tsx`
  - `client/src/components/DocumentView/UndoToast.tsx`
  - `.github/workflows/android-ci.yml`
  - `.planning/PROJECT.md`
- Android 401 retry guard pattern: commit `0457017` ("fix(android): only clear tokens on definitive auth failures, not transient errors") — HIGH confidence

---
*Architecture research for: Notes app v2.3 Robustness & Quality*
*Researched: 2026-03-19*
