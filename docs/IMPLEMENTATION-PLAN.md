# Implementation Plan: Notes App
**Repo:** https://github.com/gmaingret/notes
**Maintained by:** Claude Code
**Last updated:** February 2026

---

## Overview

This document is the authoritative implementation plan for all phases of the Notes app. It is intended to be used directly by Claude Code as a task reference. Each phase is broken down into backend tasks, frontend tasks, and test requirements. No phase should be considered complete until all tests pass.

**Companion docs:**
- `PRD-Notes-App.md` — Product requirements
- `SYSTEM-DESIGN-Notes-App.md` — Architecture and data model

---

## Repository Structure

```
notes/
├── server/                        # Python FastAPI backend
│   ├── app/
│   │   ├── main.py                # FastAPI app init, middleware, router registration
│   │   ├── config.py              # Settings via pydantic-settings (env vars)
│   │   ├── db/
│   │   │   ├── database.py        # aiosqlite connection pool + lifespan setup
│   │   │   └── migrations/        # SQL migration files (v001_init.sql, etc.)
│   │   ├── models/
│   │   │   ├── document.py        # Pydantic request/response models
│   │   │   ├── bullet.py
│   │   │   ├── attachment.py
│   │   │   ├── sync.py
│   │   │   └── auth.py
│   │   ├── routers/
│   │   │   ├── auth.py            # POST /auth/google, POST /auth/refresh
│   │   │   ├── documents.py       # CRUD /documents
│   │   │   ├── bullets.py         # CRUD /bullets
│   │   │   ├── attachments.py     # Upload/serve /attachments
│   │   │   ├── search.py          # GET /search, GET /tags
│   │   │   └── sync.py            # POST /sync
│   │   ├── services/
│   │   │   ├── auth_service.py    # Google token verify, JWT issue/refresh
│   │   │   ├── document_service.py
│   │   │   ├── bullet_service.py
│   │   │   ├── attachment_service.py
│   │   │   ├── search_service.py
│   │   │   ├── sync_service.py
│   │   │   └── tag_service.py     # Tag extraction and indexing
│   │   └── utils/
│   │       ├── fractional_index.py  # Fractional index generation
│   │       └── jwt_utils.py
│   ├── tests/
│   │   ├── conftest.py            # Shared fixtures (test DB, test client, mock auth)
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
│   └── requirements-dev.txt       # pytest, httpx, pytest-asyncio, etc.
│
├── client/                        # Flutter app
│   ├── lib/
│   │   ├── main.dart
│   │   ├── app.dart               # MaterialApp, routing, Riverpod scope
│   │   ├── core/
│   │   │   ├── db/
│   │   │   │   ├── app_database.dart     # drift DB definition
│   │   │   │   ├── tables/               # drift table definitions
│   │   │   │   └── daos/                 # drift DAOs per entity
│   │   │   ├── api/
│   │   │   │   ├── api_client.dart       # Dio HTTP client, JWT injection
│   │   │   │   └── endpoints/            # typed API call wrappers
│   │   │   ├── sync/
│   │   │   │   ├── sync_manager.dart     # operation queue, flush logic
│   │   │   │   └── connectivity_service.dart
│   │   │   └── utils/
│   │   │       ├── fractional_index.dart
│   │   │       └── markdown_parser.dart  # inline Markdown token parser
│   │   └── features/
│   │       ├── auth/
│   │       │   ├── providers/auth_provider.dart
│   │       │   └── screens/login_screen.dart
│   │       ├── documents/
│   │       │   ├── providers/documents_provider.dart
│   │       │   ├── repositories/document_repository.dart
│   │       │   └── widgets/document_sidebar.dart
│   │       ├── bullets/
│   │       │   ├── providers/bullet_tree_provider.dart
│   │       │   ├── repositories/bullet_repository.dart
│   │       │   ├── widgets/
│   │       │   │   ├── bullet_item.dart        # single bullet row
│   │       │   │   ├── bullet_tree.dart        # recursive tree widget
│   │       │   │   ├── bullet_editor.dart      # text field + MD rendering
│   │       │   │   ├── breadcrumb_bar.dart
│   │       │   │   ├── swipe_action_wrapper.dart
│   │       │   │   └── context_menu.dart
│   │       ├── search/
│   │       │   ├── providers/search_provider.dart
│   │       │   └── screens/search_screen.dart
│   │       └── attachments/
│   │           ├── providers/attachment_provider.dart
│   │           └── widgets/
│   │               ├── attachment_picker.dart
│   │               └── attachment_viewer.dart
│   ├── test/
│   │   ├── helpers/               # shared test utilities, mock providers
│   │   ├── unit/
│   │   │   ├── fractional_index_test.dart
│   │   │   ├── markdown_parser_test.dart
│   │   │   ├── tag_extraction_test.dart
│   │   │   ├── sync_manager_test.dart
│   │   │   └── bullet_repository_test.dart
│   │   └── widget/
│   │       ├── bullet_item_test.dart
│   │       ├── bullet_tree_test.dart
│   │       ├── breadcrumb_bar_test.dart
│   │       ├── swipe_action_test.dart
│   │       └── document_sidebar_test.dart
│   ├── integration_test/
│   │   ├── auth_flow_test.dart
│   │   ├── outliner_flow_test.dart    # create doc → bullets → zoom → breadcrumb
│   │   ├── offline_sync_test.dart     # edit offline → reconnect → verify sync
│   │   └── search_flow_test.dart
│   └── pubspec.yaml
│
├── docker-compose.yml
├── docker-compose.dev.yml         # local dev with hot reload
├── .env.example
├── docs/
│   ├── nginx-notes.conf           # nginx block to add to 192.168.1.204
│   ├── PRD-Notes-App.md
│   ├── SYSTEM-DESIGN-Notes-App.md
│   └── IMPLEMENTATION-PLAN.md
├── .github/
│   └── workflows/
│       ├── backend.yml            # lint + unit + integration tests on PR
│       └── frontend.yml           # analyze + unit + widget tests on PR
└── README.md
```

