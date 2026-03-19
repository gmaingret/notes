# Notes

## What This Is

Notes is a self-hosted, multi-user web application that replicates the core Dynalist/Workflowy infinite outlining experience — without paywalls. Users manage personal knowledge via unlimited nested bullet points with markdown, tags, attachments, comments, search, and bookmarks. Data is completely private per user with no sharing or collaboration. The app is fully usable on both desktop and mobile browsers.

## Core Value

Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## Requirements

### Validated

- ✓ Native Android client with full feature parity (auth, documents, bullets, search, bookmarks, attachments) — v2.0
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
- ✓ Responsive sidebar hidden on mobile, full-width content; hamburger opens/closes with off-canvas animation — v1.1
- ✓ System-preference dark mode with WCAG AA colors and no FOUC on hard refresh — v1.1
- ✓ Lucide SVG icons throughout, Inter Variable + JetBrains Mono Variable self-hosted fonts — v1.1
- ✓ PWA manifest for home screen installation (standalone mode, 192/512px icons) — v1.1
- ✓ Swipe animations: proportional icon scale + exit-direction slide-off before mutation — v1.1
- ✓ Quick-open palette (Ctrl+F) with recent docs, grouped search, keyboard navigation, mobile search button — v1.1
- ✓ Sidebar persistent footer with Export all / Logout; + button auto-opens inline rename — v1.1
- ✓ Ctrl/Cmd+E sidebar toggle on desktop — v1.1
- ✓ Android home screen widget with document picker, root-level bullet display, Material 3 theming — v2.1
- ✓ Widget background sync via WorkManager (15-min interval), in-app mutation triggers, independent auth — v2.1
- ✓ Add and delete bullets directly from widget with optimistic updates and rollback — v2.1
- ✓ Fix ILIKE wildcard injection in search and tag queries — v2.2
- ✓ Fix JWT token exposure via URL query string (use hash fragment) — v2.2
- ✓ Restrict file upload types and validate extensions — v2.2
- ✓ Sanitize filenames in Content-Disposition headers — v2.2
- ✓ Serve SVG as attachment (not inline) to prevent stored XSS — v2.2
- ✓ Add bullet ownership check on attachment upload — v2.2
- ✓ Add rate limiting on data endpoints — v2.2
- ✓ Strengthen password policy beyond minimum length — v2.2
- ✓ Implement server-side refresh token revocation — v2.2
- ✓ Add CSRF protection beyond SameSite cookie (resolved-by-design: Bearer auth) — v2.2

### Active

- [ ] Add CI/CD workflows for server and client (lint, test, build validation on PRs)
- [ ] Add 401 interceptor with automatic token refresh in web client
- [ ] Standardize API error response format across all endpoints
- [ ] Add undo/redo error handling with user-friendly responses
- [ ] Add React error boundary and toast notification system for mutation failures
- [ ] Extend undo coverage to mark-complete, note edits, and bulk delete
- [ ] Wire up UPLOAD_MAX_SIZE_MB and UPLOAD_PATH env vars (currently hardcoded)
- [ ] Refactor BulletContent and BulletNode into smaller, testable components

### Deferred

- Manual dark mode toggle (requires three-state settings UI — defer to v1.2)
- PWA richer install prompt on Android (requires service worker — defer)
- Quick-open action commands beyond navigation (full VS Code-style palette — defer)

### Out of Scope

- ~~Native mobile apps~~ — delivered in v2.0: Android client shipped
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

## Current Milestone: v2.3 Robustness & Quality

**Goal:** Improve reliability, error handling, developer experience, and code quality across the full stack.

**Target improvements:**
- CI/CD pipelines for server and client (HIGH)
- Web client 401 interceptor with automatic token refresh (HIGH)
- React error boundary and toast notifications for mutation failures (HIGH)
- Standardized API error response format (MEDIUM)
- Undo route error handling with user-friendly responses (MEDIUM)
- Extended undo coverage for mark-complete, note edits, bulk delete (MEDIUM)
- Environment variable wiring for upload config (LOW)
- BulletContent/BulletNode refactor with test coverage (MEDIUM)

