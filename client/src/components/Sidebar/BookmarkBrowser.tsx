import { useNavigate } from 'react-router-dom';
import { useBookmarks, useRemoveBookmark } from '../../hooks/useBookmarks';
import { useUiStore } from '../../store/uiStore';

export function BookmarkBrowser() {
  const navigate = useNavigate();
  const { setCanvasView } = useUiStore();
  const { data: bookmarks = [], isLoading } = useBookmarks();
  const removeBookmark = useRemoveBookmark();

  if (isLoading) {
    return <div style={{ padding: '1rem', color: '#999', fontSize: '0.85rem' }}>Loading…</div>;
  }

  if (bookmarks.length === 0) {
    return (
      <div style={{ padding: '1rem', color: '#bbb', fontSize: '0.85rem' }}>
        No bookmarks yet. Star a bullet to bookmark it.
      </div>
    );
  }

  return (
    <div>
      {bookmarks.map(b => (
        <div
          key={b.id}
          style={{
            padding: '0.5rem 0.75rem',
            borderBottom: '1px solid #f0f0f0',
            cursor: 'pointer',
          }}
          onClick={() => {
            navigate(`/doc/${b.documentId}#bullet/${b.id}`);
            setCanvasView({ type: 'document' });
          }}
        >
          <div style={{ fontSize: '0.7rem', color: '#999', marginBottom: 2 }}>
            {b.documentTitle}
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 4 }}>
            <span style={{ fontSize: '0.85rem', color: '#333', flex: 1, lineHeight: 1.4 }}>
              {b.content || <em style={{ color: '#bbb' }}>Empty bullet</em>}
            </span>
            <button
              onClick={(e) => {
                e.stopPropagation();
                removeBookmark.mutate(b.id);
              }}
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                color: '#ccc',
                fontSize: '0.85rem',
                padding: '0 2px',
                lineHeight: 1,
                flexShrink: 0,
              }}
              title="Remove bookmark"
            >
              ×
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
