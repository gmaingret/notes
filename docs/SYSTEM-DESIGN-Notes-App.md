# System Design: Notes — Self-Hosted Personal Outliner

**Version:** 1.1
**Date:** February 2026
**Status:** Draft
**Companion doc:** PRD v0.2

---

## 1. Requirements Summary

### Functional (from PRD)
- Infinite nested bullet list with zoom + breadcrumb navigation
- Multiple named documents in a sidebar
- Live WYSIWYG Markdown rendering
- Global `#tag` system across all documents
- Full-text search across all bullets
- Swipe gestures + drag-and-drop on mobile
- Image, file, and audio attachments
- Google SSO authentication
- Offline-first with automatic sync on reconnect (last-write-wins)

### Non-Functional
| Attribute | Target |
|---|---|
| Availability | Personal use — downtime acceptable, data safety is critical |
| Latency | UI interactions < 16ms (local SQLite); sync < 2s on reconnect |
| Data durability | Zero loss — local-first means data survives server downtime |
| Scale | Single user, ~10k–100k bullets over lifetime, ~10GB attachments |
| Deployment | Single Docker Compose stack, runs on a home server or VPS |
| Maintainability | Minimal ops — no external DB, no message queue, no cache layer |

### Constraints
- Solo developer, personal project
- No ops budget — must be zero-maintenance after deploy
- Flutter for both Android and web (shared codebase)
- Self-hosted: no managed cloud services

---

## 2. High-Level Architecture

```
  LAN (192.168.1.x)
  ┌──────────────────────────────────────────────────────────┐
  │  192.168.1.204                                           │
  │  ┌────────────────────────────────────────────────────┐  │
  │  │  nginx (existing reverse proxy)                    │  │
  │  │  - TLS termination                                 │  │
  │  │  - notes.yourdomain.lan → Docker host:8000         │  │
  │  └────────────────────────┬───────────────────────────┘  │
  │                           │ HTTP proxy_pass               │
  │  Docker host (192.168.1.X)│                               │
  │  ┌────────────────────────▼───────────────────────────┐  │
  │  │  Docker Compose                                    │  │
  │  │                                                    │  │
  │  │  ┌─────────────────────────────────────────────┐  │  │
  │  │  │   FastAPI App Server          port 8000      │  │  │
  │  │  │                                              │  │  │
  │  │  │  ┌──────────────┐  ┌─────────────────────┐  │  │  │
  │  │  │  │  SQLite DB   │  │  Attachment Store   │  │  │  │
  │  │  │  │  (notes.db)  │  │  (/data/files/)     │  │  │  │
  │  │  │  └──────────────┘  └─────────────────────┘  │  │  │
  │  │  └─────────────────────────────────────────────┘  │  │
  │  │           (volumes: notes_data)                    │  │
  │  └────────────────────────────────────────────────────┘  │
  └──────────────────────────────────────────────────────────┘
          │ HTTPS (via nginx)
    ┌─────┴──────┐
    │            │
┌───┴───┐   ┌───┴────────┐
│Android│   │  Web       │
│Flutter│   │  Flutter   │
│       │   │  (WASM)    │
│Local  │   │  LocalDB   │
│SQLite │   │  (OPFS)    │
└───────┘   └────────────┘
```

### Components

**nginx (existing, 192.168.1.204)** — The existing LAN reverse proxy handles TLS termination and routes `notes.yourdomain.lan` to the Docker host's port 8000. No changes required to the Docker stack for TLS — nginx owns that concern. The nginx config needs one new `server` block (see Section 8).

**FastAPI App Server** — Thin REST API. Handles auth token validation, CRUD operations, sync, file uploads, and serves the Flutter web static build via `StaticFiles`. Runs in a single process on port 8000 — no workers, queues, or cache needed at this scale.

**SQLite** — Embedded in the server container, mounted as a Docker volume for persistence. Single file (`notes.db`), zero maintenance, ACID-compliant. Perfectly sufficient for one user and millions of rows.

**Attachment Store** — Local filesystem directory inside the container, mounted as a Docker volume. Files served directly via FastAPI or via Caddy static routing.

**Flutter clients** — Android uses `sqflite` (native SQLite). Web uses SQLite compiled to WASM with OPFS (Origin Private File System) for persistent local storage. Both maintain a local operation queue for offline sync.

---

## 3. Data Model

### Design Decisions

**Tree representation: Adjacency list + fractional index ordering**

