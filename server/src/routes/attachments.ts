import { Router } from 'express';
import multer, { MulterError } from 'multer';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

const EXT_MIME: Record<string, string> = {
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png',
  '.gif': 'image/gif', '.webp': 'image/webp', '.avif': 'image/avif',
  '.svg': 'image/svg+xml', '.pdf': 'application/pdf',
};

function resolveMimeType(filename: string, browserMime: string): string {
  if (browserMime && browserMime !== 'application/octet-stream') return browserMime;
  const ext = path.extname(filename).toLowerCase();
  return EXT_MIME[ext] ?? browserMime;
}
import { unlink } from 'node:fs/promises';
import { requireAuth } from '../middleware/auth.js';
import {
  createAttachment,
  getAttachmentsByBullet,
  getAttachment,
  deleteAttachment,
  verifyBulletOwnership,
} from '../services/attachmentService.js';

const ALLOWED_EXTENSIONS = new Set([
  '.jpg', '.jpeg', '.png', '.gif', '.webp', '.avif', '.svg',
  '.pdf', '.txt', '.csv', '.json', '.xml', '.md',
  '.docx', '.xlsx', '.pptx',
  '.zip', '.gz', '.tar',
]);

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
  fileFilter: (_req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    if (ALLOWED_EXTENSIONS.has(ext)) {
      cb(null, true);
    } else {
      cb(new Error(`File type ${ext || '(none)'} is not allowed. Supported: images, PDFs, documents, archives`));
    }
  },
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
      if (err) {
        // fileFilter rejection comes as a plain Error with our message
        return res.status(400).json({ error: err.message });
      }
      next();
    });
  },
  async (req, res) => {
    const user = req.user as { id: string };
    const bulletId = req.params.bulletId as string;

    if (!req.file) {
      return res.status(400).json({ error: 'No file uploaded' });
    }

    // Verify bullet belongs to the authenticated user
    const owned = await verifyBulletOwnership(user.id, bulletId);
    if (!owned) {
      // Delete the uploaded file to prevent orphan
      try { await unlink(req.file.path); } catch { /* ignore */ }
      return res.status(404).json({ error: 'Bullet not found' });
    }

    try {
      const attachment = await createAttachment(user.id, bulletId, {
        filename: req.file.originalname,
        mimeType: resolveMimeType(req.file.originalname, req.file.mimetype),
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
  const bulletId = req.params.bulletId as string;
  try {
    // Verify bullet belongs to the authenticated user
    const owned = await verifyBulletOwnership(user.id, bulletId);
    if (!owned) {
      return res.status(404).json({ error: 'Bullet not found' });
    }
    const list = await getAttachmentsByBullet(user.id, bulletId);
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
    // Sanitize filename for use in Content-Disposition header:
    // replace double quotes with single quotes, strip control characters (0x00-0x1F)
    const safeFilename = attachment.filename
      .replace(/[\x00-\x1F]/g, '')
      .replace(/"/g, "'");

    // SVG files must be forced to download (not rendered inline) to prevent stored XSS
    const disposition = attachment.mimeType === 'image/svg+xml' ? 'attachment' : 'inline';

    res.setHeader('Content-Type', attachment.mimeType);
    res.setHeader('Content-Disposition', `${disposition}; filename="${safeFilename}"`);
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