---

## Phase 0 — Repository Setup

> One-time scaffold. Must be complete before any feature work begins.

### Tasks

**Repository**
- [ ] Initialize GitHub repo at `github.com/gmaingret/notes`
- [ ] Create branch protection rule: require PR + passing CI on `main`
- [ ] Add `.gitignore` (Python, Flutter, Docker, `.env`)
- [ ] Add `.env.example` with all required environment variables documented

**Backend scaffold**
- [ ] Create `server/` Python project with `pyproject.toml` or `requirements.txt`
- [ ] Install: `fastapi`, `uvicorn`, `aiosqlite`, `python-jose`, `google-auth`, `pydantic-settings`, `python-multipart`
- [ ] Install dev: `pytest`, `pytest-asyncio`, `httpx`, `anyio`
- [ ] Implement `config.py` — load all settings from environment variables
- [ ] Implement `database.py` — async SQLite connection, migration runner on startup
- [ ] Write `v001_init.sql` — full schema from system design (all tables + FTS5 + indexes)
- [ ] Implement `main.py` — FastAPI app, CORS middleware, router registration, lifespan hook

**Frontend scaffold**
- [ ] Create Flutter project with `flutter create --org com.gmaingret client`
- [ ] Add dependencies to `pubspec.yaml`: `riverpod`, `flutter_riverpod`, `drift`, `sqflite`/`sqlite3_flutter_libs`, `dio`, `google_sign_in`, `go_router`, `connectivity_plus`
- [ ] Add dev dependencies: `build_runner`, `drift_dev`, `mocktail`
- [ ] Generate drift DB skeleton with empty tables
- [ ] Configure `go_router` with initial routes: `/login`, `/documents`, `/documents/:id`
- [ ] Configure Riverpod `ProviderScope` at app root

**Docker**
- [ ] Write `Dockerfile` for FastAPI server (multi-stage: build → slim runtime; bundle Flutter web build into image at `/app/web`)
- [ ] Write `docker-compose.yml` with single `app` service exposing port 8000; named volume for data
- [ ] Write `docker-compose.dev.yml` with hot-reload volume mounts and a `--reload` uvicorn flag
- [ ] Write `nginx-notes.conf` (in `docs/`) with the proxy config block to add to the existing nginx at 192.168.1.204 — **not deployed automatically; Greg adds manually**

