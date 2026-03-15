import { db } from '../../db/index.js';
import { attachments, bullets } from '../../db/schema.js';
import { eq, and } from 'drizzle-orm';
import { unlink } from 'node:fs/promises';

export async function verifyBulletOwnership(userId: string, bulletId: string): Promise<boolean> {
  const [bullet] = await db
    .select({ id: bullets.id })
    .from(bullets)
    .where(and(eq(bullets.id, bulletId), eq(bullets.userId, userId)));
  return !!bullet;
}

export interface AttachmentFile {
  filename: string;
  mimeType: string;
  size: number;
  storagePath: string;
}

export async function createAttachment(
  userId: string,
  bulletId: string,
  file: AttachmentFile
) {
  const [attachment] = await db
    .insert(attachments)
    .values({
      userId,
      bulletId,
      filename: file.filename,
      mimeType: file.mimeType,
      size: file.size,
      storagePath: file.storagePath,
    })
    .returning();
  return attachment;
}

export async function getAttachmentsByBullet(userId: string, bulletId: string) {
  return db
    .select()
    .from(attachments)
    .where(and(eq(attachments.userId, userId), eq(attachments.bulletId, bulletId)));
}

export async function getAttachment(userId: string, attachmentId: string) {
  const [attachment] = await db
    .select()
    .from(attachments)
    .where(and(eq(attachments.id, attachmentId), eq(attachments.userId, userId)));
  return attachment ?? null;
}

export async function deleteAttachment(userId: string, attachmentId: string) {
  const attachment = await getAttachment(userId, attachmentId);
  if (!attachment) {
    throw new Error('Attachment not found or access denied');
  }

  // Delete file from disk (ignore ENOENT — file already gone)
  try {
    await unlink(attachment.storagePath);
  } catch (err: any) {
    if (err.code !== 'ENOENT') throw err;
  }

  // Delete DB record
  await db.delete(attachments).where(eq(attachments.id, attachmentId));

  return { deleted: true };
}
