import { useRef, useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { ChevronRight, Check, Trash2, Bookmark } from 'lucide-react';
import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import { useSetCollapsed, useMarkComplete, useSoftDeleteBullet } from '../../hooks/useBullets';
import { useBulletAttachments, useDeleteAttachment, useUploadAttachment } from '../../hooks/useAttachments';
import { useBookmarks } from '../../hooks/useBookmarks';
import { BulletContent } from './BulletContent';
import { ContextMenu } from './ContextMenu';
import { NoteRow } from './NoteRow';
import { AttachmentRow } from './AttachmentRow';
import { UndoToast } from './UndoToast';
import { useSwipeGesture } from './useSwipeGesture';
import { useDotDrag } from './useDotDrag';

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  depth: number;
  isDragging?: boolean;
  onDragStart: (bulletId: string, x: number, y: number) => void;
  onDragMove: (x: number, y: number) => void;
  onDragEnd: () => void;
  onDragCancel: () => void;
};

function SwipeBackground({ swipeX, swipeBackground, iconScale }: {
  swipeX: number; swipeBackground: string; iconScale: number;
}) {
  if (swipeX === 0) return null;
  return (
    <div style={{ position: 'absolute', inset: 0, background: swipeBackground, display: 'flex',
      alignItems: 'center', justifyContent: swipeX > 0 ? 'flex-start' : 'flex-end',
      padding: '0 16px', fontSize: '1.25rem', zIndex: 0 }}>
      {swipeX > 0 && <span style={{ transform: `scale(${iconScale})`, display: 'inline-flex', transition: 'transform 0.1s ease' }}><Check size={20} strokeWidth={1.5} /></span>}
      {swipeX < 0 && <span style={{ transform: `scale(${iconScale})`, display: 'inline-flex', transition: 'transform 0.1s ease' }}><Trash2 size={20} strokeWidth={1.5} /></span>}
    </div>
  );
}

