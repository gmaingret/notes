# Feature Research

**Domain:** Robustness, error handling, CI/CD, and code quality improvements for a mature self-hosted outliner app
**Researched:** 2026-03-19
**Confidence:** HIGH — based on direct codebase inspection (client.ts, app.ts, undo.ts, attachments.ts, BulletContent.tsx, BulletNode.tsx, package.json files) plus well-established patterns for each improvement area

---

## Scope Note

This file covers the **v2.3 Robustness & Quality** milestone. The app is feature-complete with
fully functional auth, document management, bullets, undo/redo, search, bookmarks, attachments,
dark mode, PWA, and an Android client. All security hardening (v2.2) is done.

The 8 improvements in scope are not new user-visible features — they are reliability, developer
experience, and error-handling improvements on top of an already-working system. Categories below
reflect what is "expected" (i.e., what production apps of this maturity must have) vs. what
genuinely goes beyond the baseline.

---

## Feature Landscape

### Table Stakes (Expected in a Production App)

Features that any well-maintained self-hosted web app at this maturity level must have. Missing
these creates real friction for the developer and silent failures for the user.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| CI/CD: server lint + test on PR | Every non-trivial project tests on CI before merge — broken merges destroy confidence | LOW | Server already has `vitest` and TypeScript in package.json. GitHub Actions workflow file needed. Pattern: `actions/checkout`, `actions/setup-node`, `npm ci`, `npm run build`, `npm test`. No secrets needed for lint/test. |
| CI/CD: client lint + build on PR | Vite build failures are otherwise silent until deploy; ESLint is already configured | LOW | Client already has `eslint` and `tsc -b && vite build` in scripts. Same workflow pattern — `npm ci`, `npm run lint`, `npm run build`. Must set `NODE_ENV=production` to avoid dev-only code paths. |
| 401 interceptor with automatic token refresh | Users should never see "Request failed" on token expiry — silent refresh is the standard UX | MEDIUM | `apiClient` in `client.ts` currently throws on any non-ok response. The interceptor pattern: on 401, call `POST /api/auth/refresh`, update the access token in AuthContext and `apiClient`, then retry the original request once. Must handle concurrent 401s (queue them; only one refresh call). The httpOnly refresh cookie is already in place. |
| Standardized API error response format | Mixed `{ error }` / `{ errors }` / raw Express 500 HTML across routes makes client-side error handling fragile | LOW | Currently: attachments uses `{ error: string }`, bullets uses `{ errors: fieldErrors }` for validation failures, undo routes re-throw raw errors. Standard is `{ error: string, code?: string }` with optional machine-readable code. Requires touching ~8 route files. |
| Undo route error handling | Undo/redo throwing raw 500s means the user sees a broken mutation with no explanation | LOW | `undo.ts` currently `throw err` on both routes — Express default handler returns HTML 500. Should return `{ error: 'Undo failed — try again' }` with status 500 and log internally. Also handle empty-stack gracefully: `{ error: 'Nothing to undo' }` with 409. |
| React error boundary | An unhandled JS exception in `BulletContent` (768 lines) currently unmounts the whole DocumentView silently | MEDIUM | React's class-based `ErrorBoundary` wrapping `BulletTree` or each `BulletNode` catches render/lifecycle errors and shows a recovery UI ("Something went wrong — reload?"). React 19 also has `useErrorBoundary` hook but class boundary is still the reliable cross-version pattern. Should wrap at the document level, not per-bullet (per-bullet adds 1000 extra boundary instances). |
| Toast notifications for mutation failures | Users get no feedback today when a PATCH/DELETE fails silently via React Query's `onError` | MEDIUM | `@tanstack/react-query` mutation `onError` callbacks currently don't show anything visible. A minimal toast system (CSS-only, no library dependency) added to `useBullets.ts` hooks provides "Failed to save" / "Failed to delete" feedback. Should auto-dismiss after 4-5 seconds. Existing `UndoToast.tsx` shows the pattern already exists at the component level. |
| Env var wiring for UPLOAD_MAX_SIZE_MB and UPLOAD_PATH | Config that is hardcoded in source cannot be changed without rebuilding the Docker image — violates 12-factor app principles | LOW | `attachments.ts` currently hardcodes `100 * 1024 * 1024` and `/data/attachments`. Reading from `process.env.UPLOAD_MAX_SIZE_MB` with a fallback of `100` and `process.env.UPLOAD_PATH` with a fallback of `/data/attachments` is a one-line change per constant, plus `.env.example` updates. |

