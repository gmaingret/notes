# Phase 17: Auth and Session Security - Context

**Gathered:** 2026-03-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix JWT token exposure in OAuth callback (query string → hash fragment), implement server-side refresh token revocation on logout and password change, create a password change endpoint, and strengthen password policy with character diversity and common password checking.

</domain>

<decisions>
## Implementation Decisions

### OAuth token fix
- Server redirect changes from `?token=` to `#token=` in Google OAuth callback (auth.ts:165)
- Client already reads from `window.location.hash` — no client change needed (AuthContext.tsx:50-57)
- Client already clears hash fragment via `replaceState` — no additional cleanup needed

### Token revocation strategy
- Claude's Discretion on storage mechanism (DB table vs in-memory blocklist — Claude picks what fits the stack best)
- On logout: revoke the refresh token that was in the cookie
- On password change: revoke all OTHER refresh tokens for the user, keep the current session active
- The /refresh endpoint must check if the token has been revoked before issuing a new access token

### Password change endpoint
- Create new `POST /api/auth/change-password` route
- Require: current password + new password (both validated)
- New password must pass the same policy as registration (character diversity + common password check)
- After successful change: revoke all other refresh tokens, keep current session

### Password policy
- Claude's Discretion on common password approach (built-in list vs npm package — pick simplest effective option)
- Enforce character diversity (specific rules at Claude's discretion)
- Enforce only on registration and password change — NOT on login for existing users
- Return clear, specific error messages explaining what's wrong (e.g., "Password must include at least one uppercase letter and one number")

### Claude's Discretion
- Token revocation storage mechanism (DB table recommended for durability, but Claude decides)
- Common password check implementation (static list vs package)
- Character diversity rules specifics (3-of-4 classes, or simpler)
- Drizzle migration naming/structure for any new tables
- Error message formatting for password policy violations

</decisions>

<specifics>
## Specific Ideas

No specific requirements — standard security patterns apply.

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- `authService.ts`: Already has `issueRefreshToken`, `setRefreshCookie`, `clearRefreshCookie` — revocation hooks into these
- `auth.ts` route: All auth routes in one file — `/register`, `/login`, `/refresh`, `/logout`, `/google/*`
- `auth.ts` middleware: `requireAuth` extracts user from JWT — can be reused for the change-password route
- Zod schemas already used for register/login validation — extend for password change

### Established Patterns
- JWT access token (15m TTL) + refresh token cookie (7d TTL, httpOnly, secure, sameSite: strict)
- Drizzle ORM for all DB operations, migrations in `server/db/migrations/`
- bcrypt with 12 rounds for password hashing
- Error pattern: `res.status(4xx).json({ error: '...' })` or `{ errors: fieldErrors }` for validation

### Integration Points
- `authService.ts:setRefreshCookie` — currently creates JWT and sets cookie; needs to also store token reference server-side
- `auth.ts:POST /refresh` — currently just verifies JWT signature; needs to check revocation
- `auth.ts:POST /logout` — currently just clears cookie; needs to revoke token server-side
- `auth.ts:165` — single line change: `?token=` → `#token=`
- Client: Android `AuthRepository.kt` also handles token — check if it uses query string or its own flow (it uses `/google/token` endpoint, not OAuth redirect, so unaffected)

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 17-auth-and-session-security*
*Context gathered: 2026-03-15*