**CI/CD**
- [ ] `.github/workflows/backend.yml`: on PR → `ruff` lint → `pytest` (unit + integration)
- [ ] `.github/workflows/frontend.yml`: on PR → `flutter analyze` → `flutter test` (unit + widget)

---

## Phase 1 — Core Outliner + Auth + Basic Online Sync

> Goal: A working outliner with login, documents, nested bullets, zoom, breadcrumb, and real-time (online) sync. No offline support yet.

### Backend Tasks

**Auth**
- [ ] `auth_service.py`: verify Google ID token using `google-auth` library; extract `sub`, `email`, `name`
- [ ] `auth_service.py`: upsert user into `users` table on first login
- [ ] `jwt_utils.py`: issue 24h server JWT (`python-jose`); validate on every request via FastAPI dependency
- [ ] `auth_service.py`: JWT refresh endpoint — reissue if token age < 48h (grace period)
- [ ] `routers/auth.py`: `POST /auth/google`, `POST /auth/refresh`

**Documents API**
- [ ] `document_service.py`: create, list, rename, soft-delete documents
- [ ] `document_service.py`: fractional index position management for sidebar ordering
- [ ] `routers/documents.py`: `GET /documents`, `POST /documents`, `PATCH /documents/:id`, `DELETE /documents/:id`

**Bullets API**
- [ ] `fractional_index.py`: utility to generate position strings between two existing positions (`before`, `after`, `between`)
- [ ] `bullet_service.py`: create bullet (with document_id, parent_id, position)
- [ ] `bullet_service.py`: update bullet content, is_complete, position, parent_id
- [ ] `bullet_service.py`: soft-delete bullet (set `deleted_at`); cascade soft-delete children
- [ ] `bullet_service.py`: `GET /documents/:id/bullets` — fetch full tree (all non-deleted bullets for document, ordered by position, returned as flat list for client to build tree)
- [ ] `tag_service.py`: extract `#tags` from bullet content via regex; sync `tags` + `bullet_tags` tables on every bullet upsert
- [ ] `routers/bullets.py`: `GET /documents/:id/bullets`, `POST /bullets`, `PATCH /bullets/:id`, `DELETE /bullets/:id`

**Sync (Phase 1 — online only)**
- [ ] `sync_service.py`: basic `POST /sync` that applies client operations sequentially by `client_timestamp`; returns empty `server_delta` (delta pull deferred to Phase 3)
- [ ] Idempotency: if `operation.id` already exists in `sync_operations`, skip without error

**Cleanup job**
- [ ] Background task (FastAPI `BackgroundTasks` or APScheduler): purge records where `deleted_at` is older than 60 seconds from `bullets`, `documents`, `attachments`

### Backend Tests — Phase 1

**Unit tests**
- [ ] `test_fractional_index.py`: generate first position, insert before/after/between, handle edge cases (empty list, insert at start/end), verify lexicographic ordering holds for 1000 sequential inserts
- [ ] `test_jwt_utils.py`: issue token, validate token, reject expired token, reject tampered signature, test refresh grace period logic
- [ ] `test_tag_extraction.py`: extract tags from plain text, text with multiple tags, tags with numbers/hyphens, no false positives on URLs, case normalization (stored lowercase)
- [ ] `test_bullet_service.py`: create bullet, update content, move to new parent, soft-delete (verify children also soft-deleted), cascade test with 3-level nesting

**Integration tests** (real in-memory SQLite, real HTTP via `httpx.AsyncClient`)
- [ ] `test_auth.py`: valid Google token → JWT issued; invalid token → 401; expired JWT → 401; refresh within grace → new JWT; refresh outside grace → 401
- [ ] `test_documents.py`: CRUD lifecycle (create, rename, reorder, delete); list returns correct order; deleted document not returned; 404 on unknown ID
- [ ] `test_bullets.py`: create root bullet; create child bullet; fetch tree (verify parent/child relationship and position ordering); update content + verify tag sync; soft-delete bullet + verify children deleted; move bullet to new parent
- [ ] `test_sync.py`: push 5 operations → all applied; push duplicate operation_id → idempotent; timestamp ordering verified; auth required

