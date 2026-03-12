# Requirements: Notes v2.0 — Native Android Client

**Defined:** 2026-03-12
**Core Value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## v2.0 Requirements

### Authentication & Network

- [ ] **AUTH-01**: User can register with email and password
- [ ] **AUTH-02**: User can log in with email and password
- [x] **AUTH-03**: User can log in with Google SSO (Credential Manager API)
- [x] **AUTH-04**: JWT bearer token injected on all API requests via OkHttp Interceptor
- [x] **AUTH-05**: Token refresh via httpOnly cookie with Mutex-synchronized Authenticator
- [ ] **AUTH-06**: Silent re-login on cold start via persisted refresh cookie

### Document Management

- [ ] **DOCM-01**: User can view document list in ModalNavigationDrawer
- [ ] **DOCM-02**: User can create a new document
- [ ] **DOCM-03**: User can rename a document
- [ ] **DOCM-04**: User can delete a document
- [ ] **DOCM-05**: User can drag-reorder documents in the drawer
- [ ] **DOCM-06**: App remembers and re-opens last viewed document

### Bullet Tree

- [ ] **TREE-01**: User can view nested bullets in flat LazyColumn with depth-based indent
- [ ] **TREE-02**: User can create new bullets (Enter creates sibling; second Enter on empty bullet outdents instead of creating another)
- [ ] **TREE-03**: User can edit bullet content with debounced save
- [ ] **TREE-04**: User can delete bullets (Backspace on empty / toolbar)
- [ ] **TREE-05**: User can indent/outdent bullets (toolbar buttons)
- [ ] **TREE-06**: User can collapse/expand bullets with children (animated)
- [ ] **TREE-07**: User can mark bullets complete (strikethrough + opacity)
- [ ] **TREE-08**: User can zoom into any bullet with breadcrumb navigation
- [ ] **TREE-09**: User can drag-reorder bullets with projection algorithm
- [ ] **TREE-10**: User can add/edit notes field per bullet
- [ ] **TREE-11**: User can view and add comments on a bullet

### Content Rendering

- [ ] **CONT-01**: Bullet text renders markdown (bold, italic, strikethrough, links)
- [ ] **CONT-02**: #tags, @mentions, !!dates render as clickable chips
- [ ] **CONT-03**: User can view and download file attachments on bullets
- [ ] **CONT-04**: User can view bookmarked bullets in a dedicated screen

### Reactivity & Polish

- [ ] **POLL-01**: Loading and error states on all screens
- [ ] **POLL-02**: Pull-to-refresh on document and bullet views
- [ ] **POLL-03**: Optimistic updates with rollback on failure
- [ ] **POLL-04**: Swipe right to complete bullet, swipe left to delete
- [ ] **POLL-05**: Search across documents and bullets with debounce
- [ ] **POLL-06**: Undo/redo (server-side, 50 levels)
- [ ] **POLL-07**: Material 3 dark theme
- [ ] **POLL-08**: Material 3 animations (AnimatedVisibility, animateItemPlacement, Crossfade)

## v2.1 Requirements

### Deferred

- **TREE-11**: User can upload attachments from Android (view/download only in v2.0)
- **CONT-05**: Inline image rendering in bullets
- **CONT-06**: Tag browser sidebar
- **POLL-09**: Physical keyboard shortcuts (Tab, Ctrl+arrows)
- **POLL-10**: Export document(s) as Markdown

## Out of Scope

| Feature | Reason |
|---------|--------|
| Offline mode / Room database | No offline by design — always connected to server |
| Dynamic color (Material You wallpaper) | Custom theme tokens for brand consistency |
| PWA features | Native app replaces PWA on Android |
| Firebase / Play Services | Not required — Credential Manager API handles Google SSO directly |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 9 | Pending |
| AUTH-02 | Phase 9 | Pending |
| AUTH-03 | Phase 9 | Complete |
| AUTH-04 | Phase 9 | Complete |
| AUTH-05 | Phase 9 | Complete |
| AUTH-06 | Phase 9 | Pending |
| DOCM-01 | Phase 10 | Pending |
| DOCM-02 | Phase 10 | Pending |
| DOCM-03 | Phase 10 | Pending |
| DOCM-04 | Phase 10 | Pending |
| DOCM-05 | Phase 10 | Pending |
| DOCM-06 | Phase 10 | Pending |
| TREE-01 | Phase 11 | Pending |
| TREE-02 | Phase 11 | Pending |
| TREE-03 | Phase 11 | Pending |
| TREE-04 | Phase 11 | Pending |
| TREE-05 | Phase 11 | Pending |
| TREE-06 | Phase 11 | Pending |
| TREE-07 | Phase 11 | Pending |
| TREE-08 | Phase 11 | Pending |
| TREE-09 | Phase 11 | Pending |
| TREE-10 | Phase 11 | Pending |
| CONT-01 | Phase 11 | Pending |
| CONT-02 | Phase 11 | Pending |
| TREE-11 | Phase 11 | Pending |
| CONT-03 | Phase 12 | Pending |
| CONT-04 | Phase 12 | Pending |
| POLL-01 | Phase 12 | Pending |
| POLL-02 | Phase 12 | Pending |
| POLL-03 | Phase 12 | Pending |
| POLL-04 | Phase 12 | Pending |
| POLL-05 | Phase 12 | Pending |
| POLL-06 | Phase 12 | Pending |
| POLL-07 | Phase 12 | Pending |
| POLL-08 | Phase 12 | Pending |

**Coverage:**
- v2.0 requirements: 35 total
- Mapped to phases: 35
- Unmapped: 0

---
*Requirements defined: 2026-03-12*
*Last updated: 2026-03-12 — added TREE-11 comments, refined TREE-02 Enter behavior*