export function BulletNode({
  bullet, bulletMap, depth,
  isDragging = false,
  onDragStart, onDragMove, onDragEnd, onDragCancel,
}: Props) {
  const setCollapsed = useSetCollapsed();
  const markComplete = useMarkComplete();
  const softDelete = useSoftDeleteBullet();
  const { data: attachments = [] } = useBulletAttachments(bullet.id);
  const { data: bookmarks = [] } = useBookmarks();
  const isBookmarked = bookmarks.some(b => b.id === bullet.id);
  const deleteAttachment = useDeleteAttachment();
  const attachFileInputRef = useRef<HTMLInputElement>(null);
  const uploadAttachment = useUploadAttachment();
  const [noteVisible, setNoteVisible] = useState(false);
  const [noteFocusTrigger, setNoteFocusTrigger] = useState(0);
  const [contextMenuPos, setContextMenuPos] = useState<{ x: number; y: number } | null>(null);

  useEffect(() => {
    function handler(e: Event) {
      const detail = (e as CustomEvent<{ bulletId: string }>).detail;
      if (detail.bulletId !== bullet.id) return;
      setNoteVisible(true);
      setNoteFocusTrigger(c => c + 1);
    }
    document.addEventListener('focus-note', handler);
    return () => document.removeEventListener('focus-note', handler);
  }, [bullet.id]);

  useEffect(() => {
    function handler(e: Event) {
      const detail = (e as CustomEvent<{ bulletId: string }>).detail;
      if (detail.bulletId !== bullet.id) return;
      attachFileInputRef.current?.click();
    }
    document.addEventListener('attach-file', handler);
    return () => document.removeEventListener('attach-file', handler);
  }, [bullet.id]);

  const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
  const hasChildren = children.length > 0;

  const { isDragActiveRef, handleDotPointerDown, handleDotPointerMove, handleDotPointerUp, handleDotPointerCancel } =
    useDotDrag(bullet.id, { onDragStart, onDragMove, onDragEnd, onDragCancel });

  const {
    rowRef, swipeX, isSwiping, exitDirection, showUndoToast, setShowUndoToast,
    swipeBackground, iconScale,
    handleRowPointerDown, handleRowPointerMove, handleRowPointerUp,
    handleCtxTouchStart, handleCtxTouchMove, handleCtxTouchEnd,
    handleTransitionEnd, isCtxTouchActive,
  } = useSwipeGesture(bullet, {
    onComplete: (id, docId, isComplete) => markComplete.mutate({ id, documentId: docId, isComplete }),
    onDelete: (id, docId) => softDelete.mutate({ id, documentId: docId }),
    onContextMenu: (pos) => setContextMenuPos(pos),
  }, isDragActiveRef);

  const rowTransform = exitDirection === 'complete' ? 'translateX(110%)' :
    exitDirection === 'delete' ? 'translateX(-110%)' : `translateX(${swipeX}px)`;
  const rowTransition = exitDirection ? 'transform 0.25s ease-out' : isSwiping ? 'none' : 'transform 0.2s ease';

  return (
    <div
      ref={rowRef}
      style={{ position: 'relative', display: 'flex', alignItems: 'flex-start',
        paddingLeft: depth * 24, paddingTop: 5, paddingBottom: 5,
        textDecoration: 'none', touchAction: 'pan-y', overflow: 'hidden',
        opacity: isDragging ? 0.3 : bullet.isComplete ? 0.5 : 1 }}
      onContextMenu={(e) => {
        e.preventDefault(); e.stopPropagation();
        if (!isCtxTouchActive()) setContextMenuPos({ x: e.clientX, y: e.clientY });
      }}
      onPointerDown={handleRowPointerDown}
      onPointerMove={handleRowPointerMove}
      onPointerUp={handleRowPointerUp}
      onTouchStart={handleCtxTouchStart}
      onTouchMove={handleCtxTouchMove}
      onTouchEnd={handleCtxTouchEnd}
    >
      <SwipeBackground swipeX={swipeX} swipeBackground={swipeBackground} iconScale={iconScale} />

      {/* Row content — translated by swipeX, or slid off screen on commit via exitDirection */}
      <div style={{ display: 'flex', alignItems: 'flex-start', width: '100%',
        transform: rowTransform, transition: rowTransition,
        background: 'var(--color-bg-base)', zIndex: 1, position: 'relative' }}
        onTransitionEnd={handleTransitionEnd}
      >
        {/* Dot — drag handle (mobile long-press) + click to zoom (desktop) */}
        <div className="bullet-dot bullet-timestamp"
          style={{ width: 28, flexShrink: 0, cursor: 'grab', userSelect: 'none',
            fontSize: '1.5rem', lineHeight: '1.6rem', display: 'flex',
            alignItems: 'center', touchAction: 'none' }}
          onPointerDown={handleDotPointerDown}
          onPointerMove={handleDotPointerMove}
          onPointerUp={handleDotPointerUp}
          onPointerCancel={handleDotPointerCancel}
        >•</div>
        {isBookmarked && (
          <span className="bullet-date-label" style={{ fontSize: '0.6rem', lineHeight: '1.6rem', flexShrink: 0 }}>
            <Bookmark size={16} strokeWidth={1.5} className="star-filled" />
          </span>
        )}
        <div style={{ flex: 1, minWidth: 0, textDecoration: bullet.isComplete ? 'line-through' : 'none' }}>
          <input ref={attachFileInputRef} type="file" style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              uploadAttachment.mutate({ bulletId: bullet.id, file });
              e.target.value = '';
            }}
          />
          <BulletContent bullet={bullet} bulletMap={bulletMap} />
          {(!!bullet.note?.trim() || noteVisible) && (
            <NoteRow bulletId={bullet.id} initialNote={bullet.note}
              focusTrigger={noteFocusTrigger} onHide={() => setNoteVisible(false)} />
          )}
          {attachments.map(a => (
            <AttachmentRow key={a.id} attachment={a}
              onDelete={() => deleteAttachment.mutate({ attachmentId: a.id, bulletId: bullet.id })} />
          ))}
          {contextMenuPos && createPortal(
            <ContextMenu bullet={bullet} bulletMap={bulletMap}
              position={contextMenuPos} onClose={() => setContextMenuPos(null)} />,
            document.body,
          )}
        </div>

        {/* Chevron */}
        <div className="bullet-chevron"
          style={{ width: 20, flexShrink: 0, cursor: hasChildren ? 'pointer' : 'default',
            userSelect: 'none', fontSize: '0.5rem', lineHeight: '1.6rem', textAlign: 'center' }}
          onClick={() => {
            if (hasChildren) setCollapsed.mutate({ id: bullet.id, documentId: bullet.documentId, isCollapsed: !bullet.isCollapsed });
          }}
        >
          {hasChildren && (
            <span className="bullet-chevron" style={{ display: 'inline-block',
              transform: bullet.isCollapsed ? 'none' : 'rotate(90deg)', transition: 'transform 0.15s ease' }}>
              <ChevronRight size={16} strokeWidth={1.5} />
            </span>
          )}
        </div>
      </div>

      {showUndoToast && <UndoToast onDismiss={() => setShowUndoToast(false)} />}
    </div>
  );
}
