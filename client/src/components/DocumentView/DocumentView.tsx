import type { Document } from '../../hooks/useDocuments';

type Props = { document: Document };

export function DocumentView({ document }: Props) {
  return (
    <div style={{ padding: '2rem 3rem', maxWidth: 720, margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 600, margin: '0 0 1.5rem', color: '#111' }}>
        {document.title}
      </h1>

      {/* Placeholder bullet — Phase 2 will replace this with the real bullet tree */}
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.5rem' }}>
        <span style={{ color: '#999', fontSize: '0.875rem', userSelect: 'none' }}>•</span>
        <div
          contentEditable
          suppressContentEditableWarning
          style={{
            flex: 1,
            outline: 'none',
            fontSize: '0.9375rem',
            color: '#333',
            lineHeight: 1.6,
            minHeight: '1.6em',
          }}
          data-placeholder="Start typing..."
        />
      </div>
      <p style={{ color: '#bbb', fontSize: '0.75rem', marginTop: '3rem' }}>
        Bullet editing coming in Phase 2
      </p>
    </div>
  );
}
