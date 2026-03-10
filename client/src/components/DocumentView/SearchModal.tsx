import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSearch } from '../../hooks/useSearch';
import { FilteredBulletList } from './FilteredBulletList';

type Props = {
  onClose: () => void;
};

export function SearchModal({ onClose }: Props) {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);

  // Debounce: update debouncedQuery 300ms after query changes
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(t);
  }, [query]);

  // Auto-focus input on mount
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Close on Escape key
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  const { data: results = [], isLoading } = useSearch(debouncedQuery);

  return (
    <>
      {/* Backdrop */}
      <div
        style={{ position: 'fixed', inset: 0, background: 'var(--color-bg-overlay)', zIndex: 1000 }}
        onClick={onClose}
      />
      {/* Modal box */}
      <div
        style={{
          position: 'fixed',
          top: '20%',
          left: '50%',
          transform: 'translateX(-50%)',
          width: 'min(600px, 90vw)',
          background: 'var(--color-bg-raised)',
          borderRadius: 8,
          boxShadow: '0 8px 32px rgba(0,0,0,0.2)',
          zIndex: 1001,
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <input
          ref={inputRef}
          type="text"
          placeholder="Search bullets..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{
            display: 'block',
            width: '100%',
            boxSizing: 'border-box',
            padding: '0.875rem 1rem',
            border: 'none',
            outline: 'none',
            fontSize: '1rem',
            borderBottom: '1px solid var(--color-border-default)',
            color: 'var(--color-text-secondary)',
          }}
        />
        {/* Results */}
        <div style={{ maxHeight: 400, overflowY: 'auto' }}>
          {debouncedQuery.length < 2 && (
            <div className="search-modal-empty" style={{ padding: '0.75rem 1rem', fontSize: '0.875rem' }}>
              Type to search...
            </div>
          )}
          {debouncedQuery.length >= 2 && (
            <FilteredBulletList
              title=""
              rows={results.map((r) => ({
                bulletId: r.id,
                bulletContent: r.content,
                documentId: r.documentId,
                documentTitle: r.documentTitle,
                highlightText: debouncedQuery.replace(/^[#@!]+/, '').trim(),
              }))}
              onRowClick={(row) => {
                navigate(`/doc/${row.documentId}#bullet/${row.bulletId}`);
                onClose();
              }}
              isLoading={isLoading}
            />
          )}
        </div>
      </div>
    </>
  );
}
