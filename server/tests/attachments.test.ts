import { describe, it } from 'vitest';

// Phase 4 RED stubs — attachment service tests
// These tests will be made GREEN in plan 04-03
//
// ATT-01: uploadAttachment returns {id, filename, mimeType, size, bulletId}
// ATT-06: storagePath starts with /data/attachments
// ATT-02: getAttachmentsByBullet returns array
// deleteAttachment returns {deleted: true}

describe('attachmentService.uploadAttachment', () => {
  it('uploads attachment and returns record', () => {
    // uploadAttachment(userId, bulletId, file) → {id, filename, mimeType, size, bulletId}
    throw new Error('not implemented — implement in 04-03');
  });

  it('storagePath is under /data/attachments', () => {
    // ATT-06: uploaded file stored at /data/attachments/<uuid>-<filename>
    throw new Error('not implemented — implement in 04-03');
  });
});

describe('attachmentService.getAttachmentsByBullet', () => {
  it('lists attachments by bullet', () => {
    // ATT-02: getAttachmentsByBullet(userId, bulletId) → Attachment[]
    throw new Error('not implemented — implement in 04-03');
  });
});

describe('attachmentService.deleteAttachment', () => {
  it('deletes attachment removes record', () => {
    // deleteAttachment(userId, attachmentId) → {deleted: true}
    throw new Error('not implemented — implement in 04-03');
  });
});
