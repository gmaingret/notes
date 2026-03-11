import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar/Sidebar';
import { DocumentView } from '../components/DocumentView/DocumentView';
import { QuickOpenPalette } from '../components/DocumentView/QuickOpenPalette';
import { useDocuments, useOpenDocument } from '../hooks/useDocuments';
import { useUiStore } from '../store/uiStore';
import { useIsMobile } from '../hooks/useIsMobile';

export function AppPage() {
  const { docId } = useParams<{ docId?: string }>();
  const navigate = useNavigate();
  const { data: docs } = useDocuments();
  const { mutate: openDocument } = useOpenDocument();
  const { lastOpenedDocId, setLastOpenedDocId, sidebarOpen, setSidebarOpen, quickOpenOpen, setQuickOpenOpen } = useUiStore();
  const isMobile = useIsMobile();

  // On mobile, close sidebar on initial load when a document is already in the URL.
  // sidebarOpen defaults to true in the store (good for desktop), but on mobile the sidebar
  // is an overlay — it should start closed so the document is immediately visible.
  // Empty dep array: runs once on mount only, intentionally ignoring later changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (isMobile && docId) {
      setSidebarOpen(false);
    }
  }, []);

  // After login, navigate to last opened doc or first doc (Inbox)
  useEffect(() => {
    if (!docId && docs && docs.length > 0) {
      const target = docs.find(d => d.id === lastOpenedDocId) ?? docs[0];
      navigate(`/doc/${target.id}`, { replace: true });
    }
  }, [docId, docs, lastOpenedDocId, navigate]);

  // Track last opened doc and call open endpoint
  useEffect(() => {
    if (docId) {
      setLastOpenedDocId(docId);
      openDocument(docId);
    }
  }, [docId, setLastOpenedDocId, openDocument]);

  // Ctrl+E / Cmd+E keyboard shortcut to toggle sidebar; Ctrl+Shift+K / Cmd+Shift+K to open quick-open palette
  // Note: Ctrl+K is intercepted by Chrome to focus the address bar and cannot be overridden in web apps
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key === 'e') {
        e.preventDefault();
        setSidebarOpen(!sidebarOpen);
      }
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'K') {
        e.preventDefault();
        setQuickOpenOpen(true);
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [sidebarOpen, setSidebarOpen, setQuickOpenOpen]);

  const activeDoc = docs?.find(d => d.id === docId) ?? null;

  return (
    <div style={{ display: 'flex', height: '100dvh', overflow: 'hidden' }}>
      <Sidebar activeDocId={docId ?? null} />
      <main style={{ flex: 1, overflow: 'auto' }}>
        {activeDoc ? (
          <DocumentView document={activeDoc} />
        ) : (
          <div style={{ padding: '2rem' }} className="app-empty-state">Select a document</div>
        )}
      </main>
      {quickOpenOpen && <QuickOpenPalette onClose={() => setQuickOpenOpen(false)} />}
    </div>
  );
}
