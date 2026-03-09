# Stack Research

**Domain:** Self-hosted multi-user outliner / PKM web app (Dynalist/Workflowy clone)
**Researched:** 2026-03-09
**Confidence:** MEDIUM-HIGH (core stack verified via official docs and npm; tree storage and editor patterns verified via multiple sources; some version numbers cross-checked against npm)

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Node.js | 22 LTS | Backend runtime | LTS through 2027; Express 5 requires ≥ v18; use 22 for longevity |
| Express.js | 5.2.x | HTTP framework | v5 is now stable (April 2025); async error handling built in; no need for express-async-errors wrapper |
| PostgreSQL | 16 or 17 | Primary database | Adjacency list + WITH RECURSIVE is first-class in PG; ltree extension available for path queries |
| Drizzle ORM | 0.45.x | DB access layer | Code-first TypeScript schema; zero-dependency, no code gen step; SQL-close API makes recursive CTEs readable; better fit than Prisma for a self-hosted Express app without serverless complexity |
| React | 19.x | Frontend UI | Largest ecosystem; TipTap 3.x has a dedicated @tiptap/react adapter; TanStack Query 5.x integrates cleanly; React 19 concurrent features help with complex tree renders |
| Vite | 6.x | Frontend build | De facto standard since CRA deprecation; instant HMR; `npm create vite@latest -- --template react-ts` scaffolds correctly |
| TypeScript | 5.x | Type safety across stack | Apply to both server (compiled with tsc/tsx) and client; catches tree mutation bugs at compile time |

### Auth Libraries

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| passport | 0.7.x | Auth middleware orchestrator | De facto Node.js auth; pluggable strategies |
| passport-local | 1.0.x | Email/password strategy | Standard, stable, battle-tested |
| passport-google-oauth20 | 2.0.x | Google OAuth 2.0 strategy | Official Jared Hanson package; well-maintained |
| passport-jwt | 4.0.x | JWT verification strategy | Verifies Bearer tokens on protected routes |
| jsonwebtoken | 9.0.x | JWT sign/verify | 18M+ weekly downloads; stable; use RS256 or HS256 with a strong secret |
| bcryptjs | 5.x | Password hashing | Pure JS bcrypt (no native bindings = no compile step in Docker); use cost factor 12 |

**Auth pattern:** Issue a short-lived JWT (15 min) + a long-lived refresh token (7 days) stored as `httpOnly` cookie. Google OAuth callback issues the same JWT pair. `passport.authenticate('jwt', { session: false })` protects API routes — never use sessions.

### Editor (Rich Text per Bullet)

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @tiptap/core | 3.20.x | Headless editor engine | Built on ProseMirror; custom node schema defines a bullet node; tree-shakable packages |
| @tiptap/react | 3.20.x | React adapter | First-party; `<EditorContent>` and `useEditor` hook |
| @tiptap/extension-bold | 3.20.x | Bold mark | Table-stakes markdown formatting |
| @tiptap/extension-italic | 3.20.x | Italic mark | Table-stakes markdown formatting |
| @tiptap/extension-strike | 3.20.x | Strikethrough mark | Used for "completed" bullet visual |
| @tiptap/extension-link | 3.20.x | Link mark | Clickable links |
| @tiptap/extension-placeholder | 3.20.x | Placeholder text | UX polish for empty bullets |

**Why TipTap over alternatives:**
- ProseMirror directly: too low-level; no React integration, enormous API surface
- Slate.js: React-only and has had API instability; no ProseMirror backing
- Quill: legacy; not tree-shakable; schema customization harder
- Lexical (Meta): viable but younger ecosystem; less community extension coverage

**Key design decision:** Each bullet in the outliner is its own `<EditorContent>` instance — NOT a single TipTap document tree. The document tree lives in PostgreSQL (adjacency list); TipTap manages only the per-bullet inline content. This avoids trying to shoehorn an infinitely nested tree into ProseMirror's own node hierarchy.

### Data Fetching & State

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @tanstack/react-query | 5.x | Server state, mutations, caching | Built-in optimistic updates via `onMutate`/`onError` rollback; `useMutation` for bullet CRUD; better mutation story than SWR |
| Zustand | 5.x | Local UI state | Lightweight; manages expand/collapse state, focused bullet, undo stack UI state; no boilerplate vs Redux |

