import { useQueryClient } from '@tanstack/react-query';
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

    await apiClient.delete<void>(`/api/bullets/documents/${documentId}/completed`);
    queryClient.invalidateQueries({ queryKey: ['bullets', documentId] });
  }

  return (
    <div
      style={{
        display: 'flex',
        gap: 8,
        alignItems: 'center',
        padding: '4px 0',
        borderBottom: '1px solid #f0f0f0',
        marginBottom: 8,
      }}
    >
      <button
        onClick={onToggleHideCompleted}
        style={{
          fontSize: '0.8rem',
          color: hideCompleted ? '#4A90E2' : '#999',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
        }}
      >
        {hideCompleted ? 'Show completed' : 'Hide completed'}
      </button>
      <button
        onClick={handleDeleteCompleted}
        style={{
          fontSize: '0.8rem',
          color: '#e55',
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
