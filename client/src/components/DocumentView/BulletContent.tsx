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
  // At end of text node
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
    onDate(input.value); // YYYY-MM-DD
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

  const createBullet = useCreateBullet();
  const patchBullet = usePatchBullet();
  const softDeleteBullet = useSoftDeleteBullet();
  const indentBullet = useIndentBullet();
  const outdentBullet = useOutdentBullet();
  const moveBullet = useMoveBullet();
  const undoCheckpoint = useBulletUndoCheckpoint();

  const { setSidebarTab, setCanvasView } = useUiStore();

  // Undo/redo handlers — call API and invalidate all bullet queries (global scope per UNDO-02)
  const handleUndo = useCallback(async () => {
    await apiClient.post('/api/undo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }, [queryClient]);

  const handleRedo = useCallback(async () => {
    await apiClient.post('/api/redo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }, [queryClient]);

  // Sync from props only when not editing
  useEffect(() => {
    if (!isEditing) {
      setLocalContent(bullet.content);
      if (divRef.current) divRef.current.textContent = bullet.content;
    }
  }, [bullet.content, isEditing]);

  // Set initial textContent
  useEffect(() => {
    if (divRef.current && divRef.current.textContent !== localContent) {
      divRef.current.textContent = localContent;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Inject shake + chip styles once
  useEffect(() => {
    ensureShakeStyle();
    ensureChipStyle();
  }, []);

  // When entering edit mode, set textContent and focus the div
  useLayoutEffect(() => {
    if (isEditing && divRef.current) {
      divRef.current.textContent = localContent;
      divRef.current.focus();
    }
  }, [isEditing]);

  function triggerShake() {
    setIsShaking(true);
    setTimeout(() => setIsShaking(false), 400);
  }

  function handleInput() {
    const el = divRef.current;
    if (!el) return;
    const content = el.textContent ?? '';
    setLocalContent(content);

    // Detect !! trigger for date picker (only when not already a date chip)
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
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content });
      undoCheckpoint.mutate({ id: bullet.id, content });
    }, 1000);
  }

  function handleBlur() {
    // Flush any pending save timer immediately
    if (saveTimerRef.current) {
      clearTimeout(saveTimerRef.current);
      const content = divRef.current?.textContent ?? '';
      if (content !== bullet.content) {
        patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content });
      }
      setLocalContent(content);
    }
    setIsEditing(false);
  }

  function handleRenderedClick(e: React.MouseEvent<HTMLSpanElement>) {
    const target = e.target as HTMLElement;
    const chipType = target.dataset.chipType as 'tag' | 'mention' | 'date' | undefined;
    const chipValue = target.dataset.chipValue;
    if (chipType && chipValue) {
      e.stopPropagation();
      if (chipType === 'tag' || chipType === 'mention') {
        setSidebarTab('tags');
        setCanvasView({ type: 'filtered', chipType, chipValue });
      } else if (chipType === 'date') {
        // Open date picker to edit the date
        triggerDatePicker((newDate: string) => {
          const updated = localContent.replace(`!![${chipValue}]`, `!![${newDate}]`);
          setLocalContent(updated);
          patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: updated });
        });
      }
      return;
    }
    // Not a chip — switch to edit mode
    setIsEditing(true);
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
      e.preventDefault(); // block browser native undo on contenteditable
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

    // ── Ctrl/Cmd+ArrowUp: move bullet up ──────────────────────────────────
    if (isMeta && e.key === 'ArrowUp') {
      e.preventDefault();
      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);
      if (myIdx <= 0) return; // already first
      // Move bullet to before the previous sibling: afterId = the one before prevSibling
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
      if (myIdx >= siblings.length - 1) return; // already last
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
        // Empty bullet with indent — outdent
        outdentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
        return;
      }

      if (currentContent === '' && bullet.parentId === null) {
        // Empty root bullet — create blank sibling
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

      // Flush debounce immediately to save 'before' content
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: before });
      setIsEditing(false);

      if (children.length > 0) {
        // Has children: create as first child
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
        // Create sibling below
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
        if (bullet.parentId === null) return; // already root
        outdentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
      } else {
        // Check if this is first sibling — if so, no-op
        const siblings = getChildren(bulletMap, bullet.parentId);
        const myIdx = siblings.findIndex(s => s.id === bullet.id);
        if (myIdx === 0) return; // first sibling — no-op
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

      // Find previous bullet in render order
      const allBullets = Object.values(bulletMap).filter(b => !b.deletedAt);
      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);

      let prevBullet: typeof allBullets[0] | undefined;
      if (myIdx > 0) {
        // Previous sibling (or its last visible descendant)
        prevBullet = siblings[myIdx - 1];
        // Walk to deepest last child of prev sibling
        let candidate = prevBullet;
        while (true) {
          if (candidate.isCollapsed) break;
          const prevChildren = getChildren(bulletMap, candidate.id).filter(b => !b.deletedAt);
          if (prevChildren.length === 0) break;
          candidate = prevChildren[prevChildren.length - 1];
        }
        prevBullet = candidate;
      } else if (bullet.parentId !== null) {
        // First child — ignore backspace rather than merging into parent
        return;
      }

      if (!prevBullet) return;

      const prevContent = prevBullet.content;
      const joinOffset = prevContent.length;
      const mergedContent = prevContent + (el.textContent ?? '');

      patchBullet.mutate({ id: prevBullet.id, documentId: bullet.documentId, content: mergedContent });
      softDeleteBullet.mutate({ id: bullet.id, documentId: bullet.documentId });

      // Focus prev bullet and set cursor to join point
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
      // Find next sibling
      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);
      const nextSibling = siblings[myIdx + 1];
      if (!nextSibling) return;

      const mergedContent = (el.textContent ?? '') + nextSibling.content;
      const cursorOffset = (el.textContent ?? '').length;

      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: mergedContent });
      softDeleteBullet.mutate({ id: nextSibling.id, documentId: bullet.documentId });

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

    // Replace selected text with wrapped version in textContent
    const text = el.textContent ?? '';
    const start = range.startOffset;
    // Find actual offset in full textContent
    const beforeRange = document.createRange();
    beforeRange.setStart(el, 0);
    beforeRange.setEnd(range.startContainer, range.startOffset);
    const beforeText = beforeRange.toString();
    const startIdx = beforeText.length;
    const endIdx = startIdx + selected.length;

    const newContent = text.slice(0, startIdx) + marker + selected + marker + text.slice(endIdx);
    el.textContent = newContent;
    setLocalContent(newContent);

    // Place cursor after the closing marker
    const newCursorPos = startIdx + marker.length + selected.length + marker.length;
    setCursorAtPosition(el, newCursorPos);
    void start; // suppress unused warning

    // Trigger save
    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
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

  // Compute rendered HTML for view mode
  const renderedHtml = !isEditing
    ? renderWithChips(renderBulletMarkdown(localContent))
    : '';

  if (!isEditing) {
    return (
      <span
        dangerouslySetInnerHTML={{ __html: renderedHtml }}
        onClick={handleRenderedClick}
        style={{
          flex: 1,
          fontSize: '0.9375rem',
          color: '#333',
          lineHeight: 1.6,
          minHeight: '1.6em',
          wordBreak: 'break-word',
          cursor: 'text',
          display: 'block',
        }}
      />
    );
  }

  return (
    <div
      id={`bullet-${bullet.id}`}
      ref={divRef}
      contentEditable
      suppressContentEditableWarning
      className={isShaking ? 'bullet-shake' : undefined}
      onInput={handleInput}
      onKeyDown={handleKeyDown}
      onFocus={onFocus}
      onBlur={handleBlur}
      style={{
        flex: 1,
        outline: 'none',
        fontSize: '0.9375rem',
        color: '#333',
        lineHeight: 1.6,
        minHeight: '1.6em',
        wordBreak: 'break-word',
      }}
    />
  );
}
