# Phase 19: Server Foundation - Context

**Gathered:** 2026-03-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Standardize API error responses across all Express routes, fix undo/redo error handling, add CI/CD workflows for server and client, and wire upload env vars to actual code. All server-side or infrastructure — no client code changes in this phase.

</domain>

<decisions>
## Implementation Decisions

### Error response format
- Standard shape for all non-validation errors: `{ error: string }` — single string message
- Zod validation errors keep `{ errors: { field: ['message'] } }` for field-level form UX (auth routes use this)
- No `code` field needed for v2.3 — the HTTP status code is sufficient for client branching
- All unhandled exceptions return `{ error: 'Internal server error' }` with status 500 — never raw HTML

### Undo route error handling
- Empty undo/redo stack returns **422** with `{ error: 'Nothing to undo' }` / `{ error: 'Nothing to redo' }`
- Remove the dead `try/catch/throw err` pattern — Express 5 handles async throws natively
- Service-level exceptions that aren't empty-stack should return 500 with `{ error: string }` via global handler

### Global error middleware
- Register in `index.ts` AFTER all routes (not in `app.ts` — routes are mounted after `createApp()`)
- Catch-all Express error handler: log error, return `{ error: 'Internal server error' }` with 500
- Must handle both sync and async errors (Express 5 handles async automatically)

### CI/CD workflows
- Two separate workflow files: `server-ci.yml` and `client-ci.yml`
- Trigger on PRs to `main` and pushes to `phase-*` branches (match existing android-ci.yml pattern)
- Path filters: `server/**` for server CI, `client/**` for client CI
- Server CI: `npm ci` → `npm run build` → `npm test` (check if vitest tests need Postgres service container)
- Client CI: `npm ci` → `npm run lint` → `npm run build` (tsc + vite build)
- Use `actions/checkout@v4` and `actions/setup-node@v4` (match existing android-ci.yml versions)
- **NO SSH deploy steps** — CI is validation-only per CLAUDE.md deploy workflow
- Server CI may need `services: postgres:17-alpine` with env vars if tests hit the database

### Upload env var wiring
- Replace hardcoded `'/data/attachments'` in multer destination with `process.env.UPLOAD_PATH || '/data/attachments'`
- Replace hardcoded `100 * 1024 * 1024` in multer limits with `(Number(process.env.UPLOAD_MAX_SIZE_MB) || 100) * 1024 * 1024`
- Both changes in `server/src/routes/attachments.ts` (lines 35 and 44)
- Update `.env.example` comments to indicate these are now actually used

### Claude's Discretion
- Exact error messages for edge cases (attachment not found, bullet not found, etc.) — keep existing messages where they already use `{ error: string }`
- Whether to add a lint script to server package.json (currently has none)
- CI workflow Node.js version selection

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Error handling
- `server/src/routes/undo.ts` — Current dead try/catch/throw pattern to replace
- `server/src/routes/auth.ts` — Zod validation `{ errors }` format to preserve
- `server/src/routes/attachments.ts` — Existing `{ error: string }` pattern (already correct)
- `server/src/index.ts` — Where global error middleware must be registered (after routes, line 30+)
- `server/src/app.ts` — App factory; routes mounted in index.ts, NOT here

### CI/CD
- `.github/workflows/android-ci.yml` — Existing CI pattern to follow for structure, triggers, and action versions
- `server/package.json` — Scripts: `build` (tsc), `test` (vitest run); no `lint` script yet
- `client/package.json` — Scripts: `build` (tsc -b && vite build), `lint` (eslint .)

### Upload config
- `server/src/routes/attachments.ts:35` — Hardcoded `/data/attachments` destination
- `server/src/routes/attachments.ts:44` — Hardcoded `100 * 1024 * 1024` file size limit
- `.env.example:19-20` — UPLOAD_MAX_SIZE_MB and UPLOAD_PATH already declared but unused

### Research
- `.planning/research/ARCHITECTURE.md` — Integration points and error middleware placement analysis
- `.planning/research/PITFALLS.md` — CI pitfalls (no SSH deploy, Postgres in CI)
- `.planning/research/FEATURES.md` — Feature complexity notes and dependency chain

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `server/src/routes/attachments.ts`: Already uses `{ error: string }` consistently — model for other routes
- `.github/workflows/android-ci.yml`: CI workflow structure, trigger pattern, action versions

### Established Patterns
- Routes return `{ error: string }` for most errors (attachments, auth non-validation)
- Auth validation uses `{ errors: { field: ['msg'] } }` via Zod `.flatten().fieldErrors`
- Express 5 handles async route errors automatically (no need for express-async-errors)
- Rate limiting already in `app.ts` via `express-rate-limit`

### Integration Points
- Global error handler goes in `index.ts` after line 30 (after all `app.use('/api/...')` calls)
- Upload constants in `attachments.ts` lines 35 and 44
- Undo routes in `undo.ts` — 3 handlers, all with dead try/catch

### Error format inventory (current state)
- `attachments.ts`: `{ error: string }` — correct
- `auth.ts`: `{ errors: { field: ['msg'] } }` for Zod, `{ error: string }` for auth failures — keep both
- `bullets.ts`: needs audit for consistent format
- `documents.ts`: needs audit for consistent format
- `bookmarks.ts`: needs audit for consistent format
- `search.ts`: needs audit for consistent format
- `tags.ts`: needs audit for consistent format
- `undo.ts`: no error responses at all — throws raw errors

</code_context>

<specifics>
## Specific Ideas

No specific requirements — standard patterns apply. The user's codebase review identified the exact problems; implementation follows established Express/GitHub Actions conventions.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 19-server-foundation*
*Context gathered: 2026-03-19*
