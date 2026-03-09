import { useState, useRef, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useRenameDocument, useDeleteDocument, useExportDocument } from '../../hooks/useDocuments';
import type { Document } from '../../hooks/useDocuments';
import { useUiStore } from '../../store/uiStore';

type Props = { document: Document; isActive: boolean };

export function DocumentRow({ document, isActive }: Props) {
  const [showMenu, setShowMenu] = useState(false);
  const [isRenaming, setIsRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState(document.title);
  const navigate = useNavigate();
  const { setCanvasView } = useUiStore();
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
        background: isActive ? 'rgba(0,0,0,0.06)' : 'transparent',
        borderRadius: 4,
        margin: '0 4px',
        position: 'relative',
      }}
      onClick={() => { if (!isRenaming) { setCanvasView({ type: 'document' }); navigate(`/doc/${document.id}`); } }}
      className="document-row"
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
            border: '1px solid #4a90e2',
            borderRadius: 3,
            padding: '0.125rem 0.25rem',
            fontSize: '0.875rem',
            outline: 'none',
          }}
          autoFocus
          onClick={e => e.stopPropagation()}
        />
      ) : (
        <span style={{
          flex: 1,
          fontSize: '0.875rem',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          color: '#333',
        }}>
          {document.title}
        </span>
      )}

      {/* 3-dot menu button — appears on hover/focus via CSS (locked UX decision) */}
      <div className="row-menu-trigger" onClick={e => { e.stopPropagation(); setShowMenu(v => !v); }}>
        <button style={{
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          padding: '0.125rem 0.25rem',
          borderRadius: 3,
          color: '#666',
          fontSize: '0.875rem',
          lineHeight: 1,
        }} title="Document options">
          ⋯
        </button>

        {showMenu && (
          <div style={{
            position: 'absolute',
            right: 4,
            top: '100%',
            background: '#fff',
            border: '1px solid #e0e0e0',
            borderRadius: 4,
            boxShadow: '0 2px 8px rgba(0,0,0,0.12)',
            zIndex: 200,
            minWidth: 150,
          }}>
            <button style={menuItemStyle} onClick={e => { e.stopPropagation(); setIsRenaming(true); setShowMenu(false); }}>
              Rename
            </button>
            <button style={menuItemStyle} onClick={e => { e.stopPropagation(); exportDoc({ id: document.id, title: document.title }); setShowMenu(false); }}>
              Export as Markdown
            </button>
            <button style={{ ...menuItemStyle, color: '#e53e3e' }} onClick={e => { e.stopPropagation(); handleDelete(); }}>
              Delete
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

const menuItemStyle = {
  display: 'block',
  width: '100%',
  padding: '0.5rem 0.75rem',
  background: 'none',
  border: 'none',
  cursor: 'pointer',
  textAlign: 'left' as const,
  fontSize: '0.875rem',
  color: '#333',
} as const;
