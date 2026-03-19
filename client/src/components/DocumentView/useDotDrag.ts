import { useRef } from 'react';
import { useNavigate } from 'react-router-dom';

interface DotDragActions {
  onDragStart: (bulletId: string, x: number, y: number) => void;
  onDragMove: (x: number, y: number) => void;
  onDragEnd: () => void;
  onDragCancel: () => void;
}

export function useDotDrag(
  bulletId: string,
  actions: DotDragActions,
) {
  const navigate = useNavigate();

  const pointerDownPos = useRef<{ x: number; y: number } | null>(null);
  const longPressTimerRef = useRef<number | null>(null);
  const isDragActiveRef = useRef(false);

  function handleDotPointerDown(e: React.PointerEvent<HTMLDivElement>) {
    if (e.button !== 0) return;
    pointerDownPos.current = { x: e.clientX, y: e.clientY };
    e.currentTarget.setPointerCapture(e.pointerId);

    if (e.pointerType === 'touch') {
      e.stopPropagation(); // prevent row swipe handler

      const sx = e.clientX;
      const sy = e.clientY;
      longPressTimerRef.current = window.setTimeout(() => {
        isDragActiveRef.current = true;
        actions.onDragStart(bulletId, sx, sy);
        try { navigator.vibrate?.(50); } catch { /* unsupported */ }
      }, 250);
    }
    // Mouse: drag activates on move (see handleDotPointerMove), no long-press needed
  }

  function handleDotPointerMove(e: React.PointerEvent<HTMLDivElement>) {
    if (isDragActiveRef.current) {
      e.preventDefault();
      actions.onDragMove(e.clientX, e.clientY);
      return;
    }

    if (!pointerDownPos.current) return;
    const dx = e.clientX - pointerDownPos.current.x;
    const dy = e.clientY - pointerDownPos.current.y;
    const dist2 = dx * dx + dy * dy;

    if (e.pointerType === 'touch') {
      // Cancel long-press if finger moved too far (> 8px)
      if (longPressTimerRef.current !== null && dist2 > 64) {
        clearTimeout(longPressTimerRef.current);
        longPressTimerRef.current = null;
        try { e.currentTarget.releasePointerCapture(e.pointerId); } catch { /* ok */ }
      }
    } else {
      // Mouse: start drag after 5px movement
      if (dist2 > 25) {
        isDragActiveRef.current = true;
        actions.onDragStart(bulletId, pointerDownPos.current.x, pointerDownPos.current.y);
        actions.onDragMove(e.clientX, e.clientY);
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
        actions.onDragEnd();
        pointerDownPos.current = null;
        return;
      }
      // Short tap on dot — zoom into bullet
      if (pointerDownPos.current) {
        const dx = e.clientX - pointerDownPos.current.x;
        const dy = e.clientY - pointerDownPos.current.y;
        if (dx * dx + dy * dy < 25) {
          navigate(`#bullet/${bulletId}`);
        }
      }
      pointerDownPos.current = null;
      return;
    }

    // Desktop mouse
    if (isDragActiveRef.current) {
      isDragActiveRef.current = false;
      actions.onDragEnd();
      pointerDownPos.current = null;
      return;
    }
    // Click without drag — zoom into bullet
    if (pointerDownPos.current) {
      const dx = e.clientX - pointerDownPos.current.x;
      const dy = e.clientY - pointerDownPos.current.y;
      if (dx * dx + dy * dy < 25) {
        navigate(`#bullet/${bulletId}`);
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
      actions.onDragCancel();
    }
    pointerDownPos.current = null;
  }

  return {
    isDragActiveRef,
    handleDotPointerDown,
    handleDotPointerMove,
    handleDotPointerUp,
    handleDotPointerCancel,
  };
}
