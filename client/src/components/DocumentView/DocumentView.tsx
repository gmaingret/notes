import { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { Document } from '../../hooks/useDocuments';
import { BulletTree, buildBulletMap } from './BulletTree';
import { Breadcrumb } from './Breadcrumb';
import { useDocumentBullets } from '../../hooks/useBullets';
import { useUiStore } from '../../store/uiStore';
import { useTagBullets } from '../../hooks/useTags';
import { FilteredBulletList, type FilteredBulletRow } from './FilteredBulletList';
import { useBookmarks, useRemoveBookmark } from '../../hooks/useBookmarks';

type Props = { document: Document };

export function DocumentView({ document }: Props) {
  const location = useLocation();
  const navigate = useNavigate();
  const { canvasView, setCanvasView, sidebarOpen, setSidebarOpen } = useUiStore();
  const { data: flatBullets = [] } = useDocumentBullets(document.id);
  const bulletMap = useMemo(() => buildBulletMap(flatBullets), [flatBullets]);

  const { data: bookmarkedBullets = [], isLoading: bookmarksLoading } = useBookmarks();
  const removeBookmark = useRemoveBookmark();

  const isFiltered = canvasView.type === 'filtered';
  const { data: tagBullets, isLoading: tagBulletsLoading } = useTagBullets(
    isFiltered ? (canvasView as { type: 'filtered'; chipType: string; chipValue: string }).chipType : '',
    isFiltered ? (canvasView as { type: 'filtered'; chipType: string; chipValue: string }).chipValue : '',
    isFiltered,
  );

  const zoomedBulletId = useMemo(() => {
    const hash = location.hash;
    const match = hash.match(/^#bullet\/(.+)$/);
    return match ? match[1] : null;
  }, [location.hash]);

  if (canvasView.type === 'bookmarks') {
    const rows: FilteredBulletRow[] = bookmarkedBullets.map(b => ({
      bulletId: b.id, bulletContent: b.content,
      documentId: b.documentId, documentTitle: b.documentTitle, isBookmarked: true,
    }));
    return (
      <div style={{ padding: '2rem 3rem', maxWidth: 720, margin: '0 auto' }}>
        <FilteredBulletList
          title="Bookmarks" rows={rows}
          onRowClick={(row) => { navigate(`/doc/${row.documentId}#bullet/${row.bulletId}`); setCanvasView({ type: 'document' }); }}
          onToggleBookmark={(row) => { removeBookmark.mutate(row.bulletId); }}
          isLoading={bookmarksLoading}
        />
      </div>
    );
  }

  if (canvasView.type === 'filtered') {
    const cv = canvasView as { type: 'filtered'; chipType: 'tag' | 'mention' | 'date'; chipValue: string };
    const rows: FilteredBulletRow[] = (tagBullets ?? []).map(b => ({
      bulletId: b.id, bulletContent: b.content,
      documentId: b.documentId, documentTitle: b.documentTitle,
    }));
    const prefix = cv.chipType === 'tag' ? '#' : cv.chipType === 'mention' ? '@' : '!!';
    return (
      <div style={{ padding: '2rem 3rem', maxWidth: 720, margin: '0 auto' }}>
        <FilteredBulletList
          title={`Results for ${prefix}${cv.chipValue}`} rows={rows}
          onRowClick={(row) => { navigate(`/doc/${row.documentId}#bullet/${row.bulletId}`); setCanvasView({ type: 'document' }); }}
          isLoading={tagBulletsLoading}
        />
      </div>
    );
  }

  return (
    <div style={{ padding: '0 1rem', maxWidth: 720, margin: '0 auto' }}>
      {/* Title row: hamburger (CSS-shown on mobile when sidebar closed) + title/breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '1.5rem' }}>
        {!sidebarOpen && (
          <button
            className="hamburger-btn"
            onClick={() => setSidebarOpen(true)}
            className="doc-hamburger"
            style={{
              background: 'none', border: 'none', cursor: 'pointer',
              fontSize: '1.25rem', lineHeight: 1,
              minWidth: 36, minHeight: 36, flexShrink: 0,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              padding: '0.25rem',
            }}
            aria-label="Open sidebar"
          >
            &#9776;
          </button>
        )}
        {zoomedBulletId ? (
          <Breadcrumb
            documentTitle={document.title}
            zoomedBulletId={zoomedBulletId}
            bulletMap={bulletMap}
          />
        ) : (
          <h1 className="doc-title" style={{ fontSize: '1.5rem', fontWeight: 600, margin: 0, flex: 1 }}>
            {document.title}
          </h1>
        )}
      </div>
      <div style={{ marginTop: '1.5rem' }}>
        <BulletTree documentId={document.id} zoomedBulletId={zoomedBulletId} />
      </div>
    </div>
  );
}
