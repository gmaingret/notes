import { useEffect, useLayoutEffect, useRef } from 'react';
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
  // Empty dep array: runs once on mount only, intentionally ignoring later changes.
  useEffect(() => {
    if (isMobile && docId) {
      setSidebarOpen(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Sync sidebar visibility when viewport crosses the mobile/desktop breakpoint.
  // desktop → mobile: close sidebar (it becomes a full-screen overlay — would black out the screen)
  // mobile → desktop: open sidebar (it's a permanent panel on desktop)
  // useLayoutEffect fires before browser paint — prevents the one-frame flash where the
  // sidebar backdrop CSS activates (via media query) before React can close the sidebar.
  const prevIsMobileRef = useRef(isMobile);
  useLayoutEffect(() => {
    if (isMobile && !prevIsMobileRef.current) {
      setSidebarOpen(false);
    } else if (!isMobile && prevIsMobileRef.current) {
      setSidebarOpen(true);
    }
    prevIsMobileRef.current = isMobile;
  }, [isMobile, setSidebarOpen]);

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

  // Keyboard shortcuts — capture:true lets us intercept Ctrl+F before the browser's Find bar
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key === 'e') {
        e.preventDefault();
        setSidebarOpen(!sidebarOpen);
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
        e.preventDefault();
        setQuickOpenOpen(true);
      }
    }
    document.addEventListener('keydown', handleKeyDown, { capture: true });
    return () => document.removeEventListener('keydown', handleKeyDown, { capture: true });
  }, [sidebarOpen, setSidebarOpen, setQuickOpenOpen]);

  const activeDoc = docs?.find(d => d.id === docId) ?? null;

  return (
    <div style={{ display: 'flex', height: '100dvh', overflow: 'hidden', background: 'var(--color-bg-base)' }}>
      <Sidebar activeDocId={docId ?? null} />
      <main style={{ flex: 1, overflow: 'auto', background: 'var(--color-bg-base)' }}>
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
