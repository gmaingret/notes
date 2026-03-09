import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import { useSetCollapsed } from '../../hooks/useBullets';
import { BulletContent } from './BulletContent';
import { ContextMenu } from './ContextMenu';

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  depth: number;
  isDragOverlay?: boolean;
};

export function BulletNode({ bullet, bulletMap, depth, isDragOverlay = false }: Props) {
  const navigate = useNavigate();
  const setCollapsed = useSetCollapsed();
  const [contextMenuPos, setContextMenuPos] = useState<{ x: number; y: number } | null>(null);

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: bullet.id });

  const style = isDragOverlay
    ? { opacity: 0.5 }
    : {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.3 : 1,
      };

  const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
  const hasChildren = children.length > 0;

  // Click vs drag disambiguation on the dot
  const pointerDownPos = useRef<{ x: number; y: number } | null>(null);

  function handleDotPointerDown(e: React.PointerEvent<HTMLDivElement>) {
    pointerDownPos.current = { x: e.clientX, y: e.clientY };
  }

  function handleDotPointerUp(e: React.PointerEvent<HTMLDivElement>) {
    if (!pointerDownPos.current) return;
    const dx = e.clientX - pointerDownPos.current.x;
    const dy = e.clientY - pointerDownPos.current.y;
    const distance = Math.sqrt(dx * dx + dy * dy);
    pointerDownPos.current = null;

    // If moved less than 5px — treat as click, navigate to zoom
    if (distance < 5) {
      navigate(`#bullet/${bullet.id}`);
    }
  }

  return (
    <div
      ref={isDragOverlay ? undefined : setNodeRef}
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        paddingLeft: depth * 24,
        opacity: bullet.isComplete ? 0.5 : 1,
        textDecoration: bullet.isComplete ? 'line-through' : 'none',
        ...style,
      }}
      onContextMenu={isDragOverlay ? undefined : (e) => {
        e.preventDefault();
        setContextMenuPos({ x: e.clientX, y: e.clientY });
      }}
    >
      {/* Chevron column — always reserves space, only shows icon when children exist */}
      {!isDragOverlay && (
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
      )}

      {/* Dot — drag handle + click to zoom */}
      <div
        style={{
          width: 16,
          flexShrink: 0,
          cursor: isDragOverlay ? 'grabbing' : 'grab',
          color: '#999',
          userSelect: 'none',
          lineHeight: '1.6rem',
          touchAction: 'none',
        }}
        {...(isDragOverlay ? {} : listeners)}
        {...(isDragOverlay ? {} : attributes)}
        onPointerDown={isDragOverlay ? undefined : handleDotPointerDown}
        onPointerUp={isDragOverlay ? undefined : handleDotPointerUp}
      >
        •
      </div>

      {/* Content — not rendered in drag overlay (just the dot + text stub) */}
      <BulletContent
        bullet={bullet}
        bulletMap={isDragOverlay ? {} : bulletMap}
        isDragOverlay={isDragOverlay}
      />
      {contextMenuPos && (
        <ContextMenu
          bullet={bullet}
          bulletMap={bulletMap}
          position={contextMenuPos}
          onClose={() => setContextMenuPos(null)}
        />
      )}
    </div>
  );
}
