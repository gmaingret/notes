/**
 * Imperative date picker DOM helper.
 * Framework-free (no React imports) — creates a hidden <input type="date"> element,
 * programmatically opens the native date picker, and calls the callback with the
 * selected date string.
 */

/**
 * Opens a native date picker by creating a hidden input element.
 * Calls `onDate` with the selected ISO date string (YYYY-MM-DD) when the user picks a date.
 * Cleans up the input element on change or blur.
 */
export function triggerDatePicker(onDate: (date: string) => void): void {
  const input = document.createElement('input');
  input.type = 'date';
  // On mobile/PWA, the input must be visible and interactable for the native
  // picker to open. Use a small off-screen-ish but technically visible element.
  input.style.cssText = 'position:fixed;top:50%;left:50%;opacity:0.01;width:1px;height:1px;z-index:9999;';
  document.body.appendChild(input);

  function cleanup() {
    if (document.body.contains(input)) document.body.removeChild(input);
  }

  input.addEventListener('change', () => {
    onDate(input.value);
    cleanup();
  });
  input.addEventListener('blur', () => {
    // Delay cleanup so 'change' can fire first on some browsers
    setTimeout(cleanup, 200);
  });

  // Focus then click — required sequence for mobile browsers to honour the user gesture
  input.focus();
  input.click();
  // Fallback: some mobile browsers need showPicker() (Chrome 99+, Safari 16+)
  try { input.showPicker(); } catch { /* not supported — click() is the fallback */ }
}
