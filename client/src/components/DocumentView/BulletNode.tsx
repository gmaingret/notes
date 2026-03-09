import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import { useSetCollapsed } from '../../hooks/useBullets';
import { BulletContent } from './BulletContent';

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  depth: number;
};

export function BulletNode({ bullet, bulletMap, depth }: Props) {
  const setCollapsed = useSetCollapsed();

  const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
  const hasChildren = children.length > 0;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        paddingLeft: depth * 24,
        opacity: bullet.isComplete ? 0.5 : 1,
        textDecoration: bullet.isComplete ? 'line-through' : 'none',
      }}
    >
      {/* Chevron column — always reserves space, only shows icon when children exist */}
      <div
        style={{
          width: 16,
          flexShrink: 0,
          cursor: hasChildren ? 'pointer' : 'default',
          userSelect: 'none',
          fontSize: '0.75rem',
          lineHeight: '1.6rem',
          color: '#666',
        }}
        onClick={() => {
          if (hasChildren) {
            setCollapsed.mutate({
              id: bullet.id,
              documentId: bullet.documentId,
              isCollapsed: !bullet.isCollapsed,
            });
          }
        }}
      >
        {hasChildren ? (bullet.isCollapsed ? '▶' : '▾') : null}
      </div>

      {/* Dot — drag handle placeholder; click = zoom (handled in Plan 05/06) */}
      <div
        style={{
          width: 16,
          flexShrink: 0,
          cursor: 'grab',
          color: '#999',
          userSelect: 'none',
          lineHeight: '1.6rem',
        }}
      >
        •
      </div>

      {/* Content */}
      <BulletContent bullet={bullet} bulletMap={bulletMap} />
    </div>
  );
}
