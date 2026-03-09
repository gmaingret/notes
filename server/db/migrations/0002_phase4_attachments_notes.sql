ALTER TABLE bullets ADD COLUMN note text;

CREATE TABLE IF NOT EXISTS attachments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  bullet_id uuid NOT NULL REFERENCES bullets(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  filename text NOT NULL,
  mime_type text NOT NULL,
  size bigint NOT NULL,
  storage_path text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS attachments_bullet_id_idx ON attachments(bullet_id);
CREATE INDEX IF NOT EXISTS attachments_user_id_idx ON attachments(user_id);