### Frontend Tasks — Phase 1

**Auth**
- [ ] `auth_provider.dart`: `GoogleSignIn` flow → send ID token to `POST /auth/google` → store JWT in `flutter_secure_storage`
- [ ] Auto-login on app start if stored JWT is valid
- [ ] JWT injected into all API requests via Dio interceptor
- [ ] Unauthenticated redirect to `/login`

**Local DB (drift)**
- [ ] Define drift tables: `DocumentsTable`, `BulletsTable`, `SyncOperationsTable`
- [ ] Generate drift code via `build_runner`
- [ ] Implement DAOs: `DocumentDao` (list, insert, update, delete), `BulletDao` (tree fetch by document, insert, update, delete)

**Documents**
- [ ] `document_repository.dart`: write to local DB + enqueue sync operation
- [ ] `documents_provider.dart`: Riverpod `AsyncNotifier` watching local DB
- [ ] `document_sidebar.dart`: list of documents; tap to navigate; create/rename via dialog; delete with confirmation

**Bullets — tree rendering**
- [ ] `bullet_repository.dart`: load flat list from local DB → build in-memory tree; write mutations to local DB + enqueue sync op
- [ ] `bullet_tree_provider.dart`: reactive notifier for open document tree
- [ ] `bullet_tree.dart`: recursive `ListView`/`CustomScrollView` rendering the tree with proper indentation (indent = 16px per level)
- [ ] `bullet_item.dart`: single row with bullet glyph, text field, expand/collapse toggle

**Bullet editor**
- [ ] `bullet_editor.dart`: `TextField` with custom `TextSpan` painter
- [ ] `markdown_parser.dart`: regex-based tokenizer for `**bold**`, `*italic*`, `` `code` ``, `[text](url)`; returns token list with spans
- [ ] Live WYSIWYG: render formatted spans when cursor is outside token; show raw syntax when cursor is inside token
- [ ] Enter key: create sibling bullet; Tab: indent; Shift+Tab: outdent; Ctrl+Z: undo; Ctrl+Y: redo (web)

**Zoom + Breadcrumb**
- [ ] `bullet_tree_provider.dart`: track `zoomedNodeId` (null = document root)
- [ ] Zoom: double-tap bullet glyph or "zoom" button in context menu → set `zoomedNodeId`; tree re-renders from that node as root
- [ ] `breadcrumb_bar.dart`: horizontal scrollable row showing path from document root to current zoom; each crumb is tappable to zoom to that level; document title is always the first crumb

**Basic Sync Manager (Phase 1 — online only)**
- [ ] `sync_manager.dart`: on every mutation, POST /sync with pending operations queue; on success clear applied ops from queue
- [ ] `connectivity_service.dart`: expose connectivity stream using `connectivity_plus`
- [ ] Initial full pull: on first login or new device, `GET /documents` + `GET /documents/:id/bullets` for each document → populate local DB

### Frontend Tests — Phase 1

**Unit tests**
- [ ] `fractional_index_test.dart`: mirror of backend unit tests — same edge cases
- [ ] `markdown_parser_test.dart`: bold, italic, code, link, nested (bold + italic), cursor-inside vs cursor-outside behaviour, no false positives
- [ ] `bullet_repository_test.dart`: build tree from flat list, insert child, move node to new parent, delete node + children
- [ ] `sync_manager_test.dart`: mutation enqueues operation; successful POST clears queue; failed POST retains queue; idempotency on retry

**Widget tests**
- [ ] `bullet_item_test.dart`: renders content; collapses/expands children; bullet glyph tap triggers callback
- [ ] `breadcrumb_bar_test.dart`: renders correct crumbs for 3-level zoom; tap crumb triggers zoom change; scrolls horizontally when overflow
- [ ] `document_sidebar_test.dart`: lists documents; tap navigates; create dialog appears; delete confirmation appears

