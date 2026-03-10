import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Sidebar } from '../components/Sidebar/Sidebar';
import { DocumentView } from '../components/DocumentView/DocumentView';
import { useDocuments, useOpenDocument } from '../hooks/useDocuments';
import { useUiStore } from '../store/uiStore';

export function AppPage() {
  const { docId } = useParams<{ docId?: string }>();
  const navigate = useNavigate();
  const { data: docs } = useDocuments();
  const { mutate: openDocument } = useOpenDocument();
  const { lastOpenedDocId, setLastOpenedDocId, sidebarOpen, setSidebarOpen } = useUiStore();

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

  // Mobile init: force sidebar closed on mobile viewport at mount
  useEffect(() => {
    if (window.matchMedia('(max-width: 768px)').matches) {
      setSidebarOpen(false);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Ctrl+E / Cmd+E keyboard shortcut to toggle sidebar
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key === 'e') {
        e.preventDefault();
        setSidebarOpen(!sidebarOpen);
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [sidebarOpen, setSidebarOpen]);

  const activeDoc = docs?.find(d => d.id === docId) ?? null;

  return (
    <div style={{ display: 'flex', height: '100dvh', overflow: 'hidden' }}>
      <Sidebar activeDocId={docId ?? null} />
      <main style={{ flex: 1, overflow: 'auto' }}>
        {activeDoc ? (
          <DocumentView document={activeDoc} />
        ) : (
          <div style={{ padding: '2rem', color: '#999' }}>Select a document</div>
        )}
      </main>
    </div>
  );
}
