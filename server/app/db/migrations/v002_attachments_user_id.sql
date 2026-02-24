-- v002_attachments_user_id.sql
-- Add user_id to attachments for per-user ownership checks.
-- DEFAULT '' covers any pre-existing rows (none in practice at this stage).
ALTER TABLE attachments ADD COLUMN user_id TEXT NOT NULL DEFAULT '';