Each bullet stores its `parent_id` and a `position` (fractional index string). This combination gives us:
- Simple parent-child queries
- Insert-anywhere ordering without reindexing siblings
- Clean representation of move operations (update `parent_id` + `position`)

Fractional indexing uses lexicographically sortable strings (e.g. `"a0"`, `"a1"`, `"b0"`). Inserting between `"a0"` and `"a1"` yields `"a0V"`. Libraries like `fractional-indexing` implement this. This is the same approach used by Figma and Linear.

**Why not nested sets or closure table?** Both optimize read performance at the cost of write complexity. For a personal app with moderate data, the simpler adjacency list is the right trade-off.

### Schema

```sql
-- Users (single user in practice, but keeps auth clean)
CREATE TABLE users (
  id          TEXT PRIMARY KEY,  -- Google sub claim
  email       TEXT NOT NULL UNIQUE,
  name        TEXT,
  avatar_url  TEXT,
  created_at  INTEGER NOT NULL   -- Unix timestamp ms
);

-- Documents (top-level files in the sidebar)
CREATE TABLE documents (
  id          TEXT PRIMARY KEY,  -- UUID v4
  title       TEXT NOT NULL DEFAULT 'Untitled',
  position    TEXT NOT NULL,     -- fractional index for sidebar ordering
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  deleted_at  INTEGER            -- soft delete for sync safety
);

-- Bullets (the core entity)
CREATE TABLE bullets (
  id           TEXT PRIMARY KEY,  -- UUID v4
  document_id  TEXT NOT NULL REFERENCES documents(id),
  parent_id    TEXT REFERENCES bullets(id),  -- NULL = root bullet
  content      TEXT NOT NULL DEFAULT '',
  position     TEXT NOT NULL,     -- fractional index among siblings
  is_complete  INTEGER NOT NULL DEFAULT 0,   -- boolean
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL,
  deleted_at   INTEGER            -- soft delete (permanent delete = set this)
);

CREATE INDEX idx_bullets_document   ON bullets(document_id);
CREATE INDEX idx_bullets_parent     ON bullets(parent_id);
CREATE INDEX idx_bullets_position   ON bullets(document_id, parent_id, position);

-- Tags (extracted and indexed for fast filter queries)
CREATE TABLE tags (
  id    TEXT PRIMARY KEY,
  name  TEXT NOT NULL UNIQUE   -- stored lowercase, without '#'
);

CREATE TABLE bullet_tags (
  bullet_id  TEXT NOT NULL REFERENCES bullets(id) ON DELETE CASCADE,
  tag_id     TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (bullet_id, tag_id)
);

CREATE INDEX idx_bullet_tags_tag ON bullet_tags(tag_id);

-- Attachments
CREATE TABLE attachments (
  id           TEXT PRIMARY KEY,
  bullet_id    TEXT NOT NULL REFERENCES bullets(id) ON DELETE CASCADE,
  type         TEXT NOT NULL CHECK(type IN ('image', 'file', 'audio')),
  filename     TEXT NOT NULL,
  mime_type    TEXT NOT NULL,
  size_bytes   INTEGER NOT NULL,
  storage_path TEXT NOT NULL,    -- relative path on server filesystem
  created_at   INTEGER NOT NULL,
  deleted_at   INTEGER
);

CREATE INDEX idx_attachments_bullet ON attachments(bullet_id);

-- Sync operation log (client-side queue; also persisted on server for debugging)
CREATE TABLE sync_operations (
  id               TEXT PRIMARY KEY,   -- UUID v4
  device_id        TEXT NOT NULL,
  operation_type   TEXT NOT NULL,      -- 'upsert' | 'delete'
  entity_type      TEXT NOT NULL,      -- 'document' | 'bullet' | 'attachment'
  entity_id        TEXT NOT NULL,
  payload          TEXT NOT NULL,      -- JSON snapshot of the entity
  client_timestamp INTEGER NOT NULL,   -- UTC ms, set by client at mutation time
  server_timestamp INTEGER,            -- set when applied on server
  applied          INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_sync_device_applied ON sync_operations(device_id, applied, client_timestamp);

-- Full-text search (SQLite FTS5)
CREATE VIRTUAL TABLE bullets_fts USING fts5(
  content,
  bullet_id UNINDEXED,
  document_id UNINDEXED,
  tokenize = 'unicode61 remove_diacritics 2'
);
-- Populated and updated via triggers on bullets table
```

### Soft Deletes

