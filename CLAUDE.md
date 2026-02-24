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
├── CLAUDE.md                      ← you are here
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── docs/                          ← planning docs + nginx config
├── server/                        ← Python FastAPI backend
│   ├── app/
│   │   ├── main.py
│   │   ├── config.py
│   │   ├── db/
│   │   ├── models/
│   │   ├── routers/
│   │   ├── services/
│   │   └── utils/
│   ├── tests/
│   │   ├── conftest.py
│   │   ├── unit/
│   │   └── integration/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── requirements-dev.txt
└── client/                        ← Flutter app
    ├── lib/
    │   ├── main.dart
    │   ├── app.dart
    │   ├── core/
    │   └── features/
    ├── test/
    │   ├── unit/
    │   └── widget/
    ├── integration_test/
    └── pubspec.yaml
```

Full directory tree with file-level descriptions is in `docs/IMPLEMENTATION-PLAN.md`.

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

---

## Running Locally (Development)

### Backend

```bash
cd server
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt -r requirements-dev.txt

# Copy and fill in env vars
cp ../.env.example ../.env

# Run with hot reload
uvicorn app.main:app --reload --port 8000
```

The server runs migrations automatically on startup. No manual migration step needed.

### Flutter (Android)

```bash
cd client
flutter pub get
flutter run                      # connects to backend at http://10.0.2.2:8000 in emulator
```

### Flutter (Web)

```bash
cd client
flutter run -d chrome --web-port 3000
# Backend should be running on localhost:8000
```

---

## Running Tests

### Backend

```bash
cd server
pytest                           # all tests
pytest tests/unit/               # unit tests only
pytest tests/integration/        # integration tests only
pytest --cov=app --cov-report=term-missing   # with coverage
```

Tests use an in-memory SQLite database. Google OAuth is mocked via a fixture in `conftest.py`. No real network calls are made in tests.

### Flutter

```bash
cd client
flutter test                             # unit + widget tests
flutter test integration_test/           # integration tests (requires emulator)
flutter analyze                          # static analysis (must be clean before PR)
```

---

## Git Workflow

- `main` is protected — no direct commits
- Work in feature branches named `phase-X/description`, e.g. `phase-0/scaffold`, `phase-1/auth`
- Open a PR to `main` for each logical chunk of work
- CI must pass (lint + all tests) before merging
- Commit messages: imperative present tense, e.g. `Add bullet tree rendering` not `Added` or `Adding`
- Commits should be atomic — one logical change per commit

---

## Deployment (for reference — not automated)

The Docker host is on the home LAN. The existing nginx at `192.168.1.204` proxies to the Docker host.

```bash
# Build and start
docker compose up -d --build

# View logs
docker compose logs -f app

# Stop
docker compose down
```

The nginx config block to add to `192.168.1.204` is in `docs/nginx-notes.conf`. Greg adds it manually — do not attempt to configure the remote nginx.

The Flutter web build must be compiled and bundled into the Docker image at build time:
```bash
cd client && flutter build web --release
# Output lands in client/build/web/ — Dockerfile copies this to /app/web
```

---

## Architecture Constraints (do not violate)

1. **Offline-first**: All writes go to local SQLite immediately. Server sync is always secondary and asynchronous. Never block the UI on a network call.
2. **Single user**: No multi-user auth, no row-level security, no tenant isolation needed. Keep it simple.
3. **Last-write-wins sync**: Conflict resolution is by timestamp. No conflict UI. Do not add complexity here.
4. **No external services**: No Redis, no Celery, no separate search engine, no message queue. SQLite FTS5 for search. Everything runs in one container.
5. **Permanent deletes**: Swipe-left on a bullet is permanent after the 5s undo snackbar. No trash/archive table.
6. **Global tags**: `#tags` are scoped globally across all documents. No per-document tag scope.
7. **Raw SQL only**: Use `aiosqlite` with raw SQL strings. No SQLAlchemy ORM. Keeps the backend thin and transparent.

---

## Phase-Based Development

Work one phase at a time. A phase is complete only when **all its tests pass in CI**. Do not start the next phase until the current one is merged.

| Phase | Summary |
|---|---|
| 0 | Repo scaffold, CI, Docker skeleton, project structure |
| 1 | Core outliner + Google SSO + basic online sync |
| 2 | Mobile gestures + drag-drop + attachments |
| 3 | Full-text search + tag filtering + offline-first sync |
| V2 | Voice capture + speech-to-text + native Android (future) |

Task checklists for each phase are in `docs/IMPLEMENTATION-PLAN.md`.

---

## Key Data Model Facts

- **Tree storage**: Adjacency list — each bullet has `parent_id` (nullable) and `position` (fractional index string)
- **Fractional index**: Lexicographically sortable strings. Utility in `server/app/utils/fractional_index.py` and `client/lib/core/utils/fractional_index.dart`. Both must produce identical output for the same inputs.
- **Soft deletes**: `deleted_at` timestamp column on `bullets`, `documents`, `attachments`. Background task hard-deletes rows older than 60 seconds. Queries always filter `WHERE deleted_at IS NULL`.
- **FTS5**: `bullets_fts` virtual table, kept in sync via triggers. Do not manually insert into it — let the triggers handle it.
- **Tags**: Extracted from bullet `content` via regex on every upsert. Stored in `tags` + `bullet_tags` tables. Tag scope is global.

Full schema in `docs/SYSTEM-DESIGN-Notes-App.md`, Section 3.

---

## Common Mistakes to Avoid

- Don't add a `Caddyfile` or Caddy service — nginx at 192.168.1.204 handles TLS
- Don't use SQLAlchemy — raw `aiosqlite` only
- Don't store binary attachment data in SQLite — filesystem only
- Don't block offline usage behind JWT validation — expired tokens should degrade gracefully
- Don't push directly to `main`
- Don't mark a phase complete if any test is skipped or failing
