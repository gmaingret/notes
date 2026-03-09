import { useEffect, useRef } from 'react';
import type { FlatBullet, BulletMap } from './BulletTree';
import { getChildren } from './BulletTree';
import {
  useIndentBullet,
  useOutdentBullet,
  useMoveBullet,
  useMarkComplete,
  useSoftDeleteBullet,
} from '../../hooks/useBullets';

type Props = {
  bullet: FlatBullet;
  bulletMap: BulletMap;
  position: { x: number; y: number };
  onClose: () => void;
};

export function ContextMenu({ bullet, bulletMap, position, onClose }: Props) {
  const menuRef = useRef<HTMLDivElement>(null);

  const indentBullet = useIndentBullet();
  const outdentBullet = useOutdentBullet();
  const moveBullet = useMoveBullet();
  const markComplete = useMarkComplete();
  const softDelete = useSoftDeleteBullet();

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

  const previousSibling = hasPreviousSibling ? siblings[siblingIndex - 1] : null;
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

  const buttonStyle: React.CSSProperties = {
    display: 'block',
    width: '100%',
    padding: '6px 12px',
    textAlign: 'left',
    border: 'none',
    background: 'transparent',
    cursor: 'pointer',
    fontSize: '0.875rem',
    color: '#333',
  };

  const disabledButtonStyle: React.CSSProperties = {
    ...buttonStyle,
    color: '#ccc',
    cursor: 'default',
  };

  const deleteButtonStyle: React.CSSProperties = {
    ...buttonStyle,
    color: '#e55',
  };

  const separatorStyle: React.CSSProperties = {
    height: 1,
    backgroundColor: '#e0e0e0',
    margin: '4px 0',
  };

  return (
    <div
      ref={menuRef}
      style={{
        position: 'fixed',
        top: position.y,
        left: position.x,
        zIndex: 1000,
        background: 'white',
        border: '1px solid #e0e0e0',
        borderRadius: 4,
        boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
        minWidth: 160,
        padding: '4px 0',
      }}
    >
      <button
        style={hasPreviousSibling ? buttonStyle : disabledButtonStyle}
        disabled={!hasPreviousSibling}
        onClick={handleIndent}
        onMouseEnter={e => { if (hasPreviousSibling) (e.target as HTMLElement).style.background = '#f5f5f5'; }}
        onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
      >
        Indent
      </button>
      <button
        style={!isRootLevel ? buttonStyle : disabledButtonStyle}
        disabled={isRootLevel}
        onClick={handleOutdent}
        onMouseEnter={e => { if (!isRootLevel) (e.target as HTMLElement).style.background = '#f5f5f5'; }}
        onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
      >
        Outdent
      </button>
      <button
        style={hasPreviousSibling ? buttonStyle : disabledButtonStyle}
        disabled={!hasPreviousSibling}
        onClick={handleMoveUp}
        onMouseEnter={e => { if (hasPreviousSibling) (e.target as HTMLElement).style.background = '#f5f5f5'; }}
        onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
      >
        Move Up
      </button>
      <button
        style={hasNextSibling ? buttonStyle : disabledButtonStyle}
        disabled={!hasNextSibling}
        onClick={handleMoveDown}
        onMouseEnter={e => { if (hasNextSibling) (e.target as HTMLElement).style.background = '#f5f5f5'; }}
        onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
      >
        Move Down
      </button>
      <div style={separatorStyle} />
      <button
        style={buttonStyle}
        onClick={handleToggleComplete}
        onMouseEnter={e => { (e.target as HTMLElement).style.background = '#f5f5f5'; }}
        onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
      >
        {bullet.isComplete ? 'Unmark complete' : 'Mark complete'}
      </button>
      <button
        style={deleteButtonStyle}
        onClick={handleDelete}
        onMouseEnter={e => { (e.target as HTMLElement).style.background = '#f5f5f5'; }}
        onMouseLeave={e => { (e.target as HTMLElement).style.background = 'transparent'; }}
      >
        Delete
      </button>
    </div>
  );
}
