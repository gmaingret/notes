# Implementation Plan: Notes App
**Repo:** https://github.com/gmaingret/notes
**Maintained by:** Claude Code
**Last updated:** February 2026

---

## Overview

This document is the authoritative implementation plan for all phases of the Notes app. It is intended to be used directly by Claude Code as a task reference. Each phase is broken into numbered steps — give Claude Code one step at a time to avoid token limit issues.

**Companion docs:**
- `docs/PRD-Notes-App.md` — Product requirements
- `docs/SYSTEM-DESIGN-Notes-App.md` — Architecture and data model

---

## How to Use This Plan with Claude Code

1. Start every new session with `/clear` to reset context
2. Each step has an exact **Claude Code prompt** to copy-paste
3. Wait for a step to be committed and pushed before starting the next one
4. When CI fails, tell Claude Code: *"Fix the CI failures on [branch], then push"*
5. When a full phase is done and CI is green, open a PR, merge, then pull on the server

---

## Repository Structure

```
notes/
├── CLAUDE.md
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── docs/
│   ├── nginx-notes.conf
│   ├── PRD-Notes-App.md
│   ├── SYSTEM-DESIGN-Notes-App.md
│   └── IMPLEMENTATION-PLAN.md
├── server/
│   ├── app/
│   │   ├── main.py
│   │   ├── config.py
│   │   ├── db/
│   │   │   ├── database.py
│   │   │   └── migrations/
│   │   ├── models/
│   │   │   ├── document.py
│   │   │   ├── bullet.py
│   │   │   ├── attachment.py
│   │   │   ├── sync.py
│   │   │   └── auth.py
│   │   ├── routers/
│   │   │   ├── auth.py
│   │   │   ├── documents.py
│   │   │   ├── bullets.py
│   │   │   ├── attachments.py
│   │   │   ├── search.py
│   │   │   └── sync.py
│   │   ├── services/
│   │   │   ├── auth_service.py
│   │   │   ├── document_service.py
│   │   │   ├── bullet_service.py
│   │   │   ├── attachment_service.py
│   │   │   ├── search_service.py
│   │   │   ├── sync_service.py
│   │   │   └── tag_service.py
│   │   └── utils/
│   │       ├── fractional_index.py
│   │       └── jwt_utils.py
│   ├── tests/
│   │   ├── conftest.py
│   │   ├── unit/
│   │   │   ├── test_fractional_index.py
│   │   │   ├── test_tag_extraction.py
│   │   │   ├── test_jwt_utils.py
│   │   │   ├── test_sync_service.py
│   │   │   └── test_bullet_service.py
│   │   └── integration/
│   │       ├── test_auth.py
│   │       ├── test_documents.py
│   │       ├── test_bullets.py
│   │       ├── test_attachments.py
│   │       ├── test_search.py
│   │       └── test_sync.py
│   ├── Dockerfile
│   ├── requirements.txt
│   └── requirements-dev.txt
└── client/
    ├── lib/
    │   ├── main.dart
    │   ├── app.dart
    │   ├── core/
    │   │   ├── db/
    │   │   │   ├── app_database.dart
    │   │   │   ├── tables/
    │   │   │   └── daos/
    │   │   ├── api/
    │   │   │   ├── api_client.dart
    │   │   │   └── endpoints/
    │   │   ├── sync/
    │   │   │   ├── sync_manager.dart
    │   │   │   └── connectivity_service.dart
    │   │   └── utils/
    │   │       ├── fractional_index.dart
    │   │       └── markdown_parser.dart
    │   └── features/
    │       ├── auth/
    │       │   ├── providers/auth_provider.dart
    │       │   └── screens/login_screen.dart
    │       ├── documents/
    │       │   ├── providers/documents_provider.dart
    │       │   ├── repositories/document_repository.dart
    │       │   └── widgets/document_sidebar.dart
    │       ├── bullets/
    │       │   ├── providers/bullet_tree_provider.dart
    │       │   ├── repositories/bullet_repository.dart
    │       │   └── widgets/
    │       │       ├── bullet_item.dart
    │       │       ├── bullet_tree.dart
    │       │       ├── bullet_editor.dart
    │       │       ├── breadcrumb_bar.dart
    │       │       ├── swipe_action_wrapper.dart
    │       │       └── context_menu.dart
    │       ├── search/
    │       │   ├── providers/search_provider.dart
    │       │   └── screens/search_screen.dart
    │       └── attachments/
    │           ├── providers/attachment_provider.dart
    │           └── widgets/
    │               ├── attachment_picker.dart
    │               └── attachment_viewer.dart
    ├── test/
    │   ├── helpers/
    │   ├── unit/
    │   │   ├── fractional_index_test.dart
    │   │   ├── markdown_parser_test.dart
    │   │   ├── tag_extraction_test.dart
    │   │   ├── sync_manager_test.dart
    │   │   └── bullet_repository_test.dart
    │   └── widget/
    │       ├── bullet_item_test.dart
    │       ├── bullet_tree_test.dart
    │       ├── breadcrumb_bar_test.dart
    │       ├── swipe_action_test.dart
    │       └── document_sidebar_test.dart
    ├── integration_test/
    │   ├── auth_flow_test.dart
    │   ├── outliner_flow_test.dart
    │   ├── offline_sync_test.dart
    │   └── search_flow_test.dart
    └── pubspec.yaml
```

