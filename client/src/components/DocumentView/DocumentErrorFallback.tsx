import type { FallbackProps } from 'react-error-boundary';

export function DocumentErrorFallback({ error, resetErrorBoundary }: FallbackProps) {
  console.error('[DocumentErrorFallback] Rendering error caught:', error);

  return (
    <div
      style={{
        maxWidth: 400,
        margin: '4rem auto 0',
        padding: '2rem',
        borderRadius: 8,
        background: 'var(--color-bg-raised)',
        border: '1px solid var(--color-border-default)',
        textAlign: 'center',
      }}
    >
      <h2
        style={{
          margin: '0 0 0.75rem',
          fontSize: '1.1rem',
          fontWeight: 600,
          color: 'var(--color-text-primary)',
        }}
      >
        Something went wrong
      </h2>
      <p
        style={{
          margin: '0 0 1.5rem',
          fontSize: '0.9rem',
          color: 'var(--color-text-muted)',
          lineHeight: 1.5,
        }}
      >
        This document could not be displayed. Try reloading it, or navigate to a
        different document.
      </p>
      <button
        onClick={resetErrorBoundary}
        style={{
          padding: '0.5rem 1.25rem',
          borderRadius: 6,
          border: '1px solid var(--color-border-default)',
          background: 'var(--color-bg-surface)',
          color: 'var(--color-text-primary)',
          cursor: 'pointer',
          fontSize: '0.875rem',
          fontWeight: 500,
        }}
      >
        Reload document
      </button>
    </div>
  );
}