### File Uploads

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| multer | 1.4.x | Multipart form-data parsing | Built on busboy; Express middleware; simple disk-storage adapter writes directly to Docker volume at `/data/attachments`; 100MB limit configurable |

**Why multer over busboy directly:** Multer gives you `req.file` with path, size, and mimetype already parsed. For a single-server Docker deployment writing to a local volume, disk storage is appropriate — no S3 or streaming complexity needed.

### Drag-and-Drop (Reordering)

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| @dnd-kit/core | 6.x | Drag-and-drop primitives | Accessible, pointer/touch/keyboard; better mobile support than react-beautiful-dnd (deprecated); tree-specific helpers in @dnd-kit/sortable |
| @dnd-kit/sortable | 8.x | Sortable lists | `SortableContext` for bullet reordering within a parent |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| zod | 3.x | Runtime validation | Validate all request bodies on the server; share schemas with the client for form validation |
| helmet | 8.x | Express security headers | Apply as first middleware; sets CSP, X-Frame-Options, etc. |
| cors | 2.x | CORS middleware | Dev only (Vite dev server on :5173 vs Express on :3000); in production, same origin via nginx |
| morgan | 1.x | HTTP request logging | Development; swap for structured logger (pino) if log aggregation needed |
| dotenv | 16.x | .env loading | Load secrets in development; Docker passes env vars in production |
| pg | 8.x | PostgreSQL driver | Drizzle uses this under the hood; install explicitly for direct query access |
| uuid | 10.x | UUID generation | Generate UUIDs for bullet IDs in application layer rather than relying on DB default |
| express-rate-limit | 7.x | Rate limiting | Apply to `/auth/*` routes to prevent brute-force |
| multer | 1.4.x | File upload middleware | See above |
| node-cron | 3.x | Scheduled tasks | Periodic cleanup of orphaned attachments (soft-deleted bullets) |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| tsx | Run TypeScript directly in Node | Use `tsx watch server/index.ts` for dev; no compile step |
| vitest | Backend unit tests | Same config as Vite; jest-compatible API |
| Playwright or Supertest | API integration tests | Supertest for route-level; Playwright for E2E on the live server |
| ESLint + Prettier | Code quality | Use `@typescript-eslint` rules; Prettier for formatting |
| drizzle-kit | Migration generation | `drizzle-kit generate` diffs schema → SQL; `drizzle-kit migrate` applies |

---

## Tree Data Structure: Adjacency List (Recommended)

This is the most important architecture decision after the frontend framework.

**Recommended approach: Adjacency list + fractional index for ordering**

```sql
CREATE TABLE bullets (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  parent_id   UUID REFERENCES bullets(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id),
  content     TEXT NOT NULL DEFAULT '',
  position    DOUBLE PRECISION NOT NULL,   -- fractional index for ordering
  is_collapsed BOOLEAN NOT NULL DEFAULT false,
  is_completed BOOLEAN NOT NULL DEFAULT false,
  deleted_at  TIMESTAMPTZ,                 -- soft delete for undo support
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX bullets_parent_id_idx ON bullets(parent_id);
CREATE INDEX bullets_document_id_idx ON bullets(document_id);
```

**Why adjacency list:**
- Simple inserts: `INSERT INTO bullets (parent_id, ...)` — no table scans
- Simple moves: `UPDATE bullets SET parent_id = $new_parent WHERE id = $id`
- Simple subtree fetch: `WITH RECURSIVE` CTE — supported since PostgreSQL 8.2
- Sufficient for an outliner with thousands of bullets per user; performance only becomes an issue at 100K+ nodes per tree

**Why fractional indexing for position:**
- `DOUBLE PRECISION` position between siblings avoids renumbering rows on every reorder
- Insert between position 1.0 and 2.0 → assign 1.5; keeps positions stable for concurrent edits
- When precision exhausts (after thousands of reorders), a periodic "rebalance" job renumbers 0.0, 1.0, 2.0, ...

