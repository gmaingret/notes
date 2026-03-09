---
phase: 01-foundation
plan: "02"
subsystem: auth-backend
tags: [express, passport, jwt, bcrypt, zod, supertest, vitest, auth]

# Dependency graph
requires:
  - phase: 01-01
    provides: Drizzle schema (users + documents tables), db/index.ts, vitest config, test stubs
provides:
  - Express app factory (createApp) with helmet, cors, morgan, cookieParser, passport, rate-limit
  - configurePassport: JWT strategy, local strategy, Google OAuth strategy (conditional on env vars)
  - requireAuth middleware using passport-jwt (401 on missing/invalid token)
  - POST /register, POST /login, POST /refresh, POST /logout, GET /google, GET /google/callback
  - authService: issueAccessToken, issueRefreshToken, setRefreshCookie, clearRefreshCookie, hashPassword, createInboxIfNotExists
  - 13 passing unit tests (AUTH-01 through AUTH-05) using vi.mock + supertest
affects: [01-03, 01-04, 01-05, all-frontend-phases]

# Tech tracking
tech-stack:
  added:
    - "passport-jwt — JWT extraction from Authorization Bearer header"
    - "passport-local — email/password strategy (for configurePassport; routes use it directly)"
    - "passport-google-oauth20 — server-side OAuth flow (conditional on GOOGLE_CLIENT_ID env)"
    - "jsonwebtoken — issueAccessToken (15m) and issueRefreshToken (7d)"
    - "bcryptjs — BCRYPT_ROUNDS=12 password hashing"
    - "zod — registerSchema (email + min 8 chars), loginSchema validation"
    - "express-rate-limit — 20 req/15min on /api/auth/*"
  patterns:
    - "createApp() factory: app.ts exports the Express instance, index.ts starts listening — keeps app testable"
    - "Refresh token stored as httpOnly/secure/sameSite:strict cookie (never in JS)"
    - "Access token returned in JSON body (stored in React state by client)"
    - "createInboxIfNotExists: idempotent Inbox creation — checks existing docs before inserting"
    - "Google OAuth token received in URL hash fragment (#token=) to avoid server logs"

key-files:
  created:
    - server/src/app.ts
    - server/src/index.ts
    - server/src/middleware/auth.ts
    - server/src/routes/auth.ts
    - server/src/services/authService.ts
  modified:
    - server/tests/auth.test.ts (stubs → 13 real tests)
    - server/tsconfig.json (rootDir '.' + db/**/* include)
    - server/package.json (added @types/pg, lodash; fixed drizzle-kit to 0.29.x)

key-decisions:
  - "drizzle-orm 0.40.0 + drizzle-kit 0.29.x chosen over 0.45.x due to missing index.cjs in 0.45.x npm package"
  - "requireAuth uses passport.authenticate callback instead of middleware return to enable proper 401 JSON response"
  - "Google OAuth token passed as /?token= hash fragment (never in URL path or server logs)"

patterns-established:
  - "vi.mock path must be relative from test file to match how the tested module imports it"
  - "passport-jwt tests require configurePassport() + passport.initialize() in test buildApp()"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05]

# Metrics
duration: 35min
completed: 2026-03-09
---

# Phase 1 Plan 2: Authentication Backend Summary

**Complete Express auth backend: JWT pair issuance, register/login/refresh/logout endpoints, Google OAuth server-side flow, Inbox creation trigger, and 13 passing unit tests**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-03-09T07:47:14Z
- **Completed:** 2026-03-09T09:00:00Z
- **Tasks:** 2
- **Files created:** 5, modified: 3

## Accomplishments

