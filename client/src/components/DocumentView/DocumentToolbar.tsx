import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Eye, EyeOff, Trash2 } from 'lucide-react';
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
    <>
      <button
        className="header-search-btn"
        onClick={onToggleHideCompleted}
        aria-label={hideCompleted ? 'Show completed' : 'Hide completed'}
        title={hideCompleted ? 'Show completed' : 'Hide completed'}
      >
        {hideCompleted ? <Eye size={20} strokeWidth={1.5} /> : <EyeOff size={20} strokeWidth={1.5} />}
      </button>
      <button
        className="header-search-btn"
        onClick={handleDeleteCompleted}
        aria-label="Delete completed"
        title="Delete completed"
        style={{ color: 'var(--color-accent-danger)' }}
      >
        <Trash2 size={20} strokeWidth={1.5} />
      </button>
    </>
  );
}
