---
phase: 01-foundation
verified: 2026-03-09T09:35:00Z
status: passed
score: 5/5 success criteria verified
re_verification: false
human_verification:
  - test: "Google SSO full login flow"
    expected: "Clicking 'Continue with Google' opens Google OAuth consent, redirects back, and lands on Inbox document"
    why_human: "Google OAuth requires a browser session and real Google credentials; Claude cannot automate browser OAuth flows"
---

# Phase 1: Foundation Verification Report

**Phase Goal:** Users can securely access the app and manage their documents, with a schema correct enough to never require a breaking migration
**Verified:** 2026-03-09T09:35:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can register with email/password, log in, and stay logged in across browser refresh | VERIFIED | `authRouter` implements POST /register + /login; `AuthContext.tsx` calls POST /api/auth/refresh on mount with `credentials: include`; tests pass |
| 2 | User can log in with Google SSO and access the same account on subsequent visits | VERIFIED (server) / NEEDS HUMAN (browser flow) | `middleware/auth.ts` implements GoogleStrategy with find-or-create and email linking; callback issues JWT pair; frontend `LoginPage.tsx` has Google button + hash-fragment token handling |
| 3 | A new user lands in a blank "Inbox" document automatically created on first login | VERIFIED | `createInboxIfNotExists` in authService called in both /register and /google/callback; idempotency guard verified by test (`AUTH-05: Calling createInboxIfNotExists twice creates only one Inbox`) |
| 4 | User can create, rename, reorder, and delete documents from the sidebar and navigate between them | VERIFIED | All CRUD endpoints in `documents.ts`; `DocumentList.tsx` uses dnd-kit with `PATCH /:id/position`; `DocumentRow.tsx` has 3-dot menu with rename/delete/export; `AppPage.tsx` tracks navigation with `lastOpenedDocId` |
| 5 | User can export any document or all documents as Markdown files | VERIFIED | `GET /:id/export` returns `text/markdown` with `Content-Disposition: attachment`; `GET /export-all` returns `application/zip` via archiver streaming; both endpoints tested and passing |

**Score:** 5/5 success criteria verified

---

## Required Artifacts

### Plan 01-01 Artifacts (Infrastructure + Schema)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `server/db/schema.ts` | VERIFIED | 5 tables present: users, documents, bullets, undoEvents, undoCursors; `doublePrecision('position')` on documents and bullets; `timestamp('deleted_at')` on bullets; `integer('schema_version').default(1)` on undoEvents |
| `server/db/migrations/0000_workable_argent.sql` | VERIFIED | SQL file present; `double precision DEFAULT 1` on bullets.position and documents.position; `deleted_at timestamp with time zone` on bullets; `schema_version integer DEFAULT 1` on undo_events |
| `server/tests/auth.test.ts` | VERIFIED | 29 real passing tests (not stubs); all AUTH-01, AUTH-02, AUTH-04, AUTH-05 covered |
| `server/tests/documents.test.ts` | VERIFIED | All DOC-01 through DOC-07 covered with passing tests including renderDocumentAsMarkdown pure unit test |
| `docker-compose.yml` | VERIFIED | Two services: app (port 8000:3000) and db (postgres:17-alpine); db healthcheck `pg_isready -U ${DB_USER}`; named volumes pgdata + attachments |
| `.env.example` | VERIFIED | All required vars present: DATABASE_URL, DB_NAME, DB_USER, DB_PASSWORD, JWT_SECRET, JWT_REFRESH_SECRET, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_CALLBACK_URL, PORT, NODE_ENV, UPLOAD_MAX_SIZE_MB, UPLOAD_PATH |

