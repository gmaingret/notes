# Project Research Summary

**Project:** Notes v2.3 — Robustness & Quality
**Domain:** Self-hosted outliner/PKM app — reliability and CI/CD improvements on a mature codebase
**Researched:** 2026-03-19
**Confidence:** HIGH (all four research files grounded in direct codebase inspection)

## Executive Summary

The Notes app is a feature-complete self-hosted outliner (Dynalist/Workflowy clone) with full auth, document management, hierarchical bullets, undo/redo, search, bookmarks, attachments, dark mode, PWA, and an Android client with home-screen widget. The v2.3 milestone is not a feature expansion — it is a reliability and developer-experience hardening phase on top of an already-working system. The research covers eight concrete improvements: CI/CD pipelines, token refresh interceptor, error boundaries, toast notifications, API error standardization, undo coverage extension, env var wiring, and component refactoring.

The recommended approach builds on the existing validated stack (React 19, Express 5, Drizzle, PostgreSQL, TanStack Query, Zustand, Kotlin/Compose Android) and adds two small dependencies: `sonner` for toast notifications and `react-error-boundary` for declarative boundaries. All other work is architectural pattern application to existing code. The build order is dictated by dependency chains: server error infrastructure must come first (error middleware, undo route fixes), then client infrastructure (ToastProvider, ErrorBoundary), then the 401 interceptor (which depends on both), then undo extensions, and refactoring last.

The top risks are all implementation-pattern mistakes, not design unknowns: the token refresh race condition (multiple concurrent 401s each triggering an independent refresh), the infinite refresh loop (missing `_isRetry` guard), error boundary scope (too high causes full-app crash on localized errors), and the CI pipeline accidentally including a deploy step (which would fill the 30GB server disk). All risks have clear prevention strategies already validated in this codebase — the Android client commit `0457017` demonstrates the correct `_isRetry` guard pattern.

## Key Findings

### Recommended Stack

The v2.3 stack additions are minimal by design. Two new client dependencies are justified: `sonner@2.0.7` (2-3KB gzipped, imperative API works inside TanStack Query `onError` callbacks, React 19 compatible) and `react-error-boundary@6.1.1` (avoids class component boilerplate, provides `resetKeys` for automatic boundary reset on navigation). All server-side work uses already-installed packages. CI/CD uses GitHub Actions (free, already in use for Android CI) with `actions/checkout@v6.0.2`, `actions/setup-node@v6.3.0`, and `actions/cache@v5.0.4` — no new npm dependencies for CI.

**Core technologies (v2.3 additions only):**
- `sonner@2.0.7`: toast notifications — preferred over react-toastify (heavier bundle, separate CSS import) and react-hot-toast (comparable size, slightly weaker API); imperative `toast.error(...)` call required for mutation `onError` callbacks
- `react-error-boundary@6.1.1`: declarative error boundaries — `resetKeys` prop eliminates manual lifecycle boilerplate; `useErrorBoundary` hook surfaces async errors into the boundary
- GitHub Actions workflows (no npm packages): `server-ci.yml` and `client-ci.yml` following the same structure as the already-working `android-ci.yml`

**Critical version constraint:** Drizzle ORM pinned at `0.40.0` — `0.45.x` has a broken npm package (missing `index.cjs`). Do not upgrade until resolved.

### Expected Features

The v2.3 feature set is distinguished from normal user-visible features. These are reliability fixes and quality-of-life improvements for a production app that is otherwise fully working.

**Must have (P1 — user-visible reliability failures without these):**
- 401 interceptor with automatic token refresh — silent session expiry currently breaks mutations with no feedback
- React error boundary at document level — unhandled render errors currently blank the whole screen
- Toast notifications for mutation failures — users get no feedback when PATCH/DELETE fails
- Undo route error handling — bare `throw err` currently returns HTML 500 from Express default handler

