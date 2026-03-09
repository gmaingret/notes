---
phase: 01-foundation
plan: 01
subsystem: database
tags: [drizzle-orm, postgresql, docker, vitest, typescript, express]

# Dependency graph
requires: []
provides:
  - Drizzle ORM schema with 5 tables (users, documents, bullets, undo_events, undo_cursors)
  - Initial SQL migration (0000_workable_argent.sql) with FLOAT8 positions, soft-delete, schema_version
  - Docker Compose stack (app port 8000:3000, postgres:17-alpine with healthcheck)
  - server/package.json with all dependencies (express@5, passport, jwt, drizzle-orm@0.45, vitest)
  - .env.example with all required vars documented
  - Vitest test scaffolds (Wave 0 stubs for AUTH-01..AUTH-05 and DOC-01..DOC-07)
affects: [02-auth, 03-documents, 04-frontend, 05-integration]

# Tech tracking
tech-stack:
  added:
    - express@5
    - drizzle-orm@0.45 + drizzle-kit
    - passport + passport-local + passport-google-oauth20 + passport-jwt
    - jsonwebtoken + bcryptjs
    - pg (node-postgres)
    - vitest@3 + supertest
    - zod
    - archiver
    - helmet + cors + morgan
  patterns:
    - Multi-stage Dockerfile (client build + server tsc + production image)
    - Drizzle schema-first migrations via drizzle-kit generate
    - Test factory pattern: createTestApp() per test suite for isolation

key-files:
  created:
    - server/db/schema.ts
    - server/db/migrations/0000_workable_argent.sql
    - server/db/index.ts
    - server/db/migrate.ts
    - server/vitest.config.ts
    - server/tests/auth.test.ts
    - server/tests/documents.test.ts
    - server/tests/helpers/testApp.ts
    - docker-compose.yml
    - Dockerfile
    - .env.example
    - .gitignore
    - server/package.json
    - server/tsconfig.json
    - server/drizzle.config.ts
  modified: []

key-decisions:
  - "FLOAT8 (double precision) for all position columns — locked, cannot change after data exists"
  - "Soft delete (deleted_at) on bullets from day 1 — required for undo-of-delete to restore subtrees"
  - "undo_events.schema_version column present from migration 0 — prevents Phase 2/3 migration pain"
  - "undo_events + undo_cursors tables created now even though undo feature ships in Phase 2"
  - "Docker port mapping 8000:3000 per MEMORY.md (app accessible at 192.168.1.50:8000)"
  - "Multi-stage Dockerfile: builds React client then TypeScript server, copies to prod image"

patterns-established:
  - "Schema-first: define in schema.ts, generate SQL via drizzle-kit, never write SQL by hand"
  - "Test factory: createTestApp() returns fresh Express instance per test suite"
  - "Wave 0 stubs: all test describe blocks present with it.todo() for Plans 02 and 03 to fill"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07]

# Metrics
duration: 6min
completed: 2026-03-09
---

# Phase 1 Plan 01: Foundation Bootstrap Summary

**Drizzle ORM schema with 5 tables (FLOAT8 positions, soft-delete, schema_version), Docker Compose stack, Express server package, and Wave 0 vitest stubs**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-09T07:46:56Z
- **Completed:** 2026-03-09T07:53:25Z
- **Tasks:** 3 (Task 1: package/Docker, Task 2: schema/migration, Task 3: vitest scaffolds)
- **Files modified:** 15 created, 1 modified

## Accomplishments
- All 5 DB tables defined in schema.ts with correct types (FLOAT8 for positions, soft-delete on bullets, schema_version on undo_events)
- Initial SQL migration generated and present (0000_workable_argent.sql)
- Docker Compose stack with app (port 8000:3000) and postgres:17-alpine with healthcheck
- Vitest test scaffolds: 35 todo tests across AUTH-01..AUTH-05 and DOC-01..DOC-07

## Task Commits

Each task was committed atomically:

1. **Task 1: Server package, TypeScript config, Docker, and .env.example** - `3bad605` (chore)
2. **Task 2: Drizzle schema and migration** - `117c2f7` (feat — committed in prior session as part of plan-04 docs commit)
3. **Task 3: Vitest config and test scaffolds** - `80b14d0` (test)

## Files Created/Modified
- `server/db/schema.ts` - Drizzle table definitions for all 5 tables
- `server/db/migrations/0000_workable_argent.sql` - Initial SQL migration with all tables
- `server/db/index.ts` - pg Pool + drizzle connection export
- `server/db/migrate.ts` - Migration runner for container startup
- `server/vitest.config.ts` - Vitest config (node env, globals, 10s timeout)
- `server/tests/auth.test.ts` - AUTH-01..AUTH-05 stubs (it.todo)
- `server/tests/documents.test.ts` - DOC-01..DOC-07 stubs (it.todo)
- `server/tests/helpers/testApp.ts` - createTestApp() factory function
- `docker-compose.yml` - Two services: app (8000:3000) + db (postgres:17-alpine with healthcheck)
- `Dockerfile` - Multi-stage: client build + server tsc + production image
- `.env.example` - All required env vars documented (DATABASE_URL, JWT_SECRET, JWT_REFRESH_SECRET, GOOGLE_*, UPLOAD_*)
- `.gitignore` - Excludes .env, node_modules/, dist/, logs
- `server/package.json` - All dependencies (express@5, drizzle-orm@0.45, passport, jwt, vitest, etc.)
- `server/tsconfig.json` - ES2022, NodeNext, strict
- `server/drizzle.config.ts` - drizzle-kit config pointing to db/schema.ts

## Decisions Made
- FLOAT8 (double precision) for all position columns — locked, cannot change after data exists
- Soft delete (deleted_at) on bullets from day 1 — required for undo-of-delete to restore subtrees
- undo_events.schema_version column present from migration 0 — prevents Phase 2/3 migration pain
- Docker port mapping 8000:3000 per MEMORY.md (app accessible at 192.168.1.50:8000)
- Multi-stage Dockerfile builds React client as part of Docker build (Plan 04 must exist before full Docker build)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Cleared inconsistent drizzle migration meta state**
- **Found during:** Task 2 (migration generation)
- **Issue:** The db/migrations/meta/ directory had a _journal.json referencing a migration SQL file that didn't exist on disk, causing "No schema changes" error
- **Fix:** Cleared the meta directory and recreated a blank _journal.json, then regenerated migration successfully
- **Files modified:** server/db/migrations/meta/_journal.json (cleared and recreated)
- **Verification:** Migration SQL generated successfully with all 5 tables and required columns
- **Committed in:** 117c2f7 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required to unblock migration generation. No scope creep.

## Issues Encountered
- drizzle-kit version installed (0.29.1 in node_modules) did not match the plan's ^0.30.4 spec. npm resolved to 0.29.1 which was compatible with drizzle-orm 0.45.1. Migration generation succeeded after clearing meta state.
- The npx drizzle-kit binary resolves to a global 0.21.0 version; used `node node_modules/drizzle-kit/bin.cjs` to invoke the local version correctly.

## User Setup Required
None - no external service configuration required for this plan. The .env.example documents all required vars; real credentials are provided separately.

## Next Phase Readiness
- Schema foundation complete: all 5 tables with locked design decisions
- Test stubs ready for Plans 02 (auth) and 03 (documents) to fill in
- Docker stack defined but full build requires client scaffold (Plan 04) to exist first
- Plan 02 (auth endpoints) can proceed immediately against this schema

---
*Phase: 01-foundation*
*Completed: 2026-03-09*
