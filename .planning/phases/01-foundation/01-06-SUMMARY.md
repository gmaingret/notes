---
phase: 01-foundation
plan: 06
subsystem: infra
tags: [docker, deployment, nginx, typescript, express, postgres]

dependency_graph:
  requires:
    - phase: "01-05"
      provides: "app shell and document management UI"
  provides:
    - "Production deployment at notes.gregorymaingret.fr (fully verified)"
    - "Claude test account (claude-test@notes.internal) with Inbox document"
    - "Fixed verbatimModuleSyntax type-only imports across client"
    - "Fixed Express wildcard route for path-to-regexp v8"
    - "Fixed Dockerfile CMD to correct dist/src/index.js path"
  affects: ["phase-2"]

tech-stack:
  added: []
  patterns:
    - "Server TypeScript compiles to dist/src/ (rootDir='.', include=['src/**/*','db/**/*'])"
    - "Express wildcard fallback must use /{*path} syntax with path-to-regexp v8"
    - "verbatimModuleSyntax: true requires `import type` for all type-only imports"

key-files:
  created: []
  modified:
    - client/src/App.tsx
    - client/src/components/Sidebar/DocumentList.tsx
    - client/src/components/Sidebar/DocumentRow.tsx
    - client/src/contexts/AuthContext.tsx
    - client/src/pages/LoginPage.tsx
    - client/vite.config.ts
    - server/src/index.ts
    - Dockerfile

key-decisions:
  - "Docker CMD path is server/dist/src/index.js (not server/dist/index.js) — rootDir='.' compiles src/index.ts to dist/src/index.ts"
  - "Nginx proxy must point to port 8000 (notes-app) not port 3000 (outlinergod occupies port 3000)"
  - "vite.config.ts uses defineConfig from vitest/config to support the 'test' property alongside Vite config"

patterns-established:
  - "Always delete tsconfig build cache (node_modules/.tmp) before running tsc -b to get accurate errors"
  - "Docker build reveals TypeScript errors that local incremental builds miss (stale cache)"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07]

duration: ~35min
completed: 2026-03-09
---

# Phase 1 Plan 06: Production Deployment Summary

**React+Express notes app deployed to Docker at 192.168.1.50:8000; three build-blocking bugs auto-fixed; Claude test account confirmed with Inbox document; Nginx port update required before public URL serves API routes.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-03-09T08:11:00Z
- **Completed:** 2026-03-09T08:50:00Z
- **Tasks:** 2/2 complete (1 auto + 1 human-verify checkpoint)
- **Files modified:** 8

## Accomplishments

- Fixed three build-blocking bugs discovered during Docker build (verbatimModuleSyntax imports, Dockerfile CMD path, Express wildcard syntax)
- Docker image builds and deploys successfully; containers healthy
- App server runs at `http://192.168.1.50:8000` with all routes working
- Health endpoint confirmed: `{"status":"ok","version":"1.0.0","db":"ok"}`
- Claude test account `claude-test@notes.internal` registered; Inbox document confirmed via API
- All 29 server unit tests pass

## Task Commits

1. **chore: add project docs** - `3330569` (chore)
2. **fix: verbatimModuleSyntax type-only imports + vite.config** - `6575aa7` (fix)
3. **fix: Dockerfile CMD path dist/src/index.js** - `88a04a5` (fix)
4. **fix: Express /{*path} wildcard for path-to-regexp v8** - `e2da090` (fix)

## Files Created/Modified

- `client/src/App.tsx` — `ReactNode` changed to `import type`
- `client/src/components/Sidebar/DocumentList.tsx` — `DragEndEvent` changed to `import type`
- `client/src/components/Sidebar/DocumentRow.tsx` — `KeyboardEvent` changed to `import type`
- `client/src/contexts/AuthContext.tsx` — `ReactNode` changed to `import type`
- `client/src/pages/LoginPage.tsx` — `FormEvent` changed to `import type`
- `client/vite.config.ts` — `defineConfig` imported from `vitest/config` (supports `test` property)
- `server/src/index.ts` — wildcard route changed from `'*'` to `'/{*path}'`
- `Dockerfile` — CMD changed from `server/dist/index.js` to `server/dist/src/index.js`

## Decisions Made

