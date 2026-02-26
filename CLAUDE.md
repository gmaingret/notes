# CLAUDE.md — Notes App

> This file is read automatically by Claude Code at session start.
> It contains all the context needed to work on this project without additional explanation.

---

## What This Project Is

A self-hosted personal outliner app (Dynalist/WorkFlowy clone) built for single-user use. Infinite nested bullet lists, zoom with breadcrumb navigation, offline-first sync, and full mobile gesture support. Runs as a Docker container behind an existing nginx reverse proxy on a home LAN.

**Full product and architecture docs are in `docs/`:**
- `docs/PRD-Notes-App.md` — what to build and why
- `docs/SYSTEM-DESIGN-Notes-App.md` — architecture, data model, API design
- `docs/IMPLEMENTATION-PLAN.md` — authoritative task list, phase by phase
- `docs/API-CONTRACTS.md` — full HTTP API contracts (request/response shapes)

Read the relevant doc before starting any phase. Do not guess at requirements — they are fully specified.

---

## Stack (locked in — do not change without explicit instruction)

| Layer | Technology |
|---|---|
| Backend language | Python 3.12 |
| Backend framework | FastAPI + uvicorn |
| Backend DB | SQLite via `aiosqlite` |
| Backend ORM/query | Raw SQL with `aiosqlite` (no SQLAlchemy) |
| Auth | Google OAuth 2.0 → server-issued JWT (`python-jose`) |
| Frontend | Flutter 3.x (single codebase for Android + web) |
| State management | Riverpod 2.x |
| Local DB (Android) | `sqflite` + `drift` |
| Local DB (Web) | `sqlite3` WASM + OPFS + `drift` |
| HTTP client | `dio` |
| Routing | `go_router` |
| Reverse proxy | Existing nginx at 192.168.1.204 (not in Docker) |
| Containers | Docker Compose (single `app` service) |

---

## Repository Layout