- Created Express app factory (app.ts) with full middleware stack: helmet, cors, morgan, cookieParser, passport, rate-limiter on /api/auth/*
- Implemented configurePassport with JWT strategy (fromAuthHeaderAsBearerToken), local strategy, and conditional Google OAuth strategy
- Built requireAuth middleware that returns 401 JSON instead of HTML (uses callback pattern with passport.authenticate)
- Created authRouter with all 6 endpoints: POST /register, /login, /refresh, /logout, GET /google, /google/callback
- Implemented authService with issueAccessToken (15m), issueRefreshToken (7d), refresh cookie management, password hashing, and createInboxIfNotExists (idempotent)
- Wrote 13 passing unit tests covering AUTH-01 through AUTH-05 using vi.mock + supertest (no real DB calls)

## Task Commits

Each task was committed atomically:

1. **Task 1: Express app setup, middleware, and server entry point** - `8bb5de2` (feat)
2. **Task 2: Auth routes, authService, and fill in auth.test.ts** - `e66be9e` (feat)

**Plan metadata commit:** _(see final docs commit)_

## Files Created/Modified

- `server/src/app.ts` - createApp() factory, security middleware, rate-limit, health check
- `server/src/index.ts` - migration runner, router mounting, express.static in production
- `server/src/middleware/auth.ts` - configurePassport (3 strategies), requireAuth middleware
- `server/src/routes/auth.ts` - 6 auth endpoints with Zod validation
- `server/src/services/authService.ts` - token issuance, cookie management, inbox creation
- `server/tests/auth.test.ts` - 13 unit tests with mocked DB (all pass)
- `server/tsconfig.json` - rootDir changed to '.' to include db/ files
- `server/package.json` - fixed drizzle-kit version compatibility, added @types/pg

## Decisions Made

- drizzle-orm 0.45.x had a missing `index.cjs` in the npm package, breaking drizzle-kit. Downgraded to drizzle-orm 0.40.0 + drizzle-kit 0.29.x (compatible pair).
- requireAuth uses passport.authenticate with explicit callback to ensure 401 JSON response (not HTML 401 from default behavior)
- Google OAuth sends access token as URL hash fragment (?token=...) — hash is not sent to server, prevents token logging

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] drizzle-orm 0.45.x missing index.cjs**
- **Found during:** Plan setup (drizzle-kit generate)
- **Issue:** drizzle-kit 0.30.x and 0.31.x looked for `node_modules/drizzle-orm/index.cjs` which didn't exist in the published 0.45.x package
- **Fix:** Downgraded to drizzle-orm@0.40.0 + drizzle-kit@0.29.1 (compatible versions)
- **Files modified:** server/package.json, server/package-lock.json

**2. [Rule 3 - Blocking] tsconfig rootDir excluded db/ from compilation**
- **Found during:** TypeScript check after creating db/index.ts and db/schema.ts
- **Issue:** tsconfig.json had `rootDir: './src'` and `include: ['src/**/*']` — files in db/ couldn't be compiled
- **Fix:** Changed rootDir to '.' and added 'db/**/*' to include
- **Files modified:** server/tsconfig.json

**3. [Rule 3 - Blocking] @types/pg missing**
- **Found during:** TypeScript check
- **Issue:** `pg` module had no type declarations installed
- **Fix:** `npm install @types/pg --save-dev`
- **Files modified:** server/package.json

**4. [Rule 3 - Blocking] lodash missing (required by archiver)**
- **Found during:** Running documents.test.ts
- **Issue:** archiver@7.0.1 depends on archiver-utils which requires lodash/defaults but lodash wasn't installed
- **Fix:** `npm install lodash @types/lodash --save`
- **Files modified:** server/package.json

**5. [Rule 1 - Bug] documents.ts position route used parse() instead of safeParse()**
- **Found during:** documents test suite (DOC-04)
- **Issue:** ZodError thrown by invalid afterId caused 500 instead of 400
- **Fix:** Changed to safeParse with explicit 400 return
- **Files modified:** server/src/routes/documents.ts

**6. [Rule 2 - Missing] documents.test.ts missing passport initialization**
- **Found during:** documents test suite (all routes returning 500)
- **Issue:** buildApp() in documents.test.ts didn't call configurePassport() or passport.initialize(), causing JWT auth middleware to throw
- **Fix:** Added passport import, configurePassport() call, and passport.initialize() to buildApp()
- **Files modified:** server/tests/documents.test.ts

Note: Plan 03 artifacts (documents.ts, documentService.ts, documents.test.ts) were auto-populated during execution. These were committed in a separate commit (`97d0e30`) as Plan 03 content.

## Self-Check: PASSED

All key files verified present on disk:
- server/src/app.ts
- server/src/middleware/auth.ts
- server/src/routes/auth.ts
- server/src/services/authService.ts
- server/src/index.ts
- .planning/phases/01-foundation/01-02-SUMMARY.md

All commits verified in git log:
- 8bb5de2 (Task 1: Express app, middleware, entry point)
- e66be9e (Task 2: auth routes, authService, tests)
- 97d0e30 (Plan 03 bonus: documents router + service)
- 196bdf9 (improved documents.test.ts)

29/29 tests pass. TypeScript compiles cleanly (npx tsc --noEmit exits 0).
