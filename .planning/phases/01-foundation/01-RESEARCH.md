# Phase 1: Foundation - Research

**Researched:** 2026-03-09
**Domain:** Node.js/Express backend + React frontend — auth, database schema, document CRUD, export
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Auth UX**: Login and Register on a single page with tabs — no separate routes
- **Auth UX**: Unauthenticated users hitting `/` are immediately redirected to `/login`; no landing page
- **Auth UX**: Google SSO button appears above the email/password form with an "or" divider
- **Auth UX**: Auth errors display inline below the relevant input field
- **Sidebar**: Document rows show name only — no metadata (no date, no bullet count)
- **Sidebar**: Active document highlighted with a subtle background color on its row
- **Sidebar**: Fixed width ~240px on desktop (not resizable)
- **Sidebar**: Document actions triggered by a 3-dot menu that appears on hover/focus — no always-visible icons
- **App entry**: After login, land on last opened document; falls back to Inbox for new users
- **App entry**: Empty document shows a single placeholder bullet ready to type in
- **App entry**: When sidebar hidden (default desktop): no-focus toolbar + breadcrumb bar shown at top
- **App entry**: On mobile, sidebar slides over the canvas from the left; tap outside to dismiss
- **Export**: Single document export triggered from document's 3-dot menu in sidebar
- **Export**: All documents export: bulk option accessible from a sidebar-level menu entry
- **Export**: All-docs format: .zip archive with one .md file per document
- **Export**: Immediate browser download — no progress UI, no toast
- **Export**: Nesting format in exported Markdown: indented dashes (`- ` with 2-space indent per level)
- **Schema**: Tree position uses `FLOAT8` midpoint positioning — NOT string fractional keys
- **Schema**: Soft delete (`deleted_at`) on bullets from day 1
- **Schema**: `undo_events` and `undo_cursors` tables created in Phase 1 even though undo ships in Phase 2
- **Editor model**: Per-bullet contenteditable (not ProseMirror document)

### Claude's Discretion
- Exact color palette and typography (keep clean and minimal — Dynalist-inspired)
- Loading states, skeleton screens
- Form validation timing (on blur vs on submit vs real-time)
- JWT token storage mechanism (localStorage vs httpOnly cookie — Claude decides based on security tradeoffs)
- Docker Compose structure and container count

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within Phase 1 scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| AUTH-01 | User can register an account with email and password | bcryptjs hashing + Drizzle INSERT + zod validation |
| AUTH-02 | User can log in with email and password (JWT session, persists across refresh) | passport-local + jsonwebtoken + httpOnly cookie refresh token |
| AUTH-03 | User can log in with Google SSO (OAuth 2.0) | passport-google-oauth20 server-side callback → same JWT pair |
| AUTH-04 | User can log out via sidebar button | Clear httpOnly refresh cookie + invalidate in-memory access token |
| AUTH-05 | New user automatically gets a blank "Inbox" document on first login | Post-registration/first-OAuth-login trigger in auth service |
| DOC-01 | User can create a new document (flat list, no folders) | POST /api/documents with Drizzle INSERT |
| DOC-02 | User can rename a document | PATCH /api/documents/:id — title field only |
| DOC-03 | User can delete a document (with confirmation) | DELETE /api/documents/:id — cascades to bullets via FK |
| DOC-04 | User can reorder documents via drag in the sidebar | @dnd-kit/sortable + PATCH /api/documents/:id/position with FLOAT8 midpoint |
| DOC-05 | User can navigate between documents by clicking in the sidebar | React Router v6 + client-side route change + store last_opened_doc_id |
| DOC-06 | User can export a single document as a Markdown file | Server: GET /api/documents/:id/export → text/markdown; Client: anchor download |
| DOC-07 | User can export all documents as a Markdown archive | Server: GET /api/documents/export-all → application/zip (JSZip or archiver); Client: blob download |
</phase_requirements>

---

## Summary

Phase 1 establishes everything else depends on: a correct database schema, working authentication (email/password + Google OAuth), document CRUD, and export. The schema decisions made here are permanent — position column type, soft delete, GIN index, and undo table structure cannot be changed once data exists. The ecosystem research has already resolved all major architecture questions; this phase implements well-documented patterns with no novel integrations.

