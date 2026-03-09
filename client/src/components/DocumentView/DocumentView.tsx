import { useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { Document } from '../../hooks/useDocuments';
import { BulletTree, buildBulletMap } from './BulletTree';
import { Breadcrumb } from './Breadcrumb';
import { useDocumentBullets } from '../../hooks/useBullets';
import { useUiStore } from '../../store/uiStore';
import { useTagBullets } from '../../hooks/useTags';
import { FilteredBulletList, type FilteredBulletRow } from './FilteredBulletList';

type Props = { document: Document };

export function DocumentView({ document }: Props) {
  const location = useLocation();
  const navigate = useNavigate();
  const { canvasView, setCanvasView } = useUiStore();
  const { data: flatBullets = [] } = useDocumentBullets(document.id);
  const bulletMap = useMemo(() => buildBulletMap(flatBullets), [flatBullets]);

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

  if (canvasView.type === 'filtered') {
    const cv = canvasView as { type: 'filtered'; chipType: 'tag' | 'mention' | 'date'; chipValue: string };
    const rows: FilteredBulletRow[] = (tagBullets ?? []).map(b => ({
      bulletId: b.id,
      bulletContent: b.content,
      documentId: b.documentId,
      documentTitle: b.documentTitle,
    }));
    const prefix = cv.chipType === 'tag' ? '#' : cv.chipType === 'mention' ? '@' : '!!';
    return (
      <div style={{ padding: '2rem 3rem', maxWidth: 720, margin: '0 auto' }}>
        <FilteredBulletList
          title={`Results for ${prefix}${cv.chipValue}`}
          rows={rows}
          onRowClick={(row) => {
            navigate(`/doc/${row.documentId}#bullet/${row.bulletId}`);
            setCanvasView({ type: 'document' });
          }}
          isLoading={tagBulletsLoading}
        />
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem 3rem', maxWidth: 720, margin: '0 auto' }}>
      {/* Show breadcrumb when zoomed, h1 title otherwise */}
      {zoomedBulletId ? (
        <Breadcrumb
          documentTitle={document.title}
          zoomedBulletId={zoomedBulletId}
          bulletMap={bulletMap}
        />
      ) : (
        <h1 style={{ fontSize: '1.5rem', fontWeight: 600, margin: '0 0 1.5rem', color: '#111' }}>
          {document.title}
        </h1>
      )}

      <BulletTree documentId={document.id} zoomedBulletId={zoomedBulletId} />
    </div>
  );
}
