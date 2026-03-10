import { useState } from 'react';
import { useTagCounts } from '../../hooks/useTags';
import { useUiStore } from '../../store/uiStore';

export function TagBrowser() {
  const [filterText, setFilterText] = useState('');
  const { data: tags = [], isLoading } = useTagCounts();
  const { setCanvasView } = useUiStore();

  // Filter by filterText
  const filtered = filterText
    ? tags.filter(t => t.value.toLowerCase().includes(filterText.toLowerCase()))
    : tags;

  // Group: tag, mention, date
  const groups: Array<{ label: string; type: 'tag' | 'mention' | 'date'; prefix: string }> = [
    { label: 'Tags (#)', type: 'tag', prefix: '#' },
    { label: 'Mentions (@)', type: 'mention', prefix: '@' },
    { label: 'Dates (!!)', type: 'date', prefix: '!!' },
  ];

  function handleTagClick(chipType: 'tag' | 'mention' | 'date', chipValue: string) {
    setCanvasView({ type: 'filtered', chipType, chipValue });
  }

  return (
    <div style={{ padding: '0.5rem 0' }}>
      <div style={{ padding: '0 0.75rem 0.5rem' }}>
        <input
          type="text"
          placeholder="Filter tags..."
          value={filterText}
          onChange={e => setFilterText(e.target.value)}
          style={{
            width: '100%', boxSizing: 'border-box', padding: '0.3rem 0.5rem',
            border: '1px solid var(--color-border-default)', borderRadius: 4, fontSize: '0.95rem',
            background: 'var(--color-bg-base)', outline: 'none',
          }}
        />
      </div>
      {isLoading && <div style={{ padding: '0.5rem 0.75rem', fontSize: '0.95rem' }} className="tag-browser-loading">Loading...</div>}
      {groups.map(group => {
        const items = filtered.filter(t => t.chipType === group.type);
        if (items.length === 0) return null;
        return (
          <div key={group.type}>
            <div style={{ padding: '0.4rem 0.75rem', fontWeight: 600 }} className="tag-section-header">
              {group.label}
            </div>
            {items.map(tag => (
              <button
                key={`${tag.chipType}-${tag.value}`}
                onClick={() => handleTagClick(tag.chipType, tag.value)}
                className="tag-btn"
                style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  width: '100%', padding: '0.3rem 0.75rem',
                  textAlign: 'left', fontSize: '1rem',
                }}
              >
                <span>{group.prefix}{tag.value}</span>
                <span style={{ fontSize: '0.875rem' }} className="tag-count">{tag.count}</span>
              </button>
            ))}
          </div>
        );
      })}
      {!isLoading && filtered.length === 0 && (
        <div style={{ padding: '0.5rem 0.75rem', fontSize: '0.95rem' }} className="tag-browser-empty">No tags found.</div>
      )}
    </div>
  );
}
