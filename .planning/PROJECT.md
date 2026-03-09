# Notes

## What This Is

Notes is a self-hosted, multi-user web application that replicates the core Dynalist/Workflowy infinite outlining experience — without paywalls. Users manage personal knowledge via unlimited nested bullet points with markdown, tags, attachments, comments, search, and bookmarks. Data is completely private per user with no sharing or collaboration. The app is fully usable on both desktop and mobile browsers.

## Core Value

Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] User authentication via email/password and Google SSO
- [ ] User can create, rename, reorder, and delete documents (flat list, no folders)
- [ ] User can create/edit/delete bullets with unlimited nesting
- [ ] User can indent, outdent, and reorder bullets via drag (desktop + mobile)
- [ ] User can collapse/expand bullets with children (persisted per user)
- [ ] User can zoom into any bullet as a full-screen root with breadcrumb navigation
- [ ] Bullet text supports markdown (bold, italic, strikethrough, links, inline images)
- [ ] User can mark bullets complete (strikethrough + 50% opacity) and bulk delete completed
- [ ] User can add comments to bullets (flat list, plain text)
- [ ] User can attach files to bullets (any type, 100MB max, stored on Docker volume)
- [ ] User can search across all documents (full-text + #tags + @mentions + dates)
- [ ] User can bookmark bullets and view all bookmarks in a dedicated screen
- [ ] Special syntax: #tags, @mentions, !!dates render as clickable chips with a tag browser
- [ ] Server-side undo/redo: 50 levels, global per user, persists across page refresh
- [ ] Desktop keyboard shortcuts (Enter, Tab, Shift+Tab, Ctrl+arrows, Ctrl+Z/Y, Ctrl+P, etc.)
- [ ] Mobile touch interactions (swipe right = complete, swipe left = delete, long press = context menu)
- [ ] Export document(s) as Markdown file
- [ ] New users start with one blank "Inbox" document

### Out of Scope

- Native mobile apps — web-first, no native apps in v1
- Offline mode — requires service worker complexity, defer
- Shared documents / collaboration — deliberately no sharing, privacy-first design
- Folders — flat document list by design (Dynalist/Workflowy pattern)
- Agenda / calendar view — out of scope for v1
- Import from Dynalist/Workflowy — defer
- Threaded comments — flat comments only in v1
- Version history beyond 50-step undo — explicit limit
- Admin panel — all users equal, no roles
- Push notifications — not needed for solo personal tool
- Custom domains — self-hosted, user manages their own domain

## Context

- **Inspiration**: Dynalist and Workflowy — infinite outlining tools. Goal is feature parity for core use cases without paywalls.
- **Deployment**: Docker-based on a self-hosted Linux server (`192.168.1.50`). Nginx reverse proxy at `192.168.1.204` serving `https://notes.gregorymaingret.fr`.
- **Tech stack hint**: Node.js/Express backend (port 3000), PostgreSQL database, file storage on Docker volume at `/data/attachments`. Google OAuth 2.0 + JWT for auth.
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
| Self-hosted Docker deployment | Privacy-first, no vendor lock-in | — Pending |
| Email/password + Google SSO | Standard auth with SSO convenience | — Pending |
| Server-side persisted undo/redo | Survives page refresh — key differentiator | — Pending |
| Flat document list (no folders) | Matches Dynalist/Workflowy UX pattern | — Pending |
| Web-only (no native apps) | Maximize coverage with minimum effort via responsive design | — Pending |
| Soft delete for bullets | Enables undo of deletions | — Pending |

---
*Last updated: 2026-03-09 after initialization*
