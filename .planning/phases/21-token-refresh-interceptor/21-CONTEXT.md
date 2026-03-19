# Phase 21: Token Refresh Interceptor - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Add automatic token refresh to the web client's ApiClient so that expired access tokens are silently refreshed via the httpOnly refresh cookie. Users never see a failed request due to token expiry. If the refresh token is also expired, log the user out with a notification.

</domain>

<decisions>
## Implementation Decisions

### Refresh handler injection
- ApiClient gets a new method: `setRefreshHandler(handler: () => Promise<string | null>)` — stores a callback
- AuthContext injects the handler on mount via `useEffect(() => { apiClient.setRefreshHandler(refreshFn) }, [])`
- The refresh handler calls `POST /api/auth/refresh` (same logic as the existing mount-time silent refresh in AuthContext), returns the new accessToken or null on failure
- This avoids circular ES module imports between `client.ts` and `AuthContext.tsx`

### 401 interception in request()
- When `res.status === 401` and no `_isRetry` flag: call the refresh handler
- If refresh succeeds: update token via `this.setToken(newToken)`, retry the original request with `_isRetry: true`
- If refresh fails (returns null): call a logout handler (also injected), show toast, do NOT retry
- The `_isRetry` flag prevents infinite loops — a retried request that still gets 401 fails cleanly

### Shared promise lock for concurrent 401s
- Store the in-flight refresh promise: `private refreshPromise: Promise<string | null> | null = null`
- First 401 creates the promise; subsequent concurrent 401s await the same promise
- After promise resolves, clear `refreshPromise = null`
- All waiting requests then retry with the new token

### Upload and download 401 handling
- `upload()` and `download()` methods also need 401 interception — they bypass `request()` and use raw fetch
- Extract the 401 check + refresh + retry logic into a private helper method that all three entry points use
- Or refactor `upload()` and `download()` to go through `request()` with appropriate options

### User notification on session expiry
- Inject a logout handler: `setLogoutHandler(handler: () => void)` — AuthContext provides `clearAuth`
- On refresh failure: `toast.error('Session expired — please log in again')` + call logout handler
- React Router will redirect to `/login` automatically when accessToken becomes null (RequireAuth component)

### Claude's Discretion
- Whether to refactor upload/download to use request() internally vs adding 401 handling inline
- Exact implementation of the shared promise lock pattern
- Whether to also add the user object to the refresh response handling (currently mount refresh doesn't set user)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Client auth
- `client/src/api/client.ts` — ApiClient class, all request methods (request, get, post, patch, delete, upload, download)
- `client/src/contexts/AuthContext.tsx` — Auth state, mount-time refresh, login/register/logout, applyToken/clearAuth helpers

### Server auth
- `server/src/routes/auth.ts` — POST /api/auth/refresh endpoint, refresh token validation, revocation checks

### Prior art
- Android commit `0457017` — fix(android): only clear tokens on definitive auth failures, not transient errors — reference for _isRetry guard pattern

### Research
- `.planning/research/ARCHITECTURE.md` — 401 interceptor pattern, callback injection, errorMiddleware placement
- `.planning/research/PITFALLS.md` — Three token refresh failure modes (race, loop, AuthContext sync)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuthContext.clearAuth()` — already clears accessToken, user, and apiClient token in one call
- `AuthContext.applyToken()` — sets token + user + apiClient.setToken in one call
- Mount-time refresh logic (AuthContext lines 35-46) — same POST /api/auth/refresh call the interceptor needs
- `toast` from sonner (installed in Phase 20) — available for session expiry notification

### Established Patterns
- ApiClient stores token as `private accessToken` and exposes `setToken()`
- All methods include `credentials: 'include'` for httpOnly refresh cookie
- Error format: `throw Object.assign(new Error(error.error ?? 'Request failed'), { status: res.status })`
- `upload()` and `download()` are separate methods that DON'T go through `request()` — they need separate 401 handling

### Integration Points
- `apiClient.setRefreshHandler()` called in AuthContext useEffect on mount
- `apiClient.setLogoutHandler()` called in AuthContext useEffect on mount
- 401 check added to `request()` method before the existing `if (!res.ok)` throw
- Same 401 check pattern needed in `upload()` and `download()`

</code_context>

<specifics>
## Specific Ideas

- The user just experienced the exact bug this phase fixes — they were logged out mid-session during Phase 20 UAT because the access token expired with no refresh interceptor
- Android client already solved this problem (commit `0457017`) — the web client should use the same `_isRetry` guard pattern

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 21-token-refresh-interceptor*
*Context gathered: 2026-03-19*
