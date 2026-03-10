import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../../api/client';
import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import {
  useIndentBullet,
  useOutdentBullet,
  useMoveBullet,
  useMarkComplete,
  useSoftDeleteBullet,
} from '../../hooks/useBullets';
import { useBookmarks, useAddBookmark, useRemoveBookmark } from '../../hooks/useBookmarks';

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  position: { x: number; y: number };
  onClose: () => void;
};

export function ContextMenu({ bullet, bulletMap, position, onClose }: Props) {
  const menuRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  const indentBullet = useIndentBullet();
  const outdentBullet = useOutdentBullet();
  const moveBullet = useMoveBullet();
  const markComplete = useMarkComplete();
  const softDelete = useSoftDeleteBullet();

  const { data: bookmarks = [] } = useBookmarks();
  const addBookmark = useAddBookmark();
  const removeBookmark = useRemoveBookmark();
  const isBookmarked = bookmarks.some(b => b.id === bullet.id);

  // Close on outside click
  useEffect(() => {
    function handleMouseDown(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    }
    document.addEventListener('mousedown', handleMouseDown);
    return () => document.removeEventListener('mousedown', handleMouseDown);
  }, [onClose]);

  // Close on Escape
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  // Compute siblings to determine indent/outdent/move enabled state
  const siblings = getChildren(bulletMap, bullet.parentId);
  const siblingIndex = siblings.findIndex(s => s.id === bullet.id);
  const hasPreviousSibling = siblingIndex > 0;
  const hasNextSibling = siblingIndex < siblings.length - 1;
  const isRootLevel = bullet.parentId === null;

  const nextSibling = hasNextSibling ? siblings[siblingIndex + 1] : null;

  function handleIndent() {
    indentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
    onClose();
  }

  function handleOutdent() {
    outdentBullet.mutate({ id: bullet.id, documentId: bullet.documentId });
    onClose();
  }

  function handleMoveUp() {
    // Move before previous sibling: afterId = the sibling before the previous sibling
    const beforePrevious = siblingIndex > 1 ? siblings[siblingIndex - 2] : null;
    moveBullet.mutate({
      id: bullet.id,
      documentId: bullet.documentId,
      newParentId: bullet.parentId,
      afterId: beforePrevious ? beforePrevious.id : null,
    });
    onClose();
  }

  function handleMoveDown() {
    // Move after next sibling: afterId = the next sibling
    if (nextSibling) {
      moveBullet.mutate({
        id: bullet.id,
        documentId: bullet.documentId,
        newParentId: bullet.parentId,
        afterId: nextSibling.id,
      });
    }
    onClose();
  }

  function handleToggleComplete() {
    markComplete.mutate({
      id: bullet.id,
      documentId: bullet.documentId,
      isComplete: !bullet.isComplete,
    });
    onClose();
  }

  function handleDelete() {
    softDelete.mutate({ id: bullet.id, documentId: bullet.documentId });
    onClose();
  }

  function handleToggleBookmark() {
    if (isBookmarked) {
      removeBookmark.mutate(bullet.id);
    } else {
      addBookmark.mutate(bullet.id);
    }
    onClose();
  }

  function handleAttachFile() {
    document.dispatchEvent(new CustomEvent('attach-file', { detail: { bulletId: bullet.id } }));
    onClose();
  }

  function handleAddNote() {
    document.dispatchEvent(new CustomEvent('focus-note', { detail: { bulletId: bullet.id } }));
    onClose();
  }

  async function handleUndo() {
    await apiClient.post('/api/undo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
    onClose();
  }

  async function handleRedo() {
    await apiClient.post('/api/redo', {});
    await queryClient.invalidateQueries({ queryKey: ['bullets'] });
    onClose();
  }

  return (
    <div
      ref={menuRef}
      style={{
        position: 'fixed',
        top: position.y,
        left: position.x,
        zIndex: 1000,
        background: 'var(--color-bg-raised)',
        border: '1px solid var(--color-border-default)',
        borderRadius: 4,
        boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
        minWidth: 160,
        padding: '4px 0',
      }}
    >
      <button
        className={`context-menu-item${!hasPreviousSibling ? ' context-menu-item--disabled' : ''}`}
        disabled={!hasPreviousSibling}
        onClick={handleIndent}
      >
        Indent
      </button>
      <button
        className={`context-menu-item${isRootLevel ? ' context-menu-item--disabled' : ''}`}
        disabled={isRootLevel}
        onClick={handleOutdent}
      >
        Outdent
      </button>
      <button
        className={`context-menu-item${!hasPreviousSibling ? ' context-menu-item--disabled' : ''}`}
        disabled={!hasPreviousSibling}
        onClick={handleMoveUp}
      >
        Move Up
      </button>
      <button
        className={`context-menu-item${!hasNextSibling ? ' context-menu-item--disabled' : ''}`}
        disabled={!hasNextSibling}
        onClick={handleMoveDown}
      >
        Move Down
      </button>
      <div className="context-menu-divider" />
      <button
        className="context-menu-item"
        onClick={() => void handleUndo()}
      >
        Undo
      </button>
      <button
        className="context-menu-item"
        onClick={() => void handleRedo()}
      >
        Redo
      </button>
      <div className="context-menu-divider" />
      <button
        className="context-menu-item"
        onClick={handleToggleComplete}
      >
        {bullet.isComplete ? 'Unmark complete' : 'Mark complete'}
      </button>
      <button
        className="context-menu-item"
        onClick={handleToggleBookmark}
      >
        {isBookmarked ? 'Remove bookmark' : 'Bookmark'}
      </button>
      <button
        className="context-menu-item"
        onClick={handleAttachFile}
      >
        Attach file
      </button>
      <button
        className="context-menu-item"
        onClick={handleAddNote}
      >
        Add note
      </button>
      <button
        className="context-menu-item context-menu-item--destructive"
        onClick={handleDelete}
      >
        Delete
      </button>
    </div>
  );
}
