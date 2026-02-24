-- v001_init.sql
-- Initial schema for Notes app
-- Applied automatically by the migration runner on startup.

-- Users (single user in practice, keeps auth clean)
CREATE TABLE IF NOT EXISTS users (
    id          TEXT PRIMARY KEY,   -- Google sub claim
    email       TEXT NOT NULL UNIQUE,
    name        TEXT,
    avatar_url  TEXT,
    created_at  INTEGER NOT NULL    -- Unix timestamp ms
);

-- Documents (top-level files in the sidebar)
CREATE TABLE IF NOT EXISTS documents (
    id          TEXT PRIMARY KEY,   -- UUID v4
    title       TEXT NOT NULL DEFAULT 'Untitled',
    position    TEXT NOT NULL,      -- fractional index for sidebar ordering
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    deleted_at  INTEGER             -- soft delete for sync safety
);

-- Bullets (the core entity)
CREATE TABLE IF NOT EXISTS bullets (
    id           TEXT PRIMARY KEY,  -- UUID v4
    document_id  TEXT NOT NULL REFERENCES documents(id),
    parent_id    TEXT REFERENCES bullets(id),  -- NULL = root bullet
    content      TEXT NOT NULL DEFAULT '',
    position     TEXT NOT NULL,     -- fractional index among siblings
    is_complete  INTEGER NOT NULL DEFAULT 0,   -- boolean (0/1)
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL,
    deleted_at   INTEGER            -- soft delete (permanent delete = set this)
);

CREATE INDEX IF NOT EXISTS idx_bullets_document
    ON bullets(document_id);

CREATE INDEX IF NOT EXISTS idx_bullets_parent
    ON bullets(parent_id);

CREATE INDEX IF NOT EXISTS idx_bullets_position
    ON bullets(document_id, parent_id, position);

-- Tags (extracted and indexed for fast filter queries)
CREATE TABLE IF NOT EXISTS tags (
    id    TEXT PRIMARY KEY,
    name  TEXT NOT NULL UNIQUE  -- stored lowercase, without '#'
);

CREATE TABLE IF NOT EXISTS bullet_tags (
    bullet_id  TEXT NOT NULL REFERENCES bullets(id) ON DELETE CASCADE,
    tag_id     TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (bullet_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_bullet_tags_tag
    ON bullet_tags(tag_id);

-- Attachments
CREATE TABLE IF NOT EXISTS attachments (
    id           TEXT PRIMARY KEY,
    bullet_id    TEXT NOT NULL REFERENCES bullets(id) ON DELETE CASCADE,
    type         TEXT NOT NULL CHECK(type IN ('image', 'file', 'audio')),
    filename     TEXT NOT NULL,
    mime_type    TEXT NOT NULL,
    size_bytes   INTEGER NOT NULL,
    storage_path TEXT NOT NULL,   -- relative path on server filesystem
    created_at   INTEGER NOT NULL,
    deleted_at   INTEGER
);

CREATE INDEX IF NOT EXISTS idx_attachments_bullet
    ON attachments(bullet_id);

-- Sync operation log
CREATE TABLE IF NOT EXISTS sync_operations (
    id               TEXT PRIMARY KEY,  -- UUID v4
    device_id        TEXT NOT NULL,
    operation_type   TEXT NOT NULL,     -- 'upsert' | 'delete'
    entity_type      TEXT NOT NULL,     -- 'document' | 'bullet' | 'attachment'
    entity_id        TEXT NOT NULL,
    payload          TEXT NOT NULL,     -- JSON snapshot of the entity
    client_timestamp INTEGER NOT NULL,  -- UTC ms, set by client at mutation time
    server_timestamp INTEGER,           -- set when applied on server
    applied          INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sync_device_applied
    ON sync_operations(device_id, applied, client_timestamp);

-- Full-text search (SQLite FTS5)
CREATE VIRTUAL TABLE IF NOT EXISTS bullets_fts USING fts5(
    content,
    bullet_id   UNINDEXED,
    document_id UNINDEXED,
    tokenize = 'unicode61 remove_diacritics 2'
);

-- FTS5 sync triggers
CREATE TRIGGER IF NOT EXISTS bullets_fts_insert
AFTER INSERT ON bullets
WHEN NEW.deleted_at IS NULL
BEGIN
    INSERT INTO bullets_fts(content, bullet_id, document_id)
    VALUES (NEW.content, NEW.id, NEW.document_id);
END;

CREATE TRIGGER IF NOT EXISTS bullets_fts_update
AFTER UPDATE ON bullets
BEGIN
    DELETE FROM bullets_fts WHERE bullet_id = OLD.id;
    INSERT INTO bullets_fts(content, bullet_id, document_id)
    SELECT NEW.content, NEW.id, NEW.document_id
    WHERE NEW.deleted_at IS NULL;
END;

CREATE TRIGGER IF NOT EXISTS bullets_fts_delete
AFTER DELETE ON bullets
BEGIN
    DELETE FROM bullets_fts WHERE bullet_id = OLD.id;
END;
