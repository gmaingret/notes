---
phase: 17-auth-and-session-security
plan: 01
subsystem: auth
tags: [password-policy, oauth, jwt, security, vitest]

# Dependency graph
requires:
  - phase: 16-injection-and-upload-hardening
    provides: Hardened upload and injection defenses that this phase builds on
provides:
  - Password policy validation module (validatePasswordPolicy) rejecting weak and common passwords
  - Register route enforcing password policy with consistent Zod-compatible error shape
  - OAuth callback using hash fragment (#token=) instead of query string (?token=)
affects: [18-rate-limiting-and-headers]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Static common-password blocklist checked case-insensitively before character rules
    - Password policy returns first-failing-rule error message (null = valid)
    - Consistent error shape for policy errors — errors.password[0] — matches Zod field error format

key-files:
  created:
    - server/src/services/passwordPolicy.ts
    - server/tests/passwordPolicy.test.ts
  modified:
    - server/src/routes/auth.ts
    - server/tests/auth.test.ts

key-decisions:
  - "Common password check runs before character-diversity rules — gives more actionable error for well-known passwords like 'password123' that also fail uppercase check"
  - "Static in-process blocklist of ~100 passwords chosen over npm package — simpler, no dependency, adequate for self-hosted single-user app"
  - "Password policy only enforced at registration (not login) — per plan spec"

patterns-established:
  - "passwordPolicy.ts: validatePasswordPolicy(password): string | null — null means valid, string is user-facing error"
  - "OAuth token delivery via hash fragment (#token=) to prevent JWT appearing in server-bound request URLs"

requirements-completed: [AUTH-01, AUTH-02, SESS-01, SESS-02]

# Metrics
duration: 15min
completed: 2026-03-15
---

# Phase 17 Plan 01: Auth and Session Security Summary

**Password policy module with ~100 common-password blocklist wired into register route; OAuth callback token delivery fixed from query string to hash fragment**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-15T10:36:00Z
- **Completed:** 2026-03-15T10:39:00Z
- **Tasks:** 2 (TDD for Task 1)
- **Files modified:** 4

## Accomplishments

- Created `validatePasswordPolicy` service enforcing min-length, character diversity (uppercase/lowercase/digit), and case-insensitive blocklist of ~100 breached passwords
- Wired policy into POST /register — returns 400 with `errors.password[0]` error message matching Zod field error shape
- Fixed OAuth callback: `?token=` changed to `#token=` so JWTs are never sent to the server in subsequent navigation requests (SESS-01)
- Updated 5 existing tests to use valid passwords; added 3 new register policy tests; all 126 server tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED): Failing tests for password policy** - `5e81751` (test)
2. **Task 1 (GREEN): Password policy module implementation** - `7e8a4e5` (feat)
3. **Task 2: Wire policy into register and fix OAuth redirect** - `7fd070f` (feat)

## Files Created/Modified

- `server/src/services/passwordPolicy.ts` — New module: `validatePasswordPolicy(password): string | null`
- `server/tests/passwordPolicy.test.ts` — 9 tests covering all rejection and acceptance cases
- `server/src/routes/auth.ts` — Added policy check in POST /register; fixed OAuth redirect to use `/#token=`
- `server/tests/auth.test.ts` — Updated 5 tests to use valid passwords; added 3 new policy tests

## Decisions Made

- Common password check runs before character-diversity rules so well-known breached passwords (e.g., "password123") get a "too common" error instead of the misleading "needs uppercase" error
- Static in-process blocklist preferred over npm dependency — sufficient for single-user self-hosted app, no external network call or maintenance burden
- Policy enforced at registration only, not login — consistent with the plan specification

## Deviations from Plan

None - plan executed exactly as written.

One minor implementation adjustment (not a deviation): reordered the common-password check to run before character rules. This was required to satisfy the test behavior specification in the plan (which expects "common" in the error for "password123"). The plan's behavior spec was the authority; the check order was not explicitly specified.

## Issues Encountered

None - all tests passed after standard TDD RED/GREEN cycle.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- AUTH-01, AUTH-02, SESS-01, SESS-02 requirements fully satisfied
- Password policy module is reusable for future password-change flows
- Ready for Phase 17 Plan 02 (if any) or Phase 18 rate limiting and headers

---
*Phase: 17-auth-and-session-security*
*Completed: 2026-03-15*

## Self-Check: PASSED

- FOUND: server/src/services/passwordPolicy.ts
- FOUND: server/tests/passwordPolicy.test.ts
- FOUND: .planning/phases/17-auth-and-session-security/17-01-SUMMARY.md
- FOUND: commit 5e81751 (test: failing tests for password policy)
- FOUND: commit 7e8a4e5 (feat: password policy module implementation)
- FOUND: commit 7fd070f (feat: wire policy and fix OAuth redirect)