```
notes/
├── CLAUDE.md                        ← you are here
├── README.md
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── .github/
│   └── workflows/
│       ├── backend.yml              ← CI: ruff lint + pytest (80% coverage required)
│       ├── frontend.yml             ← CI: build_runner, flutter analyze --fatal-infos, flutter test
│       ├── claude.yml               ← Claude Code GitHub Actions (@claude mentions)
│       └── claude-code-review.yml   ← Claude Code automated PR review
├── docs/
│   ├── PRD-Notes-App.md
│   ├── SYSTEM-DESIGN-Notes-App.md
│   ├── IMPLEMENTATION-PLAN.md
│   ├── API-CONTRACTS.md
│   └── nginx-notes.conf
├── server/                          ← Python FastAPI backend
│   ├── app/
│   │   ├── main.py                  ← FastAPI app, CORS, lifespan, router registration, static files
│   │   ├── config.py                ← pydantic-settings (reads .env)
│   │   ├── db/
│   │   │   ├── database.py          ← aiosqlite connection, migration runner, get_test_db
│   │   │   └── migrations/
│   │   │       ├── v001_init.sql    ← full schema: all tables, FTS5 virtual table, sync triggers
│   │   │       └── v002_attachments_user_id.sql  ← adds user_id to attachments
│   │   ├── models/
│   │   │   ├── auth.py
│   │   │   ├── bullet.py
│   │   │   ├── document.py
│   │   │   ├── attachment.py
│   │   │   └── sync.py
│   │   ├── routers/
│   │   │   ├── auth.py              ← POST /auth/google, POST /auth/refresh
│   │   │   ├── documents.py         ← GET/POST /documents, PATCH/DELETE /documents/:id
│   │   │   ├── bullets.py           ← GET /documents/:id/bullets, POST/PATCH/DELETE /bullets/:id
│   │   │   ├── attachments.py       ← POST /attachments, GET /attachments/:id/file, DELETE
│   │   │   └── sync.py              ← POST /sync
│   │   ├── services/
│   │   │   ├── auth_service.py
│   │   │   ├── bullet_service.py
│   │   │   ├── document_service.py
│   │   │   ├── attachment_service.py
│   │   │   ├── sync_service.py
│   │   │   └── tag_service.py
│   │   └── utils/
│   │       ├── fractional_index.py  ← must match client/lib/core/utils/fractional_index.dart exactly
│   │       └── jwt_utils.py
│   ├── tests/
│   │   ├── conftest.py              ← db, client, auth_headers, mock_google_token fixtures
│   │   ├── unit/
│   │   │   ├── test_bullet_service.py
│   │   │   ├── test_database.py
│   │   │   ├── test_fractional_index.py
│   │   │   ├── test_jwt_utils.py
│   │   │   ├── test_migrations.py
│   │   │   └── test_tag_extraction.py
│   │   └── integration/
│   │       ├── test_health.py
│   │       ├── test_auth.py
│   │       ├── test_bullets.py
│   │       ├── test_documents.py
│   │       ├── test_attachments.py
│   │       └── test_sync.py
│   ├── Dockerfile                   ← two-stage build: Flutter web (stage 1) + Python (stage 2)
│   ├── pyproject.toml               ← ruff config (line-length=100, rules: E,F,I,UP)
│   ├── pytest.ini                   ← asyncio_mode=auto, testpaths=tests
│   ├── requirements.txt
│   └── requirements-dev.txt
└── client/                          ← Flutter app
    ├── lib/
    │   ├── main.dart                ← entry point: opens Drift DB, wraps in ProviderScope
    │   ├── app.dart                 ← GoRouter with auth redirect, NotesApp widget
    │   ├── core/
    │   │   ├── api/
    │   │   │   └── api_client.dart  ← Dio builder, JWT interceptor, 401 → logout callback
    │   │   ├── db/
    │   │   │   ├── app_database.dart       ← Drift DB class (schema v2)
    │   │   │   ├── app_database.g.dart     ← generated — do not edit by hand
    │   │   │   ├── database_provider.dart  ← Riverpod provider for AppDatabase
    │   │   │   ├── connection.dart
    │   │   │   ├── connection_native.dart  ← sqflite backend (Android)
    │   │   │   ├── connection_web.dart     ← sqlite3 WASM + OPFS backend (Web)
    │   │   │   ├── tables/                ← Drift table definitions
    │   │   │   └── daos/                  ← DocumentDao, BulletDao, AttachmentDao, SyncOperationDao
    │   │   ├── sync/
    │   │   │   ├── sync_manager.dart      ← offline queue, server flush, last-write-wins merge
    │   │   │   └── connectivity_service.dart
    │   │   └── utils/
    │   │       ├── fractional_index.dart  ← must match server/app/utils/fractional_index.py exactly
    │   │       └── markdown_parser.dart   ← live WYSIWYG markdown rendering
    │   └── features/
    │       ├── auth/
    │       │   ├── providers/auth_provider.dart
    │       │   └── screens/login_screen.dart
    │       ├── documents/
    │       │   ├── providers/
    │       │   ├── repositories/
    │       │   ├── screens/             ← documents_screen.dart, document_detail_screen.dart
    │       │   └── widgets/
    │       ├── bullets/
    │       │   ├── providers/
    │       │   ├── repositories/
    │       │   └── widgets/             ← bullet_tree, bullet_item, bullet_editor, breadcrumb_bar,
    │       │                            ←   swipe_action_wrapper, context_menu, gesture_flow
    │       └── attachments/
    │           ├── providers/
    │           └── widgets/             ← attachment_picker, attachment_viewer
    ├── test/
    │   ├── unit/
    │   └── widget/
    ├── integration_test/
    ├── analysis_options.yaml
    └── pubspec.yaml
```

---

## Environment Variables

Copy `.env.example` to `.env` before running anything. Never commit `.env`.

```
GOOGLE_CLIENT_ID=        # from Google Cloud Console OAuth 2.0 credentials
GOOGLE_CLIENT_SECRET=    # same
JWT_SECRET=              # random 32+ char string, generate with: openssl rand -hex 32
DB_PATH=/data/notes.db
ATTACHMENTS_PATH=/data/files
WEB_BUILD_PATH=/app/web  # path to Flutter web build inside container
```