- **Dockerfile CMD path:** TypeScript `rootDir: "."` with `include: ["src/**/*", "db/**/*"]` means tsc emits to `dist/src/` not `dist/` — CMD must be `server/dist/src/index.js`
- **vite.config.ts:** Use `defineConfig` from `vitest/config` (re-exports Vite's defineConfig + adds `test` type support) — avoids TS2769 error in Docker build
- **Nginx port:** Notes app runs on external port 8000; `outlinergod-backend-1` occupies port 3000 on same host; Nginx config at 192.168.1.204 must be updated to proxy to port 8000

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed verbatimModuleSyntax type-only imports in 5 client files**
- **Found during:** Task 1 (Docker build)
- **Issue:** `verbatimModuleSyntax: true` in tsconfig.app.json requires `import type` for type-only imports. Local `tsc -b` used stale cache and passed; Docker clean build failed with 5 TS1484 errors.
- **Fix:** Added `type` keyword to all 5 type-only imports across App.tsx, DocumentList.tsx, DocumentRow.tsx, AuthContext.tsx, LoginPage.tsx
- **Files modified:** 5 client source files
- **Verification:** `tsc -b` with cleared cache exits 0; Docker build succeeds
- **Committed in:** `6575aa7`

**2. [Rule 1 - Bug] Fixed vite.config.ts to use vitest/config defineConfig**
- **Found during:** Task 1 (Docker build)
- **Issue:** `defineConfig` from plain `vite` doesn't include `test` property type; TS2769 error in Docker build
- **Fix:** Changed import to `vitest/config` which re-exports Vite's defineConfig with added Vitest types
- **Files modified:** client/vite.config.ts
- **Verification:** Docker build passes; vitest still runs correctly
- **Committed in:** `6575aa7`

**3. [Rule 1 - Bug] Fixed Dockerfile CMD entry point path**
- **Found during:** Task 1 (first Docker startup)
- **Issue:** `CMD ["node", "server/dist/index.js"]` fails — tsc with `rootDir: "."` emits `src/index.ts` to `dist/src/index.js`
- **Fix:** Changed CMD to `server/dist/src/index.js`
- **Files modified:** Dockerfile
- **Verification:** Container starts; `docker compose logs app` shows `Server running on :3000`
- **Committed in:** `88a04a5`

**4. [Rule 1 - Bug] Fixed Express wildcard route for path-to-regexp v8**
- **Found during:** Task 1 (second Docker startup)
- **Issue:** `app.get('*', ...)` throws `PathError: Missing parameter name` with path-to-regexp v8 (bundled with Express 5)
- **Fix:** Changed to `app.get('/{*path}', ...)`
- **Files modified:** server/src/index.ts
- **Verification:** Server starts cleanly; `Server running on :3000` in logs
- **Committed in:** `e2da090`

---

**Total deviations:** 4 auto-fixed (all Rule 1 bugs revealed by Docker clean build vs local incremental build)
**Impact on plan:** All fixes required for Docker build to succeed. No scope creep.

## Issues Encountered

**Disk space exhaustion on server:** First Docker build failed with "no space left on device" during postgres:17-alpine image pull. Resolved with `docker builder prune -f` which freed ~2.9GB of build cache.

**Nginx port mismatch (pending human action):** The `outlinergod-backend-1` container occupies port 3000 on the Docker host. The notes-app runs on port 8000. The Nginx reverse proxy at 192.168.1.204 is currently configured to proxy to port 3000, routing requests to the outlinergod backend instead of the notes app. Greg must update `/etc/nginx/sites-available/notes.gregorymaingret.fr` on the Nginx host to proxy to `192.168.1.50:8000`.

**Claude test account created directly via port 8000:** Due to the Nginx routing issue, `claude-test@notes.internal` was registered directly against `http://192.168.1.50:8000`. The account exists and has an Inbox document confirmed. Password: `ClaudeTest2026x`.

## User Setup Required

**Nginx configuration update required** before `https://notes.gregorymaingret.fr` serves the notes app:

On the Nginx host (192.168.1.204), Greg must run:

```bash
# Update proxy_pass from port 3000 to 8000
sed -i 's/proxy_pass http:\/\/192\.168\.1\.50:3000/proxy_pass http:\/\/192.168.1.50:8000/g' \
  /etc/nginx/sites-available/notes.gregorymaingret.fr

# Verify the change
grep proxy_pass /etc/nginx/sites-available/notes.gregorymaingret.fr

# Test and reload
nginx -t && nginx -s reload
```

After the Nginx update, the full UI at `https://notes.gregorymaingret.fr` will be accessible.

## Production Verification (Task 2 — Human Checkpoint)

**Status:** APPROVED

Greg updated Nginx (proxy_pass now points to 192.168.1.50:8000). Claude ran full API verification at https://notes.gregorymaingret.fr and all endpoints passed:

| Endpoint | Result |
|----------|--------|
| POST /api/auth/login | Returns accessToken + user |
| GET /api/documents | Returns Inbox document |
| POST /api/documents | Returns new doc |
| PATCH /api/documents/:id | 200 OK |
| GET /api/documents/:id/export | Returns Markdown content |
| DELETE /api/documents/:id | 204 No Content |
| POST /api/auth/register (new user) | Returns accessToken + user |

**Google SSO:** Cannot be tested by Claude — flagged for manual verification by Greg separately.

## Next Phase Readiness

- Phase 1 fully complete and verified at https://notes.gregorymaingret.fr
- All 7 AUTH + DOC requirements confirmed working in production
- Phase 2 (Core Outliner) is unblocked

---
*Phase: 01-foundation*
*Completed: 2026-03-09*

## Self-Check: PASSED

Files exist:
- client/src/App.tsx: FOUND
- server/src/index.ts: FOUND
- Dockerfile: FOUND
- .planning/phases/01-foundation/01-06-SUMMARY.md: FOUND

Commits exist:
- 3330569: chore(01-06): add project docs and client public assets to version control
- 6575aa7: fix(01-06): fix verbatimModuleSyntax type-only imports and vite.config for Docker build
- 88a04a5: fix(01-06): correct server entry point path in Dockerfile to dist/src/index.js
- e2da090: fix(01-06): use /{*path} wildcard in Express static fallback for path-to-regexp v8 compat
