# Requirements: Notes v2.1 — Android Home Screen Widget

**Defined:** 2026-03-14
**Core Value:** Users can capture and organize personal knowledge in an infinitely nested bullet outline that works seamlessly on both desktop and mobile, with all data staying private on their own server.

## v2.1 Requirements

### Widget Setup

- [x] **SETUP-01**: User can add the Notes widget to their Android home screen
- [x] **SETUP-02**: User is presented with a document picker when adding the widget

### Widget Display

- [x] **DISP-01**: Widget shows the document title in a header row
- [x] **DISP-02**: Widget shows root-level bullets as a scrollable flat list
- [x] **DISP-03**: Widget shows an empty state when the document has no bullets
- [x] **DISP-04**: Widget shows loading and error states appropriately
- [x] **DISP-05**: Widget uses Material 3 theming consistent with the app

### Widget Actions

- [x] **ACT-01**: User can tap a "+" button to add a new bullet at the top of the list via a lightweight overlay dialog with pre-focused text field
- [x] **ACT-02**: User can tap a delete icon on any bullet to remove it

### Widget Sync

- [x] **SYNC-01**: Widget refreshes automatically when bullets are changed in the Android app
- [x] **SYNC-02**: Widget refreshes periodically in the background via WorkManager (15-min interval)
- [x] **SYNC-03**: Widget authenticates independently using the persisted refresh token

## v2.0 Requirements (Complete)

### Authentication & Network

- [x] **AUTH-01**: User can register with email and password
- [x] **AUTH-02**: User can log in with email and password
- [x] **AUTH-03**: User can log in with Google SSO (Credential Manager API)
- [x] **AUTH-04**: JWT bearer token injected on all API requests via OkHttp Interceptor
- [x] **AUTH-05**: Token refresh via httpOnly cookie with Mutex-synchronized Authenticator
- [x] **AUTH-06**: Silent re-login on cold start via persisted refresh cookie

### Document Management

- [x] **DOCM-01**: User can view document list in ModalNavigationDrawer
- [x] **DOCM-02**: User can create a new document
- [x] **DOCM-03**: User can rename a document
- [x] **DOCM-04**: User can delete a document
- [x] **DOCM-05**: User can drag-reorder documents in the drawer
- [x] **DOCM-06**: App remembers and re-opens last viewed document

### Bullet Tree

- [x] **TREE-01**: User can view nested bullets in flat LazyColumn with depth-based indent
- [x] **TREE-02**: User can create new bullets (Enter creates sibling; second Enter on empty bullet outdents instead of creating another)
- [x] **TREE-03**: User can edit bullet content with debounced save
- [x] **TREE-04**: User can delete bullets (Backspace on empty / toolbar)
- [x] **TREE-05**: User can indent/outdent bullets (toolbar buttons)
- [x] **TREE-06**: User can collapse/expand bullets with children (animated)
- [x] **TREE-07**: User can mark bullets complete (strikethrough + opacity)
- [x] **TREE-08**: User can zoom into any bullet with breadcrumb navigation
- [x] **TREE-09**: User can drag-reorder bullets with projection algorithm
- [x] **TREE-10**: User can add/edit notes field per bullet
- [x] **TREE-11**: User can view and add comments on a bullet

### Content Rendering

- [x] **CONT-01**: Bullet text renders markdown (bold, italic, strikethrough, links)
- [x] **CONT-02**: #tags, @mentions, !!dates render as clickable chips
- [x] **CONT-03**: User can view and download file attachments on bullets
- [x] **CONT-04**: User can view bookmarked bullets in a dedicated screen

### Reactivity & Polish

- [x] **POLL-01**: Loading and error states on all screens
- [x] **POLL-02**: Pull-to-refresh on document and bullet views
- [x] **POLL-03**: Optimistic updates with rollback on failure
- [x] **POLL-04**: Swipe right to complete bullet, swipe left to delete
- [x] **POLL-05**: Search across documents and bullets with debounce
- [x] **POLL-06**: Undo/redo (server-side, 50 levels)
- [x] **POLL-07**: Material 3 dark theme
- [x] **POLL-08**: Material 3 animations (AnimatedVisibility, animateItemPlacement, Crossfade)

## Future Requirements

### Widget Enhancements

- **WIDG-01**: Multiple widget instances pointing to different documents
- **WIDG-02**: Manual refresh button in widget header
- **WIDG-03**: Swipe-to-delete gesture on widget items
- **WIDG-04**: Completion checkbox / clear-completed in widget
- **WIDG-05**: Widget appearance customization (color, font size)

### Android App Enhancements

- **TREE-12**: User can upload attachments from Android (view/download only in v2.0)
- **CONT-05**: Inline image rendering in bullets
- **CONT-06**: Tag browser sidebar
- **POLL-09**: Physical keyboard shortcuts (Tab, Ctrl+arrows)
- **POLL-10**: Export document(s) as Markdown

## Out of Scope

| Feature | Reason |
|---------|--------|
| Nested bullet display in widget | Root level only — keeps widget simple and readable |
| Drag-to-reorder in widget | Glance LazyColumn backed by ListView, no drag support |
| Offline/local-only mode | No Room database; server is source of truth |
| iOS widget | Android only |
| Widget notification on document changes | No push notification infrastructure |
| Dynamic color (Material You wallpaper) | Custom theme tokens for brand consistency |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SETUP-01 | Phase 13 | Complete |
| SETUP-02 | Phase 13 | Complete |
| DISP-01 | Phase 13 | Complete |
| DISP-02 | Phase 13 | Complete |
| DISP-03 | Phase 13 | Complete |
| DISP-04 | Phase 13 | Complete |
| DISP-05 | Phase 13 | Complete |
| ACT-01 | Phase 15 | Complete |
| ACT-02 | Phase 15 | Complete |
| SYNC-01 | Phase 14 | Complete |
| SYNC-02 | Phase 14 | Complete |
| SYNC-03 | Phase 14 | Complete |

**Coverage:**
- v2.1 requirements: 12 total (note: original count of 11 was incorrect; SYNC-03 brings total to 12)
- Mapped to phases: 12
- Unmapped: 0

---
*Requirements defined: 2026-03-14*
*Last updated: 2026-03-14 after roadmap creation — traceability complete*