**Should have (P2 — materially improves codebase and operator experience):**
- CI/CD: server lint + test workflow — broken server merges are currently silent until deploy
- CI/CD: client lint + build workflow — Vite build failures are currently invisible until deploy
- Standardized API error response format — mixed `{ error }` / `{ errors }` / raw HTML across routes
- Extended undo coverage: mark-complete — inconsistent with the "50-level global undo" promise
- Env var wiring for `UPLOAD_MAX_SIZE_MB` and `UPLOAD_PATH` — hardcoded values violate 12-factor principles

**Defer to v2.4 (no user impact or higher complexity):**
- Extended undo: note edits — recoverable by re-typing; lower urgency
- Extended undo: bulk delete — requires a compound undo event type; highest complexity in milestone
- BulletContent refactor (768 lines to subcomponents) — pure structural improvement, no user impact
- BulletNode refactor (487 lines to subcomponents) — pure structural improvement, no user impact

### Architecture Approach

The v2.3 architecture is additive and layered. New server middleware is registered last in Express (after all routes, in `index.ts` not `createApp()` — critical ordering constraint). New client infrastructure wraps the existing App tree in `main.tsx`. The `ApiClient` gains injectable callbacks for auth refresh and failure notification using a runtime callback injection pattern that avoids a circular import between `client.ts` and `AuthContext.tsx`.

**Major components (new and modified):**
1. `server/src/middleware/errors.ts` (NEW) — Express 4-arg error handler registered last in `index.ts`; normalizes all unhandled errors to `{ error: string }` JSON
2. `client/src/components/ErrorBoundary.tsx` (NEW) — class component wrapping App tree; document-level boundary uses `key={documentId}` for auto-reset on navigation
3. `client/src/components/ToastProvider.tsx` (NEW) — React context + portal container; global notification layer distinct from the existing per-bullet `UndoToast`
4. `ApiClient.request()` (MODIFIED) — 401 guard with shared `refreshPromise` lock preventing concurrent refresh races; `_isRetry` flag preventing infinite loops
5. `AuthContext` (MODIFIED) — injects refresh and failure callbacks into `apiClient` on mount; keeps React state synchronized with background token refresh via `onTokenRefreshed` callback
6. `.github/workflows/server-ci.yml` + `client-ci.yml` (NEW) — path-filtered validation workflows; no deploy step

### Critical Pitfalls

1. **Token refresh race condition** — Multiple concurrent 401s each trigger `POST /api/auth/refresh` independently. With server-side refresh token rotation (v2.2), the second call hits a revoked token and logs the user out. Prevention: shared `private refreshPromise: Promise<string> | null` in `ApiClient`; all concurrent 401s `await` the same promise rather than starting new ones.

2. **Infinite refresh loop on persistent 401s** — Without a `_isRetry: true` flag, a 401 on the retried request re-triggers the interceptor indefinitely until the rate limiter (60 req/15min) returns 429. Prevention: `_isRetry` flag on retry; call `onAuthExpired()` and stop on second 401. Reference implementation: Android commit `0457017`.

3. **Error boundary placed too high** — A single boundary wrapping all of App catches localized document render errors as full-app failures. Prevention: layered strategy — root boundary for catastrophic failures; document-level boundary with `key={documentId}` for bullet tree errors; no per-node boundaries.

4. **Error boundary does not reset on navigation** — Without `key={documentId}`, navigating from an errored document to a healthy one leaves the boundary in its error state permanently. Prevention: `key={documentId}` on the document-level boundary; React remounts it on document change.

5. **CI deploy step fills server disk** — The 30GB server disk hit 100% in Phase 8 from Docker build cache accumulation. A CI workflow that SSH-deploys to `192.168.1.50` repeats this. Prevention: CI must validate only (lint, test, build); no `ssh root@192.168.1.50` in any workflow YAML; deployment remains manual SCP per CLAUDE.md.

## Implications for Roadmap

Based on the dependency graph in ARCHITECTURE.md, the natural build order is five phases in dependency sequence, with the refactoring phase isolated at the end.

