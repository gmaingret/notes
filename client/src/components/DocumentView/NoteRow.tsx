import { useRef, useLayoutEffect, useEffect } from 'react';
import { usePatchNote } from '../../hooks/useBullets';

type Props = {
  bulletId: string;
  initialNote: string | null;
  focusTrigger?: number;
};

export function NoteRow({ bulletId, initialNote, focusTrigger = 0 }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const patchNote = usePatchNote();
  // Keep a stable ref to the current initialNote for Escape revert
  const initialNoteRef = useRef(initialNote);
  initialNoteRef.current = initialNote;

  useLayoutEffect(() => {
    if (!ref.current) return;
    ref.current.textContent = initialNote ?? '';
  }, [initialNote]);

  // Focus whenever focusTrigger increments (fires for both new and existing notes)
  useEffect(() => {
    if (focusTrigger > 0) {
      ref.current?.focus();
    }
  }, [focusTrigger]);

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
    if (e.key === 'Enter' && e.shiftKey) {
      e.preventDefault();
      e.stopPropagation();
      ref.current?.blur();
      document.getElementById(`bullet-${bulletId}`)?.focus();
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
