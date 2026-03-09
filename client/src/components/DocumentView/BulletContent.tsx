import { useRef, useEffect, useLayoutEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../api/client';
import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import {
  useCreateBullet,
  usePatchBullet,
  useSoftDeleteBullet,
  useIndentBullet,
  useOutdentBullet,
  useMoveBullet,
  useBulletUndoCheckpoint,
} from '../../hooks/useBullets';
import { renderBulletMarkdown } from '../../utils/markdown';
import { renderWithChips } from '../../utils/chips';
import { useUiStore } from '../../store/uiStore';

// ─── Cursor helpers ────────────────────────────────────────────────────────────

export function isCursorAtStart(el: HTMLDivElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return false;
  const range = sel.getRangeAt(0);
  return (
    range.collapsed &&
    range.startOffset === 0 &&
    (range.startContainer === el || range.startContainer === el.firstChild)
  );
}

export function isCursorAtEnd(el: HTMLDivElement): boolean {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return false;
  const range = sel.getRangeAt(0);
  if (!range.collapsed) return false;
  if (range.startContainer === el) {
    return range.startOffset === el.childNodes.length;
  }
  if (range.startContainer === el.lastChild) {
    const node = range.startContainer;
    return range.startOffset === (node.textContent?.length ?? 0);
  }
  return false;
}

export function splitAtCursor(el: HTMLDivElement): { before: string; after: string } {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    return { before: el.textContent ?? '', after: '' };
  }
  const range = sel.getRangeAt(0);
  const beforeRange = document.createRange();
  beforeRange.setStart(el, 0);
  beforeRange.setEnd(range.startContainer, range.startOffset);
  const before = beforeRange.toString();
  const after = (el.textContent ?? '').slice(before.length);
  return { before, after };
}

function setCursorAtPosition(el: HTMLDivElement, offset: number) {
  el.focus();
  const textNode = el.firstChild;
  if (!textNode) return;
  const range = document.createRange();
  const safeOffset = Math.min(offset, textNode.textContent?.length ?? 0);
  range.setStart(textNode, safeOffset);
  range.collapse(true);
  const sel = window.getSelection();
  if (sel) {
    sel.removeAllRanges();
    sel.addRange(range);
  }
}

function placeCursorAtEnd(el: HTMLDivElement) {
  const range = document.createRange();
  const sel = window.getSelection();
  range.selectNodeContents(el);
  range.collapse(false);
  sel?.removeAllRanges();
  sel?.addRange(range);
}

// ─── Shake animation style ─────────────────────────────────────────────────────

const SHAKE_STYLE = `
@keyframes bullet-shake {
  0%,100% { transform: translateX(0); }
  25%      { transform: translateX(-4px); }
  75%      { transform: translateX(4px); }
}
.bullet-shake {
  animation: bullet-shake 400ms ease;
}
`;

let shakeStyleInjected = false;
function ensureShakeStyle() {
  if (shakeStyleInjected) return;
  const style = document.createElement('style');
  style.textContent = SHAKE_STYLE;
  document.head.appendChild(style);
  shakeStyleInjected = true;
}

// ─── Chip styles ───────────────────────────────────────────────────────────────

const CHIP_STYLE = `
.chip { display: inline-block; border-radius: 3px; padding: 0 4px; font-size: 0.85em; cursor: pointer; font-weight: 500; }
.chip-tag { background: #e8f0fe; color: #1a56db; }
.chip-mention { background: #f3e8fd; color: #7c3aed; }
.chip-date { background: #fef3c7; color: #d97706; }
`;

let chipStyleInjected = false;
function ensureChipStyle() {
  if (chipStyleInjected) return;
  const style = document.createElement('style');
  style.textContent = CHIP_STYLE;
  document.head.appendChild(style);
  chipStyleInjected = true;
}

// ─── Date picker helper ────────────────────────────────────────────────────────

function triggerDatePicker(onDate: (date: string) => void) {
  const input = document.createElement('input');
  input.type = 'date';
  input.style.cssText = 'position:fixed;opacity:0;pointer-events:none;top:0;left:0;';
  document.body.appendChild(input);
  input.addEventListener('change', () => {
    onDate(input.value);
    document.body.removeChild(input);
  });
  input.addEventListener('blur', () => {
    if (document.body.contains(input)) document.body.removeChild(input);
  });
  input.click();
}

// ─── BulletContent component ───────────────────────────────────────────────────

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  onFocus?: () => void;
  isDragOverlay?: boolean;
};