The JWT storage decision (Claude's discretion) resolves clearly in favor of **access token in memory (React state) + refresh token in httpOnly cookie**. This is the 2025 consensus and avoids XSS token theft from localStorage while not requiring server-side session storage. The pattern is: 15-minute access token stored in React context (lost on page reload) + 7-day refresh token in a `Secure; HttpOnly; SameSite=Strict` cookie. On page load, the app silently calls `POST /api/auth/refresh` — if the cookie is valid, a new access token is issued; otherwise the user is redirected to `/login`.

The export decision (Claude's discretion, effectively) resolves as: single-doc export is a server-generated text/markdown response (simple `res.setHeader` + `res.send`); all-docs export is server-generated via Node's `archiver` npm package (a zip stream written to the response), not client-side JSZip. Server-side zip avoids sending all document content to the client before zipping.

**Primary recommendation:** Implement in strict order: Drizzle schema + migrations → auth endpoints → document CRUD endpoints → React routing + auth pages → sidebar + document list → export endpoints. Never build the frontend for a feature until its server endpoint is tested.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Node.js | 22 LTS | Backend runtime | LTS through 2027; Express 5 requires ≥ 18 |
| Express | 5.2.x | HTTP framework | Stable since April 2025; async error propagation built-in |
| PostgreSQL | 16 or 17 | Database | Adjacency list + WITH RECURSIVE is first-class |
| Drizzle ORM | 0.45.x | DB access + migrations | TypeScript-native; no code-gen step; stays on 0.45.x (not 1.0-beta) |
| drizzle-kit | 0.45.x | Migration generation | `drizzle-kit generate` → SQL files; `drizzle-kit migrate` applies them; same minor as drizzle-orm |
| React | 19.x | Frontend UI | Largest ecosystem; concurrent features for tree renders |
| Vite | 6.x | Frontend build | Standard since CRA deprecation; `npm create vite@latest -- --template react-ts` |
| TypeScript | 5.x | Type safety | Applied to both server and client |

### Auth Libraries

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| passport | 0.7.x | Auth middleware orchestrator | De facto Node.js auth; pluggable strategies |
| passport-local | 1.0.x | Email/password strategy | Standard, stable |
| passport-google-oauth20 | 2.0.x | Google OAuth 2.0 | Server-side code exchange → profile; issues app JWT pair |
| passport-jwt | 4.0.x | JWT verification on protected routes | Reads Bearer token from Authorization header |
| jsonwebtoken | 9.0.x | JWT sign/verify | HS256 with strong secret; 15-min access tokens |
| bcryptjs | 5.x | Password hashing | Pure JS (no native bindings = no compile step in Docker); cost factor 12 |
| cookie-parser | 1.4.x | Parse httpOnly refresh cookie | Required to read `res.cookies.refreshToken` |
| express-rate-limit | 7.x | Rate limit auth endpoints | Prevent brute-force on `/api/auth/*` |

### Routing + State (Frontend)

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| react-router-dom | 6.x | Client routing | v6 outlet-based protected routes; `<Navigate>` for redirects |
| @tanstack/react-query | 5.x | Server state + mutations | Optimistic updates with rollback; document list cache |
| zustand | 5.x | UI state | Last-opened doc ID; sidebar open/close; auth token in memory |

### Drag-and-Drop (Sidebar Document Reorder)

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @dnd-kit/core | 6.x | DnD primitives | Accessible; touch + keyboard; actively maintained |
| @dnd-kit/sortable | 8.x | Flat sortable list | `SortableContext` + `useSortable` for sidebar document list |

### Export

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| archiver | 7.x | Server-side ZIP generation | Streams zip directly to HTTP response; no buffering entire zip in memory; standard Node zip library |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| zod | 3.x | Runtime validation | All request bodies server-side; shared schemas with client |
| helmet | 8.x | Security headers | First middleware on Express app |
| cors | 2.x | CORS | Dev only (Vite :5173 vs Express :3000); same-origin in production |
| morgan | 1.x | HTTP logging | Development |
| dotenv | 16.x | .env loading | Development; Docker passes env vars in production |
| pg | 8.x | PostgreSQL driver | Drizzle uses this under the hood; install explicitly |
| express-rate-limit | 7.x | Rate limiting | Auth routes only |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| archiver (server) | JSZip (client) | Client-side JSZip requires all content sent to browser first, then re-zipped — wasteful and slower |
| httpOnly cookie refresh | localStorage | localStorage is XSS-vulnerable; httpOnly cookie is immune to JS access |
| Access token in memory | httpOnly cookie for access token too | Memory access token enables silent refresh without CSRF; both in httpOnly works but adds complexity |
| Drizzle 0.45.x | Drizzle 1.0-beta | 1.0-beta changes migration format; stay on stable until 1.0 reaches stable release |

**Installation:**
```bash
# Backend (inside /server)
npm install express@^5 passport passport-local passport-google-oauth20 passport-jwt \
  jsonwebtoken bcryptjs cookie-parser express-rate-limit \
  pg drizzle-orm zod helmet cors morgan dotenv archiver

npm install -D typescript tsx drizzle-kit @types/express @types/node \
  @types/passport @types/passport-local @types/passport-google-oauth20 \
  @types/passport-jwt @types/jsonwebtoken @types/bcryptjs @types/cookie-parser \
  @types/cors @types/morgan @types/archiver vitest

# Frontend (scaffold then add)
npm create vite@latest client -- --template react-ts

cd client
npm install react-router-dom @tanstack/react-query zustand \
  @dnd-kit/core @dnd-kit/sortable zod

npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

---

## Architecture Patterns

### Recommended Project Structure
```
notes/
├── server/
│   ├── db/
│   │   ├── schema.ts          # Drizzle table definitions (single source of truth)
│   │   ├── index.ts           # pg Pool + Drizzle instance
│   │   └── migrations/        # SQL files generated by drizzle-kit
│   ├── routes/
│   │   ├── auth.ts            # register, login, google, logout, refresh
│   │   └── documents.ts       # CRUD + export endpoints
│   ├── services/
│   │   ├── authService.ts     # password hash/verify, JWT sign, user create
│   │   └── documentService.ts # doc CRUD, position midpoint logic, export
│   ├── middleware/
│   │   └── auth.ts            # passport-jwt middleware (requireAuth)
│   ├── app.ts                 # Express setup, middleware, route mounting
│   └── index.ts               # Server entry point (listen)
├── client/
│   └── src/
│       ├── api/
│       │   └── client.ts      # fetch wrapper that injects Authorization header
│       ├── contexts/
│       │   └── AuthContext.tsx # access token in React state; silent refresh on mount
│       ├── pages/
│       │   ├── LoginPage.tsx  # tabs: Login / Register; Google SSO button above divider
│       │   └── AppPage.tsx    # layout: sidebar + main panel
│       ├── components/
│       │   ├── Sidebar/
│       │   │   ├── Sidebar.tsx
│       │   │   ├── DocumentList.tsx   # @dnd-kit sortable list
│       │   │   └── DocumentRow.tsx    # name + 3-dot menu on hover
│       │   └── DocumentView/
│       │       └── DocumentView.tsx   # placeholder bullet if empty
│       ├── hooks/
│       │   └── useDocuments.ts # TanStack Query for document list
│       └── App.tsx            # React Router routes + RequireAuth wrapper
├── docker-compose.yml
├── Dockerfile
└── .env.example
```

### Pattern 1: JWT with httpOnly Refresh Cookie

**What:** Short-lived access token in React memory + long-lived refresh token in httpOnly cookie.
**When to use:** Every protected API call; page reload triggers silent refresh.

```typescript
// Source: 2025 consensus pattern (cybersierra.co, stackinsight.dev)

// Server: issue token pair after successful auth
function issueTokens(res: Response, userId: string) {
  const accessToken = jwt.sign({ sub: userId }, process.env.JWT_SECRET!, {
    expiresIn: '15m',
  });
  const refreshToken = jwt.sign({ sub: userId }, process.env.JWT_REFRESH_SECRET!, {
    expiresIn: '7d',
  });

  res.cookie('refreshToken', refreshToken, {
    httpOnly: true,
    secure: true,          // HTTPS only
    sameSite: 'strict',    // CSRF protection
    maxAge: 7 * 24 * 60 * 60 * 1000,
  });

  return accessToken; // Sent in response body; client stores in React state
}

// Client: AuthContext silently refreshes on mount
useEffect(() => {
  fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
    .then(r => r.ok ? r.json() : null)
    .then(data => data ? setAccessToken(data.accessToken) : null);
}, []);
```

### Pattern 2: Drizzle Schema + Migration Flow

**What:** Code-first TypeScript schema → SQL migration files → applied at container start.
**When to use:** All schema changes go through this flow; never use `push` in production.

```typescript
// Source: https://orm.drizzle.team/docs/get-started/postgresql-new

// server/db/schema.ts
import { pgTable, uuid, text, timestamp, doublePrecision, boolean, bigserial, integer, jsonb } from 'drizzle-orm/pg-core';

export const users = pgTable('users', {
  id: uuid('id').primaryKey().defaultRandom(),
  email: text('email').notNull().unique(),
  passwordHash: text('password_hash'),            // null for OAuth-only users
  googleId: text('google_id').unique(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

export const documents = pgTable('documents', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  title: text('title').notNull().default('Untitled'),
  position: doublePrecision('position').notNull().default(1.0),  // FLOAT8 sidebar order
  lastOpenedAt: timestamp('last_opened_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

export const bullets = pgTable('bullets', {
  id: uuid('id').primaryKey().defaultRandom(),
  documentId: uuid('document_id').notNull().references(() => documents.id, { onDelete: 'cascade' }),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  parentId: uuid('parent_id'),                    // self-reference, nullable = root bullet
  content: text('content').notNull().default(''),
  position: doublePrecision('position').notNull().default(1.0),  // FLOAT8 sibling order
  isComplete: boolean('is_complete').notNull().default(false),
  isCollapsed: boolean('is_collapsed').notNull().default(false),
  deletedAt: timestamp('deleted_at', { withTimezone: true }),     // soft delete for undo
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

export const undoEvents = pgTable('undo_events', {
  id: bigserial('id', { mode: 'number' }).primaryKey(),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  seq: integer('seq').notNull(),
  schemaVersion: integer('schema_version').notNull().default(1), // version payload format
  eventType: text('event_type').notNull(),
  forwardOp: jsonb('forward_op').notNull(),
  inverseOp: jsonb('inverse_op').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

export const undoCursors = pgTable('undo_cursors', {
  userId: uuid('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
  currentSeq: integer('current_seq').notNull().default(0),
});
```

```bash
# Generate migration SQL from schema diff
npx drizzle-kit generate

# Apply migrations (run in Dockerfile or startup script)
npx drizzle-kit migrate
```

### Pattern 3: Google OAuth Server-Side Flow

**What:** Passport handles the OAuth redirect; server exchanges code for tokens, fetches profile, issues app JWT pair, redirects client to app with access token in query param (one-time).
**When to use:** AUTH-03 implementation.

```typescript
// Source: https://www.passportjs.org/packages/passport-google-oauth20/
// Source: DEV Community — passport-google-oauth20 + JWT 2025

passport.use(new GoogleStrategy({
  clientID: process.env.GOOGLE_CLIENT_ID!,
  clientSecret: process.env.GOOGLE_CLIENT_SECRET!,
  callbackURL: process.env.GOOGLE_CALLBACK_URL!,
}, async (accessToken, refreshToken, profile, done) => {
  // Never store Google accessToken — only use profile.id and profile.emails[0].value
  const email = profile.emails?.[0].value;
  const googleId = profile.id;
  let user = await db.query.users.findFirst({ where: eq(users.googleId, googleId) });
  if (!user) {
    // Upsert by email if user registered with email/password first
    user = await findOrCreateGoogleUser(email, googleId);
    await createInboxDocument(user.id); // AUTH-05
  }
  done(null, user);
}));

// Route: GET /api/auth/google/callback
router.get('/google/callback',
  passport.authenticate('google', { session: false, failureRedirect: '/login?error=oauth' }),
  (req, res) => {
    const user = req.user as User;
    const appAccessToken = issueAccessToken(user.id);
    setRefreshCookie(res, user.id);
    // Redirect to app with short-lived token in URL fragment (not query string)
    // Client extracts token from fragment and stores in React state; fragment never sent to server
    res.redirect(`/?token=${appAccessToken}`);
  }
);
```

### Pattern 4: Protected Routes (React Router v6)

**What:** `RequireAuth` wrapper component redirects unauthenticated users to `/login`.
**When to use:** All routes except `/login`.

```typescript
// Source: https://ui.dev/react-router-protected-routes-authentication

function RequireAuth({ children }: { children: ReactNode }) {
  const { accessToken } = useAuth();
  const location = useLocation();

  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

// App.tsx
<Routes>
  <Route path="/login" element={<LoginPage />} />
  <Route path="/*" element={
    <RequireAuth>
      <AppPage />
    </RequireAuth>
  } />
</Routes>
```

### Pattern 5: Document Export — Single File

**What:** Server sends text/markdown response; browser downloads via `Content-Disposition: attachment`.
**When to use:** DOC-06 — single document export from 3-dot menu.

```typescript
// No extra library needed — pure Express
router.get('/api/documents/:id/export', requireAuth, async (req, res) => {
  const doc = await documentService.getWithBullets(req.params.id, req.user!.id);
  const markdown = renderDocumentAsMarkdown(doc); // bullets → indented dashes
  res.setHeader('Content-Type', 'text/markdown; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="${sanitize(doc.title)}.md"`);
  res.send(markdown);
});
```

### Pattern 6: Document Export — All Documents (ZIP)

**What:** Server streams zip archive via `archiver`; browser receives blob and triggers download.
**When to use:** DOC-07 — all documents export from sidebar-level menu.

```typescript
// Source: archiver npm package (standard Node streaming zip)
import archiver from 'archiver';

router.get('/api/documents/export-all', requireAuth, async (req, res) => {
  const docs = await documentService.getAllWithBullets(req.user!.id);

  res.setHeader('Content-Type', 'application/zip');
  res.setHeader('Content-Disposition', 'attachment; filename="notes-export.zip"');

  const archive = archiver('zip', { zlib: { level: 6 } });
  archive.pipe(res);

  for (const doc of docs) {
    const md = renderDocumentAsMarkdown(doc);
    archive.append(md, { name: `${sanitize(doc.title)}.md` });
  }

  await archive.finalize();
});
```

### Pattern 7: FLOAT8 Midpoint Positioning

**What:** Compute new sibling position as midpoint between neighbors; always inside a transaction.
**When to use:** Every document reorder (DOC-04) and bullet insert/move (future phases).

```typescript
// Source: PITFALLS.md, STACK.md — verified correct approach

async function computeInsertPosition(
  db: DrizzleDb,
  parentId: string | null,
  afterId: string | null,
  docId: string
): Promise<number> {
  // Get sorted siblings within a transaction (SELECT FOR UPDATE)
  const siblings = await db
    .select({ id: bullets.id, position: bullets.position })
    .from(bullets)
    .where(and(
      eq(bullets.documentId, docId),
      parentId ? eq(bullets.parentId, parentId) : isNull(bullets.parentId),
      isNull(bullets.deletedAt)
    ))
    .orderBy(asc(bullets.position))
    .for('update'); // SELECT FOR UPDATE prevents race condition

  if (!afterId) return siblings.length ? siblings[0].position / 2 : 1.0;

  const afterIdx = siblings.findIndex(s => s.id === afterId);
  const prev = siblings[afterIdx]?.position ?? 1.0;
  const next = siblings[afterIdx + 1]?.position;

  return next ? (prev + next) / 2 : prev + 1.0;
}
```

### Pattern 8: Markdown Render for Export

**What:** Convert bullets tree to indented dashes with 2-space per level.
**When to use:** Both DOC-06 and DOC-07 export functions.

```typescript
function renderDocumentAsMarkdown(doc: DocWithBullets): string {
  const lines: string[] = [`# ${doc.title}`, ''];

  function renderBullets(parentId: string | null, depth: number) {
    const children = doc.bullets
      .filter(b => b.parentId === parentId && !b.deletedAt)
      .sort((a, b) => a.position - b.position);

    for (const bullet of children) {
      const indent = '  '.repeat(depth); // 2-space indent per level (locked UX decision)
      lines.push(`${indent}- ${bullet.content}`);
      renderBullets(bullet.id, depth + 1);
    }
  }

  renderBullets(null, 0);
  return lines.join('\n');
}
```

### Anti-Patterns to Avoid

- **Storing access token in localStorage**: XSS-vulnerable; use React memory + httpOnly refresh cookie instead.
- **Storing Google OAuth `access_token` in database**: Only store `profile.id` (googleId) and email; never store the OAuth access token.
- **Computing position in application code before the UPDATE**: Race condition. Always compute midpoint inside a DB transaction with `SELECT FOR UPDATE` on sibling rows.
- **Using `drizzle-kit push` in production**: Push is dev-only (drops and recreates). Use `generate` + `migrate` in production/Docker.
- **Cascade hard-delete on bullets**: Breaks undo. Bullets use soft delete (`deleted_at`); document delete cascades through PostgreSQL FK but that is intentional (deleting a document is not undoable).
- **String fractional indexing for position**: The npm `fractional-indexing` library uses strings that break under PostgreSQL's glibc collation. Use FLOAT8 midpoint.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Password hashing | Custom hash function | bcryptjs, cost factor 12 | bcrypt is specifically designed for passwords (slow by design, salted, constant-time compare) |
| JWT sign/verify | Custom token format | jsonwebtoken | Handles signing, expiry, algorithm selection; time-tested |
| Google OAuth code exchange | Manual HTTP calls to Google | passport-google-oauth20 | Handles redirect, state param, CSRF, token exchange, profile fetch |
| ZIP archive generation | Manual zip byte construction | archiver | Streaming zip with proper headers; handles large exports without buffering |
| Request body validation | Manual type checks | zod | Type-safe, composable schemas; same schema shared between server and client |
| Drag-and-drop sortable | Custom mouse/touch event handling | @dnd-kit/sortable | Accessibility, touch, keyboard, pointer events, cross-browser — hundreds of edge cases |
| HTTP security headers | Manual `res.setHeader` per header | helmet | 15+ security headers including CSP, HSTS, X-Frame-Options; single middleware |

**Key insight:** Phase 1 uses all battle-tested patterns. The only custom code is business logic (position midpoint calculation, Markdown renderer, Inbox creation trigger) — not infrastructure.

---

## Common Pitfalls

### Pitfall 1: FLOAT8 Midpoint Race Condition

**What goes wrong:** Two rapid document reorders read sibling positions simultaneously, compute the same midpoint, and collide on the unique position constraint (or silently create duplicate positions).
**Why it happens:** Position computation is read-then-write. Without DB-level serialization, two requests can perform the same read concurrently.
**How to avoid:** Always compute position inside a transaction with `SELECT ... FOR UPDATE` on the sibling rows. Never compute positions in application code and pass the final float value in the API request body.
**Warning signs:** Unique constraint violations on `position` column; items occasionally appear in wrong order after rapid reordering.

### Pitfall 2: Soft Delete Filter Missing on Position Queries

**What goes wrong:** A soft-deleted bullet at position 2.0 is included in position calculations. The new bullet inserted "between" position 1.0 and 2.0 ends up adjacent to a logically deleted item, creating a gap in the visible list and confusing future midpoint calculations.
**Why it happens:** Developers forget to add `WHERE deleted_at IS NULL` to sibling queries after adding soft delete.
**How to avoid:** All queries that read sibling positions MUST include `AND deleted_at IS NULL`. Wrap this in a service-layer function so it can't be forgotten.
**Warning signs:** Visible gaps in document or bullet list after deletions; midpoint positions that seem too small/large.

### Pitfall 3: Google OAuth Token Leaked to Client

**What goes wrong:** The server passes Google's `accessToken` (from the strategy callback) to the client or stores it in the database. If the DB leaks, Google account access is compromised.
**Why it happens:** Developers see `accessToken` in the passport callback and mistakenly think it should be stored.
**How to avoid:** Only use `profile.id` (googleId) and `profile.emails[0].value` from the OAuth callback. Never store Google `accessToken`. Issue your own JWT pair immediately.
**Warning signs:** `google_access_token` column in schema; accessToken appearing in any DB table.

### Pitfall 4: httpOnly Cookie Not Sent in Dev (CORS)

**What goes wrong:** In development, Vite runs on port 5173 and Express on port 3000. The browser treats these as different origins. The `credentials: 'include'` flag must be set on every fetch call, and the Express CORS config must set `credentials: true` and an explicit `origin` (not `*`). If either is missing, the refresh cookie is silently not sent.
**Why it happens:** CORS for cookies has stricter requirements than CORS for regular requests. `Access-Control-Allow-Origin: *` with cookies does not work.
**How to avoid:**
```typescript
// Express CORS in dev
app.use(cors({
  origin: 'http://localhost:5173',
  credentials: true,  // required for cookies
}));

// Client fetch wrapper
fetch(url, { credentials: 'include', headers: { Authorization: `Bearer ${token}` } })
```
**Warning signs:** Refresh endpoint returns 401; `Set-Cookie` header present in response but cookie not stored by browser.

### Pitfall 5: Undo Tables Created Without schema_version

**What goes wrong:** The `undo_events` table is created without a `schema_version` column. When the undo payload format changes in Phase 3 (or later phases add new event types), old records in the DB cause 500 errors when the undo handler tries to deserialize them. Undo silently breaks for users with old history.
**Why it happens:** Schema versioning is easy to skip when the table is being created "just as a stub" and the feature doesn't ship until Phase 2/3.
**How to avoid:** The `schema_version INTEGER NOT NULL DEFAULT 1` column MUST be in the `undo_events` table from the initial migration. Handlers skip records with unknown schema versions.
**Warning signs:** No `schema_version` in the undo_events schema; undo throws 500 after a code deploy.

### Pitfall 6: Last Opened Document Not Tracked

**What goes wrong:** AUTH-05/DOC-05 require landing on the last opened document. If `last_opened_at` is not tracked on documents (or a separate `user_preferences` table), the app always falls back to Inbox, which feels broken for returning users.
**Why it happens:** Developers implement navigation without a persistence mechanism for "last opened."
**How to avoid:** Add `last_opened_at TIMESTAMPTZ` to the `documents` table. When the user navigates to a document (DOC-05), `PATCH /api/documents/:id/open` updates this field. On load, sort by `last_opened_at DESC NULLS LAST` and redirect to the first result.
**Warning signs:** App always shows Inbox even for returning users who previously had other docs open.

### Pitfall 7: Inbox Created Multiple Times

**What goes wrong:** If the Inbox creation trigger is not idempotent, a user who logs in twice during session issues (e.g., token refresh failure → re-login) gets two Inbox documents.
**Why it happens:** The trigger checks "is this user new?" but the definition of "new" is ambiguous.
**How to avoid:** Check for existing documents before creating Inbox: `IF NOT EXISTS (SELECT 1 FROM documents WHERE user_id = $userId) THEN INSERT ...`. The check and insert should be in the same transaction.
**Warning signs:** Users with multiple "Inbox" documents in the sidebar.

---

## Code Examples

### Auth Route: Register (AUTH-01)
```typescript
// Source: bcryptjs docs + zod + Express 5 pattern
const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

router.post('/register', async (req, res) => {
  const { email, password } = registerSchema.parse(req.body);
  const existing = await db.query.users.findFirst({ where: eq(users.email, email) });
  if (existing) {
    return res.status(409).json({ field: 'email', message: 'Email already registered' });
  }
  const passwordHash = await bcrypt.hash(password, 12);
  const [user] = await db.insert(users).values({ email, passwordHash }).returning();

  // AUTH-05: Create Inbox document for new user
  await documentService.createInbox(user.id);

  const accessToken = issueAccessToken(user.id);
  setRefreshCookie(res, user.id);
  res.status(201).json({ accessToken, user: { id: user.id, email: user.email } });
});
```

### Document List with Last-Opened Sort
```typescript
// GET /api/documents
router.get('/', requireAuth, async (req, res) => {
  const docs = await db
    .select()
    .from(documents)
    .where(eq(documents.userId, req.user!.id))
    .orderBy(asc(documents.position)); // sidebar order by position float
  res.json(docs);
});

// On document open — track last_opened_at
router.post('/:id/open', requireAuth, async (req, res) => {
  await db.update(documents)
    .set({ lastOpenedAt: new Date() })
    .where(and(eq(documents.id, req.params.id), eq(documents.userId, req.user!.id)));
  res.status(204).send();
});
```

### Drizzle Config
```typescript
// drizzle.config.ts (server root)
import 'dotenv/config';
import { defineConfig } from 'drizzle-kit';

export default defineConfig({
  out: './db/migrations',
  schema: './db/schema.ts',
  dialect: 'postgresql',
  dbCredentials: { url: process.env.DATABASE_URL! },
});
```

### Docker Compose Structure
```yaml
# docker-compose.yml
services:
  app:
    build: .
    ports:
      - "3000:3000"
    env_file: .env
    depends_on:
      db:
        condition: service_healthy
    volumes:
      - attachments:/data/attachments

  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 5s
      retries: 10

volumes:
  pgdata:
  attachments:
```

### .env.example (MUST be committed)
```
# Database
DATABASE_URL=postgresql://notes_user:changeme@db:5432/notes_db
DB_NAME=notes_db
DB_USER=notes_user
DB_PASSWORD=changeme

# JWT
JWT_SECRET=change-me-to-a-random-64-char-string
JWT_REFRESH_SECRET=change-me-to-a-different-random-64-char-string

# Google OAuth
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
GOOGLE_CALLBACK_URL=https://notes.gregorymaingret.fr/api/auth/google/callback

# App
PORT=3000
NODE_ENV=production
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Express 4 + express-async-errors | Express 5 native async error handling | April 2025 | Remove wrapper package; `next(err)` no longer needed in async routes |
| Create React App | Vite 6 + react-ts template | CRA deprecated 2023 | Dramatically faster HMR; standard tooling |
| Sessions (express-session + Redis) | JWT with httpOnly refresh cookie | 2022+ consensus | No session store needed; works with stateless Docker replicas |
| react-beautiful-dnd | @dnd-kit | 2022 (rbd deprecated) | Touch support, accessibility, active maintenance |
| Drizzle push (dev) → push (prod) | drizzle-kit generate + migrate | Drizzle stable 2024+ | SQL migration files are auditable and reversible |
| Client-side JSZip for export | Server-side archiver streaming | N/A — best practice | Avoids sending all data to client before zipping; no memory spike |

**Deprecated/outdated:**
- `express-async-errors` package: Express 5 handles async errors natively — do not install
- `react-beautiful-dnd`: Deprecated by Atlassian 2022 — use @dnd-kit
- `multer 2.x-beta`: Still in beta as of research; use 1.4.x stable (Phase 4+ concern, not Phase 1)

---

## Open Questions

1. **Multer version for attachments (Phase 4 concern, not Phase 1)**
   - What we know: Multer 1.4.x is stable; 2.0.x (May 2025) fixes two high-severity CVEs
   - What's unclear: Whether the CVEs affect the disk-storage use case in this app or only memory-storage
   - Recommendation: Do not install multer in Phase 1. Revisit in Phase 4 (Attachments) — at that point check CVE scope and pick appropriate version.

2. **Document position column for sidebar order**
   - What we know: Documents need sidebar reorder (DOC-04); FLOAT8 is the correct type (same pattern as bullets)
   - What's unclear: Whether position on `documents` table is sufficient, or if a separate `user_document_order` join table is cleaner
   - Recommendation: Single `position FLOAT8` column on `documents` table is sufficient for a flat list. No join table needed.

3. **React Router version**
   - What we know: React Router v6 has stable protected route patterns; v7 exists (2024+)
   - What's unclear: Whether to use react-router-dom v6 or v7
   - Recommendation: Use react-router-dom v6 (the 6.x package). v7 re-brands as "React Router" and adds framework-mode features not needed here. Both are maintained; v6 has more community examples for this use case.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | vitest (server) + vitest + @testing-library/react (client) |
| Config file | None yet — Wave 0 creates `server/vitest.config.ts` and `client/vite.config.ts` (Vite template includes vitest config) |
| Quick run command (server) | `cd server && npx vitest run --reporter=verbose` |
| Quick run command (client) | `cd client && npx vitest run` |
| Full suite command | `cd server && npx vitest run && cd ../client && npx vitest run` |
| E2E (auth smoke test) | Manual: Claude creates test account via `POST /api/auth/register` from server |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | Register with email/password; duplicate email returns 409 | unit | `npx vitest run server/tests/auth.test.ts` | ❌ Wave 0 |
| AUTH-02 | Login returns access token + sets refresh cookie; wrong password returns 401 | unit | `npx vitest run server/tests/auth.test.ts` | ❌ Wave 0 |
| AUTH-03 | Google OAuth callback finds-or-creates user; issues JWT pair | unit (mock passport) | `npx vitest run server/tests/auth.test.ts` | ❌ Wave 0 |
| AUTH-04 | Logout clears refresh cookie | unit | `npx vitest run server/tests/auth.test.ts` | ❌ Wave 0 |
| AUTH-05 | New user gets exactly one Inbox document; second login does not create second Inbox | unit | `npx vitest run server/tests/auth.test.ts` | ❌ Wave 0 |
| DOC-01 | POST /api/documents creates document with correct user_id and default position | unit | `npx vitest run server/tests/documents.test.ts` | ❌ Wave 0 |
| DOC-02 | PATCH /api/documents/:id updates title; rejects other user's document (403) | unit | `npx vitest run server/tests/documents.test.ts` | ❌ Wave 0 |
| DOC-03 | DELETE /api/documents/:id removes document + cascades bullets | unit | `npx vitest run server/tests/documents.test.ts` | ❌ Wave 0 |
| DOC-04 | Reorder: position midpoint computed correctly; SELECT FOR UPDATE prevents race | unit | `npx vitest run server/tests/documents.test.ts` | ❌ Wave 0 |
| DOC-05 | POST /api/documents/:id/open updates last_opened_at; GET / returns sorted by position | unit | `npx vitest run server/tests/documents.test.ts` | ❌ Wave 0 |
| DOC-06 | GET /api/documents/:id/export returns text/markdown with Content-Disposition attachment | unit | `npx vitest run server/tests/documents.test.ts` | ❌ Wave 0 |
| DOC-07 | GET /api/documents/export-all returns application/zip with one .md per document | unit | `npx vitest run server/tests/documents.test.ts` | ❌ Wave 0 |

Note: All server tests use vitest + supertest against an in-memory/test-DB Express instance. No live DB required for unit tests — use a test PostgreSQL instance or mock the Drizzle client.

### Sampling Rate
- **Per task commit:** `cd server && npx vitest run --reporter=verbose` (server tests only, < 30s)
- **Per wave merge:** Full suite (server + client): `cd server && npx vitest run && cd ../client && npx vitest run`
- **Phase gate:** Full suite green + Claude manually verifies auth flows on `https://notes.gregorymaingret.fr` before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `server/tests/auth.test.ts` — covers AUTH-01 through AUTH-05
- [ ] `server/tests/documents.test.ts` — covers DOC-01 through DOC-07
- [ ] `server/vitest.config.ts` — vitest configuration for server
- [ ] `server/tests/helpers/testApp.ts` — Express app factory for test isolation (separate from production app.ts)
- [ ] Framework install: `cd server && npm install -D vitest supertest @types/supertest`

---

## Sources

### Primary (HIGH confidence)
- [Drizzle ORM — PostgreSQL Get Started](https://orm.drizzle.team/docs/get-started/postgresql-new) — schema definition patterns, drizzle-kit generate/migrate flow
- [Drizzle ORM — Migrations Overview](https://orm.drizzle.team/docs/migrations) — generate vs push distinction
- [passport-google-oauth20 official docs](https://www.passportjs.org/packages/passport-google-oauth20/) — strategy callback, profile shape
- [TanStack Query v5 — Optimistic Updates](https://tanstack.com/query/v5/docs/react/guides/optimistic-updates) — mutation/rollback pattern
- [React Router — Protected Routes](https://ui.dev/react-router-protected-routes-authentication) — RequireAuth component pattern
- [PITFALLS.md] — fractional indexing collation trap, soft delete subtree, undo schema version (pre-researched)
- [STACK.md] — full stack recommendations verified against official sources (pre-researched)
- [ARCHITECTURE.md] — API shape, component hierarchy, data flow (pre-researched)

### Secondary (MEDIUM confidence)
- [cybersierra.co — JWT Storage Guide 2025](https://cybersierra.co/blog/react-jwt-storage-guide/) — httpOnly cookie recommendation
- [stackinsight.dev — JWT Storage Comparison](https://stackinsight.dev/blog/jwt-storage-cookies-vs-localstorage-which-is-right-for-your-app/) — hybrid approach (memory + httpOnly) confirmed as 2025 consensus
- [DEV Community — passport-google-oauth20 + JWT 2025](https://dev.to/fatihguzel/integrating-google-oauth-20-with-jwt-in-a-nodejs-typescript-app-using-passportjs-3ij) — server-side OAuth callback → JWT flow
- [archiver npm](https://www.npmjs.com/package/archiver) — streaming zip for Node.js
- [@dnd-kit/sortable docs](https://docs.dndkit.com/concepts/sortable) — SortableContext + useSortable pattern for document list

### Tertiary (LOW confidence — flag for validation)
- React Router v6 vs v7 recommendation: based on ecosystem observation, not official comparative docs. Verify before implementing routing layer.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — core libraries verified via official docs and npm; version numbers cross-checked
- Architecture patterns: HIGH — Drizzle schema, JWT flow, OAuth flow all verified with official sources
- Pitfalls: HIGH (DB/schema) / MEDIUM (CORS in dev) — DB pitfalls from documented production incidents; CORS behavior from official MDN + Express docs
- Export approach: MEDIUM — archiver is well-documented for Node streaming zip; single-file export is trivial Express pattern

**Research date:** 2026-03-09
**Valid until:** 2026-04-09 (stable ecosystem; Drizzle 1.0 release is the main thing to watch)
