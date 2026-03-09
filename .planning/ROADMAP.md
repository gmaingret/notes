# Roadmap: Notes

## Overview

Four phases deliver a complete self-hosted infinite outliner. Phase 1 lays the authenticated foundation — secure schema, document management, and the new-user onboarding flow. Phase 2 builds the core outliner loop — bullet CRUD, nesting, keyboard shortcuts, collapse/zoom, and the server-persisted undo system that must wrap every mutation before complexity layers on. Phase 3 enriches bullet content with markdown rendering, full-text search, tag/mention/date chip syntax, and bookmarks. Phase 4 completes the product with file attachments, comments, and mobile touch gestures.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundation** - Authenticated app with document management and correct DB schema (completed 2026-03-09)
- [ ] **Phase 2: Core Outliner** - Bullet CRUD, nesting, keyboard shortcuts, collapse/zoom, and server-persisted undo
- [ ] **Phase 3: Rich Content** - Markdown rendering, full-text search, tag/mention/date chips, and bookmarks
- [ ] **Phase 4: Attachments, Comments, and Mobile** - File attachments, comments, and mobile touch gestures

## Phase Details

### Phase 1: Foundation
**Goal**: Users can securely access the app and manage their documents, with a schema correct enough to never require a breaking migration
**Depends on**: Nothing (first phase)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, DOC-01, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07
**Success Criteria** (what must be TRUE):
  1. User can register an account with email/password, log in, and stay logged in across browser refresh
  2. User can log in with Google SSO and access the same account on subsequent visits
  3. A new user lands in a blank "Inbox" document automatically created on first login
  4. User can create, rename, reorder, and delete documents from the sidebar and navigate between them
  5. User can export any document or all documents as Markdown files
**Plans**: 6 plans

Plans:
- [x] 01-01-PLAN.md — Infrastructure, Drizzle schema (5 tables), Docker, test scaffolds
- [x] 01-02-PLAN.md — Auth backend: register, login, refresh, logout, Google OAuth
- [x] 01-03-PLAN.md — Documents backend: CRUD, reorder, export endpoints
- [x] 01-04-PLAN.md — Frontend scaffold: Vite, AuthContext, LoginPage, React Router
- [x] 01-05-PLAN.md — App shell: Sidebar, DocumentList (dnd-kit), DocumentRow, DocumentView
- [x] 01-06-PLAN.md — Deploy to production, E2E smoke test, Google SSO human checkpoint

### Phase 2: Core Outliner
**Goal**: Users can capture and organize thoughts in an infinitely nested bullet outline with full keyboard control and undo that survives page refresh
**Depends on**: Phase 1
**Requirements**: BULL-01, BULL-02, BULL-03, BULL-04, BULL-05, BULL-06, BULL-07, BULL-08, BULL-11, BULL-12, BULL-13, BULL-14, BULL-15, KB-01, KB-02, KB-03, KB-04, KB-05, KB-06, KB-07, UNDO-01, UNDO-02, UNDO-03, UNDO-04
**Success Criteria** (what must be TRUE):
  1. User can create, edit, indent, outdent, and delete bullets using Enter/Tab/Shift+Tab/Backspace; changes persist after page refresh
  2. User can drag a bullet (with all its children) to a new position in the tree without creating cycles; the move can be undone
  3. User can collapse and expand branches; collapsed state survives a page refresh
  4. User can zoom into any bullet as the full-screen root and navigate back up via the breadcrumb bar using keyboard or click
  5. User can undo and redo up to 50 actions — including deletions, reorders, and indent changes — and the undo history survives a full page refresh
**Plans**: 8 plans

Plans:
- [ ] 02-01-PLAN.md — Wave 0: Nyquist test scaffolds (failing stubs for all 24 requirements)
- [ ] 02-02-PLAN.md — bulletService + undoService (server-side tree ops + undo stack)
- [ ] 02-03-PLAN.md — Bullet + undo HTTP routes, registered in index.ts
- [ ] 02-04-PLAN.md — useBullets hooks, BulletTree/BulletNode/BulletContent + keyboard handler
- [ ] 02-05-PLAN.md — DnD reorder (flatten/projected-depth) + URL-based zoom
- [ ] 02-06-PLAN.md — Breadcrumb + global keyboard shortcuts (Ctrl+Z/Y/E/P)
- [ ] 02-07-PLAN.md — Context menu, complete/hide/bulk-delete completed
- [ ] 02-08-PLAN.md — Deploy to production + human verification checkpoint

### Phase 3: Rich Content
**Goal**: Bullet text comes alive with inline formatting, clickable syntax chips, tag browsing, bookmarks, and fast full-text search
**Depends on**: Phase 2
**Requirements**: BULL-09, BULL-10, TAG-01, TAG-02, TAG-03, TAG-04, TAG-05, SRCH-01, SRCH-02, SRCH-03, SRCH-04, BM-01, BM-02, BM-03
**Success Criteria** (what must be TRUE):
  1. Bold, italic, strikethrough, links, and inline images render in bullet text when not being edited; raw markdown shows while the cursor is in that bullet
  2. Typing #tag, @mention, or !! renders a clickable chip in the bullet; !! opens a date picker before inserting the chip
  3. The Tag Browser (sidebar tab) lists all unique #tags, @mentions, and !!dates across the user's documents with bullet counts; clicking one opens a filtered bullet list
  4. User can search across all documents using free text and tag/mention/date query syntax; clicking a result opens the bullet in zoomed focus view
  5. User can bookmark any bullet and view all bookmarks in a dedicated screen; clicking a bookmark opens the bullet in zoomed focus view
**Plans**: TBD

### Phase 4: Attachments, Comments, and Mobile
**Goal**: Users can attach files and leave comments on bullets, and the full feature set works on mobile browsers via swipe and long-press gestures
**Depends on**: Phase 3
**Requirements**: BULL-16, ATT-01, ATT-02, ATT-03, ATT-04, ATT-05, ATT-06, CMT-01, CMT-02, CMT-03, CMT-04, MOB-01, MOB-02, MOB-03, MOB-04, MOB-05
**Success Criteria** (what must be TRUE):
  1. User can attach any file (up to 100MB) to a bullet; images render inline, PDFs show a thumbnail, other files show a download icon; files survive a container restart
  2. User can add and delete plain-text comments on any bullet; bullets with comments show a note icon that opens the comments side panel
  3. On mobile, swiping right marks a bullet complete; swiping left soft-deletes it with undo available
  4. On mobile, long-pressing a bullet opens a context menu with indent, outdent, move, complete, delete, bookmark, attachment, and comment actions
  5. The mobile focus toolbar (above the keyboard) provides one-tap access to indent, outdent, move, undo, redo, attachment, comment, bookmark, complete, and delete
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 6/6 | Complete   | 2026-03-09 |
| 2. Core Outliner | 4/8 | In Progress|  |
| 3. Rich Content | 0/TBD | Not started | - |
| 4. Attachments, Comments, and Mobile | 0/TBD | Not started | - |
