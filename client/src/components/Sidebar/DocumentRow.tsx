import { useState, useRef, type KeyboardEvent } from 'react';
import { MoreHorizontal } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useRenameDocument, useDeleteDocument, useExportDocument } from '../../hooks/useDocuments';
import type { Document } from '../../hooks/useDocuments';
import { useUiStore } from '../../store/uiStore';
import { useIsMobile } from '../../hooks/useIsMobile';

type Props = { document: Document; isActive: boolean };

export function DocumentRow({ document, isActive }: Props) {
  const [showMenu, setShowMenu] = useState(false);
  const [isRenaming, setIsRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState(document.title);
  const navigate = useNavigate();
  const { setCanvasView, setSidebarOpen } = useUiStore();
  const isMobile = useIsMobile();
  const { mutate: rename } = useRenameDocument();
  const { mutate: deleteDoc } = useDeleteDocument();
  const { mutate: exportDoc } = useExportDocument();
  const renameInputRef = useRef<HTMLInputElement>(null);

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: document.id,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  const handleRenameSubmit = () => {
    const trimmed = renameValue.trim();
    if (trimmed && trimmed !== document.title) {
      rename({ id: document.id, title: trimmed });
    }
    setIsRenaming(false);
  };

  const handleRenameKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter') handleRenameSubmit();
    if (e.key === 'Escape') { setRenameValue(document.title); setIsRenaming(false); }
  };

  const handleDelete = () => {
    if (window.confirm(`Delete "${document.title}"? This cannot be undone.`)) {
      deleteDoc(document.id);
    }
    setShowMenu(false);
  };

  return (
    <div
      ref={setNodeRef}
      style={{
        ...style,
        display: 'flex',
        alignItems: 'center',
        padding: '0.375rem 0.5rem 0.375rem 1rem',
        cursor: 'pointer',
        background: 'transparent',
        borderRadius: 4,
        margin: '0 4px',
        position: 'relative',
      }}
      onClick={() => {
        if (!isRenaming) {
          setCanvasView({ type: 'document' });
          navigate(`/doc/${document.id}`);
          // Bug 1.1: close sidebar on mobile so the document is visible
          if (isMobile) setSidebarOpen(false);
        }
      }}
      className={`document-row${isActive ? ' document-row--active' : ''}`}
      {...attributes}
      {...listeners}
    >
      {isRenaming ? (
        <input
          ref={renameInputRef}
          value={renameValue}
          onChange={e => setRenameValue(e.target.value)}
          onBlur={handleRenameSubmit}
          onKeyDown={handleRenameKeyDown}
          style={{
            flex: 1,
            border: '1px solid var(--color-focus-ring)',
            borderRadius: 3,
            padding: '0.125rem 0.25rem',
            fontSize: '1rem',
            outline: 'none',
          }}
          autoFocus
          onClick={e => e.stopPropagation()}
        />
      ) : (
        <span style={{
          flex: 1,
          fontSize: '1rem',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }} className="doc-row-text">
          {document.title}
        </span>
      )}

      {/* 3-dot menu button — appears on hover/focus via CSS (locked UX decision) */}
      <div className="row-menu-trigger" onClick={e => { e.stopPropagation(); setShowMenu(v => !v); }}>
        <button className="doc-row-btn" style={{
          padding: '0.125rem 0.25rem',
          borderRadius: 3,
          fontSize: '1rem',
          lineHeight: 1,
        }} title="Document options">
          <MoreHorizontal size={20} strokeWidth={1.5} />
        </button>

        {showMenu && (
          <>
            {/* Bug 1.3: transparent full-screen overlay behind the menu — any tap outside
                hits this overlay and closes the menu. Reliable on mobile and desktop. */}
            <div
              style={{ position: 'fixed', inset: 0, zIndex: 199 }}
              onClick={e => { e.stopPropagation(); setShowMenu(false); }}
            />
            {/* Bug 1.2: right:0 aligns the menu's right edge with the row's right edge
                (the row is the position:relative ancestor). The 150px menu extends leftward
                from there and stays fully inside the 240px sidebar. */}
            <div style={{
              position: 'absolute',
              right: 0,
              top: '100%',
              background: 'var(--color-bg-raised)',
              border: '1px solid var(--color-border-default)',
              borderRadius: 4,
              boxShadow: '0 2px 8px var(--color-shadow)',
              zIndex: 200,
              minWidth: 150,
            }}>
              <button style={menuItemBaseStyle} className="doc-row-text doc-row-dropdown-item" onClick={e => { e.stopPropagation(); setIsRenaming(true); setShowMenu(false); }}>
                Rename
              </button>
              <button style={menuItemBaseStyle} className="doc-row-text doc-row-dropdown-item" onClick={e => { e.stopPropagation(); exportDoc({ id: document.id, title: document.title }); setShowMenu(false); }}>
                Export as Markdown
              </button>
              <button style={menuItemBaseStyle} className="doc-row-delete doc-row-dropdown-item" onClick={e => { e.stopPropagation(); handleDelete(); }}>
                Delete
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

const menuItemBaseStyle = {
  display: 'block',
  width: '100%',
  padding: '0.5rem 0.75rem',
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  textAlign: 'left' as const,
  fontSize: '1rem',
} as const;
