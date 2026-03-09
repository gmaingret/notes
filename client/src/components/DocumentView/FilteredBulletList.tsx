export type FilteredBulletRow = {
  bulletId: string;
  bulletContent: string;
  documentId: string;
  documentTitle: string;
  isBookmarked?: boolean;
  highlightText?: string;
};

type Props = {
  rows: FilteredBulletRow[];
  title: string;
  onRowClick: (row: FilteredBulletRow) => void;
  onToggleBookmark?: (row: FilteredBulletRow) => void;
  isLoading?: boolean;
};

function highlight(content: string, query: string): string {
  if (!query) return content;
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return content.replace(
    new RegExp(escaped, 'gi'),
    (match) => `<mark style="background:#fff3cd;border-radius:2px">${match}</mark>`
  );
}

export function FilteredBulletList({
  rows,
  title,
  onRowClick,
  onToggleBookmark,
  isLoading,
}: Props) {
  return (
    <div>
      <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '1rem' }}>{title}</h2>

      {isLoading && <div style={{ color: '#999' }}>Loading...</div>}

      {!isLoading && rows.length === 0 && (
        <div style={{ color: '#999' }}>No results.</div>
      )}

      {!isLoading &&
        rows.map((row) => (
          <div
            key={row.bulletId}
            onClick={() => onRowClick(row)}
            style={{
              padding: '0.5rem 0',
              borderBottom: '1px solid #f0f0f0',
              display: 'flex',
              gap: 8,
              cursor: 'pointer',
              alignItems: 'flex-start',
            }}
          >
            {/* Bullet dot */}
            <span style={{ color: '#999', fontSize: '0.75rem', flexShrink: 0 }}>•</span>

            {/* Bullet content */}
            <span style={{ flex: 1, fontSize: '0.9375rem', color: '#333' }}>
              {row.highlightText ? (
                <span
                  dangerouslySetInnerHTML={{
                    __html: highlight(row.bulletContent, row.highlightText),
                  }}
                />
              ) : (
                row.bulletContent
              )}
            </span>

            {/* Document title */}
            <span
              style={{ color: '#999', fontSize: '0.8rem', whiteSpace: 'nowrap', flexShrink: 0 }}
            >
              [{row.documentTitle}]
            </span>

            {/* Bookmark toggle */}
            {onToggleBookmark && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleBookmark(row);
                }}
                style={{
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  padding: 0,
                  fontSize: '1rem',
                  color: '#999',
                  flexShrink: 0,
                }}
                aria-label={row.isBookmarked ? 'Remove bookmark' : 'Add bookmark'}
              >
                {row.isBookmarked ? '★' : '☆'}
              </button>
            )}
          </div>
        ))}
    </div>
  );
}
