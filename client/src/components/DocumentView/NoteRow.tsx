import { useRef, useLayoutEffect } from 'react';
import { usePatchNote } from '../../hooks/useBullets';

type Props = {
  bulletId: string;
  initialNote: string | null;
  focusOnMount?: boolean;
};

export function NoteRow({ bulletId, initialNote, focusOnMount = false }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const patchNote = usePatchNote();
  // Keep a stable ref to the current initialNote for Escape revert
  const initialNoteRef = useRef(initialNote);
  initialNoteRef.current = initialNote;

  useLayoutEffect(() => {
    if (!ref.current) return;
    ref.current.textContent = initialNote ?? '';
    if (focusOnMount) {
      ref.current.focus();
    }
  }, [initialNote, focusOnMount]);

  function handleBlur() {
    if (!ref.current) return;
    const current = ref.current.textContent ?? '';
    const original = initialNoteRef.current ?? '';
    if (current === original) return;
    patchNote.mutate({ id: bulletId, note: current || null });
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLDivElement>) {
    if (e.key === 'Escape') {
      e.stopPropagation();
      if (ref.current) {
        ref.current.textContent = initialNoteRef.current ?? '';
      }
      ref.current?.blur();
    }
  }

  return (
    <div
      ref={ref}
      role="textbox"
      contentEditable
      suppressContentEditableWarning
      style={{
        fontSize: '0.85em',
        color: '#888',
        marginLeft: 24,
        minHeight: '1em',
        outline: 'none',
        cursor: 'text',
      }}
      onBlur={handleBlur}
      onKeyDown={handleKeyDown}
    />
  );
}
