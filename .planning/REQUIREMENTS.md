# Requirements: Notes

**Defined:** 2026-03-09
**Core Value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## v1 Requirements

### Authentication

- [x] **AUTH-01**: User can register an account with email and password
- [x] **AUTH-02**: User can log in with email and password (JWT session, persists across refresh)
- [x] **AUTH-03**: User can log in with Google SSO (OAuth 2.0)
- [x] **AUTH-04**: User can log out via sidebar button
- [x] **AUTH-05**: New user automatically gets a blank "Inbox" document on first login

### Documents

- [x] **DOC-01**: User can create a new document (flat list, no folders)
- [x] **DOC-02**: User can rename a document
- [x] **DOC-03**: User can delete a document (with confirmation)
- [x] **DOC-04**: User can reorder documents via drag in the sidebar
- [x] **DOC-05**: User can navigate between documents by clicking in the sidebar
- [x] **DOC-06**: User can export a single document as a Markdown file
- [x] **DOC-07**: User can export all documents as a Markdown archive

### Bullet Editing

- [x] **BULL-01**: User can create a new bullet by pressing Enter
- [x] **BULL-02**: User can indent a bullet (Tab / toolbar / context menu)
- [x] **BULL-03**: User can outdent a bullet (Shift+Tab / toolbar / context menu)
- [x] **BULL-04**: User can move a bullet up or down (toolbar / keyboard shortcuts / context menu)
- [x] **BULL-05**: User can reorder bullets via drag-and-drop (maintains children; desktop + mobile)
- [x] **BULL-06**: User can collapse a bullet with children (chevron glyph; persisted per user)
- [ ] **BULL-07**: User can zoom into a bullet to view it as the full-screen root
- [ ] **BULL-08**: User can navigate back out via breadcrumb bar (click any ancestor to zoom to that level)
- [ ] **BULL-09**: Bullet text supports inline markdown: **bold**, *italic*, ~~strikethrough~~, [links](url), ![image](url)
- [ ] **BULL-10**: Markdown renders on blur/Enter/Esc; raw markdown shows while editing (Dynalist-style live toggle)
- [x] **BULL-11**: User can soft-delete a bullet (undo restores it with all children)
- [x] **BULL-12**: User can mark a bullet complete (strikethrough + 50% opacity, stays in position)
- [x] **BULL-13**: User can hide completed bullets via "Hide completed" toolbar toggle
- [x] **BULL-14**: User can bulk delete all completed bullets (irreversible; permanently purges)
- [x] **BULL-15**: Desktop: right-click bullet → context menu (indent, outdent, move, complete, delete, bookmark, attachment, comment)
- [ ] **BULL-16**: Mobile: long press bullet → context menu (same actions)

### Keyboard Shortcuts (Desktop)

