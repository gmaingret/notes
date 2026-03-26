/* eslint-disable react-refresh/only-export-components */
import { useRef, useEffect, useLayoutEffect, useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../api/client';
import type { FlatBullet, BulletMap } from './BulletTree';
import { useInvalidateUndoStatus } from '../../hooks/useUndoStatus';
import {
  useCreateBullet,
  usePatchBullet,
  useSoftDeleteBullet,
  useIndentBullet,
  useOutdentBullet,
  useMoveBullet,
  useBulletUndoCheckpoint,
} from '../../hooks/useBullets';
import { useUploadAttachment } from '../../hooks/useAttachments';
import { renderBulletMarkdown } from '../../utils/markdown';
import { renderWithChips } from '../../utils/chips';
import { useUiStore } from '../../store/uiStore';
import { placeCursorAtEnd } from './cursorUtils';
import { triggerDatePicker } from './datePicker';
import { useKeyboardHandlers } from './useKeyboardHandlers';

// Re-export cursor helpers for backward compatibility with external importers
export { isCursorAtStart, isCursorAtEnd, splitAtCursor } from './cursorUtils';

// ─── BulletContent component ───────────────────────────────────────────────────

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  onFocus?: () => void;
  isDragOverlay?: boolean;
};

export function BulletContent({ bullet, bulletMap, onFocus, isDragOverlay = false }: Props) {
  const queryClient = useQueryClient();
  const divRef = useRef<HTMLDivElement>(null);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [localContent, setLocalContent] = useState(bullet.content);
  const [isShaking, setIsShaking] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  // Prevent focus from re-entering edit mode during programmatic blur (e.g. chip click)
  const suppressFocusRef = useRef(false);

  const createBullet = useCreateBullet();
  const patchBullet = usePatchBullet();
  const softDeleteBullet = useSoftDeleteBullet();
  const indentBullet = useIndentBullet();
  const outdentBullet = useOutdentBullet();
  const moveBullet = useMoveBullet();
  const undoCheckpoint = useBulletUndoCheckpoint();
  const uploadAttachment = useUploadAttachment();

  // Track the last saved content so undo-checkpoint can record the previous value
  const lastSavedContentRef = useRef(bullet.content);

  const { setSidebarTab, setCanvasView, setFocusedBulletId } = useUiStore();
  const invalidateUndoStatus = useInvalidateUndoStatus();

  const handleUndo = useCallback(async () => {
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }
    await apiClient.post('/api/undo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
    invalidateUndoStatus();
    setIsEditing(false);
  }, [queryClient, invalidateUndoStatus]);

  const handleRedo = useCallback(async () => {
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }
    await apiClient.post('/api/redo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
    invalidateUndoStatus();
    setIsEditing(false);
  }, [queryClient, invalidateUndoStatus]);

  // Sync content from server when bullet.content changes and we're not editing.
  // Intentionally excludes isEditing from deps: on blur, handleBlur already sets localContent
  // to the typed value; adding isEditing here would overwrite it with stale server content.
  useEffect(() => {
    if (!isEditing) {
      setLocalContent(bullet.content);
      lastSavedContentRef.current = bullet.content;
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bullet.content]);

  // Update div content when editing mode changes
  useLayoutEffect(() => {
    const el = divRef.current;
    if (!el) return;
    if (isEditing) {
      el.textContent = localContent;
      if (document.activeElement !== el) {
        el.focus({ preventScroll: true });
      }
      placeCursorAtEnd(el);
    } else {
      el.innerHTML = renderWithChips(renderBulletMarkdown(localContent));
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEditing]);

  // Keep view-mode HTML in sync when localContent changes while not editing.
  // Guard against re-setting identical HTML — avoids visible flash on every
  // React Query re-fetch even when the bullet content hasn't changed.
  useLayoutEffect(() => {
    const el = divRef.current;
    if (!el || isEditing) return;
    const newHtml = renderWithChips(renderBulletMarkdown(localContent));
    if (el.innerHTML !== newHtml) {
      el.innerHTML = newHtml;
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [localContent]);

  function triggerShake() {
    setIsShaking(true);
    setTimeout(() => setIsShaking(false), 400);
  }

  function handleInput() {
    const el = divRef.current;
    if (!el) return;
    const content = el.textContent ?? '';
    setLocalContent(content);

    if (/!!(?!\[)/.test(content)) {
      triggerDatePicker((date: string) => {
        if (!divRef.current) return;
        const updated = divRef.current.textContent!.replace(/!!(?!\[)/, `!![${date}]`);
        divRef.current.textContent = updated;
        setLocalContent(updated);
        if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
        const prevContent = lastSavedContentRef.current;
        patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: updated });
        undoCheckpoint.mutate({ id: bullet.id, content: updated, previousContent: prevContent });
        lastSavedContentRef.current = updated;
      });
      return;
    }

    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null;
      const prevContent = lastSavedContentRef.current;
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content });
      undoCheckpoint.mutate({ id: bullet.id, content, previousContent: prevContent });
      lastSavedContentRef.current = content;
    }, 1000);
  }

  function handleBlur(e: React.FocusEvent<HTMLDivElement>) {
    // If focus moved to an element inside the FocusToolbar, keep the toolbar visible
    // so toolbar button clicks can still fire (the button will receive the click after blur).
    const relatedTarget = e.relatedTarget as HTMLElement | null;
    if (relatedTarget && relatedTarget.closest('[data-focus-toolbar]')) {
      // Restore focus to the bullet after the toolbar action fires (next tick)
      setTimeout(() => {
        divRef.current?.focus();
      }, 0);
      return;
    }

    // Flush any pending save immediately
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
      const content = divRef.current?.textContent ?? '';
      if (content !== bullet.content) {
        patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content });
      }
      setLocalContent(content);
    }
    setIsEditing(false);
    // Clear focused bullet LAST to avoid race where toolbar disappears before action completes
    setFocusedBulletId(null);
  }

  function handleFocus() {
    if (suppressFocusRef.current) return;

    // Capture scroll position before the browser auto-scrolls the focused element
    // into view. We restore it in the next frame so only the FocusToolbar's
    // scrollBulletAboveToolbar (which checks actual visibility) moves the page.
    const main = document.querySelector<HTMLElement>('main');
    const savedScrollTop = main?.scrollTop ?? 0;

    if (!isEditing) {
      setIsEditing(true);
    }
    setFocusedBulletId(bullet.id);
    if (onFocus) onFocus();

    if (main) {
      requestAnimationFrame(() => {
        main.scrollTop = savedScrollTop;
      });
    }
  }

  // Handle chip/link clicks in view mode without entering edit mode
  function handleMouseDown(e: React.MouseEvent<HTMLDivElement>) {
    if (isEditing) return;
    const target = e.target as HTMLElement;

    // Open markdown links in a new tab without entering edit mode
    const anchor = target.closest('a') as HTMLAnchorElement | null;
    if (anchor?.href) {
      e.preventDefault();
      window.open(anchor.href, '_blank', 'noopener,noreferrer');
      return;
    }

    const chipType = target.dataset.chipType as 'tag' | 'mention' | 'date' | undefined;
    const chipValue = target.dataset.chipValue;
    if (chipType && chipValue) {
      e.preventDefault(); // Prevent focus → edit mode
      if (chipType === 'tag' || chipType === 'mention') {
        setSidebarTab('tags');
        setCanvasView({ type: 'filtered', chipType, chipValue });
      } else if (chipType === 'date') {
        suppressFocusRef.current = true;
        triggerDatePicker((newDate: string) => {
          suppressFocusRef.current = false;
          const updated = localContent.replace(`!![${chipValue}]`, `!![${newDate}]`);
          setLocalContent(updated);
          patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: updated });
        });
        setTimeout(() => { suppressFocusRef.current = false; }, 500);
      }
    }
  }

  const { handleKeyDown, handlePaste } = useKeyboardHandlers({
    bullet, bulletMap, divRef, saveTimerRef, lastSavedContentRef,
    isEditing, localContent, setLocalContent, setIsEditing, triggerShake,
    createBullet, patchBullet, softDeleteBullet, indentBullet, outdentBullet,
    moveBullet, undoCheckpoint, uploadAttachment, handleUndo, handleRedo,
  });

  if (isDragOverlay) {
    return (
      <div
        style={{
          flex: 1,
          fontSize: '0.9375rem',
          lineHeight: 1.6,
          minHeight: '1.6em',
          wordBreak: 'break-word',
          userSelect: 'none',
        }}
      >
        {bullet.content}
      </div>
    );
  }

  // Single persistent div — always in DOM with stable id for focus navigation.
  // View mode: contentEditable=false, shows rendered HTML (managed via layoutEffect).
  // Edit mode: contentEditable=true, shows plain text for editing.
  // tabIndex=0 ensures the div is focusable in both modes so arrow-key navigation works.
  return (
    <div
      id={`bullet-${bullet.id}`}
      ref={divRef}
      contentEditable={isEditing}
      suppressContentEditableWarning
      tabIndex={0}
      className={isShaking ? 'bullet-shake' : undefined}
      onInput={isEditing ? handleInput : undefined}
      onKeyDown={handleKeyDown}
      onMouseDown={handleMouseDown}
      onPaste={isEditing ? handlePaste : undefined}
      onFocus={handleFocus}
      onBlur={isEditing ? handleBlur : undefined}
      style={{
        flex: 1,
        outline: 'none',
        fontSize: '0.9375rem',
        lineHeight: 1.6,
        minHeight: '1.6em',
        wordBreak: 'break-word',
        cursor: 'text',
        userSelect: 'text',
      }}
    />
  );
}
