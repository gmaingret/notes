import {
  pgTable, uuid, text, timestamp, doublePrecision,
  boolean, bigserial, integer, jsonb, index, uniqueIndex
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
