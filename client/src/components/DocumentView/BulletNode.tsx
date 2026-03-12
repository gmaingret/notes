import { useRef, useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
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
import { swipeThresholdReached } from './gestures';

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

export function BulletNode({
  bullet, bulletMap, depth,
  isDragging = false,
  onDragStart, onDragMove, onDragEnd, onDragCancel,
}: Props) {
  const navigate = useNavigate();
  const setCollapsed = useSetCollapsed();
  const markComplete = useMarkComplete();
  const softDelete = useSoftDeleteBullet();
  const { data: attachments = [] } = useBulletAttachments(bullet.id);
  const { data: bookmarks = [] } = useBookmarks();
  const isBookmarked = bookmarks.some(b => b.id === bullet.id);
  const deleteAttachment = useDeleteAttachment();

  // Hidden file input ref for ContextMenu 'Attach file' CustomEvent wiring
  const attachFileInputRef = useRef<HTMLInputElement>(null);
  const uploadAttachment = useUploadAttachment();

  // noteVisible: true when Note button or 'Add note' context menu triggers focus-note CustomEvent
  const [noteVisible, setNoteVisible] = useState(false);
  const [noteFocusTrigger, setNoteFocusTrigger] = useState(0);

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

  const [contextMenuPos, setContextMenuPos] = useState<{ x: number; y: number } | null>(null);

  // ─── Swipe gesture state ───────────────────────────────────────────────────
  const [swipeX, setSwipeX] = useState(0);
  const [isSwiping, setIsSwiping] = useState(false);
  const [exitDirection, setExitDirection] = useState<'complete' | 'delete' | null>(null);
  const [showUndoToast, setShowUndoToast] = useState(false);
  const startX = useRef(0);
  const startY = useRef(0);
  const isPointerDown = useRef(false);
  const rowRef = useRef<HTMLDivElement>(null);
  const pendingActionRef = useRef<{
    type: 'complete' | 'delete';
    bulletId: string;
    documentId: string;
    isComplete?: boolean;
  } | null>(null);

  const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
  const hasChildren = children.length > 0;

  // ─── Dot: click-to-zoom (desktop) + long-press drag (mobile) ──────────────
  const pointerDownPos = useRef<{ x: number; y: number } | null>(null);
  const longPressTimerRef = useRef<number | null>(null);
  const isDragActiveRef = useRef(false);

  function handleDotPointerDown(e: React.PointerEvent<HTMLDivElement>) {
    if (e.button !== 0) return;
    pointerDownPos.current = { x: e.clientX, y: e.clientY };

    if (e.pointerType === 'touch') {
      e.stopPropagation(); // prevent row swipe handler
      e.currentTarget.setPointerCapture(e.pointerId);

      const sx = e.clientX;
      const sy = e.clientY;
      longPressTimerRef.current = window.setTimeout(() => {
        isDragActiveRef.current = true;
        onDragStart(bullet.id, sx, sy);
        try { navigator.vibrate?.(50); } catch { /* unsupported */ }
      }, 250);
    }
  }

  function handleDotPointerMove(e: React.PointerEvent<HTMLDivElement>) {
    if (e.pointerType !== 'touch') return;

    if (isDragActiveRef.current) {
      e.preventDefault();
      onDragMove(e.clientX, e.clientY);
      return;
    }

    // Cancel long-press if finger moved too far (> 8px)
    if (pointerDownPos.current && longPressTimerRef.current !== null) {
      const dx = e.clientX - pointerDownPos.current.x;
      const dy = e.clientY - pointerDownPos.current.y;
      if (dx * dx + dy * dy > 64) {
        clearTimeout(longPressTimerRef.current);
        longPressTimerRef.current = null;
        try { e.currentTarget.releasePointerCapture(e.pointerId); } catch { /* ok */ }
      }
    }
  }

  function handleDotPointerUp(e: React.PointerEvent<HTMLDivElement>) {
    if (e.pointerType === 'touch') {
      if (longPressTimerRef.current !== null) {
        clearTimeout(longPressTimerRef.current);
        longPressTimerRef.current = null;
      }
      if (isDragActiveRef.current) {
        isDragActiveRef.current = false;
        onDragEnd();
        pointerDownPos.current = null;
        return;
      }
      // Short tap on dot — zoom into bullet
      if (pointerDownPos.current) {
        const dx = e.clientX - pointerDownPos.current.x;
        const dy = e.clientY - pointerDownPos.current.y;
        if (dx * dx + dy * dy < 25) {
          navigate(`#bullet/${bullet.id}`);
        }
      }
      pointerDownPos.current = null;
      return;
    }

    // Desktop: click to zoom
    if (pointerDownPos.current) {
      const dx = e.clientX - pointerDownPos.current.x;
      const dy = e.clientY - pointerDownPos.current.y;
      if (dx * dx + dy * dy < 25) {
        navigate(`#bullet/${bullet.id}`);
      }
      pointerDownPos.current = null;
    }
  }

  function handleDotPointerCancel() {
    if (longPressTimerRef.current !== null) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
    if (isDragActiveRef.current) {
      isDragActiveRef.current = false;
      onDragCancel();
    }
    pointerDownPos.current = null;
  }

  // ─── Swipe gesture handlers (touch pointer events on row) ─────────────────
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
    if (Math.abs(dy) > Math.abs(dx) * 1.5) return;
    setSwipeX(dx);
  }

  function handleRowPointerUp(_e: React.PointerEvent<HTMLDivElement>) {
    if (!isPointerDown.current) return;
    isPointerDown.current = false;
    setIsSwiping(false);

    const rowWidth = rowRef.current?.offsetWidth ?? 300;
    const result = swipeThresholdReached(swipeX, rowWidth);

    if (result === 'complete') {
      pendingActionRef.current = {
        type: 'complete',
        bulletId: bullet.id,
        documentId: bullet.documentId,
        isComplete: !bullet.isComplete,
      };
      requestAnimationFrame(() => setExitDirection('complete'));
    } else if (result === 'delete') {
      pendingActionRef.current = {
        type: 'delete',
        bulletId: bullet.id,
        documentId: bullet.documentId,
      };
      requestAnimationFrame(() => setExitDirection('delete'));
    } else {
      setSwipeX(0);
    }
  }

  // ─── Long-press context menu (touch) ──────────────────────────────────────
  // Shows context menu on RELEASE after a long press (not during the hold).
  // Single tap (< 500ms) does nothing. Drag suppresses context menu.
  const ctxTouchStart = useRef<{ x: number; y: number; time: number } | null>(null);
  const ctxCancelled = useRef(false);

  const handleCtxTouchStart: React.TouchEventHandler<HTMLDivElement> = (e) => {
    const t = e.touches[0];
    ctxTouchStart.current = { x: t.clientX, y: t.clientY, time: Date.now() };
    ctxCancelled.current = false;
  };
  const handleCtxTouchMove: React.TouchEventHandler<HTMLDivElement> = (e) => {
    if (!ctxTouchStart.current || ctxCancelled.current) return;
    const t = e.touches[0];
    const dx = t.clientX - ctxTouchStart.current.x;
    const dy = t.clientY - ctxTouchStart.current.y;
    if (dx * dx + dy * dy > 64) ctxCancelled.current = true; // > 8px
  };
  const handleCtxTouchEnd: React.TouchEventHandler<HTMLDivElement> = (e) => {
    if (!ctxTouchStart.current || ctxCancelled.current || isDragActiveRef.current) {
      ctxTouchStart.current = null;
      return;
    }
    const held = Date.now() - ctxTouchStart.current.time;
    if (held >= 500) {
      e.preventDefault();
      setContextMenuPos({ x: ctxTouchStart.current.x, y: ctxTouchStart.current.y });
    }
    ctxTouchStart.current = null;
  };

  // ─── Swipe visual state ───────────────────────────────────────────────────
  const swipeBackground = swipeX > 0
    ? 'var(--color-swipe-complete)'
    : swipeX < 0
    ? 'var(--color-swipe-delete)'
    : 'transparent';

  const threshold = (rowRef.current?.offsetWidth ?? 300) * 0.4;
  const ratio = Math.min(Math.abs(swipeX) / threshold, 1);
  const atThreshold = ratio >= 1;
  const iconScale = isSwiping
    ? (atThreshold ? 1.2 : 0.5 + ratio * 0.5)
    : 1.0;

  return (
    <div
      ref={rowRef}
      style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'flex-start',
        paddingLeft: depth * 24,
        paddingTop: 5,
        paddingBottom: 5,
        textDecoration: 'none',
        touchAction: 'pan-y',
        overflow: 'hidden',
        opacity: isDragging ? 0.3 : bullet.isComplete ? 0.5 : 1,
      }}
      onContextMenu={(e) => {
        e.preventDefault();
        e.stopPropagation();
        // Only show menu for mouse right-click (desktop).
        // On touch, the contextmenu event fires during the hold — we handle
        // context menu on touchEnd instead (release after long press).
        if (!ctxTouchStart.current) {
          setContextMenuPos({ x: e.clientX, y: e.clientY });
        }
      }}
      onPointerDown={handleRowPointerDown}
      onPointerMove={handleRowPointerMove}
      onPointerUp={handleRowPointerUp}
      onTouchStart={handleCtxTouchStart}
      onTouchMove={handleCtxTouchMove}
      onTouchEnd={handleCtxTouchEnd}
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
          {swipeX > 0 && (
            <span style={{ transform: `scale(${iconScale})`, display: 'inline-flex', transition: 'transform 0.1s ease' }}>
              <Check size={20} strokeWidth={1.5} />
            </span>
          )}
          {swipeX < 0 && (
            <span style={{ transform: `scale(${iconScale})`, display: 'inline-flex', transition: 'transform 0.1s ease' }}>
              <Trash2 size={20} strokeWidth={1.5} />
            </span>
          )}
        </div>
      )}

      {/* Row content — translated by swipeX, or slid off screen on commit via exitDirection */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          width: '100%',
          transform: exitDirection === 'complete'
            ? 'translateX(110%)'
            : exitDirection === 'delete'
            ? 'translateX(-110%)'
            : `translateX(${swipeX}px)`,
          transition: exitDirection
            ? 'transform 0.25s ease-out'
            : isSwiping ? 'none' : 'transform 0.2s ease',
          background: 'var(--color-bg-base)',
          zIndex: 1,
          position: 'relative',
        }}
        onTransitionEnd={(e) => {
          if (e.propertyName !== 'transform') return;
          if (!exitDirection || !pendingActionRef.current) return;
          const action = pendingActionRef.current;
          pendingActionRef.current = null;
          setExitDirection(null);
          setSwipeX(0);
          if (action.type === 'complete') {
            markComplete.mutate({ id: action.bulletId, documentId: action.documentId, isComplete: action.isComplete! });
          } else if (action.type === 'delete') {
            softDelete.mutate({ id: action.bulletId, documentId: action.documentId });
            setShowUndoToast(true);
          }
        }}
      >
        {/* Dot — drag handle (mobile long-press) + click to zoom (desktop) */}
        <div
          className="bullet-dot bullet-timestamp"
          style={{
            width: 28,
            flexShrink: 0,
            cursor: 'grab',
            userSelect: 'none',
            fontSize: '1.5rem',
            lineHeight: '1.6rem',
            display: 'flex',
            alignItems: 'center',
            touchAction: 'none',
          }}
          onPointerDown={handleDotPointerDown}
          onPointerMove={handleDotPointerMove}
          onPointerUp={handleDotPointerUp}
          onPointerCancel={handleDotPointerCancel}
        >
          •
        </div>
        {isBookmarked && (
          <span className="bullet-date-label" style={{ fontSize: '0.6rem', lineHeight: '1.6rem', flexShrink: 0 }}>
            <Bookmark size={16} strokeWidth={1.5} className="star-filled" />
          </span>
        )}

        {/* Content */}
        <div style={{ flex: 1, minWidth: 0, textDecoration: bullet.isComplete ? 'line-through' : 'none' }}>
          <input
            ref={attachFileInputRef}
            type="file"
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              uploadAttachment.mutate({ bulletId: bullet.id, file });
              e.target.value = '';
            }}
          />
          <BulletContent
            bullet={bullet}
            bulletMap={bulletMap}
          />
          {(!!bullet.note?.trim() || noteVisible) && (
            <NoteRow
              bulletId={bullet.id}
              initialNote={bullet.note}
              focusTrigger={noteFocusTrigger}
              onHide={() => setNoteVisible(false)}
            />
          )}
          {attachments.map(a => (
            <AttachmentRow
              key={a.id}
              attachment={a}
              onDelete={() => deleteAttachment.mutate({ attachmentId: a.id, bulletId: bullet.id })}
            />
          ))}
          {contextMenuPos && createPortal(
            <ContextMenu
              bullet={bullet}
              bulletMap={bulletMap}
              position={contextMenuPos}
              onClose={() => setContextMenuPos(null)}
            />,
            document.body,
          )}
        </div>

        {/* Chevron — right side, always reserves space, only shows icon when children exist */}
        <div
          className="bullet-chevron"
          style={{
            width: 20,
            flexShrink: 0,
            cursor: hasChildren ? 'pointer' : 'default',
            userSelect: 'none',
            fontSize: '0.5rem',
            lineHeight: '1.6rem',
            textAlign: 'center',
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
          {hasChildren ? (
            <span className="bullet-chevron" style={{ display: 'inline-block', transform: bullet.isCollapsed ? 'none' : 'rotate(90deg)', transition: 'transform 0.15s ease' }}>
              <ChevronRight size={16} strokeWidth={1.5} />
            </span>
          ) : null}
        </div>
      </div>

      {showUndoToast && <UndoToast onDismiss={() => setShowUndoToast(false)} />}
    </div>
  );
}
