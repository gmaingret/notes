import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiClient } from '../../api/client';

type Props = {
  documentId: string;
  hideCompleted: boolean;
  onToggleHideCompleted: () => void;
};

export function DocumentToolbar({ documentId, hideCompleted, onToggleHideCompleted }: Props) {
  const queryClient = useQueryClient();

  async function handleDeleteCompleted() {
    const confirmed = window.confirm(
      'Permanently delete all completed bullets? This cannot be undone.'
    );
    if (!confirmed) return;

    try {
      await apiClient.delete<void>(`/api/bullets/documents/${documentId}/completed`);
      queryClient.invalidateQueries({ queryKey: ['bullets', documentId] });
    } catch (err) {
      toast.error('Failed to delete completed bullets', { description: (err as Error).message });
    }
  }

  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        alignItems: 'center',
        padding: '4px 0',
        borderBottom: '1px solid var(--color-border-subtle)',
        marginBottom: 8,
      }}
    >
      <button
        onClick={onToggleHideCompleted}
        className={hideCompleted ? 'toolbar-btn--active' : 'toolbar-btn--inactive'}
        style={{
          fontSize: '0.8rem',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
        }}
      >
        {hideCompleted ? 'Show completed' : 'Hide completed'}
      </button>
      <button
        onClick={handleDeleteCompleted}
        className="toolbar-btn--destructive"
        style={{
          fontSize: '0.8rem',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
        }}
      >
        Delete completed
      </button>
    </div>
  );
}
