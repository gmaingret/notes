import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../api/client';

type Props = {
  onDismiss: () => void;
};

export function UndoToast({ onDismiss }: Props) {
  const queryClient = useQueryClient();

  // Auto-dismiss after 4 seconds
  useEffect(() => {
    const timer = setTimeout(onDismiss, 4000);
    return () => clearTimeout(timer);
  }, [onDismiss]);

  async function handleUndo() {
    await apiClient.post('/api/undo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
    onDismiss();
  }

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 80,
        left: '50%',
        transform: 'translateX(-50%)',
        background: 'var(--color-bg-raised)',
        color: '#fff',
        padding: '8px 16px',
        borderRadius: 4,
        display: 'flex',
        gap: 12,
        alignItems: 'center',
        zIndex: 2000,
      }}
    >
      <span>Bullet deleted</span>
      <button
        onClick={() => void handleUndo()}
        style={{
          background: 'none',
          border: '1px solid #fff',
          color: '#fff',
          padding: '2px 8px',
          borderRadius: 3,
          cursor: 'pointer',
          fontSize: '0.875rem',
        }}
      >
        Undo
      </button>
    </div>
  );
}