**`config.py` defaults** (safe for local dev, not production):
- `JWT_SECRET`: `dev-secret-change-in-production`
- `JWT_EXPIRY_HOURS`: `24`
- `JWT_REFRESH_GRACE_HOURS`: `48`
- `CORS_ORIGINS`: `["*"]`

---

## Running Locally (Development)

### Backend

```bash
cd server
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt -r requirements-dev.txt

cp ../.env.example ../.env       # fill in real values or leave defaults for dev

# Run with hot reload
uvicorn app.main:app --reload --port 8000
```

The server runs all SQL migrations automatically on startup (`v001_init.sql`, `v002_attachments_user_id.sql`, ...). No manual migration step needed. The `schema_migrations` table tracks which versions have been applied.

### Docker (dev mode with hot reload)

```bash
cp .env.example .env  # fill in values
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
# ./server/app is mounted into the container — server restarts on source changes
```

### Flutter (Android emulator)

```bash
cd client
flutter pub get
dart run build_runner build --delete-conflicting-outputs  # generates Drift + Riverpod code
flutter run
# Default API base URL: http://10.0.2.2:8000 (Android emulator loopback)
```

Override the API URL for a real device or different host:
```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.204:8000
```

### Flutter (Web)

```bash
cd client
flutter pub get
dart run build_runner build --delete-conflicting-outputs

# Download WASM assets required for web sqlite3 + drift (do this once)
curl -fL https://github.com/simolus3/sqlite3.dart/releases/download/sqlite3-2.9.4/sqlite3.wasm \
     -o web/sqlite3.wasm
curl -fL https://github.com/simolus3/drift/releases/download/drift-2.28.2/drift_worker.js \
     -o web/drift_worker.js

flutter run -d chrome --web-port 3000 \
  --dart-define=GOOGLE_CLIENT_ID=<your-client-id> \
  --dart-define=API_BASE_URL=http://localhost:8000
```

**Web sign-in** uses the Google Identity Services credential flow (ID token), not the access token flow. `GOOGLE_CLIENT_ID` must be passed via `--dart-define` at run/build time.

---

## Running Tests

### Backend

```bash
cd server

# All tests
pytest

# Subset
pytest tests/unit/
pytest tests/integration/

# With coverage (CI requires ≥ 80%)
pytest --cov=app --cov-report=term-missing --cov-fail-under=80

# Lint (must pass before any push)
ruff check app/ tests/
```

**Test infrastructure** (`tests/conftest.py`):
- `db` — fresh in-memory SQLite with all migrations applied, function-scoped
- `client` — `httpx.AsyncClient` wired to FastAPI with test DB injected via `dependency_overrides[get_db]`
- `auth_headers` — pre-issued test JWT (`{"sub": "test-user-001", ...}`), bypasses Google OAuth entirely
- `mock_google_token` — fake Google token string for mocking auth endpoints

Google OAuth is never called during tests. `asyncio_mode = auto` (set in `pytest.ini`) means all `async def test_*` functions run with pytest-asyncio automatically — no `@pytest.mark.asyncio` decorator needed.

### Flutter

```bash
cd client

# Regenerate if .g.dart files are stale
dart run build_runner build --delete-conflicting-outputs

# Unit + widget tests
flutter test test/

# Static analysis — must be completely clean
# WARNING: CI uses --fatal-infos, meaning info-level findings fail the build
flutter analyze --fatal-infos

# Integration tests (requires running emulator/device)
flutter test integration_test/
```

---

## CI (GitHub Actions)

### Backend CI (`.github/workflows/backend.yml`)

Triggers on PRs touching `server/**`.

1. Python 3.12 setup
2. `pip install -r requirements.txt -r requirements-dev.txt`
3. `ruff check app/ tests/` — zero warnings allowed
4. `pytest --cov=app --cov-report=term-missing --cov-fail-under=80` — ≥ 80% coverage required

CI sets `JWT_SECRET=test-ci-secret` and `DB_PATH=:memory:`.

### Frontend CI (`.github/workflows/frontend.yml`)

Triggers on PRs touching `client/**`.

