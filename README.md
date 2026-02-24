# Notes

A self-hosted personal outliner — a WorkFlowy/Dynalist clone built for single-user use.

Infinite nested bullet lists, zoom with breadcrumb navigation, offline-first sync, and full mobile gesture support.
Runs as a Docker container behind an existing nginx reverse proxy on your home LAN.

## Stack

| Layer | Technology |
|---|---|
| Backend | Python 3.12 + FastAPI + uvicorn |
| Database | SQLite via `aiosqlite` (raw SQL, no ORM) |
| Auth | Google OAuth 2.0 → server-issued JWT |
| Frontend | Flutter 3.x (Android + Web) |
| State | Riverpod 2.x |
| Local DB | drift + sqflite (Android) / sqlite3 WASM + OPFS (Web) |
| Proxy | Existing nginx at 192.168.1.204 (not in Docker) |

## Quick Start

### Backend (local dev)

```bash
cd server
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt -r requirements-dev.txt

cp ../.env.example ../.env  # fill in GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, JWT_SECRET

uvicorn app.main:app --reload --port 8000
```

### Flutter (Android emulator)

```bash
cd client
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter run
```

### Flutter (Web)

```bash
cd client
flutter run -d chrome --web-port 3000
```

## Running Tests

### Backend

```bash
cd server
pytest                                               # all tests
pytest tests/unit/                                   # unit only
pytest --cov=app --cov-report=term-missing          # with coverage
```

### Flutter

```bash
cd client
flutter analyze                  # must be clean
flutter test test/               # unit + widget tests
```

## Docker (production)

Build the Flutter web app first, then build and start the container:

```bash
cd client && flutter build web --release
cd ..
docker compose up -d --build
```

The nginx config block to add to `192.168.1.204` is in `docs/nginx-notes.conf`.

## Architecture

See `docs/SYSTEM-DESIGN-Notes-App.md` for the full architecture, data model, and API design.

## Development Phases

| Phase | Status | Description |
|---|---|---|
| 0 | In progress | Repo scaffold, CI, Docker skeleton |
| 1 | Planned | Core outliner + Google SSO + basic online sync |
| 2 | Planned | Mobile gestures + drag-drop + attachments |
| 3 | Planned | Full-text search + tag filtering + offline-first sync |

Task checklists are in `docs/IMPLEMENTATION-PLAN.md`.
