import 'dotenv/config';
import { migrate } from 'drizzle-orm/node-postgres/migrator';
import { db } from '../db/index.js';
import { createApp } from './app.js';
import { authRouter } from './routes/auth.js';
import { documentsRouter } from './routes/documents.js';
import { bulletsRouter } from './routes/bullets.js';
import { undoRouter } from './routes/undo.js';
import { bookmarksRouter } from './routes/bookmarks.js';
import { tagsRouter } from './routes/tags.js';
import { searchRouter } from './routes/search.js';
import { attachmentsRouter } from './routes/attachments.js';
import path from 'path';
import { fileURLToPath } from 'url';
import express from 'express';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Run migrations before starting
await migrate(db, { migrationsFolder: path.join(__dirname, '../../db/migrations') });

const app = createApp();
app.use('/api/auth', authRouter);
app.use('/api/documents', documentsRouter);
app.use('/api/bullets', bulletsRouter);
app.use('/api', undoRouter);
app.use('/api/bookmarks', bookmarksRouter);
app.use('/api/tags', tagsRouter);
app.use('/api/search', searchRouter);
app.use('/api/attachments', attachmentsRouter);

// Serve React static files in production
if (process.env.NODE_ENV === 'production') {
  const publicPath = path.join(__dirname, '../../../public');
  app.use(express.static(publicPath));
  app.get('/{*path}', (_req, res) => {
    res.sendFile(path.join(publicPath, 'index.html'));
  });
}

const port = Number(process.env.PORT) || 3000;
app.listen(port, () => console.log(`Server running on :${port}`));
