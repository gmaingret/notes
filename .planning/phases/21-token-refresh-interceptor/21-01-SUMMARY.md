---
phase: 21-token-refresh-interceptor
plan: 01
subsystem: auth
tags: [jwt, token-refresh, fetch-interceptor, react, typescript]

# Dependency graph
requires:
  - phase: 20-error-boundaries
    provides: sonner toast library installed and Toaster mounted

provides:
  - Silent 401 interception in ApiClient with shared promise lock
  - _isRetry guard preventing infinite refresh loops
  - Handler injection pattern (setRefreshHandler/setLogoutHandler) for decoupled auth
  - Upload and download methods also covered by token refresh on 401
  - Session expiry toast notification via AuthContext logout handler

affects: [any future phases adding new fetch calls outside ApiClient]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Callback injection for auth handlers: ApiClient.setRefreshHandler() called from AuthContext useEffect"
    - "Shared promise lock: refreshPromise field prevents concurrent duplicate refresh calls"
    - "_isRetry flag on RequestOptions prevents infinite 401 loops on retry"
    - "handleUnauthorized() private helper centralises refresh+retry logic across request/upload/download"

key-files:
  created: []
  modified:
    - client/src/api/client.ts
    - client/src/contexts/AuthContext.tsx

key-decisions:
  - "Handler injection via setRefreshHandler/setLogoutHandler avoids circular ES module imports"
  - "refreshAccessToken only updates React state; handleUnauthorized calls setToken() to update the in-flight header"
  - "Shared promise lock (refreshPromise) ensures only one POST /api/auth/refresh per burst of concurrent 401s"
  - "_isRetry guard on request() and isRetry param on upload/download() prevent infinite retry loops"
  - "toast.error kept in AuthContext (not client.ts) to keep ApiClient framework-agnostic"

patterns-established:
  - "401 interceptor: check res.status === 401 BEFORE res.ok block so error parsing is skipped on retry path"
  - "useEffect cleanup removes handlers when AuthProvider unmounts to prevent stale closure calls"

requirements-completed: [RES-01]

# Metrics
duration: 5min
completed: 2026-03-19
---

# Phase 21 Plan 01: Token Refresh Interceptor Summary

**Silent 401 interception in ApiClient with shared promise lock, _isRetry guard, and AuthContext handler injection covering request/upload/download methods**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-03-19T16:42:39Z
- **Completed:** 2026-03-19T16:47:30Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- ApiClient now intercepts 401 responses and silently refreshes the access token before retrying the original request
- Shared promise lock (refreshPromise) prevents duplicate refresh calls when multiple requests hit 401 simultaneously
- Upload and download methods now have equivalent 401 handling, closing coverage gaps for file operations
- AuthContext injects typed refresh and logout callbacks, keeping ApiClient framework-agnostic

## Task Commits

Each task was committed atomically:

1. **Task 1: Add 401 interceptor with shared promise lock to ApiClient** - `0c34f01` (feat)
2. **Task 2: Wire refresh and logout handlers from AuthContext** - `1cc99c5` (feat)

**Plan metadata:** (docs commit follows this summary)

## Files Created/Modified

- `client/src/api/client.ts` - Added refreshHandler/logoutHandler/refreshPromise fields, setRefreshHandler/setLogoutHandler setters, handleUnauthorized() private helper, 401 interception in request()/upload()/download()
- `client/src/contexts/AuthContext.tsx` - Added refreshAccessToken callback, handleSessionExpired callback with toast, useEffect injecting both handlers into apiClient with cleanup

## Decisions Made

- Handler injection via `setRefreshHandler`/`setLogoutHandler` avoids circular ES module imports between client.ts and AuthContext.tsx
- `refreshAccessToken` only updates React state (`setAccessToken`) — `handleUnauthorized` calls `this.setToken(newToken)` internally, so the retried request always has the latest token in headers
- The shared `refreshPromise` lock reuses the in-flight promise; `finally(() => { this.refreshPromise = null })` clears it so the next expiry triggers a fresh refresh
- `toast.error` lives in AuthContext rather than client.ts to keep ApiClient free of UI framework dependencies

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None — TypeScript compiled cleanly on first attempt, Vite build succeeded without errors.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Token refresh interceptor is fully wired; users will no longer be silently logged out when the 15-minute access token expires mid-session
- Manual UAT: log in, wait for token to expire (or clear access token in devtools), perform an action — should succeed silently. Clear refresh cookie too — should see "Session expired" toast and be redirected to login

---
*Phase: 21-token-refresh-interceptor*
*Completed: 2026-03-19*