**Integration tests**
- [ ] `auth_flow_test.dart`: mock Google sign-in → JWT stored → protected screen accessible; logout → redirected to login
- [ ] `outliner_flow_test.dart`: create document → add root bullet → add child bullet (Tab) → verify indentation → zoom into child → verify breadcrumb shows path → tap breadcrumb to zoom out

---

## Phase 2 — Mobile Gestures, Drag & Drop, Attachments

> Goal: Full mobile interaction model + file/image/audio attachments.

### Backend Tasks

**Attachments API**
- [ ] `attachment_service.py`: save uploaded file to `ATTACHMENTS_PATH/{uuid}.{ext}`; insert record into `attachments` table
- [ ] `attachment_service.py`: serve file (stream from disk); enforce that attachment belongs to authenticated user
- [ ] `attachment_service.py`: delete attachment — remove DB record + delete file from disk
- [ ] `routers/attachments.py`: `POST /attachments` (multipart), `GET /attachments/:id/file`, `DELETE /attachments/:id`
- [ ] Sync: include attachment metadata in bullet payload (not binary); binary uploaded separately after sync

### Backend Tests — Phase 2

**Unit tests**
- [ ] `test_attachment_service.py`: file saved to correct path, filename sanitized, mime type validated, record inserted correctly, delete removes both DB row and file, missing file on delete handled gracefully

**Integration tests**
- [ ] `test_attachments.py`: upload image → retrieve → verify bytes match; upload audio → retrieve; delete → 404 on subsequent GET; attempt to access attachment of another user → 403 (auth check)

### Frontend Tasks — Phase 2

**Swipe gestures**
- [ ] `swipe_action_wrapper.dart`: wraps `bullet_item.dart`; `Dismissible`-style horizontal drag detection
- [ ] Swipe right (> 50% threshold): mark complete — set `is_complete = true` in local DB, apply strikethrough + dim styling, enqueue sync op
- [ ] Swipe left (> 50% threshold): permanently delete — remove from local DB, enqueue sync op, show `SnackBar` with 5s undo; if undo tapped → re-insert at same position with same ID
- [ ] Visual feedback during swipe: reveal coloured background (green right, red left) with icon

**Long-press context menu**
- [ ] `context_menu.dart`: modal bottom sheet triggered by long-press on bullet glyph (●)
- [ ] Menu items: Indent, Outdent, Move to document (submenu with document list), Add attachment, Duplicate, Delete
- [ ] "Move to document": opens document picker sheet; on select → update `document_id` + reset `parent_id` to null (moves to root of target document); enqueue sync op

**Long-press drag-to-reorder**
- [ ] Long-press on bullet text → activate drag handle (animated, appears on left)
- [ ] Use Flutter `ReorderableListView` within each sibling group, or custom drag with `LongPressDraggable` + `DragTarget`
- [ ] On drop: calculate new fractional index position; update `parent_id` if dropped under a different parent; enqueue sync op
- [ ] Visual: drag ghost of bullet row; drop targets highlighted

**Web keyboard shortcuts (complete)**
- [ ] `Ctrl+[` / `Ctrl+]`: collapse / expand current node (web only, via `HardwareKeyboard` listener)
- [ ] Arrow keys: move focus between bullets without mouse
- [ ] `Backspace` on empty bullet: delete and focus previous bullet

**Attachments**
- [ ] `attachment_picker.dart`: bottom sheet with options — Camera, Gallery, File, Audio Record
- [ ] Camera/Gallery: `image_picker` package → upload via `POST /attachments` → store metadata locally
- [ ] File: `file_picker` package → upload via `POST /attachments` → store metadata locally
- [ ] Audio: `record` package → record to temp file → upload → store metadata locally
- [ ] `attachment_viewer.dart`: inline image thumbnail (tap to full-screen); file chip with name + icon; audio player with waveform visualizer + play/pause
- [ ] Attachments displayed below bullet text, above children

### Frontend Tests — Phase 2