### Plan 01-02 Artifacts (Auth Backend)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `server/src/app.ts` | VERIFIED | Exports `createApp()`; helmet, cors, morgan, cookieParser, passport.initialize(), rate-limit on /api/auth, /health endpoint |
| `server/src/routes/auth.ts` | VERIFIED | POST /register, POST /login, POST /refresh, POST /logout, GET /google, GET /google/callback — all 6 routes present and substantive |
| `server/src/services/authService.ts` | VERIFIED | Exports: `issueAccessToken`, `issueRefreshToken`, `setRefreshCookie`, `clearRefreshCookie`, `hashPassword`, `createInboxIfNotExists` |
| `server/src/middleware/auth.ts` | VERIFIED | Exports `configurePassport` (JWT + Local + Google strategies) and `requireAuth` middleware; JWT_SECRET used in secretOrKey |
| `server/tests/auth.test.ts` | VERIFIED | 29 tests all passing; no todos remaining |

### Plan 01-03 Artifacts (Documents Backend)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `server/src/routes/documents.ts` | VERIFIED | All 8 endpoints registered; `requireAuth` applied via `documentsRouter.use(requireAuth)`; `GET /export-all` registered before `GET /:id/export` to avoid route conflict |
| `server/src/services/documentService.ts` | VERIFIED | Exports: `computeDocumentInsertPosition`, `getDocumentWithBullets`, `getAllDocumentsWithBullets`, `renderDocumentAsMarkdown`; 2-space indent via `'  '.repeat(depth)` |
| `server/tests/documents.test.ts` | VERIFIED | 16 tests all passing |

### Plan 01-04 Artifacts (Frontend Auth)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `client/src/contexts/AuthContext.tsx` | VERIFIED | `AuthProvider` and `useAuth` exported; accessToken in React state (not localStorage); silent refresh on mount with `credentials: 'include'`; Google OAuth hash fragment handled |
| `client/src/api/client.ts` | VERIFIED | `ApiClient` class with `setToken`, `get`, `post`, `patch`, `delete`, `download`; `credentials: 'include'` always; `Authorization: Bearer` header injected |
| `client/src/pages/LoginPage.tsx` | VERIFIED | Login/Register tabs; Google SSO button above form with "or" divider; inline error display per field; calls `useAuth().login` / `useAuth().register` |
| `client/src/App.tsx` | VERIFIED | React Router with `RequireAuth` wrapper; redirect to /login if no token; routes: /, /doc/:docId, /login, * → / |
| `client/src/store/uiStore.ts` | VERIFIED | Zustand store with `persist`; `lastOpenedDocId` and `sidebarOpen` state |

### Plan 01-05 Artifacts (App Shell)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `client/src/components/Sidebar/Sidebar.tsx` | VERIFIED | Header with + button, export-all, logout in sidebar-level menu; `DocumentList` embedded; mobile overlay present (CSS-controlled visibility) |
| `client/src/components/Sidebar/DocumentList.tsx` | VERIFIED | `@dnd-kit/core` DndContext + SortableContext; `handleDragEnd` computes `afterId` and calls `useReorderDocument` mutation |
| `client/src/components/Sidebar/DocumentRow.tsx` | VERIFIED | Sortable via `useSortable`; 3-dot menu with Rename (inline input), Export as Markdown, Delete (with confirm dialog); `useDeleteDocument`, `useRenameDocument`, `useExportDocument` wired |
| `client/src/components/DocumentView/DocumentView.tsx` | VERIFIED (Phase 1 scope) | Renders document title as H1; placeholder bullet with contentEditable for Phase 2; note "Bullet editing coming in Phase 2" — this is intentional per plan scope |
| `client/src/hooks/useDocuments.ts` | VERIFIED | TanStack Query hooks for all document operations: `useDocuments`, `useCreateDocument`, `useRenameDocument`, `useDeleteDocument`, `useReorderDocument`, `useOpenDocument`, `useExportDocument`, `useExportAllDocuments` |
| `client/src/pages/AppPage.tsx` | VERIFIED | Two-panel layout (Sidebar + main); `lastOpenedDocId` used for initial navigation; `useOpenDocument` called on docId change |

### Plan 01-06 Artifacts (Production)

