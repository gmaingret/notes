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
  // mutation hooks passed through
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
    const start = range.startOffset;
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
    void start;

    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null;
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: newContent });
    }, 1000);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    const el = e.currentTarget;
    const isMeta = e.ctrlKey || e.metaKey;

    // ── Escape: exit edit mode ─────────────────────────────────────────────
    if (e.key === 'Escape') {
      e.preventDefault();
      divRef.current?.blur();
      return;
    }

    // ── ArrowUp: navigate to previous bullet ──────────────────────────────
    if (e.key === 'ArrowUp' && !isMeta) {
      const allBulletEls = Array.from(
        document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]:not([id^="bullet-row-"])')
      );
      const myIdx = allBulletEls.indexOf(divRef.current!);
      if (myIdx > 0) {
        e.preventDefault();
        allBulletEls[myIdx - 1].focus();
      }
      return;
    }

    // ── ArrowDown: navigate to next bullet ────────────────────────────────
    if (e.key === 'ArrowDown' && !isMeta) {
      const allBulletEls = Array.from(
        document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]:not([id^="bullet-row-"])')
      );
      const myIdx = allBulletEls.indexOf(divRef.current!);
      if (myIdx < allBulletEls.length - 1) {
        e.preventDefault();
        allBulletEls[myIdx + 1].focus();
      }
      return;
    }

    // ── Shift+Enter: toggle note/comment section ───────────────────────────
    if (e.key === 'Enter' && e.shiftKey) {
      e.preventDefault();
      document.dispatchEvent(new CustomEvent('focus-note', { detail: { bulletId: bullet.id } }));
      return;
    }

    // ── Tab: indent / outdent (works with or without edit mode) ───────────
    if (e.key === 'Tab') {
      e.preventDefault();
      if (e.shiftKey) {
        if (bullet.parentId === null) return;
        outdentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
      } else {
        const siblings = getChildren(bulletMap, bullet.parentId);
        const myIdx = siblings.findIndex(s => s.id === bullet.id);
        if (myIdx === 0) return;
        indentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
      }
      return;
    }

    // All keys below only apply in edit mode
    if (!isEditing) return;

    // ── Ctrl/Cmd+B: bold ───────────────────────────────────────────────────
    if (isMeta && e.key === 'b') {
      e.preventDefault();
      wrapSelection('**');
      return;
    }

    // ── Ctrl/Cmd+I: italic ────────────────────────────────────────────────
    if (isMeta && e.key === 'i') {
      e.preventDefault();
      wrapSelection('*');
      return;
    }

    // ── Ctrl/Cmd+Z: undo ──────────────────────────────────────────────────
    if (isMeta && e.key === 'z' && !e.shiftKey) {
      e.preventDefault();
      void handleUndo();
      return;
    }

    // ── Ctrl/Cmd+Y: redo ──────────────────────────────────────────────────
    if (isMeta && e.key === 'y') {
      e.preventDefault();
      void handleRedo();
      return;
    }

    // ── Ctrl/Cmd+]: zoom into focused bullet ──────────────────────────────
    if (isMeta && e.key === ']') {
      e.preventDefault();
      navigate(`#bullet/${bullet.id}`);
      return;
    }

    // ── Ctrl/Cmd+[: zoom out one level ────────────────────────────────────
    if (isMeta && e.key === '[') {
      e.preventDefault();
      if (bullet.parentId) {
        navigate(`#bullet/${bullet.parentId}`);
      } else {
        navigate('', { relative: 'path' });
      }
      return;
    }

    // ── Ctrl/Cmd+ArrowUp: move bullet up ──────────────────────────────────
    if (isMeta && e.key === 'ArrowUp') {
      e.preventDefault();
      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);
      if (myIdx <= 0) return;
      const afterId = myIdx >= 2 ? siblings[myIdx - 2].id : null;
      moveBullet.mutate({
        id: bullet.id,
        documentId: bullet.documentId,
        newParentId: bullet.parentId,
        afterId,
      });
      return;
    }

    // ── Ctrl/Cmd+ArrowDown: move bullet down ──────────────────────────────
    if (isMeta && e.key === 'ArrowDown') {
      e.preventDefault();
      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);
      if (myIdx >= siblings.length - 1) return;
      const nextNextSibling = siblings[myIdx + 2] ?? null;
      moveBullet.mutate({
        id: bullet.id,
        documentId: bullet.documentId,
        newParentId: bullet.parentId,
        afterId: nextNextSibling ? nextNextSibling.id : siblings[myIdx + 1].id,
      });
      return;
    }

    // ── Enter: create / outdent ───────────────────────────────────────────
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      const currentContent = el.textContent ?? '';

      if (currentContent === '' && bullet.parentId !== null) {
        outdentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
        return;
      }

      if (currentContent === '' && bullet.parentId === null) {
        createBullet.mutate(
          { documentId: bullet.documentId, parentId: null, afterId: bullet.id, content: '' },
          {
            onSuccess: (data) => {
              setTimeout(() => {
                const newEl = document.getElementById(`bullet-${data.id}`) as HTMLDivElement | null;
                if (newEl) {
                  // Set contentEditable before focus so the browser keeps the keyboard open
                  // (focus moving from contentEditable→non-editable dismisses the keyboard).
                  newEl.contentEditable = 'true';
                  newEl.focus();
                }
              }, 50);
            },
          }
        );
        return;
      }

      const { before, after } = splitAtCursor(el);
      const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);

      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: before });
      // Update React state but leave the DOM untouched — setting el.textContent would reset
      // the cursor to position 0. The DOM will be corrected when isEditing becomes false
      // via the natural blur that fires when focus moves to the new element.
      setLocalContent(before);

      const focusNewBullet = (id: string) => {
        setTimeout(() => {
          const newEl = document.getElementById(`bullet-${id}`) as HTMLDivElement | null;
          if (newEl) {
            // Set contentEditable before focus so the browser keeps the keyboard open.
            newEl.contentEditable = 'true';
            newEl.focus();
          }
        }, 50);
      };

      if (children.length > 0) {
        createBullet.mutate(
          { documentId: bullet.documentId, parentId: bullet.id, afterId: null, content: after },
          { onSuccess: (data) => focusNewBullet(data.id) }
        );
      } else {
        createBullet.mutate(
          { documentId: bullet.documentId, parentId: bullet.parentId, afterId: bullet.id, content: after },
          { onSuccess: (data) => focusNewBullet(data.id) }
        );
      }
      return;
    }

    // ── Backspace at start ────────────────────────────────────────────────
    if (e.key === 'Backspace' && isCursorAtStart(el)) {
      e.preventDefault();
      const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
      if (children.length > 0) {
        triggerShake();
        return;
      }

      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);

      // Empty node: move focus synchronously BEFORE deleting so blur+focus fire in
      // the same React batch while both elements are still in the DOM. This keeps
      // focusedBulletId from briefly going null (which causes the toolbar to flash).
      if ((el.textContent ?? '') === '') {
        let target: HTMLDivElement | null = null;
        if (myIdx > 0) {
          let prevBullet = siblings[myIdx - 1];
          let candidate = prevBullet;
          while (true) {
            if (candidate.isCollapsed) break;
            const prevChildren = getChildren(bulletMap, candidate.id).filter(b => !b.deletedAt);
            if (prevChildren.length === 0) break;
            candidate = prevChildren[prevChildren.length - 1];
          }
          prevBullet = candidate;
          target = document.getElementById(`bullet-${prevBullet.id}`) as HTMLDivElement | null;
        } else if (bullet.parentId !== null) {
          target = document.getElementById(`bullet-${bullet.parentId}`) as HTMLDivElement | null;
        } else {
          const allBulletEls = Array.from(document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]:not([id^="bullet-row-"])'));
          const myDomIdx = allBulletEls.findIndex(d => d === divRef.current);
          target = allBulletEls[myDomIdx + 1] ?? null;
        }
        if (target) {
          target.contentEditable = 'true';
          target.focus();
        }
        softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
        return;
      }

      // Non-empty first child — ignore merge into parent
      if (myIdx === 0 && bullet.parentId !== null) return;

      // Find previous bullet in render order
      type BulletItem = (typeof bulletMap)[string];
      let prevBullet: BulletItem | undefined;
      if (myIdx > 0) {
        prevBullet = siblings[myIdx - 1];
        let candidate = prevBullet;
        while (true) {
          if (candidate.isCollapsed) break;
          const prevChildren = getChildren(bulletMap, candidate.id).filter(b => !b.deletedAt);
          if (prevChildren.length === 0) break;
          candidate = prevChildren[prevChildren.length - 1];
        }
        prevBullet = candidate;
      }

      if (!prevBullet) return;

      const prevContent = prevBullet.content;
      const joinOffset = prevContent.length;
      const mergedContent = prevContent + (el.textContent ?? '');

      patchBullet.mutate({ id: prevBullet.id, documentId: bullet.documentId, content: mergedContent });
      softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });

      setTimeout(() => {
        const prevEl = document.getElementById(`bullet-${prevBullet!.id}`) as HTMLDivElement | null;
        if (prevEl) {
          setCursorAtPosition(prevEl, joinOffset);
        }
      }, 50);
      return;
    }

    // ── Delete at end ─────────────────────────────────────────────────────
    if (e.key === 'Delete' && isCursorAtEnd(el)) {
      e.preventDefault();
      const ownChildren = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);
      if (ownChildren.length > 0) return;

      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);
      const nextSibling = siblings[myIdx + 1];

      // Empty node with no next sibling: delete it
      if (!nextSibling) {
        if ((el.textContent ?? '') === '') {
          softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
          setTimeout(() => {
            const allBulletEls = Array.from(document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]:not([id^="bullet-row-"])'));
            const myDomIdx = allBulletEls.findIndex(d => d === divRef.current);
            const target = allBulletEls[myDomIdx - 1];
            if (target) {
              target.contentEditable = 'true';
              target.focus();
            }
          }, 50);
        }
        return;
      }

      const nextSiblingChildren = getChildren(bulletMap, nextSibling.id).filter(b => !b.deletedAt);
      const mergedContent = (el.textContent ?? '') + nextSibling.content;
      const cursorOffset = (el.textContent ?? '').length;

      el.textContent = mergedContent;
      setLocalContent(mergedContent);
      if (saveTimerRef.current) {
        clearTimeout(saveTimerRef.current);
        saveTimerRef.current = null;
      }

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
        if (myEl) {
          setCursorAtPosition(myEl, cursorOffset);
        }
      }, 50);
      return;
    }
  }

  function handlePaste(e: React.ClipboardEvent<HTMLDivElement>) {
    const items = Array.from(e.clipboardData.items);
    const imageItem = items.find(item => item.type.startsWith('image/'));
    if (imageItem) {
      // Upload image as attachment instead of letting browser paste it into the div
      e.preventDefault();
      const file = imageItem.getAsFile();
      if (file) {
        uploadAttachment.mutate({ bulletId: bullet.id, file });
      }
      return;
    }
    // For non-image content: paste as plain text to avoid pasting rich HTML
    e.preventDefault();
    const text = e.clipboardData.getData('text/plain');
    if (!text) return;

    // If pasting a URL while text is selected, wrap as markdown link
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
