import { useEffect } from 'react';

type Props = {
  src: string;
  onClose: () => void;
};

export function Lightbox({ src, onClose }: Props) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.85)',
        zIndex: 3000,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
      onClick={onClose}
    >
      <img
        src={src}
        alt="Attachment full view"
        style={{
          maxWidth: '90vw',
          maxHeight: '90vh',
          objectFit: 'contain',
        }}
        onClick={(e) => e.stopPropagation()}
      />
    </div>
  );
}
