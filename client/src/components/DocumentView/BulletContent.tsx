import { useRef, useEffect, useState } from 'react';
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
  const text = el.textContent ?? '';
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

// ─── BulletContent component ───────────────────────────────────────────────────

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  onFocus?: () => void;
};

export function BulletContent({ bullet, bulletMap, onFocus }: Props) {
  const divRef = useRef<HTMLDivElement>(null);
  const saveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [localContent, setLocalContent] = useState(bullet.content);
  const [isShaking, setIsShaking] = useState(false);

  const createBullet = useCreateBullet();
  const patchBullet = usePatchBullet();
  const softDeleteBullet = useSoftDeleteBullet();
  const indentBullet = useIndentBullet();
  const outdentBullet = useOutdentBullet();
  const moveBullet = useMoveBullet();
  const undoCheckpoint = useBulletUndoCheckpoint();

  // Sync from props only when not focused
  useEffect(() => {
    const el = divRef.current;
    if (!el) return;
    if (document.activeElement !== el) {
      setLocalContent(bullet.content);
      el.textContent = bullet.content;
    }
  }, [bullet.content]);

  // Set initial textContent
  useEffect(() => {
    if (divRef.current && divRef.current.textContent !== localContent) {
      divRef.current.textContent = localContent;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Inject shake styles once
  useEffect(() => {
    ensureShakeStyle();
  }, []);

  function triggerShake() {
    setIsShaking(true);
    setTimeout(() => setIsShaking(false), 400);
  }

  function handleInput() {
    const el = divRef.current;
    if (!el) return;
    const content = el.textContent ?? '';
    setLocalContent(content);

    if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
    saveTimerRef.current = setTimeout(() => {
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content });
      undoCheckpoint.mutate({ id: bullet.id, content });
    }, 1000);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    const el = e.currentTarget;
    const isMeta = e.ctrlKey || e.metaKey;

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

    // ── Ctrl/Cmd+ArrowUp: move bullet up ──────────────────────────────────
    if (isMeta && e.key === 'ArrowUp') {
      e.preventDefault();
      const siblings = getChildren(bulletMap, bullet.parentId);
      const myIdx = siblings.findIndex(s => s.id === bullet.id);
      if (myIdx <= 0) return; // already first
      const prevSibling = siblings[myIdx - 1];
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
        createBullet.mutate({
          documentId: bullet.documentId,
          parentId: null,
          afterId: bullet.id,
          content: '',
        });
        return;
      }

      const { before, after } = splitAtCursor(el);
      const children = getChildren(bulletMap, bullet.id).filter(b => !b.deletedAt);

      // Flush debounce immediately to save 'before' content
      if (saveTimerRef.current) clearTimeout(saveTimerRef.current);
      patchBullet.mutate({ id: bullet.id, documentId: bullet.documentId, content: before });

      if (children.length > 0) {
        // Has children: create as first child
        createBullet.mutate({
          documentId: bullet.documentId,
          parentId: bullet.id,
          afterId: null,
          content: after,
        });
      } else {
        // Create sibling below
        createBullet.mutate({
          documentId: bullet.documentId,
          parentId: bullet.parentId,
          afterId: bullet.id,
          content: after,
        });
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
        prevBullet = bulletMap[bullet.parentId];
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