**Unit tests**
- [ ] `swipe_action_test.dart` (unit): threshold calculation, undo state management, re-insert logic after undo

**Widget tests**
- [ ] `swipe_action_test.dart` (widget): swipe right → complete styling applied; swipe left → item removed + snackbar shown; undo tap → item reappears
- [ ] `context_menu_test.dart`: all menu items render; indent/outdent callbacks fire; delete triggers confirmation
- [ ] `attachment_picker_test.dart`: picker options render; mock upload → metadata stored in local DB; attachment chip renders

**Integration tests**
- [ ] `gesture_flow_test.dart`: complete a bullet via swipe → verify is_complete in DB; delete via swipe → verify deleted from DB; undo → verify re-inserted; reorder two bullets via drag → verify positions updated

---

## Phase 3 — Full-Text Search, Tags, Offline-First Sync

> Goal: Search and tag filtering fully functional; app works completely offline with automatic sync on reconnect.

### Backend Tasks

**Full-text search**
- [ ] Add SQLite FTS5 triggers: `AFTER INSERT`, `AFTER UPDATE`, `AFTER DELETE` on `bullets` → maintain `bullets_fts`
- [ ] `search_service.py`: FTS5 `MATCH` query with prefix support (`query*`); join to `bullets` for metadata; join to `documents` for doc name; return result list with `bullet_id`, `document_id`, `document_title`, `parent_path` (ancestors as string)
- [ ] `routers/search.py`: `GET /search?q=:query&limit=50`

**Tags**
- [ ] `tag_service.py`: `GET /tags` — list all tags with bullet count; `GET /tags/:name/bullets` — all bullets (across all docs) with that tag, non-deleted, ordered by updated_at
- [ ] `routers/search.py`: `GET /tags`, `GET /tags/:name/bullets`

**Sync — full offline support**
- [ ] `sync_service.py`: implement `server_delta` — query `sync_operations` for all operations with `server_timestamp > last_sync_at`; return to client
- [ ] `sync_service.py`: write all applied server-side operations to `sync_operations` table with `server_timestamp = now()`
- [ ] `sync_service.py`: `GET /sync/status` — return last sync timestamp per device

### Backend Tests — Phase 3

**Unit tests**
- [ ] `test_sync_service.py`: delta returns only ops after `last_sync_at`; `last_sync_at = 0` returns full history; applied ops recorded with server_timestamp; idempotency of duplicate operations

**Integration tests**
- [ ] `test_search.py`: index 20 bullets across 3 documents → search for term present in 5 → verify 5 results with correct doc/parent context; search for nonexistent term → empty results; special characters handled; deleted bullets not returned
- [ ] `test_search.py` (tags): 10 bullets with `#project` across 2 docs → tag filter returns all 10; tag count correct; deleted bullet removed from tag results
- [ ] `test_sync.py` (offline simulation): device A pushes 10 ops at T1; device B pushes 5 different ops at T2 (no overlap); device A pulls delta → receives B's 5 ops; last-write-wins verified on same-entity overlap scenario

### Frontend Tasks — Phase 3

**Full-text search UI**
- [ ] `Ctrl+F` (web) / FAB search icon (mobile) → open search overlay
- [ ] `search_provider.dart`: debounced (300ms) query against **local SQLite FTS5** — no network request
- [ ] Results list: bullet text preview, document name, parent path breadcrumb; tap → navigate to document + zoom to bullet's parent + highlight bullet
- [ ] Clear search → return to previous view

**Tag filtering**
- [ ] `markdown_parser.dart`: extend to detect and style `#tags` as tappable `TapGestureRecognizer` spans
- [ ] Tap tag → open tag filter view (same layout as search results) showing all bullets with that tag across all documents
- [ ] `tags_provider.dart`: query local `bullet_tags` join for instant results