- [x] **KB-01**: Enter = new bullet below current
- [x] **KB-02**: Tab = indent; Shift+Tab = outdent
- [x] **KB-03**: Ctrl/Cmd+↑↓ = move bullet up/down
- [ ] **KB-04**: Ctrl/Cmd+] = zoom in; Ctrl/Cmd+[ = zoom out to parent
- [x] **KB-05**: Ctrl/Cmd+Z = undo; Ctrl/Cmd+Y = redo
- [x] **KB-06**: Ctrl/Cmd+B = bold; Ctrl/Cmd+I = italic
- [x] **KB-07**: Ctrl/Cmd+P = open search; Ctrl/Cmd+* = bookmarks; Ctrl/Cmd+E = toggle sidebar

### Mobile Touch (Responsive)

- [ ] **MOB-01**: Swipe right on bullet = mark complete
- [ ] **MOB-02**: Swipe left on bullet = soft delete (undo available)
- [ ] **MOB-03**: Long press = context menu
- [ ] **MOB-04**: Touch-friendly drag handles for bullet reordering
- [ ] **MOB-05**: Focus toolbar (appears above keyboard when bullet selected): indent | outdent | ↑ | ↓ | undo | redo | attachment | comment | bookmark | complete | delete

### Special Syntax & Tags

- [ ] **TAG-01**: `#tag` syntax creates a tag, renders as a clickable chip in bullet text
- [ ] **TAG-02**: `@mention` syntax renders as a clickable chip (personal label)
- [ ] **TAG-03**: `!!` syntax opens a date/time picker; renders as a 📅 date chip
- [ ] **TAG-04**: Sidebar Tab 2: Tag Browser lists all unique #tags, @mentions, !!dates across user's documents with bullet counts
- [ ] **TAG-05**: Clicking a tag in the Tag Browser opens a filtered view of all matching bullets

### Search

- [ ] **SRCH-01**: User can search across all personal documents (full-text)
- [ ] **SRCH-02**: Search supports #tag, @mention, and !!date query syntax
- [ ] **SRCH-03**: Search results show matching bullets; clicking a result opens it in zoomed focus view
- [ ] **SRCH-04**: Search accessible via no-focus toolbar button and Ctrl/Cmd+P keyboard shortcut

### Bookmarks

- [ ] **BM-01**: User can bookmark a bullet (via focus toolbar or context menu)
- [ ] **BM-02**: No-focus toolbar "Bookmarks" button shows a dedicated screen of all bookmarked bullets
- [ ] **BM-03**: Clicking a bookmark opens the bullet in zoomed focus view

### Comments

- [ ] **CMT-01**: Bullets with comments show a small note icon indicator
- [ ] **CMT-02**: Clicking the note icon opens a right-edge slide-in side panel with flat comment list
- [ ] **CMT-03**: User can add a plain-text comment to any bullet
- [ ] **CMT-04**: User can delete their own comments

### Attachments

- [ ] **ATT-01**: User can attach a file to a bullet (any file type, 100MB max)
- [ ] **ATT-02**: Upload accessible via focus toolbar button or context menu
- [ ] **ATT-03**: Images render as inline previews within the bullet
- [ ] **ATT-04**: PDFs show a thumbnail preview
- [ ] **ATT-05**: Other file types show a download icon + filename
- [ ] **ATT-06**: Files stored on Docker volume mount

### Undo / Redo

- [x] **UNDO-01**: User can undo the last action (text edits + structural changes: indent, reorder, delete)
- [x] **UNDO-02**: Undo history is 50 levels deep, global per user (not per document)
- [x] **UNDO-03**: Undo history is server-side and persists across page refresh and app restart
- [x] **UNDO-04**: Undoing a bullet deletion restores the bullet and all its children

## v2 Requirements

### Enhanced Mobile

- **MOB-V2-01**: Progressive Web App (PWA) manifest for home screen installation
- **MOB-V2-02**: Quick-open palette (fuzzy document + bullet search)

### Collaboration (Not Planned)

- Shared documents, real-time collaboration — explicitly out of scope, no v2 plan

## Out of Scope

| Feature | Reason |
|---------|--------|
| Native mobile apps (iOS/Android) | Web-first; responsive web covers the use case |
| Offline mode | Service worker complexity; requires conflict resolution; defer |
| Push notifications | No collaboration = nothing to notify about |
| Document sharing / workspaces | Privacy-first design; no sharing by intent |
| Folders | Flat document list matches Dynalist/Workflowy pattern |
| Agenda / calendar view | Different product; use !!date syntax + tag browser instead |
| Import from Dynalist/Workflowy | Complexity with format variability; defer |
| Threaded comments | Flat comments sufficient for personal use |
| Version history beyond 50-step undo | Explicit limit; database cost of full history |
| Admin panel / user roles | All users equal; open registration |
| Bidirectional links (Roam-style) | Different product category; scope creep |
| AI features | Out of scope for focused outliner clone |
| Real-time sync | No collaboration = no need |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Complete |
| AUTH-02 | Phase 1 | Complete |
| AUTH-03 | Phase 1 | Complete |
| AUTH-04 | Phase 1 | Complete |
| AUTH-05 | Phase 1 | Complete |
| DOC-01 | Phase 1 | Complete |
| DOC-02 | Phase 1 | Complete |
| DOC-03 | Phase 1 | Complete |
| DOC-04 | Phase 1 | Complete |
| DOC-05 | Phase 1 | Complete |
| DOC-06 | Phase 1 | Complete |
| DOC-07 | Phase 1 | Complete |
| BULL-01 | Phase 2 | Complete |
| BULL-02 | Phase 2 | Complete |
| BULL-03 | Phase 2 | Complete |
| BULL-04 | Phase 2 | Complete |
| BULL-05 | Phase 2 | Complete |
| BULL-06 | Phase 2 | Complete |
| BULL-07 | Phase 2 | Pending |
| BULL-08 | Phase 2 | Pending |
| BULL-09 | Phase 3 | Pending |
| BULL-10 | Phase 3 | Pending |
| BULL-11 | Phase 2 | Complete |
| BULL-12 | Phase 2 | Complete |
| BULL-13 | Phase 2 | Complete |
| BULL-14 | Phase 2 | Complete |
| BULL-15 | Phase 2 | Complete |
| BULL-16 | Phase 4 | Pending |
| KB-01 | Phase 2 | Complete |
| KB-02 | Phase 2 | Complete |
| KB-03 | Phase 2 | Complete |
| KB-04 | Phase 2 | Pending |
| KB-05 | Phase 2 | Complete |
| KB-06 | Phase 2 | Complete |
| KB-07 | Phase 2 | Complete |
| MOB-01 | Phase 4 | Pending |
| MOB-02 | Phase 4 | Pending |
| MOB-03 | Phase 4 | Pending |
| MOB-04 | Phase 4 | Pending |
| MOB-05 | Phase 4 | Pending |
| TAG-01 | Phase 3 | Pending |
| TAG-02 | Phase 3 | Pending |
| TAG-03 | Phase 3 | Pending |
| TAG-04 | Phase 3 | Pending |
| TAG-05 | Phase 3 | Pending |
| SRCH-01 | Phase 3 | Pending |
| SRCH-02 | Phase 3 | Pending |
| SRCH-03 | Phase 3 | Pending |
| SRCH-04 | Phase 3 | Pending |
| BM-01 | Phase 3 | Pending |
| BM-02 | Phase 3 | Pending |
| BM-03 | Phase 3 | Pending |
| CMT-01 | Phase 4 | Pending |
| CMT-02 | Phase 4 | Pending |
| CMT-03 | Phase 4 | Pending |
| CMT-04 | Phase 4 | Pending |
| ATT-01 | Phase 4 | Pending |
| ATT-02 | Phase 4 | Pending |
| ATT-03 | Phase 4 | Pending |
| ATT-04 | Phase 4 | Pending |
| ATT-05 | Phase 4 | Pending |
| ATT-06 | Phase 4 | Pending |
| UNDO-01 | Phase 2 | Complete |
| UNDO-02 | Phase 2 | Complete |
| UNDO-03 | Phase 2 | Complete |
| UNDO-04 | Phase 2 | Complete |

**Coverage:**
- v1 requirements: 66 total
- Mapped to phases: 66
- Unmapped: 0

---
*Requirements defined: 2026-03-09*
*Last updated: 2026-03-09 — traceability updated after roadmap creation (4 phases)*
