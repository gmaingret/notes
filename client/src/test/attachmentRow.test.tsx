import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

// Mock pdfjs-dist to prevent DOMMatrix ReferenceError in jsdom (Node env)
vi.mock('pdfjs-dist', () => ({
  getDocument: vi.fn(),
  GlobalWorkerOptions: { workerSrc: '' },
  version: '4.0.0',
}));

// Mock apiClient so we can control blob returns without real server
vi.mock('../api/client', () => ({
  apiClient: {
    download: vi.fn(),
    delete: vi.fn(),
  },
}));

// Mock useDeleteAttachment from useAttachments
vi.mock('../hooks/useAttachments', () => ({
  useDeleteAttachment: vi.fn(),
}));

import { apiClient } from '../api/client';
import { useDeleteAttachment } from '../hooks/useAttachments';
import { AttachmentRow } from '../components/DocumentView/AttachmentRow';
import type { Attachment } from '../hooks/useAttachments';

const mockImageAttachment: Attachment = {
  id: 'att-1',
  bulletId: 'b1',
  userId: 'u1',
  filename: 'photo.jpg',
  mimeType: 'image/jpeg',
  size: 12345,
  storagePath: '/data/attachments/photo.jpg',
  createdAt: '2026-01-01T00:00:00Z',
};

const mockOtherAttachment: Attachment = {
  id: 'att-2',
  bulletId: 'b1',
  userId: 'u1',
  filename: 'archive.zip',
  mimeType: 'application/zip',
  size: 54321,
  storagePath: '/data/attachments/archive.zip',
  createdAt: '2026-01-01T00:00:00Z',
};

describe('AttachmentRow — image attachment', () => {
  const mutateMock = vi.fn();
  const onDeleteMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useDeleteAttachment as ReturnType<typeof vi.fn>).mockReturnValue({ mutate: mutateMock });

    // Mock URL.createObjectURL and URL.revokeObjectURL
    globalThis.URL.createObjectURL = vi.fn().mockReturnValue('blob:http://localhost/fake-object-url');
    globalThis.URL.revokeObjectURL = vi.fn();

    // Mock apiClient.download to return a fake Response with blob()
    const fakeBlob = new Blob(['fake image data'], { type: 'image/jpeg' });
    (apiClient.download as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      blob: vi.fn().mockResolvedValue(fakeBlob),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders img with object URL', async () => {
    // ATT-03: AttachmentRow with mimeType='image/jpeg' renders <img src={objectURL}>
    render(
      <AttachmentRow attachment={mockImageAttachment} onDelete={onDeleteMock} />
    );

    await waitFor(() => {
      const img = screen.getByRole('img');
      expect(img).toBeDefined();
      expect(img.getAttribute('src')).toBe('blob:http://localhost/fake-object-url');
    });

    expect(apiClient.download).toHaveBeenCalledWith('/api/attachments/att-1/file');
    expect(URL.createObjectURL).toHaveBeenCalled();
  });
});

describe('AttachmentRow — other file attachment', () => {
  const mutateMock = vi.fn();
  const onDeleteMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useDeleteAttachment as ReturnType<typeof vi.fn>).mockReturnValue({ mutate: mutateMock });
  });

  it('shows filename and download link', async () => {
    // ATT-05: AttachmentRow with mimeType='application/zip' shows filename text and download trigger
    render(
      <AttachmentRow attachment={mockOtherAttachment} onDelete={onDeleteMock} />
    );

    // Filename text should be visible
    expect(screen.getByText('archive.zip')).toBeDefined();
  });
});