**Why NOT closure table:** Space grows O(depth²); modification queries require deleting and reinserting many rows; overkill for a private single-user outliner where subtree queries are infrequent.

**Why NOT ltree:** GIST index has size limits that break with deeply nested paths; paths become stale on moves (require full subtree path update); adds complexity for minimal benefit at this scale.

**Why NOT nested sets:** Inserts and moves require updating `lft`/`rgt` for every sibling → writes lock large table sections; terrible for write-heavy outlining.

---

## Installation

```bash
# Backend (in /server)
npm install express@^5 passport passport-local passport-google-oauth20 passport-jwt \
  jsonwebtoken bcryptjs pg drizzle-orm zod helmet cors morgan \
  express-rate-limit multer dotenv uuid node-cron

npm install -D typescript tsx vitest drizzle-kit @types/express @types/node \
  @types/passport @types/passport-local @types/passport-google-oauth20 \
  @types/passport-jwt @types/jsonwebtoken @types/bcryptjs @types/multer \
  @types/cors @types/morgan @types/uuid eslint prettier @typescript-eslint/eslint-plugin

# Frontend (in /client, scaffolded via Vite)
npm create vite@latest client -- --template react-ts

npm install @tiptap/core@^3 @tiptap/react@^3 @tiptap/extension-bold \
  @tiptap/extension-italic @tiptap/extension-strike @tiptap/extension-link \
  @tiptap/extension-placeholder @tanstack/react-query zustand \
  @dnd-kit/core @dnd-kit/sortable zod

npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

---

## Alternatives Considered

| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| React 19 | Vue 3 | TipTap @tiptap/react adapter is purpose-built; React ecosystem has more outliner/editor precedent; Vue is fine but no strong reason to diverge from the constraint hint |
| React 19 | Svelte 5 | Svelte is faster to start but TipTap's React adapter is better maintained; smaller extension ecosystem; switching cost if TipTap patterns change |
| Drizzle ORM | Prisma | Prisma's code generation step complicates Docker builds; larger bundle; schema file duplication; Drizzle's TypeScript-native approach is simpler for this project |
| Drizzle ORM | Knex | Knex is a query builder, not an ORM; verbose for schema definition; no built-in migration diffing |
| TipTap 3.x | ProseMirror direct | ProseMirror has no React integration; ~10x more boilerplate; TipTap is the right abstraction |
| TipTap 3.x | Lexical | Lexical is production-ready at Meta but has less community extension coverage; TipTap 3 is more mature for custom schemas |
| @dnd-kit | react-beautiful-dnd | react-beautiful-dnd is deprecated by Atlassian as of 2022 and not maintained |
| @dnd-kit | HTML5 native drag | No touch support; no keyboard accessibility |
| Adjacency list | Closure table | 5-10x more rows; complex inserts/moves; unnecessary at single-user scale |
| Fractional index | Integer position | Requires renumbering siblings on every reorder; creates unnecessary DB writes under frequent drag-and-drop |
| multer disk storage | S3/object storage | Self-hosted Docker volume is the explicit constraint; multer disk storage is simpler and sufficient |
| JWT (stateless) | Session cookies | Stateless JWT works better when the React SPA lives on a CDN or different port in dev; avoids session store setup |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| react-beautiful-dnd | Deprecated by Atlassian in 2022; unmaintained | @dnd-kit/core + @dnd-kit/sortable |
| Sequelize | Heavy, verbose, poor TypeScript support; slow migration tooling | Drizzle ORM |
| TypeORM | Decorator-based; complex configuration; known migration reliability issues | Drizzle ORM |
| Create React App | Deprecated; no longer maintained by Facebook/Meta | Vite + react-ts template |
| Quill | Legacy architecture; not tree-shakable; poor schema customization | TipTap 3.x |
| SWR | Mutation story is weaker than TanStack Query; optimistic updates require more manual wiring | @tanstack/react-query v5 |
| Redux / Redux Toolkit | Overkill for UI state in a solo-user app; significant boilerplate | Zustand |
| express-session | Stateful sessions require a session store (Redis/DB); JWT is simpler for this API-first app | passport-jwt + jsonwebtoken |
| ltree | GIST index size limits; path-stale on moves; complexity without benefit at this scale | Adjacency list + WITH RECURSIVE |
| Nested sets | Inserts/moves lock large table sections; write-heavy outlining makes this untenable | Adjacency list |
| Multer (v2.x beta) | At time of research, v2 is in beta with breaking API changes | multer@^1.4 (stable) |

---

## Stack Patterns by Variant

**If real-time collaboration is ever added (it is out of scope for v1):**
- Add Yjs (CRDT) + y-prosemirror for TipTap integration
- Add a WebSocket server (ws or Socket.IO) for document sync
- This is why TipTap/ProseMirror is a good foundation — Yjs integration is battle-tested

**If the user list grows beyond a few accounts (currently open registration):**
- Add Redis for refresh token revocation list and rate-limit state
- Currently, JWT expiry is the only revocation mechanism (acceptable for a personal server)

**If file storage needs to move off the Docker volume:**
- Swap multer `diskStorage` for multer-s3 or a custom multer storage engine
- API surface stays identical; only the storage engine changes

---

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| Express 5.2.x | Node.js ≥ 18 (use 22 LTS) | Express 5 drops Node 16 support |
| @tiptap/core 3.x | React 18 or 19 | TipTap 3 requires React 18+ |
| @tanstack/react-query 5.x | React 18 or 19 | v5 uses React 18 concurrent features |
| Drizzle ORM 0.45.x | pg 8.x | Use pg@^8; Drizzle 1.0-beta changes migration format — stay on 0.x stable until 1.0 releases |
| passport-google-oauth20 2.0.x | passport 0.7.x | passport 0.7 changed `req.user` type; ensure compatible |
| drizzle-kit | drizzle-orm | Must be same minor version; install both from same release |
| multer 1.4.x | Express 4 or 5 | Works with both; v1.4 uses busboy internally |

---

## Sources

- [TipTap 3.0 stable announcement](https://tiptap.dev/blog/release-notes/tiptap-3-0-is-stable) — confirmed 3.20.x current stable (HIGH confidence)
- [Express 5 stable announcement](https://expressjs.com/2024/10/15/v5-release.html) — confirmed v5.2.x current (HIGH confidence)
- [Drizzle ORM npm](https://www.npmjs.com/package/drizzle-orm) — confirmed 0.45.x current; 1.0-beta noted (MEDIUM confidence — beta status evolving)
- [React versions page](https://react.dev/versions) — confirmed 19.x (React 19.2 released Oct 2025) (HIGH confidence)
- [Ackee blog: Hierarchical models in PostgreSQL](https://www.ackee.agency/blog/hierarchical-models-in-postgresql) — adjacency list vs closure table vs ltree analysis (MEDIUM confidence — aligns with PostgreSQL docs)
- [PostgreSQL ltree docs](https://www.postgresql.org/docs/18/ltree.html) — official ltree documentation confirming GIST index size limits (HIGH confidence)
- [Steve Ruiz: Reordering Part 2 — Fractional Indexing](https://www.steveruiz.me/posts/reordering-fractional-indices) — fractional indexing for reorder (MEDIUM confidence)
- [Better Stack: Drizzle vs Prisma](https://betterstack.com/community/guides/scaling-nodejs/drizzle-vs-prisma/) — comparison confirming Drizzle recommendation for self-hosted Express (MEDIUM confidence)
- [TanStack Query v5 optimistic updates docs](https://tanstack.com/query/v5/docs/react/guides/optimistic-updates) — official docs for mutation/optimistic pattern (HIGH confidence)
- [Liveblocks: Rich text editor framework 2025](https://liveblocks.io/blog/which-rich-text-editor-framework-should-you-choose-in-2025) — TipTap vs ProseMirror vs Slate comparison (MEDIUM confidence)
- WebSearch: passport/jsonwebtoken/bcryptjs npm download counts and versions — confirmed versions via npmtrends (MEDIUM confidence)
- WebSearch: Vite current version and react-ts template — confirmed as standard tooling (HIGH confidence)

---

*Stack research for: self-hosted multi-user outliner (Dynalist/Workflowy clone)*
*Researched: 2026-03-09*
