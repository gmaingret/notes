import { describe, it, expect } from 'vitest';

// Phase 4 RED stub — keyboard offset calculation test
// Will be made GREEN in plan 04-05
//
// MOB-05: computeKeyboardOffset returns bottom offset when iOS keyboard is open
// Returns 0 when keyboard is not open (vvHeight approaches windowInnerHeight)

// Local placeholder — replace with real import in 04-05:
// import { computeKeyboardOffset } from '../utils/keyboardOffset';
const computeKeyboardOffset = (
  _windowInnerHeight: number,
  _vvOffsetTop: number,
  _vvHeight: number,
): number => {
  throw new Error('not implemented — implement in 04-05');
};

describe('computeKeyboardOffset', () => {
  it('returns offset when keyboard open', () => {
    // windowInnerHeight=844, vvOffsetTop=0, vvHeight=500 → 844 - (0 + 500) = 344
    expect(computeKeyboardOffset(844, 0, 500)).toBe(344);
  });

  it('returns 0 when keyboard closed', () => {
    // vvHeight === windowInnerHeight → keyboard not open → 0
    expect(computeKeyboardOffset(844, 0, 844)).toBe(0);
  });
});
