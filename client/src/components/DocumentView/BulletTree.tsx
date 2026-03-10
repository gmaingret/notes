import { useMemo, useState } from 'react';
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  TouchSensor,
  useSensor,
  useSensors,
  closestCenter,
} from '@dnd-kit/core';
import type { DragEndEvent, DragMoveEvent, DragStartEvent } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { useDocumentBullets, useMoveBullet, useCreateBullet } from '../../hooks/useBullets';
import type { Bullet } from '../../hooks/useBullets';
import { BulletNode } from './BulletNode';
import { ContextMenu } from './ContextMenu';
import { DocumentToolbar } from './DocumentToolbar';
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

function getProjectedDepth(
  flatItems: FlatBullet[],
  activeId: string,
  overId: string,
  dragOffset: number
): number {
  const activeIndex = flatItems.findIndex(f => f.id === activeId);
  const overIndex = flatItems.findIndex(f => f.id === overId);
  if (activeIndex === -1 || overIndex === -1) return 0;

  const dragDepth = flatItems[activeIndex].depth;
  const projectedDepth = dragDepth + Math.round(dragOffset / INDENTATION_WIDTH);

  const prevItem = flatItems[overIndex - 1];
  const maxDepth = prevItem ? prevItem.depth + 1 : 0;
  const minDepth = flatItems[overIndex + 1]?.depth ?? 0;

  return Math.min(Math.max(projectedDepth, minDepth), maxDepth);
}

