# Pitfalls Research

**Domain:** Adding CI/CD, token refresh interceptor, error boundaries, toast notifications, API error standardization, undo coverage extension, and component refactoring to an existing Express + React app (Notes v2.3).
**Researched:** 2026-03-19
**Confidence:** HIGH — all pitfalls grounded in the actual codebase (`client.ts`, `AuthContext.tsx`, `undoService.ts`, `BulletContent.tsx`, `BulletNode.tsx`, `bullets.ts` route, existing `android-ci.yml`)

---

## Critical Pitfalls

### Pitfall 1: Token Refresh Race Condition — Multiple Concurrent 401s

**What goes wrong:**
`ApiClient.request()` in `client.ts` has no 401 interception logic today. When you add a refresh interceptor naively, a race condition emerges: if five requests fire simultaneously and the access token is expired, all five receive 401 simultaneously. All five then independently call `/api/auth/refresh`. The first succeeds and sets a new token; the remaining four hit refresh with the now-rotated refresh token. The server's refresh token revocation (implemented in v2.2) rejects the stale token as a replay attack. Three or four requests cause the server to revoke the refresh cookie and the user is logged out unexpectedly.

**Why it happens:**
Developers add "retry with refresh on 401" logic inside `request()` without a shared in-flight lock. Each `request()` call is independent; there is no coordination across concurrent callers.

**How to avoid:**
Use a single promise lock for the refresh operation. While a refresh is in flight, all subsequent 401-triggered retries must `await` that same promise rather than starting a new refresh call:

```typescript
private refreshPromise: Promise<string> | null = null;

async refreshToken(): Promise<string> {
  if (this.refreshPromise) return this.refreshPromise;
  this.refreshPromise = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
    .then(r => r.ok ? r.json() : Promise.reject(new Error('Refresh failed')))
    .then(data => { this.setToken(data.accessToken); return data.accessToken; })
    .finally(() => { this.refreshPromise = null; });
  return this.refreshPromise;
}
```

Queue all failed requests to retry after the single refresh resolves.

**Warning signs:**
- Intermittent logouts after a period of inactivity (token expired, multiple requests in flight)
- Two `POST /api/auth/refresh` calls appearing in the network tab at the same timestamp
- Users report being kicked out while actively using the app (not during idle)

**Phase to address:** Token Refresh Interceptor phase

---

### Pitfall 2: Infinite Refresh Loop on Persistent 401s

