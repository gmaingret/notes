import { useState } from 'react';
import { useAuth } from '../../contexts/AuthContext';
import { useCreateDocument, useExportAllDocuments } from '../../hooks/useDocuments';
import { DocumentList } from './DocumentList';
import { TagBrowser } from './TagBrowser';
import { BookmarkBrowser } from './BookmarkBrowser';
import { useUiStore } from '../../store/uiStore';

type SidebarProps = {
  activeDocId: string | null;
};

export function Sidebar({ activeDocId }: SidebarProps) {
  const { logout } = useAuth();
  const { mutate: createDocument } = useCreateDocument();
  const { mutate: exportAll } = useExportAllDocuments();
  const { sidebarOpen, setSidebarOpen, sidebarTab, setSidebarTab } = useUiStore();
  const [showSidebarMenu, setShowSidebarMenu] = useState(false);

  const handleCreate = () => { createDocument(undefined); };
  const handleLogout = async () => { await logout(); };

  return (
    <>
      {/* Backdrop — only in DOM on mobile when open, so it never interferes with desktop */}
      {sidebarOpen && (
        <div
          className="sidebar-backdrop"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside className={`sidebar${sidebarOpen ? ' sidebar-open' : ''}`}>
        {/* Header — position:relative here so the dropdown anchors to the full header width */}
        <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid #e0e0e0', display: 'flex', alignItems: 'center', gap: '0.5rem', position: 'relative' }}>
          <span style={{ flex: 1, fontWeight: 600, fontSize: '0.875rem' }}>Notes</span>

          <div>
            <button onClick={() => setShowSidebarMenu(v => !v)} style={iconButtonStyle} title="More options">⋯</button>
            {showSidebarMenu && (
              <>
                {/* Transparent overlay — tapping outside the dropdown closes it (mobile + desktop) */}
                <div
                  style={{ position: 'fixed', inset: 0, zIndex: 99 }}
                  onClick={() => setShowSidebarMenu(false)}
                />
                <div style={dropdownStyle}>
                  <button style={dropdownItemStyle} onClick={() => { exportAll(); setShowSidebarMenu(false); }}>Export all documents</button>
                  <button style={dropdownItemStyle} onClick={() => { handleLogout(); setShowSidebarMenu(false); }}>Logout</button>
                </div>
              </>
            )}
          </div>

          {/* X close — only shown on mobile via CSS (.mobile-close-btn display:none on desktop).
               Do NOT apply display via inline style here — CSS controls visibility. */}
          <button
            className="mobile-close-btn"
            onClick={() => setSidebarOpen(false)}
            style={closeBtnStyle}
            title="Close sidebar"
            aria-label="Close sidebar"
          >
            ✕
          </button>

          <button onClick={handleCreate} style={iconButtonStyle} title="New document">+</button>
        </div>

        {/* Tab bar */}
        <div style={{ display: 'flex', borderBottom: '1px solid #e0e0e0' }}>
          {(['docs', 'tags', 'bookmarks'] as const).map(tab => (
            <button key={tab} onClick={() => setSidebarTab(tab)}
              style={{
                flex: 1, padding: '0.5rem', border: 'none', background: 'none', cursor: 'pointer',
                fontSize: '0.75rem',
                fontWeight: sidebarTab === tab ? 600 : 400,
                borderBottom: sidebarTab === tab ? '2px solid #333' : '2px solid transparent',
                color: sidebarTab === tab ? '#111' : '#666',
              }}>
              {tab === 'docs' ? 'Docs' : tab === 'tags' ? 'Tags' : '🔖'}
            </button>
          ))}
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {sidebarTab === 'docs' ? <DocumentList activeDocId={activeDocId} />
            : sidebarTab === 'tags' ? <TagBrowser />
            : <BookmarkBrowser />}
        </div>
      </aside>
    </>
  );
}

const iconButtonStyle = {
  background: 'none', border: 'none', cursor: 'pointer',
  padding: '0.25rem 0.375rem', borderRadius: 4,
  fontSize: '1rem', color: '#666', lineHeight: 1,
  minWidth: 44, minHeight: 44, display: 'flex',
  alignItems: 'center', justifyContent: 'center',
} as const;

// Same as iconButtonStyle but WITHOUT display — lets CSS (.mobile-close-btn) control
// visibility so the X button is hidden on desktop and shown on mobile.
const closeBtnStyle = {
  background: 'none', border: 'none', cursor: 'pointer',
  padding: '0.25rem 0.375rem', borderRadius: 4,
  fontSize: '1rem', color: '#666', lineHeight: 1,
  minWidth: 44, minHeight: 44,
  alignItems: 'center', justifyContent: 'center',
} as const;

const dropdownStyle = {
  position: 'absolute' as const, right: 0, top: '100%',
  background: '#fff', border: '1px solid #e0e0e0', borderRadius: 4,
  boxShadow: '0 2px 8px rgba(0,0,0,0.12)', zIndex: 100, minWidth: 180,
};

const dropdownItemStyle = {
  display: 'block', width: '100%', padding: '0.5rem 0.75rem',
  background: 'none', border: 'none', cursor: 'pointer',
  textAlign: 'left' as const, fontSize: '0.875rem', color: '#333',
};
