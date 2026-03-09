# Phase 1: Foundation - Context

**Gathered:** 2026-03-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Authenticated app with document management and correct DB schema. Users can register, log in (email/password + Google SSO), manage documents (create/rename/reorder/delete), export documents as Markdown, and land on a working document after login — all on a database schema stable enough to never require a breaking migration.

</domain>

<decisions>
## Implementation Decisions

### Auth UX Flow
- Login and Register on a single page with tabs (Login tab / Register tab) — no separate routes
- Unauthenticated users hitting the app root are immediately redirected to `/login`; no landing page
- Google SSO button appears above the email/password form with an "or" divider between them
- Auth errors (wrong password, duplicate email, etc.) display inline below the relevant input field

### Sidebar + Document List
- Document rows show name only — no metadata (no date, no bullet count)
- Active document highlighted with a subtle background color on its row
- Fixed sidebar width of ~240px on desktop (not resizable)
- Document actions (rename, delete, reorder) triggered by a ⋯ (3-dot) menu that appears on hover/focus — no always-visible icons, no right-click-only

### App Entry Experience
- After login, user lands on the last opened document; falls back to Inbox for new/first-time users
- Empty document shows a single placeholder bullet ready to type in (not blank canvas, not greyed hint text)
- When sidebar is hidden (default desktop state): no-focus toolbar + breadcrumb bar are shown at the top — not a top bar with document title
- On mobile, sidebar slides over the canvas from the left; tap outside to dismiss (slide-over panel pattern)

### Export UX
- Single document export: triggered from the document's 3-dot menu in the sidebar
- All documents export: bulk option accessible from a sidebar-level menu entry
- All-docs format: .zip archive with one .md file per document (not a single concatenated file)
- Feedback: immediate browser download — no progress UI, no toast (files are small enough)
- Nesting format in exported Markdown: indented dashes (`- ` with 2-space indent per level)

### Locked Schema Decisions (from research — pre-decided)
- Tree position uses `FLOAT8` midpoint positioning — NOT string fractional keys (known PostgreSQL collation bug)
- Soft delete (`deleted_at`) on bullets from day 1 — required for undo-of-delete to restore subtrees
- `undo_events` and `undo_cursors` tables created in Phase 1 even though undo feature ships in Phase 2
- Per-bullet contenteditable model (not ProseMirror document) — tree structure conflicts with ProseMirror

### Claude's Discretion
- Exact color palette and typography (keep it clean and minimal — Dynalist-inspired)
- Loading states, skeleton screens
- Form validation timing (on blur vs on submit vs real-time)
- JWT token storage mechanism (localStorage vs httpOnly cookie — Claude decides based on security tradeoffs)
- Docker Compose structure and container count

</decisions>

<specifics>
## Specific Ideas

- Feel should be clean and minimal — Dynalist/Workflowy aesthetic, not rich/colorful
- No landing page: the app is a tool, not a product to sell — get users into the document immediately

</specifics>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — greenfield project, no existing code

### Established Patterns
- None yet — Phase 1 establishes the baseline patterns for all subsequent phases

### Integration Points
- CLAUDE.md specifies: Docker deployment on `192.168.1.50`, Nginx reverse proxy at `192.168.1.204`, public URL `https://notes.gregorymaingret.fr`
- `.env` / `.env.example` pattern: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, JWT_SECRET, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_CALLBACK_URL, UPLOAD_MAX_SIZE_MB, UPLOAD_PATH
- GitHub repo: `https://github.com/gmaingret/notes.git`

</code_context>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 1 scope.

</deferred>

---

*Phase: 01-foundation*
*Context gathered: 2026-03-09*
