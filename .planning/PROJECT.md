# Notes

## What This Is

Notes is a self-hosted, multi-user web application that replicates the core Dynalist/Workflowy infinite outlining experience — without paywalls. Users manage personal knowledge via unlimited nested bullet points with markdown, tags, attachments, comments, search, and bookmarks. Data is completely private per user with no sharing or collaboration. The app is fully usable on both desktop and mobile browsers.

## Core Value

Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## Requirements

### Validated

- ✓ User authentication via email/password and Google SSO — v1.0
- ✓ User can create, rename, reorder, and delete documents (flat list, no folders) — v1.0
- ✓ User can create/edit/delete bullets with unlimited nesting — v1.0
- ✓ User can indent, outdent, and reorder bullets via drag (desktop + mobile) — v1.0
- ✓ User can collapse/expand bullets with children (persisted per user) — v1.0
- ✓ User can zoom into any bullet as a full-screen root with breadcrumb navigation — v1.0
- ✓ Bullet text supports markdown (bold, italic, strikethrough, links, inline images) — v1.0
- ✓ User can mark bullets complete (strikethrough + 50% opacity) and bulk delete completed — v1.0
- ✓ User can add comments to bullets (flat list, plain text) — v1.0
- ✓ User can attach files to bullets (any type, 100MB max, stored on Docker volume) — v1.0
- ✓ User can search across all documents (full-text + #tags + @mentions + dates) — v1.0
- ✓ User can bookmark bullets and view all bookmarks in a dedicated screen — v1.0
- ✓ Special syntax: #tags, @mentions, !!dates render as clickable chips with a tag browser — v1.0
- ✓ Server-side undo/redo: 50 levels, global per user, persists across page refresh — v1.0
- ✓ Desktop keyboard shortcuts (Enter, Tab, Shift+Tab, Ctrl+arrows, Ctrl+Z/Y, Ctrl+F, etc.) — v1.0
- ✓ Mobile touch interactions (swipe right = complete, swipe left = delete, long press = context menu) — v1.0
- ✓ Export document(s) as Markdown file — v1.0
- ✓ New users start with one blank "Inbox" document — v1.0

### Active

- [ ] Progressive Web App (PWA) manifest for home screen installation
- [ ] Quick-open palette (fuzzy document + bullet search)
- [ ] Ctrl/Cmd+E to toggle sidebar visibility on desktop

### Out of Scope

- Native mobile apps — web-first, no native apps
- Offline mode — requires service worker complexity, defer
- Shared documents / collaboration — deliberately no sharing, privacy-first design
- Folders — flat document list by design (Dynalist/Workflowy pattern)
- Agenda / calendar view — different product
- Import from Dynalist/Workflowy — defer
- Threaded comments — flat comments only
- Version history beyond 50-step undo — explicit limit
- Admin panel — all users equal, no roles
- Push notifications — not needed for solo personal tool
- Custom domains — self-hosted, user manages their own domain
- AI features — out of scope for focused outliner clone
- Real-time sync / collaboration — privacy-first means no sync

## Context

- **Shipped:** v1.0 with 42,417+ lines of code across 204 files (TypeScript/React frontend, Node/Express backend, PostgreSQL)
- **Live at:** https://notes.gregorymaingret.fr
- **Tech stack:** React + Vite + TypeScript (client), Express + Drizzle ORM + PostgreSQL (server), Docker (deployment), Nginx reverse proxy
- **Inspiration**: Dynalist and Workflowy — infinite outlining tools. Goal is feature parity for core use cases without paywalls.
- **Deployment**: Docker-based on a self-hosted Linux server (`192.168.1.50`). Nginx reverse proxy at `192.168.1.204`.
- **Testing**: Claude creates its own email/password account on the running server for E2E testing. Google SSO cannot be tested by Claude.
- **CI/CD**: Develop locally → push to GitHub (`gmaingret/notes`) → deploy to server → confirm live → merge to main.

## Constraints

- **Platform**: Web application only — must be fully usable on desktop browsers and mobile browsers (no native apps)
- **Self-hosted**: Runs in Docker on user's own server; no cloud dependencies beyond optional Google OAuth
- **Privacy**: Complete per-user data isolation; no sharing features by design
- **File storage**: Docker volume mount at `/data/attachments`; 100MB max per file
- **Auth**: Google OAuth credentials via `.env` file; a `.env.example` must be committed (never the actual `.env`)
- **No admin roles**: All users equal; open registration

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Self-hosted Docker deployment | Privacy-first, no vendor lock-in | ✓ Good — clean deployment, zero cloud cost |
| Email/password + Google SSO | Standard auth with SSO convenience | ✓ Good — both flows working in production |
| Server-side persisted undo/redo | Survives page refresh — key differentiator | ✓ Good — 50 levels, global per user, refresh-safe |
| Flat document list (no folders) | Matches Dynalist/Workflowy UX pattern | ✓ Good — simple, no overhead |
| Web-only (no native apps) | Maximize coverage with minimum effort via responsive design | ✓ Good — mobile gestures work well |
| Soft delete for bullets | Enables undo of deletions | ✓ Good — undo restores full subtrees |
| Plain contenteditable per bullet (not ProseMirror) | Tree model conflicts with ProseMirror document model | ✓ Good — simpler, fits tree structure |
| FLOAT8 midpoint positioning | Avoids string fractional key complexity | ✓ Good — locked in schema, no migrations needed |
| Single flat SortableContext over whole tree | Nested SortableContexts block cross-level drag | ✓ Good — drag-and-drop works across levels |
| AccessToken in React context only (not localStorage) | Prevents XSS token theft | ✓ Good — security best practice maintained |
| drizzle-orm 0.40.0 + drizzle-kit 0.29.x | 0.45.x has broken npm package (missing index.cjs) | ✓ Good — stable, pinned |
| Drizzle _journal.json must list ALL migrations | migrate() uses journal for discovery; missing entries = silently skipped SQL | ✓ Good — learned in Phase 4, documented |
| FocusToolbar in BulletTree (not DocumentView) | DocumentToolbar lives inside DndContext in BulletTree | ✓ Good — avoids pointer event capture issues |
| gestures.ts uses closure-based state (not useRef) | Pure functions can be unit tested without React | ✓ Good — enables isolated gesture tests |

---
*Last updated: 2026-03-10 after v1.0 milestone*
