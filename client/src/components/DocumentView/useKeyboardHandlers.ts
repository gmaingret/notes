import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import type {
  useCreateBullet,
  usePatchBullet,
  useSoftDeleteBullet,
  useIndentBullet,
  useOutdentBullet,
  useMoveBullet,
  useBulletUndoCheckpoint,
} from '../../hooks/useBullets';
import type { useUploadAttachment } from '../../hooks/useAttachments';
import { isCursorAtStart, isCursorAtEnd, splitAtCursor, setCursorAtPosition } from './cursorUtils';
import {
  getAllBulletElements,
  computeMoveUpAfterId,
  computeMoveDownAfterId,
  canIndent,
  findDeepestVisibleChild,
} from './bulletOps';

// Module-level state: survives re-renders caused by optimistic cache updates.
let createInFlight = false;
let pendingAction: ((newBulletId: string, docId: string) => void) | null = null;

export function useKeyboardHandlers(params: {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  divRef: React.RefObject<HTMLDivElement | null>;
  saveTimerRef: React.MutableRefObject<ReturnType<typeof setTimeout> | null>;
  lastSavedContentRef: React.MutableRefObject<string>;
  isEditing: boolean;
  localContent: string;
  setLocalContent: (content: string) => void;
  setIsEditing: (editing: boolean) => void;
  triggerShake: () => void;
  createBullet: ReturnType<typeof useCreateBullet>;
  patchBullet: ReturnType<typeof usePatchBullet>;
  softDeleteBullet: ReturnType<typeof useSoftDeleteBullet>;
  indentBullet: ReturnType<typeof useIndentBullet>;
  outdentBullet: ReturnType<typeof useOutdentBullet>;
  moveBullet: ReturnType<typeof useMoveBullet>;
  undoCheckpoint: ReturnType<typeof useBulletUndoCheckpoint>;
  uploadAttachment: ReturnType<typeof useUploadAttachment>;
  handleUndo: () => Promise<void>;
  handleRedo: () => Promise<void>;
}): {
  handleKeyDown: (e: React.KeyboardEvent<HTMLDivElement>) => void;
  handlePaste: (e: React.ClipboardEvent<HTMLDivElement>) => void;
} {
  const {
    bullet,
    bulletMap,
    divRef,
    saveTimerRef,
    isEditing,
    setLocalContent,
    triggerShake,
    createBullet,
    patchBullet,
    softDeleteBullet,
    indentBullet,
    outdentBullet,
    moveBullet,
    uploadAttachment,
    handleUndo,
    handleRedo,
  } = params;

  const navigate = useNavigate();

  function wrapSelection(marker: string) {
    const sel = window.getSelection();
    if (!sel || sel.rangeCount === 0) return;
    const range = sel.getRangeAt(0);
    if (range.collapsed) return;

    const selected = range.toString();
    const el = divRef.current;
    if (!el) return;

    const text = el.textContent ?? '';
    const beforeRange = document.createRange();
    beforeRange.setStart(el, 0);
    beforeRange.setEnd(range.startContainer, range.startOffset);
    const beforeText = beforeRange.toString();
    const startIdx = beforeText.length;
    const endIdx = startIdx + selected.length;

    const newContent = text.slice(0, startIdx) + marker + selected + marker + text.slice(endIdx);
    el.textContent = newContent;
    setLocalContent(newContent);

    const newCursorPos = startIdx + marker.length + selected.length + marker.length;
    setCursorAtPosition(el, newCursorPos);

    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null;
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: newContent });
    }, 1000);
  }

  /**
   * Focus a bullet element by ID, polling via rAF until it appears.
   */
  function focusNewBullet(id: string) {
    const maxAttempts = 20;
    let attempts = 0;
    function tryFocus() {
      const newEl = document.getElementById(`bullet-${id}`) as HTMLDivElement | null;
      if (newEl) {
        newEl.contentEditable = 'true';
        newEl.focus();
        return;
      }
      if (++attempts < maxAttempts) {
        requestAnimationFrame(tryFocus);
      }
    }
    requestAnimationFrame(tryFocus);
  }

  /**
   * Focus the new bullet that appears after the current one in DOM order.
   * Polls via rAF because the optimistic update from onMutate is async —
   * React may not have committed the new element by the first frame.
   */
  function focusNextBullet() {
    const elsBefore = getAllBulletElements();
    const myIdx = elsBefore.indexOf(divRef.current!);
    const oldNext = elsBefore[myIdx + 1] ?? null;

    let attempts = 0;
    function tryFocus() {
      const els = getAllBulletElements();
      const myNewIdx = els.indexOf(divRef.current!);
      const candidate = els[myNewIdx + 1];
      // A new element appeared at myIdx+1 (different from what was there before)
      if (candidate && candidate !== oldNext) {
        candidate.contentEditable = 'true';
        candidate.focus();
        return;
      }
      if (++attempts < 30) { // ~500ms max
        requestAnimationFrame(tryFocus);
      }
    }
    requestAnimationFrame(tryFocus);
  }

  /** Create a bullet, focus it immediately via DOM position, execute queued actions on success. */
  function createAndFocus(vars: Parameters<typeof createBullet.mutate>[0]) {
    createInFlight = true;
    pendingAction = null;
    createBullet.mutate(vars, {
      onSuccess: (data) => {
        // Focus the real bullet (replaces optimistic in DOM)
        focusNewBullet(data.id);
        // Execute queued action (e.g. Tab→indent) that fired while create was in-flight
        if (pendingAction) {
          const action = pendingAction;
          pendingAction = null;
          action(data.id, data.documentId);
        }
      },
      onSettled: () => { createInFlight = false; },
    });
    // Focus optimistic bullet immediately (appears after React commits on next frame)
    focusNextBullet();
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    const el = e.currentTarget;
    const isMeta = e.ctrlKey || e.metaKey;
    const isOptimistic = bullet.id.startsWith('optimistic-');

    // ── Escape: exit edit mode
    if (e.key === 'Escape') {
      e.preventDefault();
      divRef.current?.blur();
      return;
    }

    // ── ArrowUp/Down: navigate between bullets (works on optimistic too — DOM only)
    if ((e.key === 'ArrowUp' || e.key === 'ArrowDown') && !isMeta) {
      const allEls = getAllBulletElements();
      const myIdx = allEls.indexOf(divRef.current!);
      const targetIdx = e.key === 'ArrowUp' ? myIdx - 1 : myIdx + 1;
      if (targetIdx >= 0 && targetIdx < allEls.length) {
        e.preventDefault();
        allEls[targetIdx].focus();
      }
      return;
    }

    // ── Shift+Enter: toggle note/comment section
    if (e.key === 'Enter' && e.shiftKey) {
      e.preventDefault();
      document.dispatchEvent(new CustomEvent('focus-note', { detail: { bulletId: bullet.id } }));
      return;
    }

    // ── Tab: indent / outdent
    if (e.key === 'Tab') {
      e.preventDefault();

      // If a create is in-flight (Enter just pressed), queue the indent/outdent
      // to run when the server responds with the real bullet ID.
      if (isOptimistic || createInFlight) {
        if (e.shiftKey) {
          pendingAction = (id, docId) => outdentBullet.mutate({ id, documentId: docId });
        } else {
          pendingAction = (id, docId) => indentBullet.mutate({ id, documentId: docId });
        }
        return;
      }

      if (e.shiftKey) {
        if (bullet.parentId === null) return;
        outdentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
      } else {
        if (!canIndent(bulletMap, bullet)) return;
        indentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
      }
      return;
    }

    // All keys below only apply in edit mode
    if (!isEditing) return;

    // Block server mutations on optimistic bullets (waiting for real ID)
    // Arrow navigation and Escape still work above.
    if (isOptimistic) return;

    // ── Ctrl/Cmd+B: bold
    if (isMeta && e.key === 'b') { e.preventDefault(); wrapSelection('**'); return; }

    // ── Ctrl/Cmd+I: italic
    if (isMeta && e.key === 'i') { e.preventDefault(); wrapSelection('*'); return; }

    // ── Ctrl/Cmd+Z: undo
    if (isMeta && e.key === 'z' && !e.shiftKey) { e.preventDefault(); void handleUndo(); return; }

    // ── Ctrl/Cmd+Y: redo
    if (isMeta && e.key === 'y') { e.preventDefault(); void handleRedo(); return; }

    // ── Ctrl/Cmd+]: zoom into focused bullet
    if (isMeta && e.key === ']') { e.preventDefault(); navigate(`#bullet/${bullet.id}`); return; }

    // ── Ctrl/Cmd+[: zoom out one level
    if (isMeta && e.key === '[') {
      e.preventDefault();
      navigate(bullet.parentId ? `#bullet/${bullet.parentId}` : '', { relative: 'path' });
      return;
    }

    // ── Ctrl/Cmd+ArrowUp: move bullet up
    if (isMeta && e.key === 'ArrowUp') {
      e.preventDefault();
      const afterId = computeMoveUpAfterId(bulletMap, bullet);
      if (afterId === 'noop') return;
      moveBullet.mutate({ id: bullet.id, documentId: bullet.documentId, newParentId: bullet.parentId, afterId });
      return;
    }

    // ── Ctrl/Cmd+ArrowDown: move bullet down
    if (isMeta && e.key === 'ArrowDown') {
      e.preventDefault();
      const afterId = computeMoveDownAfterId(bulletMap, bullet);
      if (afterId === 'noop') return;
      moveBullet.mutate({ id: bullet.id, documentId: bullet.documentId, newParentId: bullet.parentId, afterId });
      return;
    }

    // ── Enter: create / outdent
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      const currentContent = el.textContent ?? '';

      if (currentContent === '' && bullet.parentId !== null) {
        outdentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
        return;
      }

      if (currentContent === '' && bullet.parentId === null) {
        createAndFocus({ documentId: bullet.documentId, parentId: null, afterId: bullet.id, content: '' });
        return;
      }

      const { before, after } = splitAtCursor(el);
      const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);

      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: before });
      setLocalContent(before);

      if (children.length > 0) {
        createAndFocus({ documentId: bullet.documentId, parentId: bullet.id, afterId: null, content: after });
      } else {
        createAndFocus({ documentId: bullet.documentId, parentId: bullet.parentId, afterId: bullet.id, content: after });
      }
      return;
    }

    // ── Backspace at start
    if (e.key === 'Backspace' && isCursorAtStart(el)) {
      e.preventDefault();
      const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
      if (children.length > 0) { triggerShake(); return; }

      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);

      // Empty node: move focus BEFORE deleting
      if ((el.textContent ?? '') === '') {
        let target: HTMLDivElement | null = null;
        if (myIdx > 0) {
          const prev = findDeepestVisibleChild(bulletMap, siblings[myIdx - 1]);
          target = document.getElementById(`bullet-${prev.id}`) as HTMLDivElement | null;
        } else if (bullet.parentId !== null) {
          target = document.getElementById(`bullet-${bullet.parentId}`) as HTMLDivElement | null;
        } else {
          const allEls = getAllBulletElements();
          const myDomIdx = allEls.findIndex(d => d === divRef.current);
          target = allEls[myDomIdx + 1] ?? null;
        }
        if (target) { target.contentEditable = 'true'; target.focus(); }
        softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
        return;
      }

      // Non-empty first child — ignore merge into parent
      if (myIdx === 0 && bullet.parentId !== null) return;

      // Find previous bullet in render order
      let prevBullet: typeof siblings[0] | undefined;
      if (myIdx > 0) {
        prevBullet = findDeepestVisibleChild(bulletMap, siblings[myIdx - 1]);
      }
      if (!prevBullet) return;

      const prevContent = prevBullet.content;
      const joinOffset = prevContent.length;
      const mergedContent = prevContent + (el.textContent ?? '');

      patchBullet.mutate({ id: prevBullet.id, documentId: bullet.documentId, content: mergedContent });
      softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });

      setTimeout(() => {
        const prevEl = document.getElementById(`bullet-${prevBullet!.id}`) as HTMLDivElement | null;
        if (prevEl) setCursorAtPosition(prevEl, joinOffset);
      }, 50);
      return;
    }

    // ── Delete at end
    if (e.key === 'Delete' && isCursorAtEnd(el)) {
      e.preventDefault();
      const ownChildren = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
      if (ownChildren.length > 0) return;

      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);
      const nextSibling = siblings[myIdx + 1];

      if (!nextSibling) {
        if ((el.textContent ?? '') === '') {
          softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
          setTimeout(() => {
            const allEls = getAllBulletElements();
            const myDomIdx = allEls.findIndex(d => d === divRef.current);
            const target = allEls[myDomIdx - 1];
            if (target) { target.contentEditable = 'true'; target.focus(); }
          }, 50);
        }
        return;
      }

      const nextSiblingChildren = getChildren(bulletMap, nextSibling.id).filter(b => !b.deletedAt);
      const mergedContent = (el.textContent ?? '') + nextSibling.content;
      const cursorOffset = (el.textContent ?? '').length;

      el.textContent = mergedContent;
      setLocalContent(mergedContent);
      if (saveTimerRef.current) { clearTimeout(saveTimerRef.current); saveTimerRef.current = null; }

      if (nextSiblingChildren.length > 0) {
        void (async () => {
          await patchBullet.mutateAsync({ id: bullet.id, documentId: bullet.documentId, content: mergedContent });
          for (const child of [...nextSiblingChildren].reverse()) {
            await moveBullet.mutateAsync({
              id: child.id,
              documentId: bullet.documentId,
              newParentId: bullet.id,
              afterId: null,
            });
          }
          softDeleteBullet.mutate({ id: nextSibling.id, documentId: bullet.documentId });
        })();
      } else {
        patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: mergedContent });
        softDeleteBullet.mutate({ id: nextSibling.id, documentId: bullet.documentId });
      }

      setTimeout(() => {
        const myEl = document.getElementById(`bullet-${bullet.id}`) as HTMLDivElement | null;
        if (myEl) setCursorAtPosition(myEl, cursorOffset);
      }, 50);
      return;
    }
  }

  function handlePaste(e: React.ClipboardEvent<HTMLDivElement>) {
    const items = Array.from(e.clipboardData.items);
    const imageItem = items.find(item => item.type.startsWith('image/'));
    if (imageItem) {
      e.preventDefault();
      const file = imageItem.getAsFile();
      if (file) uploadAttachment.mutate({ bulletId: bullet.id, file });
      return;
    }
    e.preventDefault();
    const text = e.clipboardData.getData('text/plain');
    if (!text) return;

    const sel = window.getSelection();
    if (sel && !sel.isCollapsed && /^https?:\/\/\S+$/.test(text.trim())) {
      const selected = sel.toString();
      document.execCommand('insertText', false, `[${selected}](${text.trim()})`);
      return;
    }
    document.execCommand('insertText', false, text);
  }

  return { handleKeyDown, handlePaste };
}
