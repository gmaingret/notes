import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock pdfjs-dist so the test runs even without a real PDF worker
// Note: vi.mock is hoisted, so we cannot reference `const` variables in the factory.
// Use vi.fn() inline and access the mock via import after mocking.
vi.mock('pdfjs-dist', () => ({
  getDocument: vi.fn(),
  GlobalWorkerOptions: { workerSrc: '' },
  version: '4.0.0',
}));

import * as pdfjsLib from 'pdfjs-dist';
import { renderPdfThumbnail } from '../components/DocumentView/AttachmentRow';

describe('renderPdfThumbnail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders first page to canvas', async () => {
    // ATT-04: renderPdfThumbnail(blob, canvas) loads PDF via pdfjs and draws first page

    // Set up mock canvas
    const canvas = {
      getContext: vi.fn().mockReturnValue({
        drawImage: vi.fn(),
      }),
      width: 0,
      height: 0,
    } as unknown as HTMLCanvasElement;

    // Create a minimal viewport/render mock chain
    const renderContextMock = { promise: Promise.resolve() };
    const pageMock = {
      getViewport: vi.fn().mockReturnValue({ width: 100, height: 150, scale: 0.3 }),
      render: vi.fn().mockReturnValue(renderContextMock),
    };
    const pdfDocMock = {
      getPage: vi.fn().mockResolvedValue(pageMock),
    };
    const getDocumentMock = pdfjsLib.getDocument as ReturnType<typeof vi.fn>;
    getDocumentMock.mockReturnValue({ promise: Promise.resolve(pdfDocMock) });

    const blob = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    await renderPdfThumbnail(blob, canvas);

    // Verify pdfjs getDocument was called
    expect(getDocumentMock).toHaveBeenCalled();
    // Verify page 1 was fetched
    expect(pdfDocMock.getPage).toHaveBeenCalledWith(1);
    // Verify render was called
    expect(pageMock.render).toHaveBeenCalled();
  });
});