Swipe-left permanently deletes from the user's perspective, but the record is kept with `deleted_at` set for **30 seconds** to support the undo snackbar. A background cleanup job purges soft-deleted rows older than 60 seconds. This keeps the sync model clean without a trash concept.

---

## 4. API Design

### Authentication

All endpoints require `Authorization: Bearer <jwt>` except `/auth/*`.

The server issues its own short-lived JWT (24h) after validating the Google ID token. This avoids passing Google tokens around and lets us work offline once the JWT is issued.

```
POST /auth/google
  Body: { id_token: string }   -- Google ID token from client
  Returns: { access_token: string, expires_at: number }

POST /auth/refresh
  Body: { access_token: string }
  Returns: { access_token: string, expires_at: number }
  -- Works only if within grace period; offline clients skip this
```

**Offline handling:** If the JWT expires while offline, the app continues to work locally. The JWT is refreshed silently on next server contact. The app never blocks the user due to an expired token when offline.

### Core REST Endpoints

```
-- Documents
GET    /documents              List all documents (sidebar)
POST   /documents              Create document
PATCH  /documents/:id          Update title or position
DELETE /documents/:id          Soft-delete document

-- Bullets
GET    /documents/:id/bullets  Fetch full bullet tree for a document
POST   /bullets                Create bullet
PATCH  /bullets/:id            Update content, position, parent, is_complete
DELETE /bullets/:id            Soft-delete (permanent after 60s cleanup)

-- Search
GET    /search?q=:query        Full-text search across all bullets
GET    /tags/:name/bullets     All bullets with a given tag (global)
GET    /tags                   List all tags with counts

-- Attachments
POST   /attachments            Upload file (multipart/form-data), link to bullet
GET    /attachments/:id/file   Stream file content
DELETE /attachments/:id        Delete attachment + file from disk

-- Sync
POST   /sync                   Push client operations + pull server deltas
GET    /sync/status            Last sync timestamp per device
```

### Sync Endpoint Detail

This is the most important endpoint. It handles the entire offline sync flow in one round-trip.

```
POST /sync
Request:
{
  device_id: string,
  last_sync_at: number,        -- UTC ms of last successful sync (0 = first sync)
  operations: [
    {
      id: string,              -- operation UUID
      operation_type: "upsert" | "delete",
      entity_type: "document" | "bullet" | "attachment",
      entity_id: string,
      payload: object,         -- full entity state at time of mutation
      client_timestamp: number -- UTC ms
    }
  ]
}

Response:
{
  server_timestamp: number,    -- server's current time; client stores as last_sync_at
  applied: string[],           -- operation IDs successfully applied
  server_delta: [              -- changes on server since client's last_sync_at
    {
      operation_type: "upsert" | "delete",
      entity_type: string,
      entity_id: string,
      payload: object,
      server_timestamp: number
    }
  ]
}
```

**Sync flow:**
1. Client goes offline → all mutations written to local SQLite + appended to local `sync_operations` queue
2. Client reconnects → POST /sync with queued operations
3. Server applies client ops in `client_timestamp` order (last-write-wins on same entity)
4. Server returns all server-side changes since `last_sync_at`
5. Client merges server delta into local DB; last-write-wins on timestamp
6. Client stores new `server_timestamp` as `last_sync_at`; clears applied ops from queue

---

## 5. Offline-First Architecture (Client)

```
┌─────────────────────────────────────────────────────┐
│                Flutter App                          │
│                                                     │
│  ┌──────────────────────────────────────────────┐   │
│  │              UI Layer                        │   │
│  │  (Widgets, gesture handlers, Markdown render)│   │
│  └──────────────────┬───────────────────────────┘   │
│                     │                               │
│  ┌──────────────────▼───────────────────────────┐   │
│  │           Repository Layer                   │   │
│  │  Single source of truth — always reads from  │   │
│  │  local SQLite. Writes go to local first.     │   │
│  └──────────┬──────────────────────┬────────────┘   │
│             │                      │                │
│  ┌──────────▼──────┐  ┌────────────▼─────────────┐  │
│  │  Local SQLite   │  │    Sync Manager          │  │
│  │  (sqflite /     │  │                          │  │
│  │   OPFS-SQLite)  │  │  - Watches connectivity  │  │
│  │                 │  │  - Queues operations     │  │
│  │  - documents    │  │  - POST /sync on connect │  │
│  │  - bullets      │  │  - Merges server delta   │  │
│  │  - attachments  │  │  - Retries on failure    │  │
│  │  - sync_ops     │  └──────────────────────────┘  │
│  └─────────────────┘                                │
└─────────────────────────────────────────────────────┘
```

