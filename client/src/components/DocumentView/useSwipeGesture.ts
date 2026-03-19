import { useRef, useState } from 'react';
import { swipeThresholdReached } from './gestures';

interface SwipeActions {
  onComplete: (bulletId: string, documentId: string, newIsComplete: boolean) => void;
  onDelete: (bulletId: string, documentId: string) => void;
  onContextMenu: (pos: { x: number; y: number }) => void;
}

export function useSwipeGesture(
  bullet: { id: string; documentId: string; isComplete: boolean },
  actions: SwipeActions,
  isDragActiveRef: React.RefObject<boolean>,
) {
  // ─── Swipe gesture state ─────────────────────────────────────────────────
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

  // ─── Swipe gesture handlers (touch pointer events on row) ────────────────
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

  // ─── Long-press context menu (touch) ─────────────────────────────────────
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
      actions.onContextMenu({ x: ctxTouchStart.current.x, y: ctxTouchStart.current.y });
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

  // ─── Exit animation transition end handler ───────────────────────────────
  function handleTransitionEnd(e: React.TransitionEvent) {
    if (e.propertyName !== 'transform') return;
    if (!exitDirection || !pendingActionRef.current) return;
    const action = pendingActionRef.current;
    pendingActionRef.current = null;
    setExitDirection(null);
    setSwipeX(0);
    if (action.type === 'complete') {
      actions.onComplete(action.bulletId, action.documentId, action.isComplete!);
    } else if (action.type === 'delete') {
      actions.onDelete(action.bulletId, action.documentId);
      setShowUndoToast(true);
    }
  }

  return {
    rowRef,
    swipeX,
    isSwiping,
    exitDirection,
    showUndoToast,
    setShowUndoToast,
    swipeBackground,
    iconScale,
    // Row event handlers
    handleRowPointerDown,
    handleRowPointerMove,
    handleRowPointerUp,
    // Context menu touch handlers
    handleCtxTouchStart,
    handleCtxTouchMove,
    handleCtxTouchEnd,
    // Transition end handler for exit animation
    handleTransitionEnd,
    // For context menu desktop right-click check
    isCtxTouchActive: () => ctxTouchStart.current !== null,
  };
}
