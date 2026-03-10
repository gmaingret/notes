import { useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../api/client';
import { useUiStore } from '../../store/uiStore';
import { SearchModal } from './SearchModal';

type Props = {
  documentId: string;
  hideCompleted: boolean;
  onToggleHideCompleted: () => void;
};

export function DocumentToolbar({ documentId, hideCompleted, onToggleHideCompleted }: Props) {
  const queryClient = useQueryClient();
  const { searchOpen, setSearchOpen } = useUiStore();

  async function handleDeleteCompleted() {
    const confirmed = window.confirm(
      'Permanently delete all completed bullets? This cannot be undone.'
    );
    if (!confirmed) return;

    await apiClient.delete<void>(`/api/bullets/documents/${documentId}/completed`);
    queryClient.invalidateQueries({ queryKey: ['bullets', documentId] });
  }

  return (
    <>
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
          onClick={() => setSearchOpen(true)}
          className="toolbar-btn"
          style={{ fontSize: '0.8rem', background: 'none', border: 'none', cursor: 'pointer' }}
          title="Search (Ctrl+F)"
        >
          Search
        </button>

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
      {searchOpen && <SearchModal onClose={() => setSearchOpen(false)} />}
    </>
  );
}
