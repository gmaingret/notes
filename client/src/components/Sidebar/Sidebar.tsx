import { useState } from 'react';
import { MoreHorizontal, X, Plus, FileText, Tag, Star } from 'lucide-react';
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
        <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--color-border-default)', display: 'flex', alignItems: 'center', gap: '0.5rem', position: 'relative' }}>
          <span style={{ flex: 1, fontWeight: 600, fontSize: '1rem' }}>Notes</span>

          <div>
            <button onClick={() => setShowSidebarMenu(v => !v)} className="sidebar-icon-btn" style={iconButtonBase} title="More options"><MoreHorizontal size={20} strokeWidth={1.5} /></button>
            {showSidebarMenu && (
              <>
                {/* Transparent overlay — tapping outside the dropdown closes it (mobile + desktop) */}
                <div
                  style={{ position: 'fixed', inset: 0, zIndex: 99 }}
                  onClick={() => setShowSidebarMenu(false)}
                />
                <div style={dropdownStyle}>
                  <button style={dropdownItemBase} className="sidebar-menu-item" onClick={() => { exportAll(); setShowSidebarMenu(false); }}>Export all documents</button>
                  <button style={dropdownItemBase} className="sidebar-menu-item" onClick={() => { handleLogout(); setShowSidebarMenu(false); }}>Logout</button>
                </div>
              </>
            )}
          </div>

          {/* X close — only shown on mobile via CSS (.mobile-close-btn display:none on desktop).
               Do NOT apply display via inline style here — CSS controls visibility. */}
          <button
            className="mobile-close-btn sidebar-icon-btn"
            onClick={() => setSidebarOpen(false)}
            style={closeBtnBase}
            title="Close sidebar"
            aria-label="Close sidebar"
          >
            <X size={20} strokeWidth={1.5} />
          </button>

          <button onClick={handleCreate} className="sidebar-icon-btn" style={iconButtonBase} title="New document"><Plus size={20} strokeWidth={1.5} /></button>
        </div>

        {/* Tab bar */}
        {(() => {
          const tabIcon = {
            docs: <FileText size={20} strokeWidth={1.5} />,
            tags: <Tag size={20} strokeWidth={1.5} />,
            bookmarks: <Star size={20} strokeWidth={1.5} />,
          } as const;
          return (
            <div style={{ display: 'flex', borderBottom: '1px solid var(--color-border-default)' }}>
              {(['docs', 'tags', 'bookmarks'] as const).map(tab => (
                <button key={tab} onClick={() => setSidebarTab(tab)}
                  className={`sidebar-tab${sidebarTab === tab ? ' sidebar-tab--active' : ''}`}
                  title={tab === 'docs' ? 'Documents' : tab === 'tags' ? 'Tags' : 'Bookmarks'}
                  style={{
                    flex: 1, border: 'none', background: 'none', cursor: 'pointer',
                    fontSize: '0.75rem',
                    fontWeight: sidebarTab === tab ? 600 : 400,
                  }}>
                  {tabIcon[tab]}
                </button>
              ))}
            </div>
          );
        })()}

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {sidebarTab === 'docs' ? <DocumentList activeDocId={activeDocId} />
            : sidebarTab === 'tags' ? <TagBrowser />
            : <BookmarkBrowser />}
        </div>
      </aside>
    </>
  );
}

const iconButtonBase = {
  padding: '0.25rem 0.375rem', borderRadius: 4,
  fontSize: '1rem', lineHeight: 1,
  minWidth: 44, minHeight: 44, display: 'flex',
  alignItems: 'center', justifyContent: 'center',
} as const;

// Same as iconButtonBase but WITHOUT display — lets CSS (.mobile-close-btn) control
// visibility so the X button is hidden on desktop and shown on mobile.
const closeBtnBase = {
  padding: '0.25rem 0.375rem', borderRadius: 4,
  fontSize: '1rem', lineHeight: 1,
  minWidth: 44, minHeight: 44,
  alignItems: 'center', justifyContent: 'center',
} as const;

const dropdownStyle = {
  position: 'absolute' as const, right: 0, top: '100%',
  background: 'var(--color-bg-raised)', border: '1px solid var(--color-border-default)', borderRadius: 4,
  boxShadow: '0 2px 8px var(--color-shadow)', zIndex: 100, minWidth: 180,
};

const dropdownItemBase = {
  display: 'block', width: '100%', padding: '0.5rem 0.75rem',
  background: 'none', border: 'none', cursor: 'pointer',
  textAlign: 'left' as const, fontSize: '1rem',
};
