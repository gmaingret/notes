import { useRef, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import { useSetCollapsed, useMarkComplete, useSoftDeleteBullet } from '../../hooks/useBullets';
import { useBulletAttachments, useDeleteAttachment } from '../../hooks/useAttachments';
import { BulletContent } from './BulletContent';
import { ContextMenu } from './ContextMenu';
import { NoteRow } from './NoteRow';
import { AttachmentRow } from './AttachmentRow';
import { UndoToast } from './UndoToast';
import { swipeThresholdReached, createLongPressHandler } from './gestures';

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  depth: number;
  isDragOverlay?: boolean;
};

export function BulletNode({ bullet, bulletMap, depth, isDragOverlay = false }: Props) {
  const navigate = useNavigate();
  const setCollapsed = useSetCollapsed();
  const markComplete = useMarkComplete();
  const softDelete = useSoftDeleteBullet();
  const { data: attachments = [] } = useBulletAttachments(bullet.id);
  const deleteAttachment = useDeleteAttachment();
  const [contextMenuPos, setContextMenuPos] = useState<{ x: number; y: number } | null>(null);

  // Swipe gesture state
  const [swipeX, setSwipeX] = useState(0);
  const [isSwiping, setIsSwiping] = useState(false);
  const [showUndoToast, setShowUndoToast] = useState(false);
  const startX = useRef(0);
  const startY = useRef(0);
  const isPointerDown = useRef(false);
  const rowRef = useRef<HTMLDivElement>(null);

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: bullet.id });

  // Extract dnd-kit's onPointerDown so we can merge it with our click-disambiguation handler
  const { onPointerDown: dndPointerDown, ...listenersWithoutPointerDown } = listeners ?? {};

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

  // Swipe gesture handlers (touch pointer events only)
  function handleRowPointerDown(e: React.PointerEvent<HTMLDivElement>) {
    if (e.pointerType !== 'touch') return;
    startX.current = e.clientX;
    startY.current = e.clientY;
    isPointerDown.current = true;
    setIsSwiping(true);
    rowRef.current?.setPointerCapture(e.pointerId);
  }

  function handleRowPointerMove(e: React.PointerEvent<HTMLDivElement>) {
    if (!isPointerDown.current || e.pointerType !== 'touch') return;
    const dx = e.clientX - startX.current;
    const dy = e.clientY - startY.current;
    // Directional lock: if mostly scrolling vertically, suppress horizontal swipe
    if (Math.abs(dy) > Math.abs(dx) * 1.5) return;
    setSwipeX(dx);
  }

  function handleRowPointerUp() {
    if (!isPointerDown.current) return;
    isPointerDown.current = false;
    setIsSwiping(false);

    const rowWidth = rowRef.current?.offsetWidth ?? 300;
    const result = swipeThresholdReached(swipeX, rowWidth);

    if (result === 'complete') {
      markComplete.mutate({
        id: bullet.id,
        documentId: bullet.documentId,
        isComplete: !bullet.isComplete,
      });
    } else if (result === 'delete') {
      softDelete.mutate({ id: bullet.id, documentId: bullet.documentId });
      setShowUndoToast(true);
    }

    setSwipeX(0);
  }

  // Long-press handler (persisted across renders via useMemo with stable ref)
  const longPressHandler = useMemo(
    () => createLongPressHandler({
      onLongPress: (x, y) => setContextMenuPos({ x, y }),
      delay: 500,
      cancelDistance: 8,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  // React-typed wrappers for gesture handler functions so they satisfy TouchEventHandler
  const handleLongPressTouchStart: React.TouchEventHandler<HTMLDivElement> = (e) =>
    longPressHandler.handleTouchStart(e as unknown as { touches: { clientX: number; clientY: number }[] });
  const handleLongPressTouchMove: React.TouchEventHandler<HTMLDivElement> = (e) =>
    longPressHandler.handleTouchMove(e as unknown as { touches: { clientX: number; clientY: number }[] });
  const handleLongPressTouchEnd: React.TouchEventHandler<HTMLDivElement> = (e) =>
    longPressHandler.handleTouchEnd(e as unknown as { preventDefault?: () => void });

  // Background color for swipe reveal
  const swipeBackground = swipeX > 0
    ? '#4caf50'  // green for complete
    : swipeX < 0
    ? '#f44336'  // red for delete
    : 'transparent';

  const swipeIcon = swipeX > 0 ? '✅' : swipeX < 0 ? '🗑️' : null;

  return (
    <div
      ref={(node) => {
        // Attach both dnd-kit ref and our own rowRef
        if (!isDragOverlay) setNodeRef(node);
        (rowRef as React.MutableRefObject<HTMLDivElement | null>).current = node;
      }}
      style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'flex-start',
        paddingLeft: depth * 24,
        textDecoration: bullet.isComplete ? 'line-through' : 'none',
        touchAction: 'pan-y',
        overflow: 'hidden',
        ...style,
        opacity: bullet.isComplete ? 0.5 : style.opacity,
      }}
      onContextMenu={isDragOverlay ? undefined : (e) => {
        e.preventDefault();
        setContextMenuPos({ x: e.clientX, y: e.clientY });
      }}
      onPointerDown={isDragOverlay ? undefined : handleRowPointerDown}
      onPointerMove={isDragOverlay ? undefined : handleRowPointerMove}
      onPointerUp={isDragOverlay ? undefined : handleRowPointerUp}
      onTouchStart={isDragOverlay ? undefined : handleLongPressTouchStart}
      onTouchMove={isDragOverlay ? undefined : handleLongPressTouchMove}
      onTouchEnd={isDragOverlay ? undefined : handleLongPressTouchEnd}
    >
      {/* Swipe background reveal layer */}
      {swipeX !== 0 && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: swipeBackground,
            display: 'flex',
            alignItems: 'center',
            justifyContent: swipeX > 0 ? 'flex-start' : 'flex-end',
            padding: '0 16px',
            fontSize: '1.25rem',
            zIndex: 0,
          }}
        >
          {swipeIcon}
        </div>
      )}

      {/* Row content — translated by swipeX */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          width: '100%',
          transform: `translateX(${swipeX}px)`,
          transition: isSwiping ? 'none' : 'transform 0.2s ease',
          background: 'var(--bg, #fff)',
          zIndex: 1,
          position: 'relative',
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
        {/* MOB-04: touch drag handled by dnd-kit PointerSensor; touch-action:none on dot enables drag without text selection */}
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
          {...(isDragOverlay ? {} : attributes)}
          {...(isDragOverlay ? {} : listenersWithoutPointerDown)}
          onPointerDown={isDragOverlay ? undefined : (e) => {
            dndPointerDown?.(e);
            handleDotPointerDown(e);
          }}
          onPointerUp={isDragOverlay ? undefined : handleDotPointerUp}
        >
          •
        </div>

        {/* Content — not rendered in drag overlay (just the dot + text stub) */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <BulletContent
            bullet={bullet}
            bulletMap={isDragOverlay ? {} : bulletMap}
            isDragOverlay={isDragOverlay}
          />
          {!isDragOverlay && bullet.note !== null && (
            <NoteRow bulletId={bullet.id} initialNote={bullet.note} />
          )}
          {!isDragOverlay && attachments.map(a => (
            <AttachmentRow
              key={a.id}
              attachment={a}
              onDelete={() => deleteAttachment.mutate({ attachmentId: a.id, bulletId: bullet.id })}
            />
          ))}
          {contextMenuPos && (
            <ContextMenu
              bullet={bullet}
              bulletMap={bulletMap}
              position={contextMenuPos}
              onClose={() => setContextMenuPos(null)}
            />
          )}
        </div>
      </div>

      {showUndoToast && <UndoToast onDismiss={() => setShowUndoToast(false)} />}
    </div>
  );
}