### Phase 1: Server Foundation
**Rationale:** Error middleware, undo route fixes, and CI workflows have zero cross-dependencies and unblock all subsequent work. Standardizing the server error format before the client consumes it prevents the `[object Object]` display breakage. CI workflows are purely additive file additions.
**Delivers:** Consistent `{ error: string }` JSON from all routes; Express error middleware; undo routes returning 422 on empty stack; `server-ci.yml` and `client-ci.yml`; env var wiring for upload config
**Addresses:** API error standardization (P2), undo route error handling (P1), CI/CD (P1/P2), env var wiring (P2)
**Avoids:** API format mismatch breakage (Pitfall 7), CI deploy step disk fill (Pitfalls 9/12), Drizzle migration schema drift in CI (Pitfall 8)

### Phase 2: Client Infrastructure
**Rationale:** `ErrorBoundary` and `ToastProvider` must exist in the component tree before any downstream phase can reference them. These are new files with no integration complexity — zero regression risk.
**Delivers:** Global `ErrorBoundary` wrapping App; `ToastProvider` context + portal container; `useToast` hook wired in mutation `onError` callbacks; document-level boundary with `key={documentId}`
**Addresses:** React error boundary (P1), toast notifications for mutation failures (P1)
**Uses:** `react-error-boundary@6.1.1`, `sonner@2.0.7`
**Avoids:** Error boundary scope issues (Pitfalls 4/5), toast stacking (Pitfall 6), merging `ToastProvider` with `UndoToast` anti-pattern

### Phase 3: 401 Token Refresh Interceptor
**Rationale:** Depends on Phase 2 — auth failure must surface via toast. This is the highest-risk phase due to the race condition and loop pitfalls. Must be implemented precisely with the shared promise lock and `_isRetry` guard.
**Delivers:** Silent token refresh on 401; concurrent request queuing (single refresh at a time); one retry with logout on second 401; `AuthContext` stays synchronized with background refresh
**Addresses:** 401 interceptor (P1)
**Implements:** Callback injection pattern from ARCHITECTURE.md — avoids circular import between `client.ts` and `AuthContext.tsx`
**Avoids:** Refresh race condition (Pitfall 1), infinite refresh loop (Pitfall 2), AuthContext/ApiClient state divergence (Pitfall 3)

### Phase 4: Undo Coverage Extension
**Rationale:** Depends on Phase 1 (server error infrastructure propagates undo errors cleanly) and Phase 2 (toast needed for undo failure feedback). The `update_bullet` op type already exists in the schema — no migration needed for mark-complete or note edits.
**Delivers:** Undo for mark-complete; undo for note edits; undo for bulk delete (one compound event, one Ctrl+Z to restore all); `recordUndoEvent` called in `bulletService.markComplete()` and note patch path
**Addresses:** Extended undo coverage (P2/P3)
**Avoids:** Redo stack corruption from view-state operations (Pitfall 11 — `setCollapsed` must NOT add undo events); bulk delete creating N events instead of 1

### Phase 5: Component Refactoring
**Rationale:** Pure structural improvement with no user impact. Runs last to avoid a regression blocking reliability work. `BulletContent` (768 lines) and `BulletNode` (487 lines) are the candidates; cursor helpers are the safest extraction target because they are already pure functions.
**Delivers:** `cursorUtils.ts` with tested pure cursor functions; `useBulletKeyboard` hook; leaner `BulletContent` (~200 lines); leaner `BulletNode` with extracted swipe hook
**Addresses:** BulletContent refactor (P3), BulletNode refactor (P3)
**Avoids:** `saveTimerRef` crossing component boundaries (Pitfall 10 — save lifecycle state must stay in one component); behavior changes disguised as structural refactoring

### Phase Ordering Rationale

- Server error format must be standardized before the client starts consuming structured error messages — changing both ends in the same commit prevents the `[object Object]` pitfall
- `ToastProvider` and `ErrorBoundary` must exist before the 401 interceptor phase can surface auth failure via toast
- 401 interceptor is sequenced after client infrastructure because its failure path requires the global notification layer
- Undo extension depends on both server error propagation and toast feedback being in place
- Refactoring is last to ensure a regression does not block reliability work; the existing test suite is the safety net