### Differentiators (Beyond the Baseline)

Improvements that go meaningfully further than what's required for a reliable app.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Extended undo coverage: mark-complete | Mark-complete is currently not undoable — inconsistent with "50-level global undo" promise | MEDIUM | `markComplete` in `bulletService.ts` needs to call `recordUndoEvent` before the update, same as `patchBullet` does. The `update_bullet` op type already handles `isComplete` field changes via `applyOp()`. Client `useMarkComplete` hook needs a checkpoint call. |
| Extended undo coverage: note edits | Note (comment) edits are not undoable — users editing notes can't recover mistaken overwrites | MEDIUM | `NoteRow.tsx` saves note content on blur. The undo service needs a new op type or reuse of `update_bullet` for note content. Requires a checkpoint before the note save. The note API route needs to call `recordUndoEvent`. |
| Extended undo coverage: bulk delete completed | Bulk delete (wipe all completed bullets) is not undoable — it's a destructive action with no recovery | HIGH | This is higher complexity: bulk delete affects multiple bullets. The undo event must record all affected bullet IDs in a single event. The `applyOp()` function would need a new `bulk_restore` op type, or a single event that loops over individual `restore_bullet` ops. |
| BulletContent refactor (768 lines → focused subcomponents) | Untestable monolith — cursor helpers, date picker, keyboard handlers, markdown rendering, and component JSX are all in one file | MEDIUM | Extractable pieces: `cursorHelpers.ts` (already pure functions — `isCursorAtStart`, `isCursorAtEnd`, `splitAtCursor`), `datePicker.ts` (imperative DOM helper), `useKeyboardHandlers.ts` (keyboard event hook), `useContentEditable.ts` (focus/blur/save lifecycle). The JSX render stays in `BulletContent.tsx` but at ~200 lines instead of 768. |
| BulletNode refactor (487 lines → focused subcomponents) | Single component handles drag, swipe gestures, context menu, attachment row, note row, undo toast — hard to test any piece in isolation | MEDIUM | `BulletNode` orchestrates too many concerns. Extractable: `useSwipeGesture.ts` (swipe state machine), `BulletActions.tsx` (action buttons strip), `BulletMeta.tsx` (attachment + note + undo toast section). The main `BulletNode` becomes a thin orchestrator. |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Retry loop on 401 (infinite refresh attempts) | "Always stay logged in" | If the refresh token is expired/revoked, infinite retries hammer the server and confuse the user with a frozen UI | Retry once on 401; on second 401 (refresh failed), call `clearAuth()` and redirect to `/login` |
| Per-route error boundaries | "Isolate failures at the finest grain" | Wrapping every `BulletNode` in an error boundary creates hundreds of boundary instances; React Dev Tools becomes noisy; catch-and-recover at bullet granularity masks bugs that need fixing | One boundary at DocumentView level; one boundary at app root level for catastrophic failures |
| Toast library (react-toastify, sonner, etc.) | "Easy, feature-rich notifications" | Adds a dependency to a codebase that already has a working `UndoToast.tsx` pattern; the existing CSS token system is sufficient for styled notifications | Build a minimal `ToastProvider` + `useToast` hook using the existing CSS variable system — 60-80 lines total |
| Separate error format per route (custom codes per endpoint) | "Granular machine-readable error codes" | Requires maintaining a cross-route enum, adds complexity to every route change, and the client currently only displays the human-readable message | Standard `{ error: string }` shape across all routes; add optional `code` field only if a specific client branch is genuinely needed (e.g., `'UNDO_EMPTY_STACK'` for the undo empty case) |
| Deploy pipeline (auto-deploy on merge) | "Full CI/CD like a real project" | This is a self-hosted app with a deliberate manual deploy workflow (SCP to server before git push); auto-deploy would break the confirm-before-commit workflow documented in CLAUDE.md | CI validates only; deploy remains manual per established workflow |

---

## Feature Dependencies

