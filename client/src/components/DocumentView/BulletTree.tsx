import { useMemo } from 'react';
import { useDocumentBullets } from '../../hooks/useBullets';
import type { Bullet } from '../../hooks/useBullets';

export type BulletMap = Record<string, Bullet>;
export type FlatBullet = Bullet & { depth: number };

export function buildBulletMap(flat: Bullet[]): BulletMap {
  return Object.fromEntries(flat.map(b => [b.id, b]));
}

export function getChildren(map: BulletMap, parentId: string | null): Bullet[] {
  return Object.values(map)
    .filter(b => b.parentId === parentId && !b.deletedAt)
    .sort((a, b) => a.position - b.position);
}

export function flattenTree(
  map: BulletMap,
  parentId: string | null = null,
  depth = 0
): FlatBullet[] {
  return getChildren(map, parentId).flatMap(b => [
    { ...b, depth },
    ...(b.isCollapsed ? [] : flattenTree(map, b.id, depth + 1)),
  ]);
}

export function BulletTree({
  documentId,
  zoomedBulletId,
}: {
  documentId: string;
  zoomedBulletId?: string | null;
}) {
  const { data: flatBullets = [] } = useDocumentBullets(documentId);
  const bulletMap = useMemo(() => buildBulletMap(flatBullets), [flatBullets]);
  const rootId = zoomedBulletId ?? null;
  const flatItems = useMemo(() => flattenTree(bulletMap, rootId), [bulletMap, rootId]);

  // Render placeholder list for now — BulletNode added in Task 2
  return (
    <div>
      {flatItems.map(b => (
        <div key={b.id} style={{ paddingLeft: b.depth * 24 }}>
          {b.content}
        </div>
      ))}
    </div>
  );
}
