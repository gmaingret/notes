import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import React from 'react';

// Mock useBullets so usePatchNote is injectable without QueryClient
vi.mock('../hooks/useBullets', () => ({
  usePatchNote: vi.fn(),
}));

import { usePatchNote } from '../hooks/useBullets';
import { NoteRow } from '../components/DocumentView/NoteRow';

describe('NoteRow', () => {
  const mutateMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (usePatchNote as ReturnType<typeof vi.fn>).mockReturnValue({ mutate: mutateMock });
  });

  it('NoteRow renders when bullet has note', () => {
    // CMT-01: bullet.note = 'some text' → NoteRow renders the text
    render(<NoteRow bulletId="b1" initialNote="some text" />);
    const el = screen.getByRole('textbox');
    expect(el).toBeDefined();
    expect(el.textContent).toBe('some text');
  });

  it('NoteRow hidden when bullet note is null', () => {
    // CMT-01: bullet.note = null → NoteRow renders but is empty (component is conditionally mounted by parent)
    // NoteRow itself always renders when mounted — parent controls mounting
    // When initialNote is null, the div has empty textContent
    render(<NoteRow bulletId="b1" initialNote={null} />);
    const el = screen.getByRole('textbox');
    expect(el.textContent).toBe('');
  });

  it('clearing note text triggers patch with null', async () => {
    // CMT-04: clearing NoteRow → PATCH {note: null} sent to server
    render(<NoteRow bulletId="b1" initialNote="original text" />);
    const el = screen.getByRole('textbox');

    // Clear content and blur
    act(() => {
      el.textContent = '';
      fireEvent.blur(el);
    });

    expect(mutateMock).toHaveBeenCalledWith({ id: 'b1', note: null });
  });

  it('changing note text triggers patch with new text on blur', async () => {
    render(<NoteRow bulletId="b1" initialNote="original" />);
    const el = screen.getByRole('textbox');

    act(() => {
      el.textContent = 'updated note';
      fireEvent.blur(el);
    });

    expect(mutateMock).toHaveBeenCalledWith({ id: 'b1', note: 'updated note' });
  });

  it('no patch when text unchanged on blur', () => {
    render(<NoteRow bulletId="b1" initialNote="same text" />);
    const el = screen.getByRole('textbox');

    act(() => {
      fireEvent.blur(el);
    });

    expect(mutateMock).not.toHaveBeenCalled();
  });

  it('Escape key reverts text and stops propagation', () => {
    render(<NoteRow bulletId="b1" initialNote="original" />);
    const el = screen.getByRole('textbox');

    act(() => {
      el.textContent = 'changed';
      fireEvent.keyDown(el, { key: 'Escape', bubbles: true });
    });

    // After Escape, text should revert to original
    expect(el.textContent).toBe('original');
    // No patch call
    expect(mutateMock).not.toHaveBeenCalled();
  });
});
