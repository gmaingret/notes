import { useState, useEffect, useRef } from 'react';
import * as pdfjsLib from 'pdfjs-dist';
import { apiClient } from '../../api/client';
import type { Attachment } from '../../hooks/useAttachments';
import { Lightbox } from './Lightbox';

// Use the .mjs worker from unpkg — must match installed pdfjs-dist version
pdfjsLib.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjsLib.version}/build/pdf.worker.min.mjs`;

type Props = {
  attachment: Attachment;
  onDelete: () => void;
};

/**
 * Renders page 1 of a PDF blob to a canvas at scale 0.3.
 * Exported for unit testing.
 */
export async function renderPdfThumbnail(blob: Blob, canvas: HTMLCanvasElement): Promise<void> {
  const arrayBuffer = await blob.arrayBuffer();
  const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer });
  const pdf = await loadingTask.promise;
  const page = await pdf.getPage(1);
  const viewport = page.getViewport({ scale: 0.3 });
  canvas.width = viewport.width;
  canvas.height = viewport.height;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  await page.render({ canvasContext: ctx, viewport, canvas }).promise;
}

function ImageAttachmentRow({ attachment, onDelete }: Props) {
  const [imgSrc, setImgSrc] = useState<string | null>(null);
  const [lightboxOpen, setLightboxOpen] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    apiClient.download(`/api/attachments/${attachment.id}/file`).then(async (res) => {
      if (!res.ok) return;
      const blob = await res.blob();
      objectUrl = URL.createObjectURL(blob);
      setImgSrc(objectUrl);
    });
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [attachment.id]);

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 24, marginTop: 4 }}>
      {imgSrc && (
        <img
          src={imgSrc}
          alt={attachment.filename}
          style={{ height: 80, objectFit: 'cover', cursor: 'pointer', borderRadius: 4 }}
          onClick={() => setLightboxOpen(true)}
        />
      )}
      <button
        onClick={onDelete}
        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#aaa', fontSize: '1rem' }}
        title="Delete attachment"
      >
        ×
      </button>
      {lightboxOpen && imgSrc && (
        <Lightbox src={imgSrc} onClose={() => setLightboxOpen(false)} />
      )}
    </div>
  );
}

function PdfAttachmentRow({ attachment, onDelete }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!canvasRef.current) return;
    const canvas = canvasRef.current;
    apiClient.download(`/api/attachments/${attachment.id}/file`).then(async (res) => {
      const blob = await res.blob();
      await renderPdfThumbnail(blob, canvas);
    });
  }, [attachment.id]);

  function handleClick() {
    apiClient.download(`/api/attachments/${attachment.id}/file`).then(async (res) => {
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      window.open(url);
      // Revoke after a short delay to allow the new tab to load the blob
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    });
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 24, marginTop: 4 }}>
      <canvas
        ref={canvasRef}
        style={{ cursor: 'pointer', border: '1px solid #ddd', borderRadius: 4 }}
        onClick={handleClick}
        title={attachment.filename}
      />
      <span style={{ fontSize: '0.85em', color: '#666' }}>{attachment.filename}</span>
      <button
        onClick={onDelete}
        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#aaa', fontSize: '1rem' }}
        title="Delete attachment"
      >
        ×
      </button>
    </div>
  );
}

function OtherAttachmentRow({ attachment, onDelete }: Props) {
  function handleClick() {
    apiClient.download(`/api/attachments/${attachment.id}/file`).then(async (res) => {
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = attachment.filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    });
  }

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginLeft: 24, marginTop: 4 }}>
      <span style={{ fontSize: '1.1em' }}>📎</span>
      <span
        style={{ fontSize: '0.85em', color: '#555', cursor: 'pointer', textDecoration: 'underline' }}
        onClick={handleClick}
      >
        {attachment.filename}
      </span>
      <button
        onClick={onDelete}
        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#aaa', fontSize: '1rem' }}
        title="Delete attachment"
      >
        ×
      </button>
    </div>
  );
}

export function AttachmentRow({ attachment, onDelete }: Props) {
  if (attachment.mimeType.startsWith('image/')) {
    return <ImageAttachmentRow attachment={attachment} onDelete={onDelete} />;
  }
  if (attachment.mimeType === 'application/pdf') {
    return <PdfAttachmentRow attachment={attachment} onDelete={onDelete} />;
  }
  return <OtherAttachmentRow attachment={attachment} onDelete={onDelete} />;
}
