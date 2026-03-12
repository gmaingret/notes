---
phase: 09-android-foundation-and-auth
plan: 02
subsystem: auth
tags: [google-oauth, android, credential-manager, id-token, jwt, nodejs]

# Dependency graph
requires:
  - phase: 09-android-foundation-and-auth
    provides: Phase 9 context, existing auth endpoints (register/login/refresh/logout/google-oauth-redirect)
provides:
  - POST /api/auth/google/token — server-side Google ID token verification endpoint for native Android Credential Manager flow
  - Account-linking logic for native Google sign-in (by googleId, by email, or new user creation)
affects:
  - 09-03 (Android AuthViewModel will call this endpoint)
  - 09-04 (token storage flows from the response of this endpoint)

# Tech tracking
tech-stack:
  added:
    - google-auth-library (server-side Google ID token verification via OAuth2Client.verifyIdToken)
  patterns:
    - Native Android Google SSO uses /google/token POST (ID token exchange), not the Passport redirect flow (/google -> /google/callback)
    - Account-linking order: find by googleId -> find by email and link -> create new user

key-files:
  created: []
  modified:
    - server/src/routes/auth.ts
    - server/package.json
    - server/package-lock.json

key-decisions:
  - "Used google-auth-library OAuth2Client.verifyIdToken() for server-side ID token verification — same library Google recommends, works with any audience (web or Android client ID)"
  - "POST /google/token placed BEFORE GET /google redirect route to avoid any potential Express route conflicts"
  - "Response shape is identical to email/password login: {accessToken, user: {id, email}} + refreshToken cookie — Android client reuses same token handling logic"

patterns-established:
  - "Native Android Google auth: client sends ID token to POST /api/auth/google/token; server verifies and returns JWTs"
  - "Account linking is idempotent: subsequent Google logins from same account always find by googleId first"

requirements-completed:
  - AUTH-03

# Metrics
duration: 12min
completed: 2026-03-12
---

# Phase 9 Plan 02: Google Token Endpoint for Android SSO Summary

**Server-side Google ID token verification endpoint using google-auth-library, enabling native Android Credential Manager to exchange Google ID tokens for app JWTs with account-linking support**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-03-12T08:15:00Z
- **Completed:** 2026-03-12T08:27:00Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments
- Installed google-auth-library and added POST /api/auth/google/token endpoint
- Server-side verification of Google ID tokens using OAuth2Client.verifyIdToken()
- Full account-linking logic: find by googleId, link by email (for existing email/password users switching to Google), or create new user
- Deployed to production and verified: missing token returns 400, invalid token returns 401, existing endpoints unaffected

## Task Commits

Each task was committed atomically:

1. **Task 1: Install google-auth-library and add POST /auth/google/token endpoint** - `036029f` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `server/src/routes/auth.ts` - Added OAuth2Client import, googleClient instance, and /google/token POST route
- `server/package.json` - Added google-auth-library dependency
- `server/package-lock.json` - Updated lockfile

## Decisions Made
- Used google-auth-library's OAuth2Client.verifyIdToken() — the canonical Google-recommended approach for server-side ID token verification
- Placed the new POST /google/token route before the existing GET /google redirect route to maintain correct ordering
- Response shape exactly mirrors existing login endpoint — Android client can reuse the same token-handling code

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Build succeeded on first attempt, all endpoint smoke tests passed.

## User Setup Required

None — the new endpoint uses the existing `GOOGLE_CLIENT_ID` environment variable already configured in `.env`.

## Next Phase Readiness
- POST /api/auth/google/token is deployed and functional at https://notes.gregorymaingret.fr
- Android client (Phase 9 Plan 3+) can call this endpoint after receiving a Google ID token from Credential Manager
- The endpoint's response shape {accessToken, user: {id, email}} + refreshToken cookie matches what the TokenAuthenticator and DataStore layer will expect

## Self-Check: PASSED

- server/src/routes/auth.ts: FOUND
- server/package.json: FOUND
- 09-02-SUMMARY.md: FOUND
- Commit 036029f: FOUND

---
*Phase: 09-android-foundation-and-auth*
*Completed: 2026-03-12*
