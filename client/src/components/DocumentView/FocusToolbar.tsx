/* eslint-disable react-refresh/only-export-components */
import { useState, useEffect, useRef } from 'react';
import {
  IndentIncrease, IndentDecrease,
  ArrowUp, ArrowDown,
  Undo2, Redo2,
  Paperclip, StickyNote,
  Bookmark, Check, Trash2,
} from 'lucide-react';
import { useQueryClient } from '@tanstack/react-query';

// MOB-05: pure function to calculate keyboard bottom offset from visualViewport values
// Exported for unit testing
export function computeKeyboardOffset(
  windowInnerHeight: number,
  vvOffsetTop: number,
  vvHeight: number,
): number {
  return Math.max(0, windowInnerHeight - vvOffsetTop - vvHeight);
}
import { apiClient } from '../../api/client';
import {
  useIndentBullet,
  useOutdentBullet,
  useMoveBullet,
  useMarkComplete,
  useSoftDeleteBullet,
  useDocumentBullets,
} from '../../hooks/useBullets';
import { useBookmarks, useAddBookmark, useRemoveBookmark } from '../../hooks/useBookmarks';
import { getChildren, buildBulletMap } from './BulletTree';

type Props = {
  bulletId: string;
  documentId: string;
};

const btnStyle: React.CSSProperties = {
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  fontSize: '1rem',
  padding: '4px 6px',
  borderRadius: 4,
  lineHeight: 1,
  minWidth: 44,
  minHeight: 44,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

function scrollBulletAboveToolbar(bulletId: string) {
  const el = document.getElementById(`bullet-${bulletId}`);
  if (!el) return;
  const toolbarEl = document.querySelector<HTMLElement>('[data-focus-toolbar="true"]');
  const toolbarHeight = toolbarEl ? toolbarEl.getBoundingClientRect().height : 64;
  const rect = el.getBoundingClientRect();
  const vvHeight = window.visualViewport?.height ?? window.innerHeight;
  if (rect.bottom > vvHeight - toolbarHeight) {
    const excess = rect.bottom - (vvHeight - toolbarHeight) + 8;
    // Query <main> directly — avoids the scrollHeight > clientHeight check which
    // fails if the container was not scrollable before the keyboard opened.
    const main = document.querySelector<HTMLElement>('main');
    if (main) {
      main.scrollTop += excess;
    }
  }
}

export function FocusToolbar({ bulletId, documentId }: Props) {
  const [keyboardOffset, setKeyboardOffset] = useState(0);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const queryClient = useQueryClient();

  // Position toolbar above soft keyboard + scroll the focused bullet above toolbar.
  // We debounce the scroll correction: the browser re-scrolls on every keyboard animation
  // frame, so we wait 100 ms after the last resize event before correcting (by then the
  // keyboard has settled and the browser won't override us).
  useEffect(() => {
    const vv = window.visualViewport;
    if (!vv) return;

    function update() {
      setKeyboardOffset(computeKeyboardOffset(window.innerHeight, vv!.offsetTop, vv!.height));
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => {
        debounceRef.current = null;
        scrollBulletAboveToolbar(bulletId);
      }, 100);
    }

    vv.addEventListener('resize', update);
    vv.addEventListener('scroll', update);
    update();
    return () => {
      vv.removeEventListener('resize', update);
      vv.removeEventListener('scroll', update);
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [bulletId]);

  // On initial focus (no keyboard involved), do a double-rAF so the browser finishes
  // its own scroll-to-caret before we correct for the toolbar.
  useEffect(() => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => scrollBulletAboveToolbar(bulletId));
    });
  }, [bulletId]);

  // Bullet data
  const { data: flatBullets = [] } = useDocumentBullets(documentId);
  const bulletMap = buildBulletMap(flatBullets);
  const bullet = bulletMap[bulletId];

  // Hooks
  const indentBullet = useIndentBullet();
  const outdentBullet = useOutdentBullet();
  const moveBullet = useMoveBullet();
  const markComplete = useMarkComplete();
  const softDelete = useSoftDeleteBullet();

  const { data: bookmarks = [] } = useBookmarks();
  const addBookmark = useAddBookmark();
  const removeBookmark = useRemoveBookmark();

  if (!bullet) return null;

  const siblings = getChildren(bulletMap, bullet.parentId);
  const siblingIndex = siblings.findIndex(s => s.id === bulletId);
  const hasPreviousSibling = siblingIndex > 0;
  const hasNextSibling = siblingIndex < siblings.length - 1;
  const isBookmarked = bookmarks.some(b => b.id === bulletId);
  const hasNote = bullet.note !== null && bullet.note !== undefined;

  function handleIndent() {
    indentBullet.mutate({ id: bulletId, documentId });
  }

  function handleOutdent() {
    if (bullet.parentId === null) return;
    outdentBullet.mutate({ id: bulletId, documentId });
  }

  function handleMoveUp() {
    if (!hasPreviousSibling) return;
    const afterId = siblingIndex >= 2 ? siblings[siblingIndex - 2].id : null;
    moveBullet.mutate({ id: bulletId, documentId, newParentId: bullet.parentId, afterId });
  }

  function handleMoveDown() {
    if (!hasNextSibling) return;
    const nextNextSibling = siblings[siblingIndex + 2] ?? null;
    moveBullet.mutate({
      id: bulletId,
      documentId,
      newParentId: bullet.parentId,
      afterId: nextNextSibling ? nextNextSibling.id : siblings[siblingIndex + 1].id,
    });
  }

  async function handleUndo() {
    await apiClient.post('/api/undo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }

  async function handleRedo() {
    await apiClient.post('/api/redo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
  }

  function handleAttach() {
    document.dispatchEvent(new CustomEvent('attach-file', { detail: { bulletId } }));
  }

  function handleNote() {
    document.dispatchEvent(new CustomEvent('focus-note', { detail: { bulletId } }));
  }

  function handleBookmark() {
    if (isBookmarked) {
      removeBookmark.mutate(bulletId);
    } else {
      addBookmark.mutate(bulletId);
    }
  }

  function handleComplete() {
    markComplete.mutate({ id: bulletId, documentId, isComplete: !bullet.isComplete });
  }

  function handleDelete() {
    softDelete.mutate({ id: bulletId, documentId });
  }

  return (
    <div
      data-focus-toolbar="true"
      onMouseDown={(e) => e.preventDefault()}
      style={{
        position: 'fixed',
        bottom: keyboardOffset,
        left: 0,
        right: 0,
        background: 'var(--color-bg-base)',
        borderTop: '1px solid var(--color-border-default)',
        display: 'flex',
        gap: 4,
        padding: '6px 8px',
        zIndex: 1000,
        overflowX: 'auto',
      }}
    >
      <button
        className="focus-toolbar-btn"
        style={{ ...btnStyle, opacity: hasPreviousSibling ? 1 : 0.3 }}
        onClick={handleIndent}
        title="Indent"
      >
        <IndentIncrease size={20} strokeWidth={1.5} />
      </button>
      <button
        className="focus-toolbar-btn"
        style={{ ...btnStyle, opacity: bullet.parentId !== null ? 1 : 0.3 }}
        onClick={handleOutdent}
        title="Outdent"
      >
        <IndentDecrease size={20} strokeWidth={1.5} />
      </button>
      <button
        className="focus-toolbar-btn"
        style={{ ...btnStyle, opacity: hasPreviousSibling ? 1 : 0.3 }}
        onClick={handleMoveUp}
        title="Move Up"
      >
        <ArrowUp size={20} strokeWidth={1.5} />
      </button>
      <button
        className="focus-toolbar-btn"
        style={{ ...btnStyle, opacity: hasNextSibling ? 1 : 0.3 }}
        onClick={handleMoveDown}
        title="Move Down"
      >
        <ArrowDown size={20} strokeWidth={1.5} />
      </button>
      <button className="focus-toolbar-btn" style={btnStyle} onClick={() => void handleUndo()} title="Undo">
        <Undo2 size={20} strokeWidth={1.5} />
      </button>
      <button className="focus-toolbar-btn" style={btnStyle} onClick={() => void handleRedo()} title="Redo">
        <Redo2 size={20} strokeWidth={1.5} />
      </button>
      <button className="focus-toolbar-btn" style={btnStyle} onClick={handleAttach} title="Attach file">
        <Paperclip size={20} strokeWidth={1.5} />
      </button>
      <button
        className={`focus-toolbar-btn${hasNote ? ' focus-toolbar-btn--note-active' : ''}`}
        style={btnStyle}
        onClick={handleNote}
        title="Note"
      >
        <StickyNote size={20} strokeWidth={1.5} />
      </button>
      <button
        className={`focus-toolbar-btn${isBookmarked ? ' focus-toolbar-btn--bookmark-active' : ''}`}
        style={btnStyle}
        onClick={handleBookmark}
        title="Bookmark"
      >
        <Bookmark size={20} strokeWidth={1.5} />
      </button>
      <button
        className={`focus-toolbar-btn${bullet.isComplete ? ' focus-toolbar-btn--complete-active' : ''}`}
        style={btnStyle}
        onClick={handleComplete}
        title="Mark complete"
      >
        <Check size={20} strokeWidth={1.5} />
      </button>
      <button className="focus-toolbar-btn focus-toolbar-btn--delete" style={btnStyle} onClick={handleDelete} title="Delete">
        <Trash2 size={20} strokeWidth={1.5} />
      </button>
    </div>
  );
}
