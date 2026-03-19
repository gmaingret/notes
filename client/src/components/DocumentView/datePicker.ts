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
  input.style.cssText = 'position:fixed;opacity:0;pointer-events:none;top:0;left:0;';
  document.body.appendChild(input);
  input.addEventListener('change', () => {
    onDate(input.value);
    document.body.removeChild(input);
  });
  input.addEventListener('blur', () => {
    if (document.body.contains(input)) document.body.removeChild(input);
  });
  input.click();
}