**What goes wrong:**
The refresh interceptor retries a 401 by refreshing the token and re-issuing the original request. If the re-issued request also returns 401 (e.g., the user's session was revoked server-side, or the endpoint returns 401 for reasons unrelated to token expiry), the interceptor retries again — infinite loop. The refresh rate limiter in `app.ts` (60 req / 15min window) eventually blocks the user with 429, after which they cannot log back in.

**Why it happens:**
The retry logic does not distinguish between "401 because access token was stale" (refresh should help) and "401 because auth is definitively invalid" (must give up). Without a `_retried` flag, the loop is unbounded.

**How to avoid:**
Track a `_retried` flag on the request options. Only attempt the refresh once per original request. If the retry after refresh also returns 401, clear auth state and redirect to `/login` immediately:

```typescript
if (res.status === 401 && !options._retried) {
  try {
    await this.refreshToken();
    return this.request(path, { ...options, _retried: true });
  } catch {
    this.onAuthExpired?.(); // notify AuthContext to clear state
    throw error;
  }
}
```

**Warning signs:**
- Network tab shows rapid repeated calls to `/api/auth/refresh` (more than one)
- 429 responses on `/api/auth/refresh`
- App becomes unresponsive or freezes after a session expires

**Phase to address:** Token Refresh Interceptor phase

---

### Pitfall 3: AuthContext and ApiClient Diverge After Background Token Refresh

**What goes wrong:**
`AuthContext` owns `accessToken` React state and calls `apiClient.setToken()` on login and on the initial silent refresh. The 401 interceptor in `ApiClient` will refresh the token internally and call `apiClient.setToken()` — but `AuthContext`'s React state still holds the old value. Components that read `useAuth().accessToken` directly receive a stale token. If any future feature checks `accessToken !== null` to decide whether to render (e.g., a profile display), it would remain in sync by accident on the initial silent refresh, but drift on interceptor-triggered refreshes.

**Why it happens:**
`apiClient` is a plain class singleton with no React awareness. When it refreshes in the background, it has no way to call `setAccessToken()` in `AuthContext`. Two sources of truth exist for the token value.

**How to avoid:**
Pass a callback into `apiClient` that the interceptor can call after a successful background refresh. Wire this in `AuthProvider`'s setup:

```typescript
// In AuthProvider useEffect:
useEffect(() => {
  apiClient.onTokenRefreshed = (newToken: string) => {
    setAccessToken(newToken);
  };
  apiClient.onAuthExpired = () => {
    clearAuth();
  };
}, []);
```

This keeps React state synchronized without making `apiClient` import React.

**Warning signs:**
- `useAuth().accessToken` returns the pre-refresh token after a background refresh succeeds
- Components that conditionally render based on `accessToken !== null` flicker unexpectedly

**Phase to address:** Token Refresh Interceptor phase

---

### Pitfall 4: Error Boundary Placed Too High — Recoverable Errors Become App-Wide Crashes

**What goes wrong:**
A single `ErrorBoundary` wrapping the entire `<App>` catches all JavaScript render errors, including recoverable errors in individual documents (e.g., a malformed bullet content string that causes a render crash). Instead of showing a retry button scoped to that document, the entire application renders an error fallback page. The user loses their working context for a minor, localized error.

**Why it happens:**
Adding an error boundary at the top level is the first obvious placement. Developers do not think about granularity until users report losing their full application state.

**How to avoid:**
Use a layered error boundary strategy:
- **Top-level** (`App.tsx`): catches catastrophic failures, shows full error page with "Reload" button
- **Per-document** (wrapping `BulletTree`): catches bullet rendering errors, shows "Could not render this document" with a "Reload document" button that resets the boundary and re-fetches
- Do **not** wrap individual `BulletNode` or `BulletContent` — the granularity is unnecessary overhead and the recovery action (re-fetch bullets) is the same at the document level

**Warning signs:**
- The entire app shows an error fallback when one bullet has bad content
- Users must do a full page reload to recover from a per-document render error

**Phase to address:** Error Boundary & Toast Notifications phase

---

### Pitfall 5: Error Boundary Does Not Reset on Document Navigation

**What goes wrong:**
A document-level error boundary catches a render error in Document A. The user clicks Document B in the sidebar. The error boundary is still in its error state because the component tree has not unmounted. Document B's content never renders — the boundary shows Document A's error fallback indefinitely.

**Why it happens:**
React error boundaries (whether class-based or `useErrorBoundary` hooks) do not auto-reset on prop changes. In a single-page app with React Router, navigating between documents does not unmount the boundary if it is placed above the router's outlet.

**How to avoid:**
Use `key={documentId}` on the error boundary component. When `documentId` changes, React remounts the boundary entirely, clearing its error state:

```tsx
<DocumentErrorBoundary key={documentId}>
  <BulletTree documentId={documentId} />
</DocumentErrorBoundary>
```

This is the simplest and most reliable reset mechanism — no lifecycle method required.

**Warning signs:**
- Navigating to a different document after an error still shows the previous document's error fallback
- Error boundary appears "stuck" even when navigating away and back

**Phase to address:** Error Boundary & Toast Notifications phase

---

### Pitfall 6: Toast Notifications Stack Unboundedly During Bulk Operations

**What goes wrong:**
The existing `UndoToast` in `BulletNode.tsx` is rendered inline, controlled by `showUndoToast` state local to each `BulletNode`. If the user swipe-deletes 10 bullets in rapid succession, 10 separate `UndoToast` instances mount simultaneously. All position at `position: fixed; bottom: 80; left: 50%` — they stack on top of each other. Only the topmost one is readable. All 10 auto-dismiss after 4 seconds, each independently calling `queryClient.invalidateQueries({ queryKey: ['bullets'] })`.

**Why it happens:**
Each `BulletNode` owns its own toast state. There is no global toast manager. This works acceptably for a single toast but breaks for concurrent operations that the current swipe gesture enables.

**How to avoid:**
Move toast state to a global toast store (Zustand slice or context). Deduplicate by action type: coalesce "N bullets deleted" into a single toast that accumulates for 1 second before showing. Cap concurrent visible toasts at 3. The toast container renders once at the app root, not inside each `BulletNode`. The `UndoToast` import in `BulletNode.tsx` must be removed.

**Warning signs:**
- Multiple overlapping toasts appear after rapid successive swipe-deletes
- `queryClient.invalidateQueries` fires N times in parallel after bulk delete
- Network tab shows N simultaneous GET requests to `/api/bullets/documents/...` after bulk operations

**Phase to address:** Error Boundary & Toast Notifications phase

---

### Pitfall 7: API Error Standardization Breaks Existing Client Error Parsing

**What goes wrong:**
`client.ts` currently parses errors as `error.error ?? 'Request failed'`. Some routes return `{ error: 'string' }`, some return `{ errors: { field: [...] } }` (Zod validation), and unhandled Express errors return `{ message: '...' }` or an HTML page. If error standardization changes the server format to `{ error: { code: string, message: string } }` but `client.ts` is not updated atomically, `error.error` becomes an object instead of a string — `error.message` in the UI displays `[object Object]`.

**Why it happens:**
Server and client error format changes are made in separate PRs (or by separate tasks) with no enforced contract between them.

**How to avoid:**
Change both ends in the same commit. If the new format is `{ error: { code, message } }`, update `client.ts` to handle both old and new shapes during the transition:

```typescript
const msg = typeof error.error === 'string'
  ? error.error
  : error.error?.message ?? error.message ?? 'Request failed';
```

Write a test that makes a failing request and asserts the string the client receives is a human-readable message, not `[object Object]`.

**Warning signs:**
- Toast or alert shows `[object Object]` or `undefined` after the server change
- TypeScript type errors in `client.ts` after the server error shape changes

**Phase to address:** API Error Standardization phase

---

### Pitfall 8: CI Pipeline Runs Against a Different Schema Than Production

**What goes wrong:**
CI spins up a fresh `postgres:17-alpine` container, runs migrations, then runs tests. If the Drizzle `_journal.json` is missing entries (the critical issue documented in PROJECT.md from Phase 4), migrations silently skip SQL files. The CI schema diverges from production. Tests pass in CI because the missing column was never read in tests, but the app fails in production after deployment.

**Why it happens:**
The Drizzle migration journal (`_journal.json` must list ALL migrations for `migrate()` to discover them) is a known project risk documented in Key Decisions. A fresh CI database exposes this more starkly than the long-running production database where the drift is invisible.

**How to avoid:**
After running migrations in CI, add a schema integrity check step: query `information_schema.columns` for key tables and assert expected columns exist. Pin the CI Postgres image to `postgres:17-alpine` to match `docker-compose.yml`. Consider running `drizzle-kit check` if available for the pinned drizzle-kit version.

**Warning signs:**
- Tests pass in CI but a `column does not exist` error appears after deployment to the server
- CI migration logs show fewer SQL statements applied than the number of migration files in `db/migrations/`

**Phase to address:** CI/CD Pipeline phase (first phase)

---

### Pitfall 9: CI Secrets Exposure

**What goes wrong:**
The project requires `JWT_SECRET`, `DB_PASSWORD`, `GOOGLE_CLIENT_SECRET`, etc. In CI, developers sometimes commit a `.env.test` with "dummy" weak values (e.g., `JWT_SECRET=test`). Even if the CI container is ephemeral, those values appear in git history permanently. The existing `android-ci.yml` already demonstrates the correct pattern (empty `GOOGLE_WEB_CLIENT_ID`, not committed). The server CI must follow the same pattern.

A secondary risk: a CI workflow that adds an SSH deploy step to `192.168.1.50` would expose the production server's SSH key as a GitHub Actions secret — if the repository is ever compromised, the production server is immediately accessible.

**How to avoid:**
Use GitHub Actions secrets for all sensitive values. Generate random test values on-the-fly for non-production-sensitive secrets:

```yaml
env:
  JWT_SECRET: ${{ secrets.JWT_SECRET || 'ci-only-not-production' }}
  DB_PASSWORD: postgres
```

Never commit a `.env.test` or `.env.ci` file. Never add an SSH deploy step in CI — deployment remains the manual SCP workflow per CLAUDE.md.

**Warning signs:**
- A `.env.test` or `.env.ci` file appears in any commit
- `git log --all -- .env` shows accidental `.env` commit history
- CI workflow YAML contains `ssh root@192.168.1.50`

**Phase to address:** CI/CD Pipeline phase (first phase)

---

### Pitfall 10: BulletContent Refactor Breaks Cursor Positioning and Save Timing

**What goes wrong:**
`BulletContent.tsx` is ~768 lines with deeply interconnected state: `isEditing`, `localContent`, `saveTimerRef`, `lastSavedContentRef`, cursor helpers, and the undo checkpoint mechanism. When splitting this into smaller pieces, developers commonly move the keyboard handler (`handleKeyDown`) into a child component or hook, but leave `saveTimerRef` in the parent. The Enter key handler in the extracted location clears a timer it no longer owns, causing unsaved content to flush at the wrong time. The most visible breakage: type a character, press Enter immediately, the new bullet is created but the character typed before Enter is lost.

**Why it happens:**
The component appears large and decomposable, but its state is tightly coupled across all handlers. Splitting along "this looks like a hook" lines without mapping the full data dependency graph breaks subtle timing contracts.

**How to avoid:**
Before refactoring, map every ref and state variable to every handler that reads or writes it. The safe decomposition strategy:
1. Extract pure utility functions (cursor helpers: `setCursorAtPosition`, `placeCursorAtEnd`, `splitAtCursor`, `wrapSelection`, `isCursorAtStart`, `isCursorAtEnd`) — zero component state dependencies, move to `utils/bulletEditing.ts`, test in isolation
2. Extract keyboard handler as a `useBulletKeyboard` hook that receives all state as explicit parameters, not via closure capture
3. Keep `saveTimerRef` and `lastSavedContentRef` in the single component that owns the save lifecycle — never pass refs across component boundaries

**Warning signs:**
- Text typed just before Enter is not saved (timer was cleared by the wrong location)
- Undo checkpoint records empty `previousContent` (the ref was reset by the wrong lifecycle)
- Cursor jumps to position 0 after splitting a bullet

**Phase to address:** Component Refactoring phase (last — after all other v2.3 phases are stable)

---

### Pitfall 11: Undo Coverage Extension Corrupts the Redo Stack for Non-Content Operations

**What goes wrong:**
The undo stack uses a sequential cursor (`currentSeq`) to track position. `recordUndoEvent` truncates all events with `seq > currentSeq` (the redo stack) on every new action. When extending undo to cover `markComplete`, note edits, and bulk delete, there is a temptation to also add undo support to `setCollapsed` (collapse/expand). Every time the user expands a bullet, an undo event is written — which wipes their redo future. A user who: types text → undoes that text → expands a bullet, can no longer redo their text edit.

**Why it happens:**
It is tempting to add undo support to every mutation. The undo stack is global per-user — any write truncates the redo stack, including "minor" view-state changes.

**How to avoid:**
Categorize operations explicitly before starting:
- **Undoable** (content mutations user expects to reverse): text edits, mark complete/incomplete, delete, note edits, indent/outdent, move, bulk delete
- **Not undoable** (view state the user expects to persist without affecting content history): collapse/expand, search filters, sidebar state

`setCollapsed` currently does NOT call `recordUndoEvent` — this is correct and must not change. For bulk delete, record a single undo event whose `inverseOp` restores all deleted bullet IDs atomically — not one event per bullet (which would require N Ctrl+Z actions to undo a single "delete all completed").

**Warning signs:**
- After adding undo to new operations, the undo stack depth unexpectedly resets after view interactions (collapse, scroll)
- Bulk delete creates N rows in `undo_events` table instead of 1

**Phase to address:** Undo Coverage Extension phase

---

### Pitfall 12: Docker Build Cache Fills the Server Disk During CI Setup

**What goes wrong:**
PROJECT.md records that the server disk (30GB) hit 100% during Phase 8 due to Docker build cache accumulation. If the new CI pipeline is misunderstood and someone adds an SSH-based deploy step (running `docker compose up -d --build` on `192.168.1.50`), each CI run adds another Docker layer cache entry on the production disk. The disk fills again.

**Why it happens:**
CI workflows for Docker-based apps often include a deploy step. Developers adapting the Android CI pattern add a server deploy step without realizing CLAUDE.md explicitly prohibits this — deployment must be manual (SCP then rebuild).

**How to avoid:**
The server/client CI workflow must only validate (lint, test, build artifact) — never deploy. Add a comment at the top of each workflow file stating explicitly: "This workflow does NOT deploy to production. Deployment is manual via SCP per CLAUDE.md." grep the YAML for `ssh root@192.168.1.50` before any merge.

**Warning signs:**
- CI workflow YAML contains any `ssh root@192.168.1.50` command
- Docker build step in CI YAML is configured to push or deploy

**Phase to address:** CI/CD Pipeline phase (first phase)

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Inline `try/catch` in every route handler matching on `err.message.toLowerCase().includes(...)` (current pattern) | No shared error middleware needed | Error format inconsistency; service message changes silently break error routing; new routes forget error cases | Acceptable short-term; eliminate during error standardization phase |
| `UndoToast` rendered inside each `BulletNode` (current pattern) | Simple per-bullet state | Toast stacking on bulk operations; N simultaneous `queryClient.invalidateQueries` calls | Never acceptable after a global toast system exists |
| `apiClient` singleton with no refresh logic (current pattern) | Stateless, simple to reason about | Requires careful callback injection when the 401 interceptor is added | Acceptable only until the interceptor phase |
| Skip error boundary at document level (currently: none exists) | No extra wrapper code | Any render error in `BulletTree` crashes the entire app with a blank screen | Never acceptable in production |
| `message.toLowerCase().includes(...)` as error type detection | Zero boilerplate for error routing | Brittle — service error messages that change silently break HTTP status mapping | Never for critical error paths |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| GitHub Actions + Docker | Running `docker compose up --build` on the production server from CI | CI only builds and tests; deployment stays manual per CLAUDE.md |
| GitHub Actions + Postgres service container | Using `postgres:latest` image | Pin to `postgres:17-alpine` to match `docker-compose.yml` |
| 401 interceptor + httpOnly refresh cookie | Omitting `credentials: 'include'` in the interceptor's refresh call | The `/api/auth/refresh` fetch inside the interceptor must use `credentials: 'include'` — identical to the silent refresh in `AuthContext` |
| Error boundary + React Query | Expecting the boundary to catch React Query errors | React Query errors are caught by `onError` callbacks or `isError` state, not by React error boundaries (boundaries catch render-phase errors only). Use `throwOnError: true` intentionally and selectively |
| Toast notifications + `queryClient.invalidateQueries` | Each toast's undo handler independently calling `invalidateQueries` | Coordinate invalidations through a single callback from the global toast store to avoid simultaneous re-fetch storms |
| Zustand toast store + React Query | Calling `useQueryClient()` inside a Zustand action | `useQueryClient()` is a React hook — cannot be called in a plain store action. Pass `queryClient` as a parameter or call invalidation from the component layer |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| N simultaneous `queryClient.invalidateQueries` after N swipe-deletes | Waterfall of concurrent GET `/api/bullets` requests | Global toast deduplication with a single coordinated invalidation | At 3+ concurrent swipe-deletes in rapid succession |
| Full bullet list re-fetch on every undo/redo | Noticeable lag after Ctrl+Z on large documents | Current design (full invalidate after undo) is acceptable for v2.3; optimistic updates are a future optimization | At 200+ bullets per document |
| Error boundary per `BulletNode` | React tree has 200+ extra class component wrappers for a typical document | Boundary at document level only — never per-node | Immediately on any document with many bullets |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Logging `JWT_SECRET` or `DB_PASSWORD` in a CI workflow `run` step | Secret value exposed in GitHub Actions logs if not used via `${{ secrets.NAME }}` notation | Use `${{ secrets.NAME }}` exclusively; never `echo $JWT_SECRET` or interpolate secrets directly in shell scripts |
| Adding SSH deploy step with production server key stored as GitHub Actions secret | If the repository or GitHub account is compromised, the production server is directly accessible | No SSH deploy step in CI; CLAUDE.md workflow (manual SCP) is the deployment path |
| Toast error messages showing raw Express error strings | Stack traces or database error details visible to users | Sanitize server error messages at the Express error middleware layer; surface only `error.message` from the standardized `{ error: { code, message } }` shape, never raw `err.stack` |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| One toast per swipe-delete in a rapid sequence | 10 overlapping unreadable toasts | Coalesce: "5 bullets deleted — Undo" as a single toast accumulating over a 1-second window |
| Error boundary fallback shows "Something went wrong" with no recovery action | User must manually reload the page | Error boundary fallback includes a "Reload document" button that resets the boundary and re-fetches bullets |
| 401 token refresh triggers visible loading state or blank content | User sees a momentary error flash while the background refresh is happening | Queue failed requests; the user never sees a failed state unless the refresh itself fails |
| Undo toast copy says "Bullet deleted" for all action types | Confusing when undo is triggered after mark-complete — "Why does it say deleted?" | Update toast copy to reflect the actual action: "Mark complete undone", "Bullet restored", "N bullets restored" |

---

## "Looks Done But Isn't" Checklist

- [ ] **Token refresh deduplication:** Open the network tab, expire the access token (wait out the TTL or manually clear it), then trigger two simultaneous requests. Only **one** `POST /api/auth/refresh` must appear.
- [ ] **Token refresh does not loop:** Revoke the user's session server-side (delete refresh token from DB). Verify the interceptor logs the user out after exactly one refresh attempt — no loop, no 429.
- [ ] **AuthContext sync after background refresh:** After an interceptor-triggered refresh, `useAuth().accessToken` must reflect the new token value (not the pre-refresh value).
- [ ] **Error boundary resets on navigation:** Force a render error in Document A (e.g., patch a bullet with invalid content server-side). Navigate to Document B. Document B must render normally, not show Document A's error fallback.
- [ ] **CI pipeline has no deploy step:** Run `grep -n "ssh root@192.168.1.50" .github/workflows/*.yml` before merging the CI phase. Result must be empty.
- [ ] **CI uses correct Postgres version:** Open the CI workflow YAML and verify `image: postgres:17-alpine` — matches `docker-compose.yml`.
- [ ] **API error standardization — no `[object Object]`:** After standardization, trigger a 400 validation error and a 401 auth error. Verify the client receives readable strings (not `[object Object]` or `undefined`) in both cases.
- [ ] **Bulk delete creates one undo event:** Delete 5 completed bullets. Check `undo_events` table directly — exactly 1 new row. Press Ctrl+Z once — all 5 bullets restore.
- [ ] **BulletContent split test — Enter key timing:** Type a character in a bullet. Immediately press Enter (do not wait for the debounce). The first bullet must contain the typed character when fetched from the server.
- [ ] **UndoToast not in BulletNode after refactor:** Verify `UndoToast` is not imported in `BulletNode.tsx` after the toast system is added.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Token refresh race condition causes logouts in production | MEDIUM | Roll back the interceptor to no-intercept behavior; re-implement with the promise lock pattern; redeploy |
| Error boundary stuck on wrong document | LOW | Add `key={documentId}` to the boundary wrapper — single-line fix, hot-deployable |
| API error format breaks display (`[object Object]`) | LOW | Update `client.ts` to handle both old and new shapes during transition; deploy immediately |
| Undo stack corrupted by accidental `recordUndoEvent` on `setCollapsed` | HIGH | Write a migration that resets `undo_cursors.currentSeq = 0` for affected users; the stack content cannot be recovered, only cleared |
| CI deploy step fills the server disk | HIGH | Run `docker builder prune -f` on `192.168.1.50`; remove the deploy step from CI; monitor disk with `df -h` |
| Toast stacking causes 10 simultaneous re-fetches | LOW | Wrap `invalidateQueries` in a debounce in the global store; deploy |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Token refresh race condition | Token Refresh Interceptor phase | Network tab shows single refresh call for concurrent 401s |
| Infinite refresh loop | Token Refresh Interceptor phase | Revoked session causes one logout, not a loop |
| AuthContext / ApiClient sync | Token Refresh Interceptor phase | `useAuth().accessToken` reflects the interceptor-refreshed token |
| Error boundary swallows whole app | Error Boundary & Toast phase | Single bullet render error shows document-level fallback only |
| Error boundary does not reset on navigation | Error Boundary & Toast phase | Navigate away after error; new document renders |
| Toast stacking | Error Boundary & Toast phase | 10 rapid swipe-deletes show one coalesced toast |
| API standardization breaks client parsing | API Error Standardization phase | No `[object Object]` in error display; TypeScript types match end-to-end |
| CI uses wrong Postgres version | CI/CD Pipeline phase | `image: postgres:17-alpine` in CI YAML matches `docker-compose.yml` |
| CI secrets in committed files | CI/CD Pipeline phase | No `.env.test` in git history; all values via GitHub Secrets |
| CI deploy step fills server disk | CI/CD Pipeline phase | No `ssh root@192.168.1.50` in CI YAML |
| Undo stack corrupted by collapse | Undo Coverage Extension phase | `setCollapsed` adds no rows to `undo_events` table |
| Bulk delete creates N undo events instead of 1 | Undo Coverage Extension phase | Single `undo_events` row inserted per bulk-delete operation |
| BulletContent refactor breaks Enter + save timing | Component Refactoring phase | Character-then-Enter test: first bullet contains the typed character |
| `saveTimerRef` crossing component boundaries | Component Refactoring phase | Save lifecycle state (`saveTimerRef`, `lastSavedContentRef`) remains in one component |

---

## Sources

- Codebase: `client/src/api/client.ts` — current request method and error parsing pattern
- Codebase: `client/src/contexts/AuthContext.tsx` — auth state management and silent refresh pattern
- Codebase: `client/src/components/DocumentView/BulletContent.tsx` — component size, ref coupling, keyboard handler
- Codebase: `client/src/components/DocumentView/BulletNode.tsx` — `UndoToast` placement per node
- Codebase: `client/src/components/DocumentView/UndoToast.tsx` — per-bullet toast pattern and inline `queryClient.invalidateQueries`
- Codebase: `server/src/services/undoService.ts` — cursor-based undo stack with redo truncation on new events
- Codebase: `server/src/routes/bullets.ts` — per-route inline error handling with message string matching
- Codebase: `server/src/app.ts` — rate limiting (refresh: 60 req / 15min window)
- Codebase: `.github/workflows/android-ci.yml` — existing CI pattern (no deploy step — correct model)
- PROJECT.md Key Decisions: Drizzle `_journal.json` must list all migrations (known migration pitfall from Phase 4)
- PROJECT.md Context: Server disk reached 100% during Phase 8 due to Docker build cache accumulation
- PROJECT.md Key Decisions: AccessToken in React context only — never localStorage (XSS prevention — must remain true after interceptor adds background refresh)

---
*Pitfalls research for: CI/CD, token refresh interceptor, error boundaries, toast notifications, API error standardization, undo coverage extension, component refactoring — Notes app v2.3*
*Researched: 2026-03-19*
