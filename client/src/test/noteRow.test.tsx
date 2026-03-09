import { describe, it } from 'vitest';

// Phase 4 RED stubs — NoteRow component tests
// Will be made GREEN in plan 04-02 (note field) and 04-04 (UI)
//
// CMT-01: BulletNode with non-empty note renders NoteRow below BulletContent
// CMT-02: Clicking note icon in focus toolbar focuses NoteRow contenteditable
// CMT-04: Clearing NoteRow text triggers PATCH with note=null

describe('NoteRow', () => {
  it('NoteRow renders when bullet has note', async () => {
    // CMT-01: bullet.note = 'some text' → NoteRow rendered below BulletContent
    throw new Error('not implemented — implement in 04-04');
  });

  it('NoteRow hidden when bullet note is null', async () => {
    // CMT-01: bullet.note = null → NoteRow not rendered
    throw new Error('not implemented — implement in 04-04');
  });

  it('clearing note text triggers patch with null', async () => {
    // CMT-04: clearing NoteRow → PATCH {note: null} sent to server
    throw new Error('not implemented — implement in 04-04');
  });
});