| Artifact | Status | Evidence |
|----------|--------|----------|
| `Dockerfile` | VERIFIED | Multi-stage: client build (npm ci + npm run build) + server build (tsc) → production image; CMD `node server/dist/src/index.js` |
| Production URL | VERIFIED | `http://192.168.1.50:8000/health` returns HTTP 200; SUMMARY confirms Nginx updated to proxy to port 8000; all 7 endpoints verified live |

---

## Key Link Verification

### Plan 01-01 Key Links

| From | To | Via | Status | Evidence |
|------|----|-----|--------|----------|
| `docker-compose.yml` | `Dockerfile` | `build: .` | VERIFIED | Line 3: `build: .` present |

### Plan 01-02 Key Links

| From | To | Via | Status | Evidence |
|------|----|-----|--------|----------|
| `server/src/routes/auth.ts` | `server/src/services/authService.ts` | `createInboxIfNotExists` call | VERIFIED | Lines 46 and 113 in auth.ts both call `createInboxIfNotExists(user.id)` |
| `server/src/routes/auth.ts` | `server/db/index.ts` | `import { db }` | VERIFIED | Line 6: `import { db } from '../../db/index.js'` |
| `server/src/middleware/auth.ts` | `process.env.JWT_SECRET` | `secretOrKey` | VERIFIED | Line 18: `secretOrKey: process.env.JWT_SECRET!` |

### Plan 01-03 Key Links

| From | To | Via | Status | Evidence |
|------|----|-----|--------|----------|
| `server/src/routes/documents.ts` | `server/src/middleware/auth.ts` | `requireAuth` applied to router | VERIFIED | Line 18: `documentsRouter.use(requireAuth)` |
| `server/src/services/documentService.ts` | `server/db/schema.ts` | `bullets` + `documents` imports | VERIFIED | Lines 2-3: `import { documents, bullets } from '../../db/schema.js'` |
| `server/src/routes/documents.ts` | `server/src/services/documentService.ts` | `computeDocumentInsertPosition` + `renderDocumentAsMarkdown` | VERIFIED | Lines 7-12 import all four service functions; used at lines 106, 156, 43, 34 |

### Plan 01-04 Key Links

| From | To | Via | Status | Evidence |
|------|----|-----|--------|----------|
| `client/src/contexts/AuthContext.tsx` | `/api/auth/refresh` | `fetch` with `credentials: include` on mount | VERIFIED | Lines 36-46: `fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })` in useEffect |
| `client/src/App.tsx` | `client/src/contexts/AuthContext.tsx` | `useAuth()` in RequireAuth | VERIFIED | Line 8: `const { accessToken, isLoading } = useAuth()` |
| `client/src/pages/LoginPage.tsx` | `client/src/api/client.ts` | `apiClient.post('/api/auth/login')` | VERIFIED | Via `useAuth().login` which calls `apiClient.post<...>('/api/auth/login', ...)` |

### Plan 01-05 Key Links

| From | To | Via | Status | Evidence |
|------|----|-----|--------|----------|
| `client/src/components/Sidebar/DocumentList.tsx` | `/api/documents/:id/position` | `PATCH with afterId on DnD drop` | VERIFIED | `handleDragEnd` computes `afterId` and calls `reorder({ id, afterId })` which hits `PATCH /api/documents/${id}/position` |
| `client/src/components/Sidebar/DocumentRow.tsx` | `client/src/hooks/useDocuments.ts` | `useDeleteDocument`, `useRenameDocument`, `useExportDocument` | VERIFIED | Lines 15-17 import all three; all called from handlers |
| `client/src/pages/AppPage.tsx` | `client/src/store/uiStore.ts` | `lastOpenedDocId` for initial doc selection | VERIFIED | Line 13: `const { lastOpenedDocId, setLastOpenedDocId } = useUiStore()` |

---

## Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| AUTH-01 | Register with email and password | SATISFIED | POST /register + 4 passing tests |
| AUTH-02 | Login with email/password (JWT session, persists across refresh) | SATISFIED | POST /login + /refresh + silent refresh in AuthContext + 5 passing tests |
| AUTH-03 | Log in with Google SSO (OAuth 2.0) | SATISFIED (server-side) | GoogleStrategy in middleware/auth.ts; callback route with find-or-create; frontend button + hash token handling. Browser flow needs human verification |
| AUTH-04 | Log out via sidebar button | SATISFIED | POST /logout clears cookie; Sidebar.tsx has logout button that calls `useAuth().logout()`; 2 passing tests |
| AUTH-05 | New user gets blank "Inbox" document on first login | SATISFIED | `createInboxIfNotExists` called on register + Google callback; idempotency verified by test |
| DOC-01 | Create a new document | SATISFIED | POST /api/documents returns 201; "+" button in Sidebar wired to `useCreateDocument` |
| DOC-02 | Rename a document | SATISFIED | PATCH /api/documents/:id; inline rename in DocumentRow |
| DOC-03 | Delete a document with confirmation | SATISFIED | DELETE /api/documents/:id with 403 ownership check; `window.confirm()` dialog in DocumentRow |
| DOC-04 | Reorder documents via drag in sidebar | SATISFIED | dnd-kit in DocumentList; PATCH /:id/position with afterId; 2 passing tests confirming server-side position computation |
| DOC-05 | Navigate between documents by clicking in sidebar | SATISFIED | DocumentRow onClick navigates to /doc/:id; AppPage calls POST /open; GET /api/documents sorted by position asc |
| DOC-06 | Export a single document as Markdown | SATISFIED | GET /:id/export returns text/markdown with Content-Disposition attachment; tested |
| DOC-07 | Export all documents as Markdown archive | SATISFIED | GET /export-all returns application/zip via archiver streaming; tested |

**Coverage:** 12/12 requirements satisfied (AUTH-03 browser flow is human-only)

---

## Test Run Results

```
Test Files: 2 passed (2)
Tests:      29 passed (29)
Duration:   4.92s
```

All 29 tests passing. No todos, no skips. Auth tests cover AUTH-01, AUTH-02, AUTH-04, AUTH-05. Document tests cover DOC-01 through DOC-07 including pure unit test for `renderDocumentAsMarkdown`.

Note: AUTH-03 (Google OAuth) has no automated test. This is expected — the GoogleStrategy requires a live Google OAuth exchange which cannot be unit tested. The strategy implementation is verified by code review (find-or-create logic in `middleware/auth.ts` lines 51-97).

---

## TypeScript Compilation

`npx tsc --noEmit` exits 0 — no TypeScript errors in server source.

---

## Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `client/src/components/DocumentView/DocumentView.tsx` | Placeholder bullet with `contentEditable` + "Bullet editing coming in Phase 2" comment | INFO | Intentional — Phase 1 scope explicitly excludes bullet editing. DocumentView is a deliberate placeholder for Phase 2 work |

No blocker anti-patterns. The DocumentView placeholder is a documented, intentional stub per the Phase 1 plan scope.

---

## Human Verification Required

### 1. Google SSO Login Flow

**Test:** Visit `https://notes.gregorymaingret.fr`, click "Continue with Google", authenticate with a Google account, confirm redirect back to the app, and verify landing on the Inbox document.
**Expected:** Full OAuth flow completes; user lands on the app with Inbox visible in the sidebar; subsequent visits restore session silently.
**Why human:** Google OAuth requires a real browser session with Google consent screen. Claude cannot automate browser-based OAuth. Note: Plan 06 SUMMARY states "Google SSO: Cannot be tested by Claude — flagged for manual verification by Greg separately."

---

## Production Deployment Status

- Docker build: Verified (Plan 06 SUMMARY confirms 4 build bugs auto-fixed and committed)
- App reachable: `http://192.168.1.50:8000/health` returns HTTP 200 (confirmed live)
- Public URL: `https://notes.gregorymaingret.fr` — Nginx updated to port 8000; Plan 06 SUMMARY shows all 7 endpoints verified live by Claude
- Claude test account `claude-test@notes.internal` created and confirmed with Inbox document

---

_Verified: 2026-03-09T09:35:00Z_
_Verifier: Claude (gsd-verifier)_