```
[CI/CD: server lint + test]
    └── independent of other improvements

[CI/CD: client lint + build]
    └── independent of other improvements

[Standardized API error response format]
    └── enhances──> [Toast notifications for mutation failures]
        (consistent { error: string } shape makes client error display trivial)
    └── enhances──> [Undo route error handling]
        (undo error responses follow the same shape)

[401 interceptor with token refresh]
    └── requires──> [AuthContext exposes a refreshToken() method or callback]
        (interceptor needs to trigger the silent refresh and update the token)
    └── conflicts with──> [Infinite retry loop] (anti-feature — must be one retry only)

[React error boundary]
    └── independent; placed at DocumentView level
    └── enhances──> [Toast notifications]
        (boundary can surface caught errors as toasts instead of blank screens)

[Toast notifications for mutation failures]
    └── requires──> [A toast context/provider visible to React Query hook callbacks]
        (hooks are outside JSX; they need a global toast emitter or context)

[Extended undo coverage: mark-complete]
    └── requires──> [recordUndoEvent called in bulletService.markComplete()]
    └── requires──> [useMarkComplete hook calls checkpoint before mutation]

[Extended undo coverage: note edits]
    └── requires──> [NoteRow saves checkpoint before blur-save]
    └── requires──> [Note API route calls recordUndoEvent]

[Extended undo coverage: bulk delete]
    └── requires──> [Extended undo coverage: mark-complete] (same service pattern established)
    └── requires──> [New bulk_restore op type in undoService, OR loop of restore_bullet ops]
    └── higher complexity than other undo extensions

[BulletContent refactor]
    └── independent of all non-refactor items
    └── enhances──> [Toast notifications] (smaller component = easier to add onError callbacks)
    └── conflicts with──> [Extended undo coverage] if done simultaneously
        (both touch BulletContent — do one before the other in the same phase)

[BulletNode refactor]
    └── independent of all non-refactor items
    └── should be done after or with BulletContent refactor (both in same phase)

[Env var wiring]
    └── independent; simplest change in the milestone
```

### Dependency Notes

- **Toast requires a global emitter:** React Query mutation `onError` callbacks run outside the React component tree rendering cycle in some patterns. A Zustand store slice or a simple event emitter (not context) is the most reliable way to trigger toasts from hooks. The existing `uiStore` (Zustand) is the natural place for a `toasts` slice.
- **401 interceptor and AuthContext coupling:** The interceptor in `apiClient` cannot import `useAuth` (that is a hook, not callable outside components). The refresh logic must be injectable — `apiClient.setRefreshHandler(fn)` called from `AuthProvider` on mount, so the client can trigger refresh without a React hook dependency.
- **Bulk delete undo is the only truly complex item:** All other undo extensions follow the exact same pattern as existing `patchBullet` undo. Bulk delete requires either a loop that creates multiple events (one per bullet) or a new compound event type. The loop approach is simpler and reuses `applyOp()` unchanged.
- **Refactors must not ship with behavior changes:** BulletContent and BulletNode refactors are pure structural changes. The test suite (existing `bulletTree.test.tsx`, `noteRow.test.tsx`, `attachmentRow.test.tsx`) should pass without modification after refactoring.

---

## MVP Definition (v2.3 Launch)

### Must Ship (Correctness and Reliability)

These have user-visible impact or real data-loss risk if absent:

- [ ] 401 interceptor with automatic token refresh — silent session expiry is a user-facing breakage
- [ ] React error boundary at DocumentView level — unhandled exceptions currently blank the screen
- [ ] Toast notifications for mutation failures — users have no feedback on failed saves/deletes
- [ ] Undo route error handling — raw 500s from undo are confusing and leave state uncertain

### Should Ship (Quality and DX)

These improve the codebase and operator experience materially:

- [ ] CI/CD: server lint + test workflow
- [ ] CI/CD: client lint + build workflow
- [ ] Standardized API error response format
- [ ] Extended undo coverage: mark-complete
- [ ] Env var wiring for UPLOAD_MAX_SIZE_MB and UPLOAD_PATH

### Can Slip to v2.4 (If Time Constrained)

- [ ] Extended undo coverage: note edits — less commonly triggered, recoverable by re-typing
- [ ] Extended undo coverage: bulk delete — higher complexity; defer unless the others are done
- [ ] BulletContent refactor — no user impact; pure structural improvement
- [ ] BulletNode refactor — no user impact; pure structural improvement

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| 401 interceptor + token refresh | HIGH | MEDIUM | P1 |
| React error boundary | HIGH | LOW | P1 |
| Toast notifications for mutation failures | HIGH | MEDIUM | P1 |
| Undo route error handling | MEDIUM | LOW | P1 |
| CI/CD server lint + test | MEDIUM | LOW | P1 |
| CI/CD client lint + build | MEDIUM | LOW | P1 |
| Standardized API error format | MEDIUM | LOW | P2 |
| Extended undo: mark-complete | MEDIUM | MEDIUM | P2 |
| Env var wiring | LOW | LOW | P2 |
| Extended undo: note edits | MEDIUM | MEDIUM | P3 |
| Extended undo: bulk delete | MEDIUM | HIGH | P3 |
| BulletContent refactor | LOW (DX) | MEDIUM | P3 |
| BulletNode refactor | LOW (DX) | MEDIUM | P3 |