### State Management

**Recommended: Riverpod** (Flutter-idiomatic, testable, no boilerplate)

- `DocumentsNotifier` — list of documents for sidebar
- `BulletTreeNotifier(documentId)` — reactive tree for open document
- `SearchNotifier` — debounced full-text search against local FTS5
- `SyncNotifier` — sync status, connectivity watch, queue length

All notifiers read from local SQLite. The Sync Manager reconciles with the server in the background without blocking the UI.

### Write Path (online or offline)

```
User edits bullet
    │
    ▼
Repository.updateBullet(id, content, timestamp: now())
    │
    ├─► Write to local SQLite (immediate, synchronous feel)
    │
    ├─► Append to sync_operations queue (local SQLite)
    │
    └─► SyncManager.notifyPendingOp()
            │
            ├─ If online: POST /sync immediately (debounced 500ms)
            └─ If offline: queue persists; will flush on reconnect
```

### Attachment Sync

Attachments are handled separately from bullet sync. Binary files are not included in the operation queue payload — only the metadata is. On reconnect:
1. Metadata synced via POST /sync as normal
2. For each attachment with `local_file_path` but no `server_url`, client uploads via POST /attachments
3. After server confirms, local record is updated with `server_url`; local file retained as cache

---

## 6. Flutter Web — Local Storage

Flutter web does not support `sqflite`. The solution: **`sqlite3_flutter_libs` compiled to WASM with OPFS backend** via the `sqlite3` package + `drift` ORM.

OPFS (Origin Private File System) is a modern browser API that provides a real filesystem-backed persistent store — much more reliable than IndexedDB for SQLite use. Supported in all modern Android WebViews and desktop browsers.

This gives web and Android an identical local SQLite experience with the same schema and query logic.

---

## 7. Technology Stack

| Layer | Choice | Rationale |
|---|---|---|
| Frontend | Flutter 3.x | Single codebase for Android + web |
| State management | Riverpod 2.x | Compile-safe, testable, async-native |
| Local DB (Android) | sqflite + drift | Mature, well-supported |
| Local DB (Web) | sqlite3 WASM + OPFS + drift | Consistent API with Android |
| Backend language | Python 3.12 | Fast to write, well-documented, huge ecosystem |
| Backend framework | FastAPI | Async, auto-generates OpenAPI docs, minimal boilerplate |
| Server DB | SQLite via `aiosqlite` | Zero ops, single file, ACID, persists via Docker volume |
| Auth | Google OAuth 2.0 + server JWT | Standard, no password management |
| Reverse proxy | nginx (existing, 192.168.1.204) | Already running in LAN; handles TLS; zero additional infra |
| Containers | Docker Compose | Simple, portable, no orchestrator needed |
| Full-text search | SQLite FTS5 | Built-in, zero dependencies, sufficient for personal scale |

### Why Python over Dart/shelf?

Dart/shelf would share the language with Flutter but has a smaller ecosystem, fewer auth/crypto libraries, and less community support for backend use. Python + FastAPI is a better choice for the backend in isolation. The API contract (OpenAPI spec) is what bridges the two.

---

## 8. Deployment

### docker-compose.yml

```yaml
version: '3.8'

services:
  app:
    build: ./server
    ports:
      - "8000:8000"        # exposed to LAN; nginx proxies to this
    environment:
      - GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
      - GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
      - JWT_SECRET=${JWT_SECRET}
      - DB_PATH=/data/notes.db
      - ATTACHMENTS_PATH=/data/files
      - WEB_BUILD_PATH=/app/web  # Flutter web build served by FastAPI
    volumes:
      - notes_data:/data
    restart: unless-stopped

volumes:
  notes_data:
```

No Caddy. The Docker stack is a single container. Flutter web static files are bundled into the server image at build time and served by FastAPI's `StaticFiles` at `/`.

### nginx config block (add to existing nginx at 192.168.1.204)

