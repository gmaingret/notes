import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createLongPressHandler } from '../components/DocumentView/gestures';

// BULL-16 / MOB-03: createLongPressHandler returns {handleTouchStart, handleTouchMove, handleTouchEnd}
// Long press fires after 500ms with no touchmove > 8px
// touchmove > 8px cancels timer (onLongPress not called)

describe('createLongPressHandler', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('fires onLongPress after 500ms', () => {
    const onLongPress = vi.fn();
    const handler = createLongPressHandler({ onLongPress, delay: 500 });

    handler.handleTouchStart({ touches: [{ clientX: 100, clientY: 100 }] });
    vi.advanceTimersByTime(500);

    expect(onLongPress).toHaveBeenCalled();
  });

  it('cancels on touchmove > 8px', () => {
    const onLongPress = vi.fn();
    const handler = createLongPressHandler({ onLongPress, delay: 500 });

    handler.handleTouchStart({ touches: [{ clientX: 100, clientY: 100 }] });
    vi.advanceTimersByTime(300);

    // Move > 8px from start
    handler.handleTouchMove({ touches: [{ clientX: 110, clientY: 100 }] });
    vi.advanceTimersByTime(200);

    expect(onLongPress).not.toHaveBeenCalled();
  });
});