1. Flutter stable (3.x)
2. `flutter pub get`
3. `dart run build_runner build --delete-conflicting-outputs` — generates Drift + Riverpod code
4. `flutter analyze --fatal-infos` — zero warnings/infos allowed
5. `flutter test test/` — all unit + widget tests must pass

### Claude Code Integration

- `claude.yml` — invokes Claude Code on issues/PR comments/reviews tagged `@claude`
- `claude-code-review.yml` — automated Claude Code PR review

---

## Code Style

### Python

- **Tool**: `ruff` (config in `server/pyproject.toml`)
- Target: Python 3.12, line length 100
- Active rule sets: `E` (pycodestyle errors), `F` (pyflakes), `I` (isort), `UP` (pyupgrade)
- Run: `ruff check app/ tests/`

### Dart/Flutter

- **Tool**: `flutter_lints` v5 (`client/analysis_options.yaml`)
- Run: `flutter analyze --fatal-infos` — info-level findings are treated as errors in CI
- Generated files (`*.g.dart`) are committed to the repo. Regenerate with:
  `dart run build_runner build --delete-conflicting-outputs`

---

## Architecture Constraints (do not violate)

1. **Offline-first**: All writes go to local SQLite immediately. Server sync is always secondary and asynchronous. Never block the UI on a network call.
2. **Single user**: No multi-user auth, no row-level security, no tenant isolation needed. Keep it simple.
3. **Last-write-wins sync**: Conflict resolution is by `client_timestamp`. No conflict UI. Do not add complexity here.
4. **No external services**: No Redis, no Celery, no separate search engine, no message queue. SQLite FTS5 for search. Everything runs in one container.
5. **Permanent deletes**: Swipe-left on a bullet is permanent after the 5s undo snackbar. No trash/archive table.
6. **Global tags**: `#tags` are scoped globally across all documents. No per-document tag scope.
7. **Raw SQL only**: Use `aiosqlite` with raw SQL strings. No SQLAlchemy ORM. Keeps the backend thin and transparent.

---

## Key Data Model Facts

### Backend (SQLite via aiosqlite)

**Tables** (defined in `server/app/db/migrations/v001_init.sql`):
- `users` — Google `sub` as PK, email, name, avatar_url
- `documents` — UUID PK, title, fractional index `position`, soft-delete `deleted_at`
- `bullets` — UUID PK, `document_id`, `parent_id` (nullable = root), `content`, `position`, `is_complete`, soft-delete `deleted_at`
- `tags` — unique lowercase names (without `#`)
- `bullet_tags` — join table (bullet_id, tag_id)
- `attachments` — bullet_id, type (`image|file|audio`), filename, storage_path, `user_id` (added in v002)
- `sync_operations` — offline operation queue (device_id, operation_type, entity_type, payload, client_timestamp)
- `bullets_fts` — FTS5 virtual table (content, bullet_id, document_id); `tokenize = 'unicode61 remove_diacritics 2'`

**Key facts:**
- **Tree storage**: Adjacency list — each bullet has `parent_id` and `position`
- **Fractional index**: Lexicographically sortable strings. `server/app/utils/fractional_index.py` and `client/lib/core/utils/fractional_index.dart` must produce identical output for the same inputs
- **Soft deletes**: `deleted_at` timestamp (ms). Background cleanup task in `main.py` hard-deletes rows where `deleted_at < now - 60s`, runs every 30s. Always filter `WHERE deleted_at IS NULL`
- **FTS5 sync**: Maintained automatically by triggers `bullets_fts_insert`, `bullets_fts_update`, `bullets_fts_delete`. Do not insert into `bullets_fts` manually
- **Tags**: Extracted via regex on every bullet upsert; stored lowercase without `#`; global scope
- **Migrations**: SQL files in `server/app/db/migrations/`, applied in alphabetical order at startup, tracked in `schema_migrations` table

### Client (Drift local DB)

- `AppDatabase` schema version: **2**
  - v1→v2 migration: `m.createTable(attachmentsTable)`
