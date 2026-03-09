import { describe, it, expect } from 'vitest';
import { swipeThresholdReached } from '../components/DocumentView/gestures';

// MOB-01: swipeThresholdReached returns 'complete' when swipeX > rowWidth * 0.4
// MOB-02: swipeThresholdReached returns 'delete' when swipeX < -(rowWidth * 0.4)

describe('swipeThresholdReached', () => {
  it('returns complete when swipeX exceeds 40% of row width (right swipe)', () => {
    // MOB-01: 130 > 300 * 0.4 = 120 → 'complete'
    expect(swipeThresholdReached(130, 300)).toBe('complete');
  });

  it('returns delete when swipeX exceeds 40% of row width (left swipe)', () => {
    // MOB-02: -130 < -(300 * 0.4) = -120 → 'delete'
    expect(swipeThresholdReached(-130, 300)).toBe('delete');
  });

  it('returns null when swipeX is below threshold', () => {
    // 50 < 120 → null
    expect(swipeThresholdReached(50, 300)).toBeNull();
  });
});
