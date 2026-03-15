---
phase: 17-auth-and-session-security
plan: "02"
subsystem: server/auth
tags: [security, session, revocation, change-password, jwt, drizzle]
dependency_graph:
  requires: ["17-01"]
  provides: ["server-side refresh token revocation", "change-password endpoint"]
  affects: ["server/src/routes/auth.ts", "server/src/services/authService.ts", "server/db/schema.ts"]
tech_stack:
  added: ["crypto.createHash (SHA-256 for token hashing)"]
  patterns: ["soft revocation (revokedAt nullable column)", "hash-not-store (SHA-256 of JWT)", "cascade-on-user-delete"]
key_files:
  created:
    - server/db/migrations/0003_refresh_tokens.sql
  modified:
    - server/db/schema.ts
    - server/src/services/authService.ts
    - server/src/routes/auth.ts
    - server/tests/auth.test.ts
    - server/db/migrations/meta/_journal.json
decisions:
  - "Store SHA-256 hash of refresh token (not raw JWT) in DB — DB compromise does not leak usable tokens"
  - "Soft revocation (revokedAt column) rather than hard delete — preserves audit trail and allows future analytics"
  - "setRefreshCookie made async — DB insert happens inline; callers must await"
  - "revokeAllUserTokensExcept preserves current session token — user stays logged in on the device that changed the password"
  - "configurePassport() called in test buildApp() to enable requireAuth middleware in unit tests"
metrics:
  duration: 4m
  completed_date: "2026-03-15"
  tasks_completed: 2
  files_changed: 5
---

# Phase 17 Plan 02: Refresh Token Revocation and Change-Password Summary

**One-liner:** SHA-256-hashed refresh tokens stored in DB with soft revocation; logout invalidates tokens and POST /change-password revokes all other sessions while keeping current session active.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add refreshTokens table and create migration | b2e7ec6 | schema.ts, 0003_refresh_tokens.sql, _journal.json |
| 2 | Implement token revocation in authService and wire into routes | 1ddfe44 | authService.ts, auth.ts, auth.test.ts |

## What Was Built

### refreshTokens DB Table (schema.ts + migration)
- New `refresh_tokens` table: id, user_id (FK cascade), token_hash (SHA-256), expires_at, revoked_at (nullable = soft revocation), created_at
- Two indexes: on user_id and token_hash for efficient lookup
- Manual SQL migration at `server/db/migrations/0003_refresh_tokens.sql`
- Journal entry added at `server/db/migrations/meta/_journal.json` (idx 3, tag `0003_refresh_tokens`)

### authService.ts Updates
- `setRefreshCookie` is now `async` — issues JWT and inserts SHA-256 hash into refreshTokens table
- New `isRefreshTokenRevoked(token)` — hashes token, queries for active row; returns true if not found
- New `revokeRefreshToken(token)` — soft-revokes by setting revokedAt
- New `revokeAllUserTokensExcept(userId, currentToken)` — revokes all active tokens for user except the specified one (used by change-password to preserve current session)

### auth.ts Route Updates
- **POST /refresh**: now calls `isRefreshTokenRevoked` before issuing new access token; returns 401 with `{ error: 'Token has been revoked' }` if revoked
- **POST /logout**: calls `revokeRefreshToken` (best effort — errors are swallowed) then clears cookie
- **POST /change-password** (new): protected by `requireAuth`; validates currentPassword via bcrypt, enforces password policy on newPassword, updates passwordHash, calls `revokeAllUserTokensExcept`, issues new refresh cookie; 400 for OAuth-only accounts

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Updated AUTH-05 test insert count from 2 to 3**
- **Found during:** Task 2 GREEN phase
- **Issue:** `setRefreshCookie` now inserts a refresh token row, so register flow calls `db.insert` 3 times (user + refresh token + Inbox document) rather than the original 2
- **Fix:** Updated test assertion `toHaveBeenCalledTimes(2)` to `toHaveBeenCalledTimes(3)`
- **Files modified:** server/tests/auth.test.ts

**2. [Rule 2 - Missing functionality] Added passport.initialize() and configurePassport() to test buildApp**
- **Found during:** Task 2 GREEN phase — SESS-04 tests returning 500
- **Issue:** `requireAuth` middleware calls `passport.authenticate('jwt', ...)` which requires the JWT strategy to be registered. The test's `buildApp()` didn't initialize passport.
- **Fix:** Import and call `configurePassport()` before test suite; add `app.use(passport.initialize())` in `buildApp()`
- **Files modified:** server/tests/auth.test.ts

## Verification Results

- `npx vitest run` — 135/135 tests pass across 12 test files
- `npx tsc --noEmit` — no TypeScript errors
- `grep "change-password" auth.ts` — endpoint exists
- `grep "isRefreshTokenRevoked" auth.ts` — revocation check on /refresh confirmed
- `grep "revokeRefreshToken" auth.ts` — revocation on /logout confirmed
- `grep "refreshTokens" authService.ts` — DB storage confirmed
- Migration file exists at `server/db/migrations/0003_refresh_tokens.sql`
- Journal entry `0003_refresh_tokens` in `_journal.json`

## Self-Check: PASSED
