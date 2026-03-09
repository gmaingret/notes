import { describe, it } from 'vitest';

// Phase 4 RED stubs — AttachmentRow component tests
// Will be made GREEN in plan 04-04
//
// ATT-03: Image attachment renders img element via object URL
// ATT-05: Non-image/PDF attachment shows filename + download trigger

describe('AttachmentRow — image attachment', () => {
  it('renders img with object URL', async () => {
    // ATT-03: AttachmentRow with mimeType='image/jpeg' renders <img src={objectURL}>
    // Dynamic import avoided — component not yet created
    throw new Error('not implemented — implement in 04-04');
  });
});

describe('AttachmentRow — other file attachment', () => {
  it('shows filename and download link', async () => {
    // ATT-05: AttachmentRow with mimeType='application/zip' shows filename text and download trigger
    throw new Error('not implemented — implement in 04-04');
  });
});