### Research Flags

No phases require `/gsd:research-phase` during planning — all patterns are well-established and the codebase has been directly inspected. Specific verification steps are noted below.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Server Foundation):** Express error middleware and GitHub Actions Node.js workflows are canonical patterns
- **Phase 2 (Client Infrastructure):** `react-error-boundary` + `sonner` are documented with clear placement guidance; ARCHITECTURE.md contains production-ready code samples
- **Phase 3 (Token Refresh):** Reference implementation exists in this codebase (Android commit `0457017`); the shared-promise pattern is the standard SPA approach
- **Phase 4 (Undo Extension):** Follows the exact existing `recordUndoEvent` pattern; no new concepts required
- **Phase 5 (Refactoring):** Standard React hook extraction; pure functions are the safest target

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Package versions verified against npm registry and GitHub API 2026-03-19; peerDeps confirmed for both new dependencies |
| Features | HIGH | Based on direct codebase inspection of all affected files; scope well-bounded by PROJECT.md |
| Architecture | HIGH | Direct source inspection of all modified files; dependency graph is explicit; anti-patterns grounded in actual code structure |
| Pitfalls | HIGH | Every pitfall references specific file locations in the codebase; Android `_isRetry` pattern validated in commit history |

**Overall confidence:** HIGH

### Gaps to Address

- **Server test isolation:** ARCHITECTURE.md notes that if server tests require a live PostgreSQL connection, the CI workflow needs a `services: postgres:17-alpine` container. Verify before finalizing `server-ci.yml` — check whether existing Vitest tests mock the DB layer or require a real connection.
- **Drizzle `_journal.json` integrity in CI:** Known risk from Phase 4. A CI schema integrity check (query `information_schema.columns` for key tables) is recommended but not yet specified precisely. Validate during Phase 1 implementation.
- **`sonner` vs custom `ToastProvider`:** FEATURES.md suggests a custom 60-80 line `ToastProvider`. STACK.md recommends `sonner`. Resolution: use `sonner` for error/success notifications; keep the existing `UndoToast` component unchanged for per-bullet delete confirmation. These serve different purposes and must not be merged.

## Sources

### Primary (HIGH confidence)
- Direct codebase inspection: `client/src/api/client.ts`, `client/src/contexts/AuthContext.tsx`, `server/src/routes/undo.ts`, `server/src/app.ts`, `server/src/index.ts`, `BulletContent.tsx`, `BulletNode.tsx`, `bulletService.ts`, `undoService.ts`, `.github/workflows/android-ci.yml`
- npm registry direct query (2026-03-19): `sonner@2.0.7` and `react-error-boundary@6.1.1` peerDeps confirmed
- GitHub API tags (2026-03-19): `actions/checkout@v6.0.2`, `actions/setup-node@v6.3.0`, `actions/cache@v5.0.4`
- [react-error-boundary GitHub](https://github.com/bvaughn/react-error-boundary) — `resetKeys` prop and `useErrorBoundary` hook API (HIGH)
- Git commit `0457017` — Android `_isRetry` guard reference implementation (HIGH)
- `.planning/PROJECT.md` — milestone priorities, key decisions, server disk history (HIGH)

### Secondary (MEDIUM confidence)
- [Knock: Top React notification libraries 2026](https://knock.app/blog/the-top-notification-libraries-for-react) — `sonner` adoption and download data
- [LogRocket: React toast libraries compared 2025](https://blog.logrocket.com/react-toast-libraries-compared-2025/) — `sonner` vs `react-hot-toast` bundle comparison
- [LogRocket: CI/CD Node.js GitHub Actions](https://blog.logrocket.com/ci-cd-node-js-github-actions/) — GitHub Actions workflow structure patterns

---
*Research completed: 2026-03-19*
*Ready for roadmap: yes*