- Tables: `DocumentsTable`, `BulletsTable`, `SyncOperationsTable`, `AttachmentsTable`
- DAOs: `DocumentDao`, `BulletDao`, `SyncOperationDao`, `AttachmentDao`
- Platform connection: `connection_native.dart` (sqflite, Android) vs `connection_web.dart` (sqlite3 WASM + OPFS, Web)
- Generated files (`app_database.g.dart`, DAO `.g.dart`) must be regenerated with `build_runner` after schema changes

### Auth Flow

- Google Sign-In → Google ID token → `POST /auth/google` → 24h JWT (48h refresh grace)
- JWT stored in `flutter_secure_storage` (Android: `encryptedSharedPreferences: true`)
- 401 response → `onUnauthorized` callback → logout (wired up in `app.dart`)
- **Web**: uses Google Identity Services credential flow (ID token), not access token
- **Default API URL**: `http://10.0.2.2:8000` (Android emulator); override via `--dart-define=API_BASE_URL=...`

---

## Phase Status

| Phase | Summary | Status |
|---|---|---|
| 0 | Repo scaffold, CI, Docker skeleton, project structure | ✅ Complete |
| 1 | Core outliner + Google SSO + basic online sync | ✅ Complete |
| 2 | Mobile gestures + drag-drop + attachments | ✅ Complete |
| 3 | Full-text search + tag filtering + offline-first sync | ⏳ Next |
| V2 | Voice capture + speech-to-text + native Android | 🔮 Future |

A phase is complete only when **all its tests pass in CI**. Do not start the next phase until the current one is merged. Task checklists are in `docs/IMPLEMENTATION-PLAN.md`.

---

## Git Workflow

- `main` is protected — no direct commits
- Work in feature branches named `phase-X/description`, e.g. `phase-3/search-sync`
- Open a PR to `main` for each logical chunk of work
- CI must pass (lint + all tests) before merging
- Commit messages: imperative present tense, e.g. `Add bullet tree rendering` not `Added` or `Adding`
- Commits should be atomic — one logical change per commit

---

## Deployment (for reference — not automated)

The Docker host is on the home LAN. The existing nginx at `192.168.1.204` proxies to the Docker host.

```bash
# Build and start (builds Flutter web inside the Docker image)
docker compose up -d --build

# View logs
docker compose logs -f app

# Stop
docker compose down
```

**Dockerfile is a two-stage build** (`server/Dockerfile`):
1. **Stage 1** (`ghcr.io/cirruslabs/flutter:stable`): `flutter pub get`, `build_runner`, downloads `sqlite3.wasm` and `drift_worker.js`, then `flutter build web --release --dart-define=GOOGLE_CLIENT_ID=... --dart-define=API_BASE_URL=` (empty string = same-origin relative paths)
2. **Stage 2** (`python:3.12-slim`): installs Python deps, copies `server/app/`, copies Flutter web output from stage 1 to `/app/web/`

The FastAPI app serves the Flutter web build from `WEB_BUILD_PATH` (default `/app/web`) via `StaticFiles`. The nginx config block is in `docs/nginx-notes.conf` — Greg adds it manually, do not attempt to configure the remote nginx.

---

## Common Mistakes to Avoid

- Don't add a `Caddyfile` or Caddy service — nginx at 192.168.1.204 handles TLS
- Don't use SQLAlchemy — raw `aiosqlite` only
- Don't store binary attachment data in SQLite — filesystem only (`/data/files/`)
- Don't block offline usage behind JWT validation — expired tokens should degrade gracefully
- Don't push directly to `main`
- Don't mark a phase complete if any test is skipped or failing
- Don't manually insert into `bullets_fts` — the FTS triggers handle it automatically
- Don't edit `*.g.dart` files by hand — they are generated by `build_runner`
- Don't run `flutter test` or `flutter analyze` without first running `dart run build_runner build` — the generated `.g.dart` files are required
- Don't use `flutter analyze` without `--fatal-infos` when checking against CI — CI treats info-level findings as failures
- Don't set `API_BASE_URL` in the Dockerfile — leave it empty so the web build uses same-origin paths
- Don't use the Google access token flow for web sign-in — use the Google Identity Services credential (ID token) flow
- Don't add a new migration by editing existing `.sql` files — always create a new `vNNN_description.sql` file