```nginx
server {
    listen 443 ssl;
    server_name notes.yourdomain.lan;  # adjust to your domain/hostname

    # Reference your existing SSL cert — self-signed or from your local CA
    ssl_certificate     /etc/nginx/certs/yourdomain.crt;
    ssl_certificate_key /etc/nginx/certs/yourdomain.key;

    # Increase for attachment uploads
    client_max_body_size 100M;

    location / {
        proxy_pass         http://192.168.1.XXX:8000;  # IP of Docker host
        proxy_http_version 1.1;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;

        # For large file uploads — avoid timeouts
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}

# HTTP → HTTPS redirect
server {
    listen 80;
    server_name notes.yourdomain.lan;
    return 301 https://$host$request_uri;
}
```

### Backup Strategy

SQLite backup is trivially simple — copy the file. A cron job inside the container runs:

```bash
# Daily backup to /data/backups/
0 2 * * * sqlite3 /data/notes.db ".backup /data/backups/notes-$(date +%Y%m%d).db"
# Retain 30 days
find /data/backups/ -name "*.db" -mtime +30 -delete
```

The backup directory is part of the Docker volume, so it survives container restarts.

---

## 9. Search Design

SQLite's FTS5 is powerful enough for this use case. Implementation:

1. FTS table (`bullets_fts`) is populated via triggers on `bullets` INSERT/UPDATE/DELETE
2. Queries use FTS5 `MATCH` syntax: `SELECT * FROM bullets_fts WHERE bullets_fts MATCH 'query*'`
3. Results joined back to `bullets` to get `document_id`, `parent_id`, etc.
4. Client-side: search is run against **local SQLite** for instant results — no network round-trip needed
5. Tags are indexed in the `tags` + `bullet_tags` tables; tag filter queries are a simple join

---

## 10. Markdown Rendering (Client)

**Live WYSIWYG approach:** Raw Markdown syntax is shown only when the cursor is within that span. The rest of the bullet renders the formatted output.

Implementation in Flutter: **`flutter_quill`** is too heavy. **`super_editor`** supports custom inline syntax. Alternatively, a simpler custom approach: use a `TextEditingController` with a custom `TextSpan` painter that:

1. Parses Markdown tokens as the user types (fast regex, not full parser)
2. Renders styled `TextSpan` for complete tokens (e.g. `**word**` → bold)
3. Shows raw syntax for the token the cursor is currently inside

This is the same approach used by Bear and iA Writer. It avoids the complexity of a full rich-text editor while giving a clean writing experience.

---

## 11. Trade-Off Analysis

| Decision | Chose | Alternative | Trade-Off |
|---|---|---|---|
| Tree storage | Adjacency list + fractional index | Closure table | Simpler writes, slightly slower deep-tree reads — acceptable at this scale |
| Sync strategy | Last-write-wins, timestamp-based | CRDT / OT | Far simpler for single user; would need revisiting for multi-user |
| Backend DB | SQLite | PostgreSQL | No separate DB process; limits future multi-user scaling but perfect for personal use |
| Backend language | Python + FastAPI | Dart + shelf | Larger ecosystem, better auth libs; costs cross-language boundary |
| Sync transport | REST (POST /sync) | WebSockets | Simpler server, no persistent connection management; 500ms sync debounce is fine for personal use |
| Flutter web storage | SQLite WASM + OPFS | IndexedDB | True SQLite semantics everywhere; OPFS requires modern browser but Android WebView supports it |
| Reverse proxy | Existing nginx (192.168.1.204) | Caddy in Docker | Zero new infra; reuses what's already running; nginx config is one extra server block |
| Search | SQLite FTS5 | Elasticsearch / Meilisearch | Zero ops, sufficient for personal scale; would not survive a multi-user product |

---

## 12. Open Questions (Engineering)

| # | Question | Recommended Answer |
|---|---|---|
| 1 | WebSockets vs HTTP polling for sync? | **HTTP POST /sync** with 500ms debounce on mutations — simpler, no persistent connection |
| 2 | Offline JWT token refresh? | **Fail gracefully** — remain fully usable offline; silently refresh on next server contact. Never block user. |

---

## 13. What to Revisit as the App Grows

These decisions are right for v1 but have known limits:

- **SQLite → PostgreSQL** if multi-user is ever added (SQLite's write concurrency doesn't scale past ~1 concurrent writer)
- **Last-write-wins → CRDT** if Greg uses more than 2 devices frequently and edit overlaps become real
- **FTS5 → Meilisearch** if search needs fuzzy matching, typo tolerance, or ranking
- **Monolith → separate services** if attachment serving becomes a bottleneck (unlikely for personal use)

---

*Next step: Phase 1 engineering spec — data model migrations, API contracts, and Flutter project scaffold.*
