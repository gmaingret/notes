import { describe, it, expect, vi } from 'vitest';

// pdfjs-dist is imported transitively via AttachmentRow → BulletNode → FocusToolbar import chain.
// Mock it at module level to prevent DOMMatrix crash in jsdom.
vi.mock('pdfjs-dist', () => ({
  getDocument: vi.fn(),
  GlobalWorkerOptions: { workerSrc: '' },
  version: '4.0.0',
}));

import { computeKeyboardOffset } from '../components/DocumentView/FocusToolbar';

// MOB-05: keyboard offset calculation tests
describe('computeKeyboardOffset', () => {
  it('returns offset when keyboard open', () => {
    // windowInnerHeight=844, vvOffsetTop=0, vvHeight=500 → 844 - (0 + 500) = 344
    expect(computeKeyboardOffset(844, 0, 500)).toBe(344);
  });

  it('returns 0 when keyboard closed', () => {
    // vvHeight === windowInnerHeight → keyboard not open → 0
    expect(computeKeyboardOffset(844, 0, 844)).toBe(0);
  });

  it('returns 0 when vvHeight exceeds windowInnerHeight (clamps negative to 0)', () => {
    // Math.max(0, ...) clamps negative values to 0
    expect(computeKeyboardOffset(844, 0, 900)).toBe(0);
  });
});
