/* eslint-disable react-refresh/only-export-components */
import { useMemo, useState, useCallback, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useDocumentBullets, useMoveBullet, useCreateBullet } from '../../hooks/useBullets';
import type { Bullet } from '../../hooks/useBullets';
import { BulletNode } from './BulletNode';
import { ContextMenu } from './ContextMenu';
import { FocusToolbar } from './FocusToolbar';
import { useUiStore } from '../../store/uiStore';

export type BulletMap = Record<string, Bullet>;
export type FlatBullet = Bullet & { depth: number };

const INDENTATION_WIDTH = 24;

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

// ─── Custom touch drag & drop ──────────────────────────────────────────────────

type DragState = {
  activeId: string;
  originDepth: number;
  startX: number;
  startY: number;
  currentX: number;
  currentY: number;
};

type DragProjection = {
  insertionIndex: number;
  projectedDepth: number;
  projectedList: FlatBullet[];
};

/**
 * Compute where a dragged bullet would land in the tree.
 * The active bullet and its descendants are excluded from the projected list,
 * preventing self-parenting and ensuring correct depth bounds.
 */
function computeDragProjection(
  visibleItems: FlatBullet[],
  drag: DragState,
): DragProjection | null {
  const activeIdx = visibleItems.findIndex(i => i.id === drag.activeId);
  if (activeIdx === -1) return null;

  // Exclude active item + its descendants
  const activeDepth = visibleItems[activeIdx].depth;
  const excluded = new Set<string>([drag.activeId]);
  for (let i = activeIdx + 1; i < visibleItems.length; i++) {
    if (visibleItems[i].depth <= activeDepth) break;
    excluded.add(visibleItems[i].id);
  }
  const projectedList = visibleItems.filter(i => !excluded.has(i.id));

  // Find insertion index from pointer Y position
  let insertionIndex = projectedList.length;
  for (let i = 0; i < projectedList.length; i++) {
    const el = document.getElementById(`bullet-row-${projectedList[i].id}`);
    if (!el) continue;
    const rect = el.getBoundingClientRect();
    if (drag.currentY < rect.top + rect.height / 2) {
      insertionIndex = i;
      break;
    }
  }

  // Depth bounds based on neighbors at insertion point
  const prevItem = insertionIndex > 0 ? projectedList[insertionIndex - 1] : null;
  const nextItem = insertionIndex < projectedList.length ? projectedList[insertionIndex] : null;
  const maxDepth = prevItem ? prevItem.depth + 1 : 0;
  const minDepth = nextItem ? nextItem.depth : 0;

  // Project depth from horizontal drag offset
  const deltaX = drag.currentX - drag.startX;
  const rawDepth = drag.originDepth + Math.round(deltaX / INDENTATION_WIDTH);
  const projectedDepth = Math.min(Math.max(rawDepth, minDepth), maxDepth);

  return { insertionIndex, projectedDepth, projectedList };
}

