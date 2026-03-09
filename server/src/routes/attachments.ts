import { Router } from 'express';
import multer, { MulterError } from 'multer';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { unlink } from 'node:fs/promises';
import { requireAuth } from '../middleware/auth.js';
import {
  createAttachment,
  getAttachmentsByBullet,
  getAttachment,
  deleteAttachment,
} from '../services/attachmentService.js';

const storage = multer.diskStorage({
  destination: '/data/attachments',
  filename: (_req, file, cb) => {
    const ext = path.extname(file.originalname);
    cb(null, `${randomUUID()}${ext}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 100 * 1024 * 1024 }, // 100MB
});

export const attachmentsRouter = Router();

// POST /api/attachments/bullets/:bulletId — upload file
attachmentsRouter.post(
  '/bullets/:bulletId',
  requireAuth,
  (req, res, next) => {
    upload.single('file')(req, res, (err) => {
      if (err instanceof MulterError && err.code === 'LIMIT_FILE_SIZE') {
        return res.status(413).json({ error: 'File too large (max 100MB)' });
      }
      if (err) return next(err);
      next();
    });
  },
  async (req, res) => {
    const user = req.user as { id: string };
    const bulletId = req.params.bulletId as string;

    if (!req.file) {
      return res.status(400).json({ error: 'No file uploaded' });
    }

    try {
      const attachment = await createAttachment(user.id, bulletId, {
        filename: req.file.originalname,
        mimeType: req.file.mimetype,
        size: req.file.size,
        storagePath: req.file.path,
      });
      res.status(201).json(attachment);
    } catch (err) {
      // Prevent orphaned file if DB insert fails
      try {
        await unlink(req.file.path);
      } catch {
        // ignore unlink errors
      }
      console.error(err);
      res.status(500).json({ error: 'Internal server error' });
    }
  }
);

// GET /api/attachments/bullets/:bulletId — list attachments for bullet
attachmentsRouter.get('/bullets/:bulletId', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    const list = await getAttachmentsByBullet(user.id, req.params.bulletId as string);
    res.json(list);
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// GET /api/attachments/:id/file — stream file (auth required)
attachmentsRouter.get('/:id/file', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    const attachment = await getAttachment(user.id, req.params.id as string);
    if (!attachment) {
      return res.status(404).json({ error: 'Attachment not found' });
    }
    res.setHeader('Content-Type', attachment.mimeType);
    res.setHeader(
      'Content-Disposition',
      `inline; filename="${attachment.filename}"`
    );
    res.sendFile(attachment.storagePath, { root: '/' });
  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// DELETE /api/attachments/:id — delete attachment
attachmentsRouter.delete('/:id', requireAuth, async (req, res) => {
  const user = req.user as { id: string };
  try {
    await deleteAttachment(user.id, req.params.id as string);
    res.status(204).send();
  } catch (err: any) {
    if (err.message === 'Attachment not found or access denied') {
      return res.status(404).json({ error: err.message });
    }
    console.error(err);
    res.status(500).json({ error: 'Internal server error' });
  }
});
