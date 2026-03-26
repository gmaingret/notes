import { useState } from 'react';
import { useIsMobile } from '../../hooks/useIsMobile';
import { X, Plus, FileText, Tag, Bookmark, Upload, Download, LogOut, Sun, Moon, Monitor } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useCreateDocument, useExportAllDocuments, useImportDocument } from '../../hooks/useDocuments';
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
  const importDocument = useImportDocument();
  const { sidebarOpen, setSidebarOpen, sidebarTab, setSidebarTab, themeMode, setThemeMode } = useUiStore();
  const isMobile = useIsMobile();
  const [pendingRenameId, setPendingRenameId] = useState<string | null>(null);

  const handleCreate = () => {
    if (sidebarTab !== 'docs') setSidebarTab('docs');
    createDocument(undefined, {
      onSuccess: (newDoc) => {
        setPendingRenameId(newDoc.id);
      },
    });
  };
  const handleLogout = async () => { await logout(); };

  const handleImport = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.md,text/markdown';
    input.onchange = async () => {
      const file = input.files?.[0];
      if (!file) return;
      const text = await file.text();
      importDocument.mutate(text);
    };
    input.click();
  };

  return (
    <>
      {/* Backdrop — only rendered on mobile when open (double guard: JS + CSS) */}
      {sidebarOpen && isMobile && (
        <div
          className="sidebar-backdrop"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside className={`sidebar${sidebarOpen ? ' sidebar-open' : ''}`}>
        {/* Header — position:relative here so the dropdown anchors to the full header width */}
        <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid var(--color-border-default)', display: 'flex', alignItems: 'center', gap: '0.5rem', position: 'relative' }}>
          <span style={{ flex: 1, fontWeight: 600, fontSize: '1rem' }}>Notes</span>

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
            bookmarks: <Bookmark size={20} strokeWidth={1.5} />,
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
          {sidebarTab === 'docs' ? (
            <DocumentList
              activeDocId={activeDocId}
              pendingRenameId={pendingRenameId}
              onRenameComplete={() => setPendingRenameId(null)}
            />
          ) : sidebarTab === 'tags' ? <TagBrowser />
            : <BookmarkBrowser />}
        </div>

        <div style={{
          borderTop: '1px solid var(--color-border-default)',
          padding: '0.5rem 0',
        }}>
          <button
            className="sidebar-footer-btn"
            onClick={() => exportAll()}
            style={footerBtnBase}
          >
            <Upload size={20} strokeWidth={1.5} />
            <span>Export all</span>
          </button>
          <button
            className="sidebar-footer-btn"
            onClick={handleImport}
            style={footerBtnBase}
          >
            <Download size={20} strokeWidth={1.5} />
            <span>Import</span>
          </button>
          <button
            className="sidebar-footer-btn"
            onClick={() => {
              const next = themeMode === 'system' ? 'light' : themeMode === 'light' ? 'dark' : 'system';
              setThemeMode(next);
            }}
            style={footerBtnBase}
          >
            {themeMode === 'dark' ? <Moon size={20} strokeWidth={1.5} /> :
             themeMode === 'light' ? <Sun size={20} strokeWidth={1.5} /> :
             <Monitor size={20} strokeWidth={1.5} />}
            <span>{themeMode === 'dark' ? 'Dark' : themeMode === 'light' ? 'Light' : 'System'}</span>
          </button>
          <button
            className="sidebar-footer-btn"
            onClick={() => void handleLogout()}
            style={footerBtnBase}
          >
            <LogOut size={20} strokeWidth={1.5} />
            <span>Logout</span>
          </button>
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

const footerBtnBase: React.CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: '0.5rem',
  width: '100%',
  padding: '0.5rem 1rem',
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  fontSize: '1rem',
  color: 'var(--color-text-muted)',
  textAlign: 'left',
};