export function BulletTree({
  documentId,
  zoomedBulletId,
  hideCompleted,
}: {
  documentId: string;
  zoomedBulletId?: string | null;
  hideCompleted: boolean;
}) {
  const { data: flatBullets = [], isLoading } = useDocumentBullets(documentId);
  const moveBullet = useMoveBullet();
  const createBullet = useCreateBullet();
  const { focusedBulletId } = useUiStore();

  const [treeContextMenu, setTreeContextMenu] = useState<{ x: number; y: number; bulletId: string } | null>(null);
  const [drag, setDrag] = useState<DragState | null>(null);

  const bulletMap = useMemo(() => buildBulletMap(flatBullets), [flatBullets]);
  const rootId = zoomedBulletId ?? null;
  const flatItems = useMemo(() => flattenTree(bulletMap, rootId), [bulletMap, rootId]);
  const visibleItems = useMemo(
    () => (hideCompleted ? flatItems.filter(b => !b.isComplete) : flatItems),
    [flatItems, hideCompleted]
  );

  // ─── Drag projection (recomputed on every pointer move) ────────────────────
  const dragProjection = useMemo(() => {
    if (!drag) return null;
    return computeDragProjection(visibleItems, drag);
  }, [drag, visibleItems]);

  // Map insertion index from projected list → visible list for drop indicator
  const dropIndicatorIdx = useMemo(() => {
    if (!dragProjection) return null;
    const { insertionIndex, projectedList } = dragProjection;
    if (insertionIndex >= projectedList.length) return visibleItems.length;
    const targetId = projectedList[insertionIndex].id;
    return visibleItems.findIndex(i => i.id === targetId);
  }, [dragProjection, visibleItems]);

  // ─── Drag callbacks ────────────────────────────────────────────────────────
  const handleDragStart = useCallback((bulletId: string, x: number, y: number) => {
    const item = visibleItems.find(i => i.id === bulletId);
    if (!item) return;
    setDrag({
      activeId: bulletId,
      originDepth: item.depth,
      startX: x,
      startY: y,
      currentX: x,
      currentY: y,
    });
  }, [visibleItems]);

  const handleDragMove = useCallback((x: number, y: number) => {
    setDrag(prev => prev ? { ...prev, currentX: x, currentY: y } : null);
  }, []);

  // Use refs so handleDragEnd closure stays stable
  const dragRef = useRef(drag);
  dragRef.current = drag; // eslint-disable-line react-hooks/refs
  const projectionRef = useRef(dragProjection);
  projectionRef.current = dragProjection; // eslint-disable-line react-hooks/refs

  const handleDragEnd = useCallback(() => {
    const d = dragRef.current;
    const p = projectionRef.current;
    if (!d || !p) {
      setDrag(null);
      return;
    }

    const { insertionIndex, projectedDepth, projectedList } = p;

    // Find newParentId: nearest item above insertion point at depth - 1
    let newParentId: string | null = null;
    if (projectedDepth > 0) {
      for (let i = insertionIndex - 1; i >= 0; i--) {
        if (projectedList[i].depth === projectedDepth - 1) {
          newParentId = projectedList[i].id;
          break;
        }
      }
    }

    // Find afterId: nearest preceding sibling under same parent
    let afterId: string | null = null;
    for (let i = insertionIndex - 1; i >= 0; i--) {
      if (projectedList[i].parentId === newParentId) {
        afterId = projectedList[i].id;
        break;
      }
    }

    const activeBullet = bulletMap[d.activeId];
    if (activeBullet) {
      moveBullet.mutate({
        id: d.activeId,
        documentId,
        newParentId,
        afterId,
      });
    }

    setDrag(null);
  }, [bulletMap, moveBullet, documentId]);

  const handleDragCancel = useCallback(() => setDrag(null), []);

  // ─── Auto-scroll during drag ──────────────────────────────────────────────
  const dragYRef = useRef(0);
  useEffect(() => {
    if (drag) dragYRef.current = drag.currentY;
  }, [drag]);

  const isDragging = drag !== null;
  useEffect(() => {
    if (!isDragging) return;
    let raf: number;
    const SCROLL_ZONE = 50;
    const SCROLL_SPEED = 8;
    function tick() {
      const main = document.querySelector<HTMLElement>('main');
      if (!main) { raf = requestAnimationFrame(tick); return; }
      const rect = main.getBoundingClientRect();
      const y = dragYRef.current;
      if (y < rect.top + SCROLL_ZONE) {
        main.scrollTop -= SCROLL_SPEED;
      } else if (y > rect.bottom - SCROLL_ZONE) {
        main.scrollTop += SCROLL_SPEED;
      }
      raf = requestAnimationFrame(tick);
    }
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [isDragging]);

  return (
    <>
      {focusedBulletId && (
        <FocusToolbar bulletId={focusedBulletId} documentId={documentId} />
      )}
      <div
        style={{ position: 'relative' }}
        onContextMenu={(e) => {
          const targetBulletId = focusedBulletId ?? visibleItems[0]?.id ?? null;
          if (!targetBulletId) return;
          e.preventDefault();
          setTreeContextMenu({ x: e.clientX, y: e.clientY, bulletId: targetBulletId });
        }}
      >
        {treeContextMenu && bulletMap[treeContextMenu.bulletId] && (
          <ContextMenu
            bullet={{ ...bulletMap[treeContextMenu.bulletId], depth: 0 }}
            bulletMap={bulletMap}
            position={{ x: treeContextMenu.x, y: treeContextMenu.y }}
            onClose={() => setTreeContextMenu(null)}
          />
        )}
        {visibleItems.length === 0 && !isLoading && (
          <div
            onClick={() => createBullet.mutate(
              { documentId, parentId: zoomedBulletId ?? null, afterId: null, content: '' },
              {
                onSuccess: (data) => {
                  setTimeout(() => {
                    const el = document.getElementById(`bullet-${data.id}`) as HTMLDivElement | null;
                    if (el) el.focus();
                  }, 50);
                },
              }
            )}
            className="bullet-tree-placeholder"
            style={{
              padding: '0.2rem 0',
              cursor: 'text',
              userSelect: 'none',
              fontSize: '0.9375rem',
              lineHeight: 1.6,
            }}
          >
            {zoomedBulletId ? 'Tap to add bullet point' : 'Click to add your first bullet...'}
          </div>
        )}
        {visibleItems.map((b, idx) => (
          <div key={b.id} id={`bullet-row-${b.id}`}>
            {dropIndicatorIdx === idx && drag && b.id !== drag.activeId && (
              <DropIndicator depth={dragProjection!.projectedDepth} />
            )}
            <BulletNode
              bullet={b}
              bulletMap={bulletMap}
              depth={b.depth}
              isDragging={drag?.activeId === b.id}
              onDragStart={handleDragStart}
              onDragMove={handleDragMove}
              onDragEnd={handleDragEnd}
              onDragCancel={handleDragCancel}
            />
          </div>
        ))}
        {dropIndicatorIdx === visibleItems.length && drag && (
          <DropIndicator depth={dragProjection!.projectedDepth} />
        )}
        {focusedBulletId && <div style={{ height: 72 }} />}
      </div>

      {/* Drag overlay — floating bullet preview following the finger */}
      {drag && bulletMap[drag.activeId] && createPortal(
        <div
          style={{
            position: 'fixed',
            left: drag.currentX + 10,
            top: drag.currentY - 20,
            opacity: 0.8,
            pointerEvents: 'none',
            background: 'var(--color-bg-base)',
            padding: '4px 12px',
            borderRadius: 6,
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
            fontSize: '0.9375rem',
            zIndex: 9999,
            maxWidth: 240,
            overflow: 'hidden',
            whiteSpace: 'nowrap',
            textOverflow: 'ellipsis',
          }}
        >
          <span style={{ marginRight: 6 }}>•</span>
          {bulletMap[drag.activeId].content || '(empty)'}
        </div>,
        document.body
      )}
    </>
  );
}

function DropIndicator({ depth }: { depth: number }) {
  return (
    <div
      style={{
        height: 2,
        backgroundColor: 'var(--color-accent-blue)',
        marginLeft: depth * INDENTATION_WIDTH + 32,
        borderRadius: 1,
        pointerEvents: 'none',
      }}
    />
  );
}