## Current State

**Shipped:** v2.2 Security Hardening (2026-03-15) — 3 phases, 5 plans, all HIGH/MEDIUM vulnerabilities fixed
**Live at:** https://notes.gregorymaingret.fr (web) + Android debug APK on device
**All milestones:** v1.0 MVP, v1.1 Mobile & UI Polish, v2.0 Native Android, v2.1 Widget, v2.2 Security — 18 phases, 85 plans total

## Context

- **Shipped:** v2.2 (2026-03-15) — five milestones complete; ~65k+ LOC (44k web + 15k Android + 4k widget)
- **Live at:** https://notes.gregorymaingret.fr
- **Tech stack:** React + Vite + TypeScript (client), Express + Drizzle ORM + PostgreSQL (server), Docker (deployment), Nginx reverse proxy
- **UI libraries:** lucide-react (icons), @fontsource-variable/inter + jetbrains-mono (fonts), @dnd-kit (drag-and-drop), zustand (state)
- **Inspiration**: Dynalist and Workflowy — infinite outlining tools. Goal is feature parity for core use cases without paywalls.
- **Deployment**: Docker-based on a self-hosted Linux server (`192.168.1.50`). Nginx reverse proxy at `192.168.1.204`.
- **Testing**: Claude creates its own email/password account on the running server for E2E testing. Google SSO cannot be tested by Claude.
- **CI/CD**: Develop locally → push to GitHub (`gmaingret/notes`) → deploy to server → confirm live → merge to main.
- **Known tech debt**: Server disk (30GB) reached 100% during Phase 8 deploy — Docker build cache grows with each image build. Consider periodic `docker builder prune` or larger disk.

## Constraints

- **Platform**: Web application (desktop + mobile browsers) and native Android (Kotlin/Jetpack Compose)
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
| CSS custom property token system (not CSS-in-JS) | Native CSS variables are zero-cost at runtime and cascade correctly | ✓ Good — clean dark mode with single @media override |
| Synchronous FOUC script in index.html (not React) | FOUC must be prevented before first paint — React hydration is too late | ✓ Good — zero white flash on hard refresh in dark mode |
| exitDirection + onTransitionEnd pattern for swipe exit | Fires mutation after CSS transition completes — no setTimeout guessing | ✓ Good — exit animation feels intentional and timing-safe |
| Ctrl+F replaces browser Find bar (capture: true) | Ctrl+F is the universal search shortcut; users already know it | ✓ Good — intuitive; intercepts browser Find cleanly |
| Inter Variable + JetBrains Mono via @fontsource (no Google Fonts) | Self-hosted fonts maintain privacy-first approach | ✓ Good — no external dependencies, fast load from same server |
| Jetpack Glance 1.1.1 for widget (not RemoteViews) | Compose-like API, Material 3 theme support, stable release | ✓ Good — clean declarative UI, color scheme switching works |
| WidgetStateStore as custom DataStore singleton (not Glance state) | Accessible from WidgetConfigActivity and NotesWidgetReceiver.onDeleted | ✓ Good — single source of truth across all widget surfaces |
| WorkManager exclusively owns widget sync schedule | OEM battery management suppresses broadcast-based polling | ✓ Good — reliable 15-min updates even after force-stop |
| @EntryPoint for Glance widget, @AndroidEntryPoint for Activity | Hilt cannot inject into GlanceAppWidget directly | ✓ Good — correct DI pattern per component type |
| Optimistic updates with rollback in widget actions | Widget must feel responsive despite network latency | ✓ Good — instant visual feedback, graceful rollback on failure |
| AddBulletActivity as transparent overlay (not Dialog fragment) | Activity context needed for Hilt injection + setFinishOnTouchOutside | ✓ Good — lightweight feel, keyboard auto-shows |

---
*Last updated: 2026-03-19 after v2.3 milestone started*