**Priority key:**
- P1: Must have for v2.3 — these are reliability fixes, not enhancements
- P2: Should have — materially improves the codebase
- P3: Nice to have — defer if time-constrained

---

## Complexity Notes by Feature

**LOW complexity (single-file or 2-file changes, well-defined pattern):**
- Undo route error handling — change 3 throw-err lines in `undo.ts` to structured responses
- Env var wiring — 2 constant replacements in `attachments.ts` + `.env.example`
- Standardized API error format — mechanical find-replace across 8 route files
- CI/CD workflows — copy GitHub Actions patterns, no app code changes

**MEDIUM complexity (cross-cutting or requires new abstraction):**
- 401 interceptor — requires `apiClient` to accept a refresh handler injected from `AuthContext`; must handle concurrent 401s with a queue (one active refresh at a time)
- Toast notifications — requires global toast state (Zustand slice) visible to React Query mutation callbacks; requires building `ToastContainer` UI; requires wiring `onError` in ~6 mutation hooks
- React error boundary — requires class component (or React 19 `useErrorBoundary`); requires error recovery UI; requires placement decision (DocumentView vs BulletTree level)
- Extended undo: mark-complete — touches `bulletService.ts`, `bullets.ts` route, `useBullets.ts` hook
- BulletContent refactor — 768 lines, multiple concerns; extraction requires careful prop interface design to avoid prop drilling
- BulletNode refactor — 487 lines; swipe state machine extraction is the non-trivial piece

**HIGH complexity (requires new data model or compound behavior):**
- Extended undo: bulk delete — multiple bullets in one undo event; new op type or loop; must handle partial failures (some bullets already hard-deleted)

---

## Dependencies on Existing Code

| Existing Component | How v2.3 Improvements Build on It |
|-------------------|-----------------------------------|
| `apiClient` (`client/src/api/client.ts`) | 401 interceptor added to the `request()` method; `setRefreshHandler()` method added |
| `AuthContext` (`contexts/AuthContext.tsx`) | Injects refresh handler into `apiClient` on mount; exposes token update for interceptor callback |
| `undoService.ts` + `undo.ts` | Error handling added to route; `recordUndoEvent` called in `markComplete` |
| `attachments.ts` | 2 constants replaced with env var reads |
| `app.ts` | Global error handler middleware added (optional but natural home for 500 formatting) |
| `useBullets.ts` hooks | `onError` callbacks added to each mutation; checkpoint call added to `useMarkComplete` |
| `UndoToast.tsx` | Existing toast component is the visual/CSS pattern for the new general toast system |
| `uiStore.ts` (Zustand) | New `toasts` slice added for global toast state |
| `BulletContent.tsx` (768 lines) | Refactored — pure functions extracted to `cursorHelpers.ts`; keyboard hook extracted |
| `BulletNode.tsx` (487 lines) | Refactored — swipe hook extracted; action strip extracted |
| `.github/workflows/android-ci.yml` | Server/client CI workflows added alongside existing Android CI workflow |

---

## Sources

- Direct codebase inspection: `client/src/api/client.ts`, `client/src/contexts/AuthContext.tsx`, `server/src/routes/undo.ts`, `server/src/routes/attachments.ts`, `server/src/routes/bullets.ts`, `server/src/app.ts`, `client/src/components/DocumentView/BulletContent.tsx`, `client/src/components/DocumentView/BulletNode.tsx` — HIGH confidence
- `client/package.json` and `server/package.json` for existing scripts and dependencies — HIGH confidence
- `.github/workflows/android-ci.yml` for existing CI pattern — HIGH confidence
- `.planning/PROJECT.md` for milestone priorities and feature list — HIGH confidence
- React error boundary patterns: React documentation (class-based `componentDidCatch`, `getDerivedStateFromError`) — HIGH confidence (established React API since React 16)
- 401 interceptor with refresh queue: Standard axios/fetch interceptor pattern (retry-after-refresh with pending queue) — HIGH confidence (canonical approach for SPA token refresh)
- GitHub Actions Node.js workflow: `actions/setup-node` + `npm ci` + lint/test — HIGH confidence (official GitHub Actions pattern)

---

*Feature research for: Notes v2.3 Robustness & Quality milestone*
*Researched: 2026-03-19*
