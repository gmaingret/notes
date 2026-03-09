/**
 * Pure gesture utility functions for mobile swipe and long-press interactions.
 * These functions are framework-free (no React hooks) so they can be unit-tested directly.
 */

/**
 * Determines whether a horizontal swipe has crossed the action threshold.
 * @param swipeX  Horizontal displacement in pixels (positive = right, negative = left)
 * @param rowWidth  Width of the bullet row in pixels
 * @returns 'complete' when swiped right past 40%, 'delete' when swiped left past 40%, null otherwise
 */
export function swipeThresholdReached(
  swipeX: number,
  rowWidth: number,
): 'complete' | 'delete' | null {
  const threshold = rowWidth * 0.4;
  if (swipeX > threshold) return 'complete';
  if (swipeX < -threshold) return 'delete';
  return null;
}

/**
 * Factory that returns touch event handlers implementing a long-press interaction.
 * The returned handlers are plain functions (no React hooks) — they share mutable
 * state via closure variables so they can be used both in React and in plain tests.
 */
export function createLongPressHandler({
  onLongPress,
  delay = 500,
  cancelDistance = 8,
}: {
  onLongPress: (x: number, y: number) => void;
  delay?: number;
  cancelDistance?: number;
}) {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let startPos: { x: number; y: number } | null = null;
  let triggered = false;

  return {
    handleTouchStart(e: { touches: { clientX: number; clientY: number }[] }) {
      const touch = e.touches[0];
      startPos = { x: touch.clientX, y: touch.clientY };
      triggered = false;
      timer = setTimeout(() => {
        triggered = true;
        onLongPress(startPos!.x, startPos!.y);
      }, delay);
    },

    handleTouchMove(e: { touches: { clientX: number; clientY: number }[] }) {
      if (!startPos || timer === null) return;
      const touch = e.touches[0];
      const dx = touch.clientX - startPos.x;
      const dy = touch.clientY - startPos.y;
      if (Math.sqrt(dx * dx + dy * dy) > cancelDistance) {
        clearTimeout(timer);
        timer = null;
      }
    },

    handleTouchEnd(e?: { preventDefault?: () => void }) {
      if (timer !== null) {
        clearTimeout(timer);
        timer = null;
      }
      if (triggered) {
        e?.preventDefault?.();
        triggered = false;
      }
      startPos = null;
    },

    get longPressTriggered() {
      return triggered;
    },
  };
}