---

## Phase 0 — Repository Setup ✅ COMPLETE

---

## Phase 1 — Core Outliner + Auth + Basic Online Sync

> Goal: A working outliner with login, documents, nested bullets, zoom, breadcrumb, and online sync.
> Branch: `phase-1/core-outliner`

---

### Step 1.1 — Backend: Auth

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-1/core-outliner. Implement the backend auth step:*
> *- `auth_service.py`: verify Google ID token via `google-auth`; extract sub/email/name; upsert user into `users` table*
> *- `jwt_utils.py`: issue 24h JWT via `python-jose`; validate JWT as FastAPI dependency; refresh if token age < 48h*
> *- `routers/auth.py`: POST /auth/google, POST /auth/refresh; register router in main.py*
> *- `models/auth.py`: Pydantic request/response models for auth endpoints*
> *- Unit tests in `tests/unit/test_jwt_utils.py`: issue token, validate token, reject expired, reject tampered, refresh grace period*
> *- Integration tests in `tests/integration/test_auth.py`: valid Google token → JWT issued; invalid token → 401; expired JWT → 401; refresh within grace → new JWT; refresh outside grace → 401. Mock Google token verification in conftest.*
> *Ensure ruff passes and pytest coverage ≥ 80% on the new files. Commit and push."*

---

### Step 1.2 — Backend: Documents + Bullets + Fractional Index

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-1/core-outliner. Implement documents API, bullets API, and supporting utilities:*
> *- `utils/fractional_index.py`: generate position strings (first, before, after, between); lexicographic ordering must hold for 1000 sequential inserts*
> *- `document_service.py`: create/list/rename/soft-delete documents; fractional index ordering for sidebar*
> *- `routers/documents.py`: GET /documents, POST /documents, PATCH /documents/:id, DELETE /documents/:id*
> *- `models/document.py`: Pydantic models*
> *- `tag_service.py`: extract #tags from bullet content via regex; sync tags + bullet_tags tables on every bullet upsert; store tags lowercase without #*
> *- `bullet_service.py`: create bullet (document_id, parent_id, position); update content/is_complete/position/parent_id; soft-delete with cascade to children; fetch flat bullet list for a document (non-deleted, ordered by position)*
> *- `routers/bullets.py`: GET /documents/:id/bullets, POST /bullets, PATCH /bullets/:id, DELETE /bullets/:id*
> *- `models/bullet.py`: Pydantic models*
> *- Unit tests: `test_fractional_index.py` (first/before/after/between, edge cases, 1000-insert ordering); `test_tag_extraction.py` (multiple tags, numbers/hyphens, no false positives on URLs, lowercase normalisation); `test_bullet_service.py` (create, update, move to new parent, soft-delete cascade on 3-level nesting)*
> *- Integration tests: `test_documents.py` (CRUD lifecycle, ordering, 404 on unknown ID); `test_bullets.py` (root bullet, child bullet, fetch tree ordering, tag sync on update, soft-delete cascade, move to new parent)*
> *Ensure ruff passes and pytest coverage ≥ 80%. Commit and push."*

