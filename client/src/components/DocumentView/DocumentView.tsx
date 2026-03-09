import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import type { Document } from '../../hooks/useDocuments';
import { BulletTree, buildBulletMap } from './BulletTree';
import { Breadcrumb } from './Breadcrumb';
import { useDocumentBullets } from '../../hooks/useBullets';

type Props = { document: Document };

export function DocumentView({ document }: Props) {
  const location = useLocation();
  const { data: flatBullets = [] } = useDocumentBullets(document.id);
  const bulletMap = useMemo(() => buildBulletMap(flatBullets), [flatBullets]);

  const zoomedBulletId = useMemo(() => {
    const hash = location.hash;
    const match = hash.match(/^#bullet\/(.+)$/);
    return match ? match[1] : null;
  }, [location.hash]);

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
