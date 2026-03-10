import { useNavigate } from 'react-router-dom';
import { useBookmarks, useRemoveBookmark } from '../../hooks/useBookmarks';
import { useUiStore } from '../../store/uiStore';

export function BookmarkBrowser() {
  const navigate = useNavigate();
  const { setCanvasView } = useUiStore();
  const { data: bookmarks = [], isLoading } = useBookmarks();
  const removeBookmark = useRemoveBookmark();

  if (isLoading) {
    return <div style={{ padding: '1rem', fontSize: '1rem' }} className="bookmark-browser-loading">Loading…</div>;
  }

  if (bookmarks.length === 0) {
    return (
      <div style={{ padding: '1rem', fontSize: '1rem' }} className="bookmark-browser-empty">
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
            borderBottom: '1px solid var(--color-border-subtle)',
            cursor: 'pointer',
          }}
          onClick={() => {
            navigate(`/doc/${b.documentId}#bullet/${b.id}`);
            setCanvasView({ type: 'document' });
          }}
        >
          <div style={{ fontSize: '0.7rem', marginBottom: 2 }} className="bookmark-date">
            {b.documentTitle}
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 4 }}>
            <span style={{ fontSize: '1rem', flex: 1, lineHeight: 1.4 }} className="bookmark-content">
              {b.content || <em className="bookmark-empty-bullet">Empty bullet</em>}
            </span>
            <button
              onClick={(e) => {
                e.stopPropagation();
                removeBookmark.mutate(b.id);
              }}
              className="bookmark-remove-btn"
              style={{
                fontSize: '1rem',
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