---

### Step 1.3 — Backend: Sync + Cleanup Job

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-1/core-outliner. Implement the sync endpoint and cleanup background task:*
> *- `models/sync.py`: Pydantic models for sync request/response (operations list, server_delta)*
> *- `sync_service.py`: POST /sync — apply client operations sequentially by client_timestamp; skip duplicate operation_id (idempotency); return empty server_delta for now (delta pull is Phase 3)*
> *- `routers/sync.py`: POST /sync; register in main.py*
> *- Background cleanup task: on startup schedule a task that runs every 30 seconds and hard-deletes rows from bullets, documents, attachments where deleted_at is older than 60 seconds*
> *- Integration tests in `tests/integration/test_sync.py`: push 5 operations → all applied; push duplicate operation_id → idempotent (no error, not double-applied); operations applied in timestamp order; unauthenticated request → 401*
> *Ensure ruff passes and overall pytest coverage ≥ 80%. Commit and push."*

---

### Step 1.4 — Frontend: Auth + Local DB

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-1/core-outliner. Implement Flutter auth and local database:*
> *- Add `flutter_secure_storage` to pubspec.yaml*
> *- Drift tables: define `DocumentsTable`, `BulletsTable`, `SyncOperationsTable` with all columns matching the server schema; run build_runner to generate .g.dart files*
> *- DAOs: `DocumentDao` (list all non-deleted, insert, update, soft-delete), `BulletDao` (list by document_id non-deleted ordered by position, insert, update, soft-delete)*
> *- `core/api/api_client.dart`: Dio instance with base URL from config; JWT interceptor that reads token from flutter_secure_storage and injects Authorization header; 401 interceptor that triggers logout*
> *- `features/auth/providers/auth_provider.dart`: GoogleSignIn flow → POST /auth/google → store JWT in flutter_secure_storage; auto-login on app start if stored JWT not expired; logout clears storage*
> *- `features/auth/screens/login_screen.dart`: Google Sign-In button; shows loading state; navigates to /documents on success*
> *- Update app.dart go_router: redirect unauthenticated users to /login; redirect authenticated users away from /login*
> *- Widget test: login_screen renders sign-in button; loading state shows during auth; mock successful auth navigates to documents route*
> *Ensure flutter analyze is clean. Commit and push."*

---

### Step 1.5 — Frontend: Documents Sidebar + Bullet Tree

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-1/core-outliner. Implement documents sidebar and bullet tree rendering:*
> *- `features/documents/repositories/document_repository.dart`: write to local DB + enqueue SyncOperation; methods: listDocuments, createDocument, renameDocument, deleteDocument*
> *- `features/documents/providers/documents_provider.dart`: Riverpod AsyncNotifier watching DocumentDao stream*
> *- `features/documents/widgets/document_sidebar.dart`: list of documents; tap to navigate to /documents/:id; create button opens rename dialog; long-press opens rename/delete options*
> *- `features/bullets/repositories/bullet_repository.dart`: loadTree (fetch flat list → build in-memory tree); createBullet; updateBullet; deleteBullet; moveBullet (new parent + new position)*
> *- `features/bullets/providers/bullet_tree_provider.dart`: AsyncNotifier for open document; holds flat list + tree structure; tracks zoomedNodeId (null = root)*
> *- `features/bullets/widgets/bullet_tree.dart`: recursive widget rendering tree from zoomedNodeId down; indentation 16px per level; each level uses a Column of bullet_item widgets*
> *- `features/bullets/widgets/bullet_item.dart`: bullet glyph (●) + text field + expand/collapse toggle for nodes with children*
> *- Update /documents/:id screen to show document_sidebar (drawer on mobile, permanent on wide screens) + bullet_tree*
> *- Unit test `bullet_repository_test.dart`: build tree from flat list; insert child; move node to new parent; delete node cascades to children*
> *- Widget test `document_sidebar_test.dart`: lists documents; tap navigates; create dialog appears*
> *- Widget test `bullet_item_test.dart`: renders content; collapse/expand toggle works*
> *Ensure flutter analyze is clean. Commit and push."*