export function BulletContent({ bullet, bulletMap, onFocus, isDragOverlay = false }: Props) {
  const navigate = useNavigate();
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

  const { setSidebarTab, setCanvasView, setFocusedBulletId } = useUiStore();

  const handleUndo = useCallback(async () => {
    await apiClient.post('/api/undo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }, [queryClient]);

  const handleRedo = useCallback(async () => {
    await apiClient.post('/api/redo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }, [queryClient]);

  // Sync content from props when not editing
  useEffect(() => {
    if (!isEditing) {
      setLocalContent(bullet.content);
    }
  }, [bullet.content, isEditing]);

  // Inject styles once
  useEffect(() => {
    ensureShakeStyle();
    ensureChipStyle();
  }, []);

  // Update div content when editing mode changes
  useLayoutEffect(() => {
    const el = divRef.current;
    if (!el) return;
    if (isEditing) {
      el.textContent = localContent;
      if (document.activeElement !== el) {
        el.focus();
      }
      placeCursorAtEnd(el);
    } else {
      el.innerHTML = renderWithChips(renderBulletMarkdown(localContent));
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isEditing]);

  // Keep view-mode HTML in sync when localContent changes while not editing
  useLayoutEffect(() => {
    const el = divRef.current;
    if (!el || isEditing) return;
    el.innerHTML = renderWithChips(renderBulletMarkdown(localContent));
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

    if (content.includes('!!') && !content.includes('!![')) {
      triggerDatePicker((date: string) => {
        if (!divRef.current) return;
        const updated = divRef.current.textContent!.replace('!!', `!![${date}]`);
        divRef.current.textContent = updated;
        setLocalContent(updated);
        if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
        patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: updated });
        undoCheckpoint.mutate({ id: bullet.id, content: updated });
      });
      return;
    }

    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      saveTimerRef.current = null;
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content });
      undoCheckpoint.mutate({ id: bullet.id, content });
    }, 1000);
  }

  function handleBlur() {
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
    if (!isEditing) {
      setIsEditing(true);
    }
    setFocusedBulletId(bullet.id);
    if (onFocus) onFocus();
  }

  // Handle chip clicks in view mode without entering edit mode
  function handleMouseDown(e: React.MouseEvent<HTMLDivElement>) {
    if (isEditing) return;
    const target = e.target as HTMLElement;
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
        document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]')
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
        document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]')
      );
      const myIdx = allBulletEls.indexOf(divRef.current!);
      if (myIdx < allBulletEls.length - 1) {
        e.preventDefault();
        allBulletEls[myIdx + 1].focus();
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
        setIsEditing(false);
        createBullet.mutate(
          { documentId: bullet.documentId, parentId: null, afterId: bullet.id, content: '' },
          {
            onSuccess: (data) => {
              setTimeout(() => {
                const newEl = document.getElementById(`bullet-${data.id}`) as HTMLDivElement | null;
                if (newEl) newEl.focus();
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
      setLocalContent(before);
      setIsEditing(false);

      if (children.length > 0) {
        createBullet.mutate(
          { documentId: bullet.documentId, parentId: bullet.id, afterId: null, content: after },
          {
            onSuccess: (data) => {
              setTimeout(() => {
                const newEl = document.getElementById(`bullet-${data.id}`) as HTMLDivElement | null;
                if (newEl) newEl.focus();
              }, 50);
            },
          }
        );
      } else {
        createBullet.mutate(
          { documentId: bullet.documentId, parentId: bullet.parentId, afterId: bullet.id, content: after },
          {
            onSuccess: (data) => {
              setTimeout(() => {
                const newEl = document.getElementById(`bullet-${data.id}`) as HTMLDivElement | null;
                if (newEl) newEl.focus();
              }, 50);
            },
          }
        );
      }
      return;
    }

    // ── Tab: indent / outdent ─────────────────────────────────────────────
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

      // Empty node: just delete it and move focus to nearest neighbor
      if ((el.textContent ?? '') === '') {
        softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
        setTimeout(() => {
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
            const prevEl = document.getElementById(`bullet-${prevBullet.id}`) as HTMLDivElement | null;
            if (prevEl) prevEl.focus();
          } else if (bullet.parentId !== null) {
            const parentEl = document.getElementById(`bullet-${bullet.parentId}`) as HTMLDivElement | null;
            if (parentEl) parentEl.focus();
          } else {
            // First root — focus next
            const allBulletEls = Array.from(document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]'));
            const myDomIdx = allBulletEls.findIndex(d => d === divRef.current);
            const next = allBulletEls[myDomIdx + 1];
            if (next) next.focus();
          }
        }, 50);
        return;
      }

      // Non-empty first child — ignore merge into parent
      if (myIdx === 0 && bullet.parentId !== null) return;

      // Find previous bullet in render order
      const allBullets = Object.values(bulletMap).filter(b => !b.deletedAt);
      let prevBullet: typeof allBullets[0] | undefined;
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
            const allBulletEls = Array.from(document.querySelectorAll<HTMLDivElement>('[id^="bullet-"]'));
            const myDomIdx = allBulletEls.findIndex(d => d === divRef.current);
            const target = allBulletEls[myDomIdx - 1];
            if (target) target.focus();
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

  if (isDragOverlay) {
    return (
      <div
        style={{
          flex: 1,
          fontSize: '0.9375rem',
          color: '#333',
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
      onFocus={handleFocus}
      onBlur={isEditing ? handleBlur : undefined}
      style={{
        flex: 1,
        outline: 'none',
        fontSize: '0.9375rem',
        color: '#333',
        lineHeight: 1.6,
        minHeight: '1.6em',
        wordBreak: 'break-word',
        cursor: 'text',
        userSelect: 'text',
      }}
    />
  );
}
