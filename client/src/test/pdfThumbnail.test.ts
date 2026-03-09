import { describe, it, vi } from 'vitest';

// Phase 4 RED stub — PDF thumbnail rendering test
// Will be made GREEN in plan 04-04
//
// ATT-04: renderPdfThumbnail(blob, canvas) calls pdfjs getDocument

// Mock pdfjs-dist so import doesn't crash when module is not installed
vi.mock('pdfjs-dist', () => ({
  getDocument: vi.fn(),
  GlobalWorkerOptions: { workerSrc: '' },
}));

describe('renderPdfThumbnail', () => {
  it('renders first page to canvas', async () => {
    // ATT-04: renderPdfThumbnail(blob, canvas) loads PDF via pdfjs and draws first page
    throw new Error('not implemented — implement in 04-04');
  });
});