---

### Step 1.6 — Frontend: Bullet Editor + Zoom + Breadcrumb

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-1/core-outliner. Implement the bullet editor with live Markdown, zoom, and breadcrumb navigation:*
> *- `core/utils/markdown_parser.dart`: regex tokenizer for **bold**, *italic*, \`code\`, [text](url) and #tags; returns list of TextSpan tokens; raw syntax shown only when cursor is inside that token span*
> *- `features/bullets/widgets/bullet_editor.dart`: TextField with custom TextSpan painter using markdown_parser; Enter creates sibling bullet; Tab indents; Shift+Tab outdents; Ctrl+Z undo; Ctrl+Y redo (web only via HardwareKeyboard)*
> *- Replace bullet_item text field with bullet_editor*
> *- Zoom: double-tap on bullet glyph sets zoomedNodeId in bullet_tree_provider; bullet_tree re-renders from that node as root*
> *- `features/bullets/widgets/breadcrumb_bar.dart`: horizontally scrollable row; shows path from document root to current zoomedNodeId; each crumb is a TextButton that sets zoomedNodeId; document title is always the first crumb*
> *- Add breadcrumb_bar above bullet_tree in the document screen*
> *- Unit test `markdown_parser_test.dart`: bold, italic, code, link, #tag, cursor-inside shows raw syntax, cursor-outside shows formatted, no false positives*
> *- Widget test `breadcrumb_bar_test.dart`: renders correct crumbs for 3-level zoom; tap crumb updates provider; scrolls horizontally on overflow*
> *Ensure flutter analyze is clean. Commit and push."*

---

### Step 1.7 — Frontend: Sync Manager + All Phase 1 Tests

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-1/core-outliner. Implement the sync manager and complete all Phase 1 tests:*
> *- `core/utils/fractional_index.dart`: mirror of server fractional_index.py — identical logic, same edge cases*
> *- `core/sync/connectivity_service.dart`: stream of connectivity status using connectivity_plus*
> *- `core/sync/sync_manager.dart`: on every mutation debounce 500ms then POST /sync with all pending SyncOperations; on success mark operations as applied in local DB; on failure retain queue for retry; on app start trigger sync if online*
> *- Initial full pull: on first login, GET /documents then GET /documents/:id/bullets for each → populate local DB*
> *- Unit test `fractional_index_test.dart`: mirrors backend tests — first, before, after, between, 1000-insert ordering*
> *- Unit test `sync_manager_test.dart`: mutation enqueues op; success clears queue; failure retains queue; idempotency on retry*
> *- Integration test `auth_flow_test.dart`: mock Google sign-in → JWT stored → /documents screen accessible; logout → back to /login*
> *- Integration test `outliner_flow_test.dart`: create document → add root bullet → Tab to create child → verify indentation → double-tap glyph to zoom → verify breadcrumb shows path → tap breadcrumb crumb to zoom out*
> *Ensure flutter analyze is clean and all flutter test passes. Commit and push."*

---

## Phase 2 — Mobile Gestures, Drag & Drop, Attachments

> Goal: Full mobile interaction model + file/image/audio attachments.
> Branch: `phase-2/gestures-attachments` (branch from main after Phase 1 is merged)

---

### Step 2.1 — Backend: Attachments API

**Claude Code prompt:**
> *"Read CLAUDE.md. Create branch phase-2/gestures-attachments from main. Implement the attachments backend:*
> *- `models/attachment.py`: Pydantic models*
> *- `attachment_service.py`: save uploaded file to ATTACHMENTS_PATH/{uuid}.{ext}; sanitise filename; validate mime type; insert record into attachments table; serve file by streaming from disk; verify attachment belongs to authenticated user before serving; delete removes DB record + file from disk; handle missing file on delete gracefully*
> *- `routers/attachments.py`: POST /attachments (multipart/form-data with bullet_id + file), GET /attachments/:id/file (stream), DELETE /attachments/:id; register router in main.py*
> *- Unit test `test_attachment_service.py`: file saved to correct path; filename sanitised; mime type validated; record inserted; delete removes both DB row and file; missing file on delete does not raise*
> *- Integration test `test_attachments.py`: upload image → retrieve → verify bytes match; upload audio → retrieve; delete → 404 on subsequent GET; access other user attachment → 403*
> *Ensure ruff passes and pytest coverage ≥ 80% on new files. Commit and push."*

---

### Step 2.2 — Frontend: Swipe Gestures + Context Menu

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-2/gestures-attachments. Implement swipe gestures and long-press context menu:*
> *- `features/bullets/widgets/swipe_action_wrapper.dart`: wraps bullet_item; horizontal drag detection; swipe right > 50% threshold → mark complete (is_complete=true in local DB, strikethrough+dim style, enqueue sync op); swipe left > 50% threshold → permanently delete (remove from local DB, enqueue sync op, show SnackBar with 5s undo — re-insert at same position with same ID if undo tapped); green background with ✓ icon on right swipe, red background with 🗑 icon on left swipe*
> *- Wrap every bullet_item in bullet_tree.dart with swipe_action_wrapper*
> *- `features/bullets/widgets/context_menu.dart`: modal bottom sheet on long-press of bullet glyph (●); items: Indent, Outdent, Move to document (shows document picker sub-sheet), Duplicate, Delete*
> *- Move to document: update document_id + set parent_id to null + new fractional index position at end of target document root; enqueue sync op*
> *- Unit test `swipe_action_test.dart`: threshold calculation; undo state management; re-insert logic after undo*
> *- Widget test `swipe_action_test.dart`: swipe right → complete styling; swipe left → removed + snackbar; undo → reappears*
> *- Widget test `context_menu_test.dart`: all items render; indent/outdent fire callbacks; delete triggers confirmation*
> *Ensure flutter analyze is clean. Commit and push."*

---

### Step 2.3 — Frontend: Drag-to-Reorder + Keyboard Shortcuts

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-2/gestures-attachments. Implement drag-to-reorder and complete web keyboard shortcuts:*
> *- Long-press on bullet text → show animated drag handle on left of bullet row*
> *- Implement drag-to-reorder using LongPressDraggable + DragTarget within each sibling group; on drop calculate new fractional index position; update parent_id if dropped under a different parent; enqueue sync op; show drag ghost of bullet row; highlight drop targets*
> *- Web keyboard shortcuts (HardwareKeyboard listener, web platform only): Ctrl+[ collapse node; Ctrl+] expand node; ArrowUp/ArrowDown move focus between bullets; Backspace on empty bullet deletes it and focuses previous*
> *- Integration test in `outliner_flow_test.dart`: reorder two bullets via drag → verify new positions in local DB*
> *Ensure flutter analyze is clean. Commit and push."*

---

### Step 2.4 — Frontend: Attachments UI + All Phase 2 Tests

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-2/gestures-attachments. Implement attachment UI and complete all Phase 2 tests:*
> *- Add to pubspec.yaml: image_picker, file_picker, record*
> *- `features/attachments/providers/attachment_provider.dart`: upload file to POST /attachments; store metadata in local DB; delete via DELETE /attachments/:id*
> *- `features/attachments/widgets/attachment_picker.dart`: bottom sheet with 4 options: Camera (image_picker), Gallery (image_picker), File (file_picker), Audio Record (record package → temp file); on pick → upload → store metadata*
> *- Add attachment picker trigger to context menu Add attachment item*
> *- `features/attachments/widgets/attachment_viewer.dart`: inline image thumbnail below bullet text (tap → full-screen); file chip with filename + type icon; audio player with play/pause button*
> *- Attachments rendered below bullet content, above children in bullet_item*
> *- Widget test `attachment_picker_test.dart`: all 4 options render; mock upload → metadata in local DB; attachment chip renders in bullet_item*
> *- Integration test `gesture_flow_test.dart`: complete via swipe → is_complete in DB; delete via swipe → deleted from DB; undo → re-inserted; reorder via drag → positions updated*
> *Ensure flutter analyze is clean and all flutter test passes. Commit and push."*

---

## Phase 3 — Full-Text Search, Tags, Offline-First Sync

> Goal: Search and tag filtering; fully offline-first with automatic sync on reconnect.
> Branch: `phase-3/search-offline` (branch from main after Phase 2 is merged)

---

### Step 3.1 — Backend: Search + Tags

**Claude Code prompt:**
> *"Read CLAUDE.md. Create branch phase-3/search-offline from main. Implement full-text search and tag APIs:*
> *- Verify FTS5 triggers on bullets table exist in v001_init.sql (AFTER INSERT, AFTER UPDATE, AFTER DELETE → maintain bullets_fts); if missing, add a v002_fts_triggers.sql migration*
> *- `search_service.py`: FTS5 MATCH query with prefix support (query*); join bullets for metadata; join documents for doc name; build parent_path string from ancestors; return list of {bullet_id, document_id, document_title, content_snippet, parent_path}*
> *- `routers/search.py`: GET /search?q=:query&limit=50*
> *- `tag_service.py` additions: GET /tags → list all tags with bullet count (non-deleted bullets only); GET /tags/:name/bullets → all non-deleted bullets with that tag across all docs, ordered by updated_at*
> *- `routers/search.py` additions: GET /tags, GET /tags/:name/bullets*
> *- Integration test `test_search.py`: index 20 bullets across 3 docs → search term present in 5 → verify 5 results with correct doc/parent context; nonexistent term → empty; deleted bullets not returned; prefix search works*
> *- Integration test `test_search.py` (tags section): 10 bullets with #project across 2 docs → tag filter returns all 10; tag count correct; deleted bullet not counted*
> *Ensure ruff passes and pytest coverage ≥ 80% on new files. Commit and push."*

---

### Step 3.2 — Backend: Offline Sync Delta

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-3/search-offline. Upgrade the sync service to support full offline sync:*
> *- `sync_service.py` upgrade: when applying client operations, write each to sync_operations table with server_timestamp = now(); implement server_delta — query sync_operations for all rows with server_timestamp > request.last_sync_at and return them to client; last-write-wins: if same entity_id appears in both client ops and server delta, the one with the higher timestamp wins*
> *- `routers/sync.py` addition: GET /sync/status → return {device_id, last_sync_at} for the calling device*
> *- Unit test `test_sync_service.py`: delta returns only ops after last_sync_at; last_sync_at=0 returns full history; applied ops recorded with server_timestamp; duplicate operation_id idempotent*
> *- Integration test `test_sync.py` offline simulation: device A pushes 10 ops at T1; device B pushes 5 different ops at T2; device A pulls with last_sync_at=T1 → receives B's 5 ops; last-write-wins verified on overlapping entity*
> *Ensure ruff passes and pytest coverage ≥ 80%. Commit and push."*

---

### Step 3.3 — Frontend: Search UI + Tag Filtering

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-3/search-offline. Implement search UI and tag filtering:*
> *- Add shared_preferences to pubspec.yaml*
> *- `core/utils/markdown_parser.dart` update: detect #tags and wrap in TapGestureRecognizer spans; tap navigates to tag filter view*
> *- `features/search/providers/search_provider.dart`: debounced 300ms query against local SQLite FTS5 (no network); returns list of {bullet_id, document_id, document_title, content_snippet, parent_path}*
> *- `features/search/screens/search_screen.dart`: search overlay opened by Ctrl+F (web) or search FAB (mobile); text field with debounced results list; each result shows snippet + doc name + parent path; tap → navigate to document + zoom to bullet parent + highlight bullet; clear button returns to previous view*
> *- `features/search/providers/tags_provider.dart`: query local bullet_tags join; returns all bullets for a given tag*
> *- Tag filter view: same layout as search results but filtered by tag; opened by tapping a #tag span in any bullet*
> *- Widget test `search_screen_test.dart`: type query → results appear; clear → results clear; result tap triggers navigation callback; empty state on no results*
> *Ensure flutter analyze is clean. Commit and push."*

---

### Step 3.4 — Frontend: Offline Sync + All Phase 3 Tests

**Claude Code prompt:**
> *"Read CLAUDE.md. We are on branch phase-3/search-offline. Implement full offline-first sync and complete all Phase 3 tests:*
> *- `core/sync/sync_manager.dart` upgrade: on reconnect, push pending ops AND request server delta since last_sync_at; apply server delta to local DB with last-write-wins (compare server_timestamp vs local updated_at, keep higher); persist last_sync_at and device_id in shared_preferences; generate device_id once on first run (UUID v4) and persist*
> *- `core/sync/connectivity_service.dart` upgrade: watch connectivity_plus stream; on offline→online transition trigger sync flush*
> *- Sync status indicator: subtle icon in AppBar — synced ✓ (green), syncing ↑ (animated), offline ⊘ (grey); driven by SyncNotifier Riverpod provider*
> *- JWT expiry while offline: if JWT is expired on reconnect, attempt POST /auth/refresh first; if refresh succeeds proceed with sync; if refresh fails redirect to login*
> *- Unit test `sync_manager_test.dart` extended: delta merge last-write-wins; newer local wins; newer server wins; connectivity change triggers flush; last_sync_at persisted and updated after successful sync*
> *- Unit test `tag_extraction_test.dart` extended: tappable span for tag; multiple tags in one bullet; tag at start/end of string; tag with numbers*
> *- Integration test `offline_sync_test.dart`: mock connectivity=none → create 3 bullets → mock connectivity=online → verify sync flush called with 3 ops → mock server returns delta with 1 new op → verify local DB updated*
> *- Integration test `search_flow_test.dart`: insert 10 bullets → search → verify result count → tap result → verify navigation to correct doc + bullet highlighted*
> *Ensure flutter analyze is clean and all flutter test passes. Commit and push."*

---

## Testing Strategy Summary

### Backend

| Layer | Tool | Scope |
|---|---|---|
| Unit | `pytest` + `pytest-asyncio` | Services, utilities, business logic in isolation |
| Integration | `pytest` + `httpx.AsyncClient` + in-memory SQLite | Full API endpoints with real DB, mocked Google OAuth |
| Coverage target | `pytest-cov` | ≥ 80% line coverage on `services/` and `utils/` |

```python
# conftest.py pattern
@pytest.fixture
async def db():
    # In-memory SQLite, run migrations, yield, teardown
    ...

