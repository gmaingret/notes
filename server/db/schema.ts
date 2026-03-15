import {
  pgTable, uuid, text, timestamp, doublePrecision,
  boolean, bigserial, integer, jsonb, index, uniqueIndex, bigint
} from 'drizzle-orm/pg-core';

export const users = pgTable('users', {
  id: uuid('id').primaryKey().defaultRandom(),
  email: text('email').notNull().unique(),
  passwordHash: text('password_hash'),       // null for OAuth-only accounts
  googleId: text('google_id').unique(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
});

export const documents = pgTable('documents', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  title: text('title').notNull().default('Untitled'),
  position: doublePrecision('position').notNull().default(1.0),  // FLOAT8 — locked decision
  lastOpenedAt: timestamp('last_opened_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('documents_user_id_idx').on(t.userId),
]);

export const bullets = pgTable('bullets', {
  id: uuid('id').primaryKey().defaultRandom(),
  documentId: uuid('document_id').notNull().references(() => documents.id, { onDelete: 'cascade' }),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  parentId: uuid('parent_id'),               // self-ref nullable = root bullet
  content: text('content').notNull().default(''),
  position: doublePrecision('position').notNull().default(1.0),  // FLOAT8 sibling order
  isComplete: boolean('is_complete').notNull().default(false),
  isCollapsed: boolean('is_collapsed').notNull().default(false),
  note: text('note'),                                            // nullable — CMT-01
  deletedAt: timestamp('deleted_at', { withTimezone: true }),    // soft delete — locked decision
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('bullets_document_id_idx').on(t.documentId),
  index('bullets_parent_id_idx').on(t.parentId),
]);

export const undoEvents = pgTable('undo_events', {
  id: bigserial('id', { mode: 'number' }).primaryKey(),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  seq: integer('seq').notNull(),
  schemaVersion: integer('schema_version').notNull().default(1), // CRITICAL: must be here from day 1
  eventType: text('event_type').notNull(),
  forwardOp: jsonb('forward_op').notNull(),
  inverseOp: jsonb('inverse_op').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('undo_events_user_id_seq_idx').on(t.userId, t.seq),
]);

export const undoCursors = pgTable('undo_cursors', {
  userId: uuid('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
  currentSeq: integer('current_seq').notNull().default(0),
});

export const bookmarks = pgTable('bookmarks', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  bulletId: uuid('bullet_id').notNull().references(() => bullets.id, { onDelete: 'cascade' }),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('bookmarks_user_id_idx').on(t.userId),
  uniqueIndex('bookmarks_user_bullet_idx').on(t.userId, t.bulletId),
]);

export const attachments = pgTable('attachments', {
  id: uuid('id').primaryKey().defaultRandom(),
  bulletId: uuid('bullet_id').notNull().references(() => bullets.id, { onDelete: 'cascade' }),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  filename: text('filename').notNull(),
  mimeType: text('mime_type').notNull(),
  size: bigint('size', { mode: 'number' }).notNull(),
  storagePath: text('storage_path').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('attachments_bullet_id_idx').on(t.bulletId),
  index('attachments_user_id_idx').on(t.userId),
]);

export const refreshTokens = pgTable('refresh_tokens', {
  id: uuid('id').primaryKey().defaultRandom(),
  userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
  tokenHash: text('token_hash').notNull(),  // SHA-256 hash of the JWT, not the JWT itself
  expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  revokedAt: timestamp('revoked_at', { withTimezone: true }),  // null = active, set = revoked
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
}, (t) => [
  index('refresh_tokens_user_id_idx').on(t.userId),
  index('refresh_tokens_token_hash_idx').on(t.tokenHash),
]);