**Offline-first sync**
- [ ] `sync_manager.dart`: upgrade — on reconnect, push pending ops AND request server delta since `last_sync_at`
- [ ] Apply server delta to local DB: for each operation, compare `server_timestamp` vs local `updated_at`; take the higher timestamp (last-write-wins)
- [ ] `connectivity_service.dart`: watch network state changes; trigger sync flush on transition from offline → online
- [ ] Sync status indicator in UI: subtle icon in app bar (synced ✓ / syncing ↑ / offline ⊘)
- [ ] Persist `last_sync_at` and `device_id` in local preferences (`shared_preferences`)
- [ ] Handle JWT expiry while offline: continue working; on reconnect attempt token refresh before sync

### Frontend Tests — Phase 3

**Unit tests**
- [ ] `tag_extraction_test.dart` (extended): tappable span generated for tag; multiple tags in one bullet; tag at start/end of string; tag with numbers
- [ ] `sync_manager_test.dart` (extended): delta merge with last-write-wins; newer local wins; newer server wins; connectivity change triggers flush; `last_sync_at` persisted and updated after successful sync

**Widget tests**
- [ ] `search_screen_test.dart`: type query → results appear; clear → results clear; result tap triggers navigation; empty state shown on no results

**Integration tests**
- [ ] `offline_sync_test.dart`: simulate offline (mock connectivity = none) → create 3 bullets → simulate reconnect → verify sync flush called with 3 ops → mock server returns delta with 1 external op → verify local DB updated
- [ ] `search_flow_test.dart`: insert 10 bullets → search → verify result count → tap result → verify navigation to correct document + bullet highlighted

---

## Testing Strategy Summary

### Backend

| Layer | Tool | Scope |
|---|---|---|
| Unit | `pytest` + `pytest-asyncio` | Services, utilities, business logic in isolation |
| Integration | `pytest` + `httpx.AsyncClient` + temp SQLite | Full API endpoints with real DB, mocked Google OAuth |
| Coverage target | `pytest-cov` | ≥ 80% line coverage on `services/` and `utils/` |

**Key fixture pattern:**
```python
# conftest.py
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
| Unit | `flutter_test` | Pure Dart logic: parsers, utils, repositories, providers |
| Widget | `flutter_test` + `mocktail` | Widget rendering, user interaction simulation, mock providers |
| Integration | `integration_test` package | Full app flows on device/emulator, real local SQLite |

**Key mock pattern:**
```dart
// Use mocktail to mock repositories/API in unit + widget tests
// Integration tests use a real local DB but mock the HTTP layer (Dio adapter)
// This lets integration tests verify offline → online sync without a real server
```

### CI Matrix

| Trigger | Jobs |
|---|---|
| PR opened/updated | Backend lint + unit + integration; Flutter analyze + unit + widget |
| Merge to `main` | All of above + Flutter integration tests (Android emulator via GitHub Actions) |

---

## V2 — Voice, Speech-to-Text, Native Android

> Planned but not yet specced in detail. Implementation begins after Phase 3 is stable.

### Scope
- Voice activation shortcut (Android foreground service or quick tile)
- Audio recording attached to a new bullet without opening the app fully
- On-device speech-to-text transcription (`speech_to_text` Flutter package as first pass; server-side Whisper as upgrade option)
- Native Android app (Kotlin + Jetpack Compose) to replace the Flutter Android shell — motivated by better foreground service support and system integration for voice features

### Pre-conditions for V2
- Phase 3 fully complete and stable (offline sync proven reliable)
- Attachment upload/download pipeline battle-tested from Phase 2
- Architecture Decision Record (ADR) written for native Android trade-offs

---

## Dependency Map

```
Phase 0 (scaffold)
    └── Phase 1 (core outliner + auth + basic sync)
            └── Phase 2 (gestures + attachments)
                    └── Phase 3 (search + tags + offline sync)
                                └── V2 (voice + native)
```

Each phase depends on the prior phase being fully tested and merged to `main`.

---

## Definition of Done (per phase)

A phase is complete when:
1. All tasks in the phase are implemented
2. All unit, widget, and integration tests listed for the phase pass in CI
3. The feature works end-to-end on both Android and web (manual smoke test)
4. No regressions in prior-phase tests
5. Code is merged to `main` via PR

---

*This document should be updated as tasks are completed and new tasks are discovered during implementation.*