@pytest.fixture
async def client(db):
    # httpx.AsyncClient with app, override DB dependency
    ...

@pytest.fixture
def auth_headers():
    # Pre-issued test JWT, bypass Google OAuth
    ...
```

### Frontend

| Layer | Tool | Scope |
|---|---|---|
| Unit | `flutter_test` | Pure Dart: parsers, utils, repositories, providers |
| Widget | `flutter_test` + `mocktail` | Widget rendering, interaction simulation, mock providers |
| Integration | `integration_test` package | Full app flows, real local SQLite, mocked HTTP (Dio adapter) |

### CI

| Trigger | Jobs |
|---|---|
| PR opened/updated | Backend: ruff lint + pytest ≥80% coverage; Flutter: analyze + unit + widget tests |
| Merge to main | All above + Flutter integration tests (Android emulator) |

---

## V2 — Voice, Speech-to-Text, Native Android

> Begins after Phase 3 is stable. Not yet broken into steps.

- Voice activation shortcut (Android foreground service or quick tile)
- Audio recording attached to a new bullet without opening the full app
- On-device speech-to-text (`speech_to_text` Flutter package; server-side Whisper as upgrade)
- Native Android app (Kotlin + Jetpack Compose) replacing Flutter Android shell

**Pre-conditions:** Phase 3 complete and stable; attachment pipeline battle-tested; ADR written for native Android trade-offs.

---

## Dependency Map

```
Phase 0 ✅
    └── Phase 1 (steps 1.1 → 1.7)
            └── Phase 2 (steps 2.1 → 2.4)
                    └── Phase 3 (steps 3.1 → 3.4)
                                └── V2
```

---

## Definition of Done (per phase)

1. All steps committed and pushed
2. All unit, widget, and integration tests pass in CI
3. Feature works end-to-end on Android and web (manual smoke test)
4. No regressions in prior-phase tests
5. PR merged to `main`

---

*Update this document as tasks are completed and new tasks are discovered.*
