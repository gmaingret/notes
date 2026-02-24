# API Contracts — Phase 1
> **Source of truth for both backend implementation and frontend consumption.**
> Backend must implement exactly these shapes. Frontend must code against exactly these shapes.
> Do not diverge without updating this document.

All endpoints (except `/auth/*`) require:
```
Authorization: Bearer <jwt>
```
On 401, client must trigger logout.

---

## Auth

### POST /auth/google
Exchange Google ID token for server JWT.

**Request:**
```json
{ "id_token": "string" }
```
**Response 200:**
```json
{ "access_token": "string", "expires_at": 1700000000000 }
```
`expires_at` is Unix ms (24 h from issue).

**Errors:** `401` invalid Google token.

---

### POST /auth/refresh
Refresh a server JWT within the 48 h grace period.

**Request:**
```json
{ "access_token": "string" }
```
**Response 200:**
```json
{ "access_token": "string", "expires_at": 1700000000000 }
```
**Errors:** `401` if token is expired beyond grace period or tampered.

---

## Documents

### GET /documents
List all non-deleted documents ordered by `position`.

**Response 200:**
```json
[
  {
    "id": "uuid",
    "title": "string",
    "position": "string",
    "created_at": 1700000000000,
    "updated_at": 1700000000000
  }
]
```

---

### POST /documents
Create a new document.

**Request:**
```json
{ "title": "string", "position": "string" }
```
`position` is a fractional index string. Client should supply it; if absent server generates one appended at end.

**Response 201:**
```json
{
  "id": "uuid",
  "title": "string",
  "position": "string",
  "created_at": 1700000000000,
  "updated_at": 1700000000000
}
```

---

### PATCH /documents/:id
Rename or reorder a document.

**Request (all fields optional):**
```json
{ "title": "string", "position": "string" }
```
**Response 200:** same shape as POST response.
**Errors:** `404` if not found.

---

### DELETE /documents/:id
Soft-delete a document (sets `deleted_at`).

**Response 204:** no body.
**Errors:** `404` if not found.

---

## Bullets

### GET /documents/:id/bullets
Fetch all non-deleted bullets for a document as a flat list. Client builds the tree in-memory.

**Response 200:**
```json
[
  {
    "id": "uuid",
    "document_id": "uuid",
    "parent_id": "uuid | null",
    "content": "string",
    "position": "string",
    "is_complete": false,
    "created_at": 1700000000000,
    "updated_at": 1700000000000
  }
]
```
Ordered by `position` within each parent group (but returned flat — client sorts/groups).

**Errors:** `404` if document not found.

---

### POST /bullets
Create a new bullet.

**Request:**
```json
{
  "id": "uuid",
  "document_id": "uuid",
  "parent_id": "uuid | null",
  "content": "string",
  "position": "string",
  "is_complete": false
}
```
`id` is client-generated UUID v4 (required — enables idempotency).

**Response 201:** same shape as bullet object above (with `created_at`/`updated_at`).

---

### PATCH /bullets/:id
Update content, position, parent, or completion state.

**Request (all fields optional):**
```json
{
  "content": "string",
  "position": "string",
  "parent_id": "uuid | null",
  "is_complete": false
}
```
**Response 200:** full bullet object.
**Errors:** `404` if not found.

---

### DELETE /bullets/:id
Soft-delete a bullet and all its descendants (cascade).

**Response 204:** no body.
**Errors:** `404` if not found.

---

## Sync

### POST /sync
Push client operations and receive server delta. This is the only sync endpoint in Phase 1.
Server delta is always empty in Phase 1 (full delta pull is Phase 3).

**Request:**
```json
{
  "device_id": "string",
  "last_sync_at": 0,
  "operations": [
    {
      "id": "uuid",
      "operation_type": "upsert",
      "entity_type": "document",
      "entity_id": "uuid",
      "payload": {},
      "client_timestamp": 1700000000000
    }
  ]
}
```
`operation_type`: `"upsert"` | `"delete"`
`entity_type`: `"document"` | `"bullet"` | `"attachment"`
`payload`: full entity JSON snapshot at time of mutation.

**Response 200:**
```json
{
  "server_timestamp": 1700000000000,
  "applied": ["op-uuid-1", "op-uuid-2"],
  "server_delta": []
}
```
`applied` lists operation IDs that were successfully applied (duplicates are included — idempotency).

**Errors:** `401` unauthenticated.

---

## Error Format

All errors return:
```json
{ "detail": "human-readable message" }
```
Standard HTTP status codes: `400` bad request, `401` unauthorized, `404` not found, `422` validation error (FastAPI default).

---

## JWT Payload

```json
{
  "sub": "google-user-id",
  "email": "user@example.com",
  "name": "Display Name",
  "iat": 1700000000,
  "exp": 1700086400
}
```
`exp` is `iat + 86400` (24 h). Grace period for refresh: up to 48 h after `iat`.

---

## Notes for Frontend

- All timestamps are **Unix milliseconds** (integer).
- All IDs are **UUID v4 strings**.
- `position` is a **fractional index string** — use the `FractionalIndex` utility in `core/utils/fractional_index.dart`.
- On startup after login: GET /documents, then GET /documents/:id/bullets for each → populate local DB.
- The sync debounce is **500 ms** — mutations write to local DB immediately, sync flushes after 500 ms idle.
- Never block the UI waiting for a server response.

## Notes for Backend

- Run DB migrations on startup; no manual migration step.
- Soft-delete: set `deleted_at = now()`. Background task hard-deletes rows where `deleted_at < now() - 60s`, runs every 30 s.
- Tag extraction runs on every bullet upsert; tags stored lowercase without `#`.
- FTS5 `bullets_fts` is maintained by SQL triggers — never insert into it directly.
- Duplicate `operation_id` in POST /sync must be silently ignored (idempotent), not cause an error.