export function BulletTree({
  documentId,
  zoomedBulletId,
}: {
  documentId: string;
  zoomedBulletId?: string | null;
}) {
  const { data: flatBullets = [], isLoading } = useDocumentBullets(documentId);
  const moveBullet = useMoveBullet();
  const createBullet = useCreateBullet();
  const { focusedBulletId } = useUiStore();

  const [activeId, setActiveId] = useState<string | null>(null);
  const [dragOffsetX, setDragOffsetX] = useState(0);
  const [overId, setOverId] = useState<string | null>(null);
  const [hideCompleted, setHideCompleted] = useState(false);

  // Tree-level context menu: handles right-clicks on empty document space (outside bullet rows)
  const [treeContextMenu, setTreeContextMenu] = useState<{ x: number; y: number; bulletId: string } | null>(null);

  const isMobile = window.matchMedia('(max-width: 768px)').matches;
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    ...(isMobile
      ? [useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } })]
      : [])
  );

  const bulletMap = useMemo(() => buildBulletMap(flatBullets), [flatBullets]);
  const rootId = zoomedBulletId ?? null;
  const flatItems = useMemo(() => flattenTree(bulletMap, rootId), [bulletMap, rootId]);
  const visibleItems = useMemo(
    () => (hideCompleted ? flatItems.filter(b => !b.isComplete) : flatItems),
    [flatItems, hideCompleted]
  );

  function handleDragStart(event: DragStartEvent) {
    setActiveId(event.active.id as string);
    setDragOffsetX(0);
    setOverId(null);
  }

  function handleDragMove(event: DragMoveEvent) {
    setDragOffsetX(event.delta.x);
    if (event.over) {
      setOverId(event.over.id as string);
    }
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;

    if (!over || !active) {
      setActiveId(null);
      setDragOffsetX(0);
      setOverId(null);
      return;
    }

    const currentActiveId = active.id as string;
    const currentOverId = over.id as string;

    const activeBullet = bulletMap[currentActiveId];
    if (!activeBullet) {
      setActiveId(null);
      setDragOffsetX(0);
      setOverId(null);
      return;
    }

    const overIndex = visibleItems.findIndex(f => f.id === currentOverId);
    const projectedDepth = getProjectedDepth(visibleItems, currentActiveId, currentOverId, dragOffsetX);

    // Determine newParentId: find the nearest item at depth === projectedDepth - 1
    // Search from overIndex inclusive (the hovered item can itself be the parent)
    // Skip the active bullet to avoid self-parenting cycles
    let newParentId: string | null = null;
    if (projectedDepth > 0) {
      for (let i = overIndex; i >= 0; i--) {
        if (visibleItems[i].id === currentActiveId) continue;
        if (visibleItems[i].depth === projectedDepth - 1) {
          newParentId = visibleItems[i].id;
          break;
        }
      }
    }

    // Determine afterId: the nearest preceding item that shares newParentId (i.e. a true sibling).
    // Walking backward from overIndex ensures we skip items that belong to a different subtree
    // (e.g. the parent node itself or children of another parent), which would otherwise cause
    // the server to append the dragged item at the end of the parent's children.
    let afterId: string | null = null;
    for (let i = overIndex - 1; i >= 0; i--) {
      const candidate = visibleItems[i];
      if (candidate.id === currentActiveId) continue;
      if (candidate.parentId === newParentId) {
        afterId = candidate.id;
        break;
      }
    }

    // Only mutate if something actually changed
    if (
      activeBullet.parentId !== newParentId ||
      currentActiveId !== currentOverId
    ) {
      moveBullet.mutate({
        id: currentActiveId,
        documentId,
        newParentId,
        afterId,
      });
    }

    setActiveId(null);
    setDragOffsetX(0);
    setOverId(null);
  }

  function handleDragCancel() {
    setActiveId(null);
    setDragOffsetX(0);
    setOverId(null);
  }

  // Compute drop indicator index: insert position within visibleItems
  const dropIndicatorIndex = useMemo(() => {
    if (!activeId || !overId) return null;
    const overIndex = visibleItems.findIndex(f => f.id === overId);
    if (overIndex === -1) return null;
    return overIndex;
  }, [activeId, overId, visibleItems]);

  const projectedDropDepth = useMemo(() => {
    if (!activeId || !overId) return 0;
    return getProjectedDepth(visibleItems, activeId, overId, dragOffsetX);
  }, [activeId, overId, visibleItems, dragOffsetX]);

  const activeBulletForOverlay = activeId ? (bulletMap[activeId] ?? null) : null;

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragStart={handleDragStart}
      onDragMove={handleDragMove}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      <DocumentToolbar
        documentId={documentId}
        hideCompleted={hideCompleted}
        onToggleHideCompleted={() => setHideCompleted(h => !h)}
      />
      {focusedBulletId && (
        <FocusToolbar bulletId={focusedBulletId} documentId={documentId} />
      )}
      <SortableContext
        items={visibleItems.map(f => f.id)}
        strategy={verticalListSortingStrategy}
      >
        <div
          style={{ position: 'relative' }}
          onContextMenu={(e) => {
            // Only fires when right-click did NOT land on a BulletNode
            // (BulletNode's handler calls stopPropagation).
            // Show the context menu for the currently focused bullet, if any.
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
                { documentId, parentId: null, afterId: null, content: '' },
                {
                  onSuccess: (data) => {
                    setTimeout(() => {
                      const el = document.getElementById(`bullet-${data.id}`) as HTMLDivElement | null;
                      if (el) el.focus();
                    }, 50);
                  },
                }
              )}
              style={{
                padding: '0.2rem 0',
                color: '#bbb',
                cursor: 'text',
                userSelect: 'none',
                fontSize: '0.9375rem',
                lineHeight: 1.6,
              }}
            >
              Click to add your first bullet...
            </div>
          )}
          {visibleItems.map((b, idx) => (
            <div key={b.id}>
              {dropIndicatorIndex === idx && activeId && b.id !== activeId && (
                <DropIndicator depth={projectedDropDepth} />
              )}
              <BulletNode
                bullet={b}
                bulletMap={bulletMap}
                depth={b.depth}
              />
            </div>
          ))}
          {/* Drop indicator at end of list */}
          {dropIndicatorIndex === visibleItems.length && activeId && (
            <DropIndicator depth={projectedDropDepth} />
          )}
        </div>
      </SortableContext>
      <DragOverlay>
        {activeBulletForOverlay ? (
          <BulletNode
            bullet={{ ...activeBulletForOverlay, depth: 0 }}
            bulletMap={bulletMap}
            depth={0}
            isDragOverlay
          />
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}

function DropIndicator({ depth }: { depth: number }) {
  return (
    <div
      style={{
        height: 2,
        backgroundColor: '#4A90E2',
        marginLeft: depth * INDENTATION_WIDTH + 32, // 32 = chevron (16) + dot (16)
        borderRadius: 1,
        pointerEvents: 'none',
      }}
    />
  );
}
